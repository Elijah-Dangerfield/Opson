package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.domain.BanReason
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.ContributionSignals
import com.opson.server.domain.ContributionVerdict
import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.DeviceIntegrity
import com.opson.server.domain.DeviceIntegrityVerifier
import com.opson.server.domain.DeviceVerdict
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.VerificationStatus
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerificationDecision
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.GrantEffect
import com.opson.server.domain.GrantKind
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelReading
import com.opson.server.domain.ModerationKind
import com.opson.server.domain.ModerationOutcome
import com.opson.server.domain.ModerationReason
import com.opson.server.domain.ModerationService
import com.opson.server.domain.NewFoodReport
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NewGrant
import com.opson.server.domain.NewModerationAction
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.ReportOutcome
import com.opson.server.domain.ReportReason
import com.opson.server.domain.ReportStatus
import com.opson.server.domain.RestrictedBy
import com.opson.server.domain.ReviewOutcome
import com.opson.server.domain.Role
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmitOutcome
import com.opson.server.domain.SuggestedValues
import com.opson.server.domain.UserId
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestImages
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** V30 end to end on real Postgres: bans, restrictions, device markers, provenance and rollback. */
@OptIn(ExperimentalTime::class)
class PostgresModerationTest : DatabaseTest() {

    @After
    fun clean() {
        database.blockingTransaction {
            listOf(
                "contribution_checks", "device_markers", "device_sightings", "moderation_events", "moderation_actions",
                "food_provenance", "food_reports", "food_corrections", "food_submissions", "food_verifications", "user_installs",
                "entitlement_grants", "user_roles", "access_audit",
            ).forEach { TransactionManager.current().exec("DELETE FROM $it") }
        }
        DiaryFixtures.cleanAll(database)
    }

