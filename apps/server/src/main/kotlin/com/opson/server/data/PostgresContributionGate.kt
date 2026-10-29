package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.boolArg
import com.opson.server.db.execute
import com.opson.server.db.instantArg
import com.opson.server.db.queryRows
import com.opson.server.db.textArg
import com.opson.server.db.uuidArg
import com.opson.server.di.ServerScope
import com.opson.server.domain.ContributionGate
import com.opson.server.domain.ContributionSignals
import com.opson.server.domain.ContributionVerdict
import com.opson.server.domain.DeviceIntegrity
import com.opson.server.domain.DeviceVerdict
import com.opson.server.domain.RestrictedBy
import com.opson.server.domain.UserId
import kotlinx.coroutines.CancellationException
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The contribution gate over `moderation_actions`, `device_sightings` and `device_markers` (V29),
 * plus DeviceCheck or Play Integrity when they are configured.
 *
 * In order: the account's own standing (any active ban, suspension or restriction); the install
 * key, recorded as a sighting and checked against the markers (a restriction made with "mark
 * devices" also marks a device it is first seen on); then, with a token and a verifier, the
 * remote bit. A pending marker is written to the bit while the device is talking to us, and a
 * cleared one is reset, which is the reset path Apple's licence requires. A bit set with no
 * marker here (a reinstall that lost the install key) becomes a marker, so it shows in the admin
 * console and can be cleared there.
 *
 * Fails open: a verifier that can't be reached, or a token that doesn't verify, is counted and
 * never blocks. Only the account's standing and a marker do. Nothing about the device is kept
 * but the keyed hash of the install key; the token is used and dropped.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
@OptIn(ExperimentalTime::class)
class PostgresContributionGate(
    private val database: Database,
    private val integrity: DeviceIntegrity,
    private val clock: Clock,
) : ContributionGate {

    private val logger = LoggerFactory.getLogger(PostgresContributionGate::class.java)

    override suspend fun check(userId: UserId, signals: ContributionSignals): ContributionVerdict {
        val platform = platformOf(signals.platform)
        val deviceKey = signals.deviceKey?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_DEVICE_KEY }
        val local = database.transaction { checkLocally(userId, platform, deviceKey) }

        val verifier = integrity.forPlatform(platform)
        val token = signals.integrityToken?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN }
        var remoteMarked = false
        if (verifier != null && token != null) {
            val verdict = try {
                verifier.read(token, signals.requestHash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("device integrity read failed signal={}", verifier.signal, e)
                DeviceVerdict.Failed
            }
            val outcome = when (verdict) {
                is DeviceVerdict.Read -> {
                    remoteMarked = settleRemote(userId, platform, local, verdict, writeMarked = { marked -> write(verifier::writeMarked, token, marked) })
                    when {
                        remoteMarked -> "marked"
                        !verdict.trusted -> "flagged"
                        else -> "ok"
                    }
                }
                DeviceVerdict.Invalid -> "invalid"
                DeviceVerdict.Failed -> "error"
            }
            ledger(platform, verifier.signal, outcome, blocked = remoteMarked || local.deviceMarked || local.accountRestricted)
        }

        if (local.deviceMarked) ledger(platform, "install_key", "marked", blocked = true)
        if (local.accountRestricted) ledger(platform, "account", "restricted", blocked = true)
        return when {
            local.accountRestricted -> ContributionVerdict.Restricted(RestrictedBy.Account)
            local.deviceMarked || remoteMarked -> ContributionVerdict.Restricted(RestrictedBy.Device)
            else -> ContributionVerdict.Allowed
        }
    }

    override suspend fun mayReceiveFreePro(userId: UserId): Boolean = database.transaction {
        val id = uuidArg(userId.value)
        queryRows(
            """
            SELECT
                EXISTS (SELECT 1 FROM moderation_actions WHERE user_id = ? AND lifted_at IS NULL AND (until IS NULL OR until > ?))
                OR EXISTS (
                    SELECT 1 FROM device_markers m
                    WHERE m.cleared_at IS NULL AND (
                        m.user_id = ?
                        OR m.device_hash IN (SELECT device_hash FROM device_sightings WHERE user_id = ?)
                        OR m.device_hash IN (SELECT public.device_key_hash(install_id) FROM user_installs WHERE user_id = ? AND install_id <> '')
                    )
                ) AS barred
            """.trimIndent(),
            listOf(id, instantArg(clock.now()), id, id, id),
        ) { !it.getBoolean("barred") }.single()
    }

    /** What the database knows, in one transaction: standing, the sighting, markers. */
    private class Local(
        val accountRestricted: Boolean,
        val deviceHash: String?,
        val deviceMarked: Boolean,
        /** The active marker on this device still waits for its remote bit. */
        val markerPending: Boolean,
        /** A cleared marker on this device, or on this account, still has a remote bit to reset. */
        val clearPending: Boolean,
    )

    private fun checkLocally(userId: UserId, platform: String, deviceKey: String?): Local {
        val now = clock.now()
        val id = uuidArg(userId.value)
        val standing = queryRows(
            "SELECT id, mark_devices FROM moderation_actions WHERE user_id = ? AND lifted_at IS NULL AND (until IS NULL OR until > ?)",
            listOf(id, instantArg(now)),
        ) { rs -> rs.getObject("id", UUID::class.java) to rs.getBoolean("mark_devices") }

        val hash = deviceKey?.let { key ->
            queryRows(
                """
                INSERT INTO device_sightings (user_id, device_hash, platform, first_seen_at, last_seen_at)
                VALUES (?, public.device_key_hash(?), ?, ?, ?)
                ON CONFLICT (user_id, device_hash) DO UPDATE SET last_seen_at = excluded.last_seen_at
                RETURNING device_hash
                """.trimIndent(),
                listOf(id, textArg(key), textArg(platform), instantArg(now), instantArg(now)),
            ) { it.getString("device_hash") }.single()
        }

        // A restriction made with "mark devices" follows the account to a device it is first seen on.
        val marking = standing.firstOrNull { it.second }?.first
        if (hash != null && marking != null) {
            execute(
                "INSERT INTO device_markers (id, device_hash, platform, action_id, user_id, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, 'moderation') ON CONFLICT (device_hash) WHERE cleared_at IS NULL DO NOTHING",
                listOf(uuidArg(UUID.randomUUID()), textArg(hash), textArg(platform), uuidArg(marking), id),
            )
        }

        val markers = if (hash == null) {
            emptyList()
        } else {
            queryRows(
                "SELECT remote_state, cleared_at IS NULL AS active FROM device_markers WHERE device_hash = ? AND remote_state <> 'cleared'",
                listOf(textArg(hash)),
            ) { rs -> rs.getString("remote_state") to rs.getBoolean("active") }
        }
        val accountClearPending = queryRows(
            "SELECT 1 FROM device_markers WHERE user_id = ? AND remote_state = 'clear_pending'",
            listOf(id),
        ) { true }.isNotEmpty()
        return Local(
            accountRestricted = standing.isNotEmpty(),
            deviceHash = hash,
            deviceMarked = markers.any { it.second },
            markerPending = markers.any { it.second && it.first == "pending" },
            clearPending = accountClearPending || markers.any { !it.second && it.first == "clear_pending" },
        )
    }

    /** Brings the remote bit and the markers into line. True when the device is marked by its bit. */
    private suspend fun settleRemote(
        userId: UserId,
        platform: String,
        local: Local,
        verdict: DeviceVerdict.Read,
        writeMarked: suspend (Boolean) -> Boolean,
    ): Boolean {
        val now = clock.now()
        when {
            local.clearPending && !local.deviceMarked -> {
                if (!verdict.marked || writeMarked(false)) {
                    database.transaction {
                        execute(
                            "UPDATE device_markers SET remote_state = 'cleared', remote_at = ? " +
                                "WHERE remote_state = 'clear_pending' AND (user_id = ? OR device_hash = ?)",
                            listOf(instantArg(now), uuidArg(userId.value), textArg(local.deviceHash)),
                        )
                    }
                }
                return false
            }
            local.deviceMarked -> {
                if (local.markerPending && (verdict.marked || writeMarked(true))) {
                    database.transaction {
                        execute(
                            "UPDATE device_markers SET remote_state = 'set', remote_at = ? WHERE device_hash = ? AND cleared_at IS NULL",
                            listOf(instantArg(now), textArg(local.deviceHash)),
                        )
                    }
                }
                return verdict.marked
            }
            verdict.marked -> {
                // Marked by a bit we wrote for some earlier install: record it here so it can be seen and cleared.
                if (local.deviceHash != null) {
                    database.transaction {
                        execute(
                            "INSERT INTO device_markers (id, device_hash, platform, user_id, created_by, remote_state, remote_at) " +
                                "VALUES (?, ?, ?, ?, 'device_bit', 'set', ?) ON CONFLICT (device_hash) WHERE cleared_at IS NULL DO NOTHING",
                            listOf(uuidArg(UUID.randomUUID()), textArg(local.deviceHash), textArg(platform), uuidArg(userId.value), instantArg(now)),
                        )
                    }
                }
                return true
            }
            else -> return false
        }
    }

    private suspend fun write(writer: suspend (String, Boolean) -> Boolean, token: String, marked: Boolean): Boolean = try {
        writer(token, marked)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn("device integrity write failed", e)
        false
    }

    private suspend fun ledger(platform: String, signal: String, outcome: String, blocked: Boolean) {
        try {
            database.transaction {
                execute(
                    "INSERT INTO contribution_checks (at, platform, signal, outcome, blocked) VALUES (?, ?, ?, ?, ?)",
                    listOf(instantArg(clock.now()), textArg(platform), textArg(signal), textArg(outcome), boolArg(blocked)),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The ledger feeds a dashboard; a failed write must not fail the contribution.
            logger.warn("contribution check ledger write failed", e)
        }
    }

    private fun platformOf(raw: String): String = when (raw.lowercase()) {
        "ios" -> "ios"
        "android" -> "android"
        else -> "other"
    }

    private companion object {
        /** A UUID is 36; anything far longer isn't an install key. */
        const val MAX_DEVICE_KEY = 128

        /** Play Integrity tokens run to a few KB; DeviceCheck's are about 2 KB of base64. */
        const val MAX_TOKEN = 16_384
    }
}
