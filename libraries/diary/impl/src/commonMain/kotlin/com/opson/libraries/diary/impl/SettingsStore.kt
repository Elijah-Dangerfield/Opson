package com.opson.libraries.diary.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsJson
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.OutboxDao
import com.opson.libraries.diary.impl.wire.SettingsRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.opson.storage.db.DatabaseTransactor
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.max
import kotlin.time.Clock

/** What is on disk: the settings plus what the store needs to know about whose they are. */
@Serializable
data class StoredSettings(
    val settings: Settings = Settings(),
    /** The user whose first pull has landed into these settings. Null until then. */
    val ownerUserId: String? = null,
    /** Changed while not yet known; pushed only if the server turns out to have none. */
    val dirtyWhileUnknown: Boolean = false,
    /** `clientUpdatedAt` of the newest copy seen, local or pulled. */
    val clientUpdatedAtEpochMs: Long? = null,
)

/** The outbox key of the one settings row. */
internal const val SETTINGS_ENTITY_ID = "settings"

/**
 * Owns the persisted [Settings] and its outbox mirror. Separate from
 * [SettingsRepositoryImpl] so the syncer can apply pulled settings without
 * depending on the repository, which depends on the syncer.
 *
 * **Whose settings these are.** After a user change the settings are the
 * defaults (All numbers), not the user's. [isKnown] stays false until that
 * user's first pull lands, and while it is false a local change is kept on the
 * device but not queued: queueing it would push a whole default Settings object
 * over the user's real one on the server, with a newer timestamp, and the
 * user's mode would be lost. If the pull brings no settings (a new account),
 * the local copy is queued then.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class SettingsStore(
    cacheFactory: CacheFactory,
    private val outbox: OutboxDao,
    private val transactor: DatabaseTransactor,
    private val clock: Clock,
    private val activeUser: ActiveUserSource,
    appScope: AppCoroutineScope,
) : UserScopedClearer {

    private val logger = KLog.withTag("DiarySettings")
    private val mutex = Mutex()

    private val cache: Cache<StoredSettings> = cacheFactory.persistent(
        name = "diary_settings",
        serializer = versionedJsonSerializer(json = SettingsJson, defaultValue = { StoredSettings() }),
    )

    private val stored: StateFlow<StoredSettings?> = cache.updates.stateIn(appScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<Settings> =
        stored.map { it?.settings ?: Settings() }.stateIn(appScope, SharingStarted.Eagerly, Settings())

    val isKnown: StateFlow<Boolean> =
        combine(stored, activeUser.user) { record, user -> record != null && record.isKnownFor(user) }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    /** Whether the stored settings are [userId]'s (null: nobody signed in), read from the record so it can't lag auth. */
    suspend fun isKnownFor(userId: String?): Boolean =
        cache.get().isKnownFor(userId?.let(ActiveUser::SignedIn) ?: ActiveUser.SignedOut)

    data class UpdateResult(val settings: Settings, val queued: Boolean)

    suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): UpdateResult =
        mutex.withLock {
            val record = cache.get()
            val before = record.settings
            val nowMs = clock.now().toEpochMilliseconds()
            val next = transform(before).let { if (stampNumbersChange) it.stampedAgainst(before, nowMs) else it }
            if (next == before) return UpdateResult(before, queued = false)

            if (!record.isKnownFor(activeUser.user.value)) {
                cache.set(record.copy(settings = next, dirtyWhileUnknown = true))
                return UpdateResult(next, queued = false)
            }

            val updatedAt = stamp(nowMs, record.clientUpdatedAtEpochMs)
            val base = record.clientUpdatedAtEpochMs?.let { settingsPayload(before, it) }
            // Outbox first: a crash after it still reaches the server and the
            // pull brings the change back; a crash after the cache write alone
            // would leave a change that never syncs.
            transactor.inTransaction {
                // Settings that never synced go up whole. After that only the keys this phone
                // changed go up, so the server's shallow merge keeps what another phone set in
                // the rest (No numbers picked there while this phone swaps its main number).
                val payload = if (base == null) {
                    settingsPayload(next, updatedAt)
                } else {
                    val waiting = outbox.pendingFor(SyncEntity.Settings.wire, SETTINGS_ENTITY_ID)?.payload
                    settingsPatchPayload(before, next, updatedAt, waiting)
                }
                outbox.enqueue(SyncEntity.Settings, SETTINGS_ENTITY_ID, payload, base, nowMs)
            }
            cache.set(record.copy(settings = next, dirtyWhileUnknown = false, clientUpdatedAtEpochMs = updatedAt))
            UpdateResult(next, queued = true)
        }

    /** A settings row from the server. The merge rule: a change still waiting locally wins. */
    suspend fun applyPulled(row: SettingsRowDto) = mutex.withLock {
        if (row.deleted) return@withLock
        if (outbox.pendingFor(SyncEntity.Settings.wire, SETTINGS_ENTITY_ID) != null) return@withLock
        val pulled = Catching { SettingsJson.decodeFromJsonElement(Settings.serializer(), row.settings) }
            .onFailure { logger.w(it) { "Pulled settings could not be read; keeping the local copy" } }
            .getOrNull() ?: return@withLock
        val record = cache.get()
        cache.set(
            record.copy(
                settings = pulled,
                dirtyWhileUnknown = false,
                clientUpdatedAtEpochMs = max(record.clientUpdatedAtEpochMs ?: 0, row.clientUpdatedAtEpochMs),
            ),
        )
    }

    /**
     * [userId]'s pull has finished. Returns true when a local change made while
     * the settings were unknown was queued and wants a sync.
     */
    suspend fun markPulled(userId: String, pulledSettings: Boolean): Boolean = mutex.withLock {
        val record = cache.get()
        if (record.ownerUserId == userId) return false
        val queue = record.dirtyWhileUnknown && !pulledSettings
        var updatedAt = record.clientUpdatedAtEpochMs
        if (queue) {
            val nowMs = clock.now().toEpochMilliseconds()
            updatedAt = stamp(nowMs, record.clientUpdatedAtEpochMs)
            transactor.inTransaction {
                outbox.enqueue(
                    SyncEntity.Settings,
                    SETTINGS_ENTITY_ID,
                    settingsPayload(record.settings, updatedAt),
                    base = null,
                    nowEpochMs = nowMs,
                )
            }
        }
        cache.set(record.copy(ownerUserId = userId, dirtyWhileUnknown = false, clientUpdatedAtEpochMs = updatedAt))
        queue
    }

    /**
     * The server refused a settings change. Restores the copy from before it,
     * when there was one; with no earlier copy the local settings stay, since
     * resetting someone's mode to the defaults would show them more numbers.
     */
    suspend fun revert(basePayload: String?) = mutex.withLock {
        val base = basePayload ?: return@withLock
        val row = Catching { WireJson.decodeFromString(SettingsRowDto.serializer(), base) }.getOrNull() ?: return@withLock
        val settings = Catching { SettingsJson.decodeFromJsonElement(Settings.serializer(), row.settings) }
            .getOrNull() ?: return@withLock
        cache.set(cache.get().copy(settings = settings))
    }

    override suspend fun clear(previousUserId: String) = mutex.withLock {
        cache.set(StoredSettings())
    }

    private fun StoredSettings.isKnownFor(user: ActiveUser): Boolean = when (user) {
        // Cold boot before auth answers: trusted only when a pull ever landed in them. A copy no
        // pull reached is the defaults (All numbers) of someone whose first sync never finished,
        // and showing it until auth answers would flash their numbers.
        ActiveUser.Unresolved -> ownerUserId != null
        // Nobody signed in: what is stored is all there is.
        ActiveUser.SignedOut -> true
        is ActiveUser.SignedIn -> ownerUserId == user.userId
    }
}