    private var now = Instant.parse("2026-10-29T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val bits = FakeDeviceIntegrity()
    private val actions by lazy { PostgresModerationActions(database, clock) }
    private val bans by lazy { PostgresModerationRepository(database, clock) }
    private val gate by lazy { PostgresContributionGate(database, bits, clock) }
    private val submissions by lazy { PostgresFoodSubmissionRepository(database, clock) }
    private val reports by lazy { PostgresFoodReportRepository(database, clock) }
    private val roles by lazy { PostgresUserRoleRepository(database, clock) }
    private val grants by lazy { PostgresEntitlementGrantRepository(database, clock) }
    private val service by lazy {
        ModerationService(
            actions, roles, grants,
            setOf(SubmissionReverter(database, clock), ReportReverter(database, reports, clock), VerificationReverter(database, reports, clock)),
            clock,
        )
    }

    private fun action(user: UserId, kind: ModerationKind, until: Instant? = null, markDevices: Boolean = false) =
        NewModerationAction(user, kind, ModerationReason.FakeData, "made-up labels", until, markDevices)

    private fun ios(deviceKey: String?, token: String? = null) = ContributionSignals("ios", deviceKey, token, requestHash = null)

    @Test
    fun aBanBlocksEveryCallUntilItIsLifted() = runTest {
        val user = seedAuthUser()
        val ban = assertIs<ModerationOutcome.Done>(actions.act(action(user, ModerationKind.Ban), "elijah")).action

        val status = bans.banStatusFor(user)
        assertEquals(BanReason.Banned, status?.reason)
        assertNull(status?.until)
        assertIs<ContributionVerdict.Restricted>(gate.check(user, ios("key")))

        assertIs<ModerationOutcome.Done>(actions.lift(ban.id, "elijah", "mistake"))
        assertNull(bans.banStatusFor(user))
        assertEquals(ModerationOutcome.AlreadyLifted, actions.lift(ban.id, "elijah", null))
        assertEquals(listOf("lift", "ban"), actions.events(user, 10).map { it.action })
    }

    @Test
    fun aSuspensionReadsAsSuspendedAndEndsOnItsOwn() = runTest {
        val user = seedAuthUser()
        actions.act(action(user, ModerationKind.Suspension, until = now + 1.days), "elijah")

        val status = bans.banStatusFor(user)
        assertEquals(BanReason.Suspended, status?.reason)
        assertEquals(now + 1.days, status?.until)

        now += 2.days
        assertNull(bans.banStatusFor(user))
    }

    @Test
    fun aRestrictionIsNotABanButStopsContributingAndFreePro() = runTest {
        val user = seedAuthUser()
        assertTrue(gate.mayReceiveFreePro(user))
        val restriction = assertIs<ModerationOutcome.Done>(actions.act(action(user, ModerationKind.Restriction), "elijah")).action

        assertNull(bans.banStatusFor(user))
        assertEquals(ContributionVerdict.Restricted(RestrictedBy.Account), gate.check(user, ios("key")))
        assertFalse(gate.mayReceiveFreePro(user))

        actions.lift(restriction.id, "elijah", null)
        assertEquals(ContributionVerdict.Allowed, gate.check(user, ios("key")))
        assertTrue(gate.mayReceiveFreePro(user))
    }

    @Test
    fun markedDevicesFollowTheInstallKeyToANewAccountUntilCleared() = runTest {
        val first = seedAuthUser()
        assertEquals(ContributionVerdict.Allowed, gate.check(first, ios("keychain-1")))
        database.blockingTransaction {
            TransactionManager.current().exec(
                "INSERT INTO user_installs (user_id, install_id, platform, app_version, build_number) VALUES ('${first.value}', 'install-9', 'android', '0.2.0', 1)",
            )
        }
        actions.act(action(first, ModerationKind.Ban, markDevices = true), "elijah")

        val second = seedAuthUser()
        assertEquals(ContributionVerdict.Restricted(RestrictedBy.Device), gate.check(second, ios("keychain-1")))
        assertFalse(gate.mayReceiveFreePro(second))
        val third = seedAuthUser()
        assertEquals(
            ContributionVerdict.Restricted(RestrictedBy.Device),
            gate.check(third, ContributionSignals("android", "install-9", null, null)),
        )

        val markers = actions.summary(first).markers
        assertEquals(2, markers.size)
        markers.forEach { assertTrue(actions.clearMarker(it.id, "elijah", "sold the phone")) }
        assertEquals(ContributionVerdict.Allowed, gate.check(second, ios("keychain-1")))
        assertTrue(gate.mayReceiveFreePro(second))
    }

    @Test
    fun theDeviceBitIsWrittenWhileTheDeviceTalksToUsAndResetWhenCleared() = runTest {
        val first = seedAuthUser()
        gate.check(first, ios("keychain-1", token = "phone-a"))
        actions.act(action(first, ModerationKind.Restriction, markDevices = true), "elijah")

        // Restricted, and its next contribution writes Apple's bit.
        assertEquals(ContributionVerdict.Restricted(RestrictedBy.Account), gate.check(first, ios("keychain-1", token = "phone-a")))
        assertEquals(true, bits.marked["phone-a"])
        assertEquals(listOf("set"), actions.summary(first).markers.map { it.remoteState })

        // A reinstall loses the keychain item; the bit still recognises the phone and records it.
        val second = seedAuthUser()
        assertEquals(ContributionVerdict.Restricted(RestrictedBy.Device), gate.check(second, ios("keychain-2", token = "phone-a")))

        // Cleared in the console: the next contribution from the phone resets the bit and goes through.
        val all = actions.summary(first).markers + actions.summary(second).markers
        all.filter { it.clearedAt == null }.forEach { actions.clearMarker(it.id, "elijah", "appeal upheld") }
        assertEquals(ContributionVerdict.Allowed, gate.check(second, ios("keychain-2", token = "phone-a")))
        assertEquals(false, bits.marked["phone-a"])
    }

    @Test
    fun aTokenThatDoesNotVerifyIsCountedAndNeverBlocks() = runTest {
        val user = seedAuthUser()
        assertEquals(ContributionVerdict.Allowed, gate.check(user, ios("key", token = "forged")))
        val outcomes = database.blockingTransaction {
            val out = mutableListOf<String>()
            TransactionManager.current().exec("SELECT signal || ':' || outcome FROM contribution_checks") { rs -> while (rs.next()) out += rs.getString(1) }
            out
        }
        assertEquals(listOf("devicecheck:invalid"), outcomes)
    }

    @Test
    fun rollbackUnpublishesApprovedFoodsAndRejectsPendingOnes_evenAfterTheAccountIsGone() = runTest {
        val user = seedAuthUser()
        val approved = submit(user, "012345678905")
        assertIs<ReviewOutcome.Done>(submissions.approve(approved.second, null, "elijah"))
        val pending = submit(user, "036000291452")
        assertEquals(1, actions.summary(user).accepted)

        val results = service.rollback(user, "elijah", "fake_data", actionId = null).associateBy { it.kind }
        assertEquals(1, results.getValue("submission").undone)
        assertEquals(1, results.getValue("submission").closed)
        assertEquals("rejected", reviewStatus(approved.first))
        assertEquals("rejected", reviewStatus(pending.first))
        assertFalse(submissions.isVisible(approved.first, seedAuthUser()))

        // Another person's food, approved, then they delete the account: the provenance stays.
        val other = seedAuthUser()
        val theirs = submit(other, "012000161155")
        submissions.approve(theirs.second, null, "elijah")
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM auth.users WHERE id = '${other.value}'") }
        assertEquals(1, service.rollback(other, "elijah", "fake_data", null).first { it.kind == "submission" }.undone)
        assertEquals("rejected", reviewStatus(theirs.first))
    }

    @Test
    fun rollingBackReportsRevertsTheCorrectionsTheyLedTo() = runTest {
        val food = seedFood()
        val user = seedAuthUser()
        val accepted = report(food, user)
        val before = FoodSnapshot.of(checkNotNull(PostgresFoodCatalogRepository(database, clock).findById(food)))
        assertIs<CorrectionOutcome.Applied>(reports.applyCorrection(food, CorrectionValues(kcal = 99.0), before, listOf(accepted), "elijah"))
        val open = report(food, user)

        val result = service.rollback(user, "elijah", "fake_data", null).first { it.kind == "report" }
        assertEquals(1, result.undone)
        assertEquals(1, result.closed)
        assertTrue(reports.liveCorrections(listOf(food)).isEmpty())
        assertEquals(ReportStatus.Rejected, reports.forFood(food).first { it.id == open }.status)
    }

    @Test
    fun rollingBackVerificationsTakesTheVerifiedMarkAndTheCorrectionsBack() = runTest {
        val verifications = PostgresFoodVerificationRepository(database, clock)
        val catalog = PostgresFoodCatalogRepository(database, clock)
        val checked = seedFood()
        val corrected = seedFood()
        val user = seedAuthUser()
        suspend fun verification(food: UUID, outcome: VerificationOutcome, status: VerificationStatus?) =
            FoodVerification(UUID.randomUUID(), food, user, now, outcome, 4, 4, "vision", status = status, snapshot = FoodSnapshot.of(checkNotNull(catalog.findById(food))))
        verifications.record(verification(checked, VerificationOutcome.Confirmed, null))
        val queued = verification(corrected, VerificationOutcome.Unconfirmed, VerificationStatus.Open)
        verifications.record(queued)
        assertIs<VerificationDecision.Accepted>(verifications.accept(queued.id, CorrectionValues(kcal = 99.0), null, "elijah", null))
        val open = verification(checked, VerificationOutcome.Unconfirmed, VerificationStatus.Open)
        verifications.record(open)
        assertTrue(checkNotNull(catalog.findById(checked)).checked)
        assertTrue(checkNotNull(catalog.findById(corrected)).checked)

        val result = service.rollback(user, "elijah", "fake_data", null).first { it.kind == "verification" }
        assertEquals(2, result.undone)
        assertEquals(1, result.closed)
        assertTrue(reports.liveCorrections(listOf(corrected)).isEmpty())
        assertFalse(checkNotNull(catalog.findById(checked)).checked)
        assertFalse(checkNotNull(catalog.findById(corrected)).checked)
        assertEquals(VerificationStatus.Rejected, verifications.get(open.id)?.status)
    }

    @Test
    fun aBanOrRestrictionTakesBuilderPerksButNotPaidOnes_aSuspensionTakesNothing() = runTest {
        val user = seedAuthUser()
        roles.setRole(user, Role.Builder, "elijah", null)
        grants.add(NewGrant(user, GrantKind.Bundle, "pro", GrantEffect.Grant, "builder", null, null), "nightly")
        grants.add(NewGrant(user, GrantKind.Bundle, "pro", GrantEffect.Grant, "comp", null, null), "elijah")

        val suspended = service.act(action(user, ModerationKind.Suspension, until = now + 1.days), "elijah", rollback = false)
        assertEquals(emptyList(), suspended.perksRemoved)

        val restricted = service.act(action(user, ModerationKind.Restriction), "elijah", rollback = false)
        assertEquals(listOf("role:builder", "grant:pro"), restricted.perksRemoved)
        assertEquals(Role.Member, roles.roleOf(user))
        assertEquals(listOf("comp"), grants.active(user, now).map { it.source })
    }

    @Test
    fun photoReuseAndFlagsReachTheBoardsViews() = runTest {
        val first = seedAuthUser()
        val second = seedAuthUser()
        submit(first, "012345678905")
        now += 1.days
        submit(second, "036000291452")

        val reuse = database.blockingTransaction {
            TransactionManager.current().exec("SET LOCAL ROLE grafana_ro")
            val out = mutableListOf<String>()
            TransactionManager.current().exec("SELECT photo || ':' || cross_account FROM support.photo_reuse ORDER BY photo") { rs ->
                while (rs.next()) out += rs.getString(1)
            }
            TransactionManager.current().exec(
                "SELECT flag || ':' || contributions FROM analytics.contribution_flags_daily WHERE flag = 'photo_reused'",
            ) { rs -> while (rs.next()) out += rs.getString(1) }
            TransactionManager.current().exec(
                "SELECT count(*) FROM support.contributors WHERE submitted_total = 1 AND standing = 'ok'",
            ) { rs -> while (rs.next()) out += "contributors:" + rs.getString(1) }
            out
        }
        assertEquals(listOf("label:true", "photo_reused:1", "contributors:2"), reuse)
    }

    private val bar = LabelFacts(
        name = "Oats & honey granola bar",
        brand = "Hillside",
        servingSize = 40.0,
        servingUnit = ServingUnit.Gram,
        servingLabel = "1 bar",
        basis = CarbBasis.Us,
        perServing = mapOf(NutrientKey.Energy to 190.0, NutrientKey.Protein to 4.0, NutrientKey.Carbs to 29.0, NutrientKey.Fat to 7.0),
    )

    /** Returns (food id, submission id). Every submission sends the same two photos. */
    private suspend fun submit(user: UserId, barcode: String): Pair<UUID, UUID> {
        val id = UUID.randomUUID()
        val outcome = submissions.submit(
            NewFoodSubmission(
                id = id,
                userId = user,
                barcode = barcode,
                origin = SubmissionOrigin.Barcode,
                facts = bar.copy(barcode = barcode),
                checks = LabelChecks.check(bar),
                acknowledged = emptyList(),
                parsed = LabelReading(bar, emptyMap(), readable = true),
                model = "test",
                photos = PackPhotos(TestImages.jpeg, ImageType.Jpeg, TestImages.png, ImageType.Png),
            ),
        )
        return assertIs<SubmitOutcome.Created>(outcome).submission.foodId to id
    }

    private fun reviewStatus(food: UUID): String = database.blockingTransaction {
        var status = ""
        TransactionManager.current().exec("SELECT review_status FROM foods WHERE id = '$food'") { rs -> if (rs.next()) status = rs.getString(1) }
        status
    }

    private fun seedFood(): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = "usda_branded"
                it[sourceId] = id.toString()
                it[name] = "Energy Drink"
                it[brand] = "Monster"
                it[kcal100g] = 0f
                it[protein100g] = 0f
                it[carbs100g] = 0.83f
                it[fat100g] = 0f
                it[portions] = """[{"label": "8.0 fl oz (240 ml)", "grams": null}]"""
            }
        }
        return id
    }

    private suspend fun report(food: UUID, user: UserId): UUID {
        val snapshot = FoodSnapshot.of(checkNotNull(PostgresFoodCatalogRepository(database, clock).findById(food)))
        val id = UUID.randomUUID()
        val outcome = reports.report(
            NewFoodReport(id, user, food, ReportReason.WrongCalories, SuggestedValues(kcal = 99.0), null, snapshot, null),
        )
        return assertIs<ReportOutcome.Created>(outcome).report.id
    }
}

/** DeviceCheck as a map of token to bit 0. `forged` never verifies. */
class FakeDeviceIntegrity : DeviceIntegrity {
    val marked = mutableMapOf<String, Boolean>()

    private val verifier = object : DeviceIntegrityVerifier {
        override val signal = "devicecheck"

        override suspend fun read(token: String, requestHash: String?): DeviceVerdict =
            if (token == "forged") DeviceVerdict.Invalid else DeviceVerdict.Read(marked = marked[token] == true, trusted = true)

        override suspend fun writeMarked(token: String, marked: Boolean): Boolean {
            this@FakeDeviceIntegrity.marked[token] = marked
            return true
        }
    }

    override fun forPlatform(platform: String): DeviceIntegrityVerifier? = if (platform == "ios") verifier else null

    override val configured: List<String> = listOf("devicecheck")
}