/**
 * `changedAt` moves when the preset, an override or the pause setting changed,
 * and at no other time; a transform cannot move it on its own.
 */
internal fun Settings.stampedAgainst(before: Settings, nowEpochMs: Long): Settings {
    val changed = numbers.copy(changedAt = null) != before.numbers.copy(changedAt = null)
    val changedAt = if (changed) kotlin.time.Instant.fromEpochMilliseconds(nowEpochMs) else before.numbers.changedAt
    return copy(numbers = numbers.copy(changedAt = changedAt))
}

/**
 * The settings row as the server stores it. `preset` is repeated at the top
 * level because it is the one key the server reads (`settings->>'preset'`, in
 * the aggregate `preset_distribution` view); in [Settings] it sits under
 * `numbers`.
 */
internal fun settingsPayload(settings: Settings, updatedAtEpochMs: Long): String {
    val encoded = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject
    val wire = JsonObject(encoded + (PRESET_KEY to JsonPrimitive(settings.numbers.preset.key)))
    return WireJson.encodeToString(
        SettingsRowDto.serializer(),
        SettingsRowDto(
            settings = wire,
            changedAtEpochMs = settings.numbers.changedAt?.toEpochMilliseconds(),
            clientUpdatedAtEpochMs = updatedAtEpochMs,
        ),
    )
}

/**
 * A settings row holding only the top-level keys that differ between [before] and [next], on top
 * of the keys a change still [waiting] in the outbox carries, since a newer change replaces that
 * row there. A key [next] no longer has goes up as null; leaving it out would keep the server's
 * value. `preset` travels with `numbers`, which it mirrors: sent alone, a phone that never saw
 * the other phone's pick would put its stale preset back at the top level.
 */
internal fun settingsPatchPayload(before: Settings, next: Settings, updatedAtEpochMs: Long, waiting: String?): String {
    val old = SettingsJson.encodeToJsonElement(Settings.serializer(), before).jsonObject
    val new = SettingsJson.encodeToJsonElement(Settings.serializer(), next).jsonObject
    val changed = new.filter { (key, value) -> old[key] != value } + (old.keys - new.keys).associateWith { JsonNull }
    val pending = waiting
        ?.let { Catching { WireJson.decodeFromString(SettingsRowDto.serializer(), it) }.getOrNull() }
        ?.settings
        .orEmpty()
    val patch = pending + changed
    val withPreset = if (NUMBERS_KEY in patch) patch + (PRESET_KEY to JsonPrimitive(next.numbers.preset.key)) else patch
    return WireJson.encodeToString(
        SettingsRowDto.serializer(),
        SettingsRowDto(
            settings = JsonObject(withPreset),
            changedAtEpochMs = next.numbers.changedAt?.toEpochMilliseconds(),
            clientUpdatedAtEpochMs = updatedAtEpochMs,
        ),
    )
}

private const val NUMBERS_KEY = "numbers"
private const val PRESET_KEY = "preset"

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SettingsRepository::class)
// AutoInit so the stored copy is read at boot: the first screen asks for it.
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class SettingsRepositoryImpl(
    private val store: SettingsStore,
    private val scheduler: DiarySyncScheduler,
) : SettingsRepository, AutoInit {
    override val settings: StateFlow<Settings> get() = store.settings

    override val isKnown: StateFlow<Boolean> get() = store.isKnown

    override suspend fun isKnownFor(userId: String?): Boolean = store.isKnownFor(userId)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
        val result = store.update(stampNumbersChange, transform)
        if (result.queued) scheduler.requestSync()
        return result.settings
    }
}
