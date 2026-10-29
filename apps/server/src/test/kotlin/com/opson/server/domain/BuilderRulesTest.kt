package com.opson.server.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BuilderRulesTest {

    private val now = Instant.parse("2026-10-29T12:00:00Z")
    private val user = UserId(UUID.fromString("66666666-6666-6666-6666-666666666666"))

    private fun member(
        status: BuilderStatus = BuilderStatus.Active,
        trustedSince: Instant? = null,
        hold: Boolean = false,
        joinedAt: Instant = now - 90.days,
    ) = BuilderMembership(user, status, joinedAt, BuilderRules.TERMS_VERSION, false, trustedSince, hold)

    /** [count] acceptances, the newest [newest] ago and one a day before that. */
    private fun accepted(count: Int, newest: kotlin.time.Duration) = (0 until count).map { now - newest - it.days }

    @Test
    fun aNewBuilderHasProForTheirFirstMonthWhateverTheyHaveDone() {
        val joined = now - 3.days

        assertEquals(joined + 30.days, BuilderRules.proUntil(joined, emptyList(), now))
    }

    @Test
    fun tenAcceptedInTheLastMonthKeepsProThirtyDaysAway() {
        val until = BuilderRules.proUntil(now - 200.days, accepted(10, newest = 1.days), now)

        assertEquals(now + 30.days, until)
    }

    @Test
    fun afterDroppingBelowTenProStopsMovingAndRunsOutThirtyDaysLater() {
        // The tenth newest was 40 days ago: the window last held ten 10 days ago, so Pro ends in 20.
        val list = accepted(9, newest = 1.days) + (now - 40.days)
        val until = BuilderRules.proUntil(now - 200.days, list.sortedDescending(), now)

        assertEquals(now - 40.days + 60.days, until)
    }

    @Test
    fun proRunsOutForSomeoneWhoStoppedLongAgo() {
        val until = BuilderRules.proUntil(now - 400.days, accepted(10, newest = 100.days), now)

        assertEquals(true, until < now)
    }

    @Test
    fun guestsAndBrandNewAccountsWaitButAnApprovedFoodLetsAnyoneIn() {
        val fresh = ContributionHistory(accountCreatedAt = now - 2.days)

        assertEquals(JoinBlock.Guest, BuilderRules.joinBlock(isAnonymous = true, membership = null, history = fresh, now = now))
        val tooNew = assertIs<JoinBlock.TooNew>(BuilderRules.joinBlock(false, null, fresh, now))
        assertEquals(now + 5.days, tooNew.availableAt)
        assertNull(BuilderRules.joinBlock(false, null, fresh.copy(approvedSubmissions = 1), now))
        assertNull(BuilderRules.joinBlock(false, null, ContributionHistory(accountCreatedAt = now - 8.days), now))
    }

    @Test
    fun someoneRemovedCanNotRejoinButSomeoneWhoLeftCan() {
        val fresh = ContributionHistory(accountCreatedAt = now - 1.hours)

        assertEquals(JoinBlock.Removed, BuilderRules.joinBlock(false, member(BuilderStatus.Removed), fresh, now))
        assertNull(BuilderRules.joinBlock(false, member(BuilderStatus.Left), fresh, now))
    }

    private fun record(agreed: Int, of: Int = 50, total: Int = 60) = ContributionHistory(
        accountCreatedAt = now - 90.days,
        reviewerAgreed = List(agreed) { true } + List(of - agreed) { false },
        reviewedTotal = total,
        decided30d = 20,
    )

    @Test
    fun trustNeedsFiftyReviewedAndNinetySevenPercentAgreement() {
        assertEquals(TrustLevel.Trusted, BuilderRules.trust(Role.Builder, member(), record(agreed = 49), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, member(), record(agreed = 47), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, member(), record(agreed = 40, of = 40, total = 40), now))
    }

    @Test
    fun aTrustedBuilderStaysTrustedDownToNinetyFivePercentThenDropsBack() {
        val trusted = member(trustedSince = now - 10.days)

        assertEquals(TrustLevel.Trusted, BuilderRules.trust(Role.Builder, trusted, record(agreed = 48), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, trusted, record(agreed = 47), now))
    }

    @Test
    fun failedAuditsAFakeAHoldOrAYoungAccountKeepSomeoneAtBuilder() {
        val trusted = member(trustedSince = now - 10.days)
        val good = record(agreed = 50)

        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, trusted, good.copy(audits30d = 10, auditsFailed30d = 1), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, trusted, good.copy(fakeOrCopied = 1), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, member(hold = true), good, now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, member(), good.copy(accountCreatedAt = now - 10.days), now))
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, member(), good.copy(rejected30d = 2), now))
    }

    @Test
    fun membersWithoutTheRoleAreAnyoneAndTheRemovedAreRemoved() {
        assertEquals(TrustLevel.Anyone, BuilderRules.trust(Role.Member, null, record(agreed = 50), now))
        assertEquals(TrustLevel.Removed, BuilderRules.trust(Role.Member, member(BuilderStatus.Removed), record(agreed = 50), now))
        // A builder role an admin set by hand, with no membership row, is a Builder.
        assertEquals(TrustLevel.Builder, BuilderRules.trust(Role.Builder, null, record(agreed = 50), now))
    }

    @Test
    fun thePolicyDefaultsToShadowAndReadsItsConfig() {
        assertEquals(BuilderPolicy(), BuilderPolicy.from(Json.parseToJsonElement("{}").jsonObject))
        val set = BuilderPolicy.from(Json.parseToJsonElement("""{"builders":{"autoApprove":"on","capacity":25,"auditRate":0.2}}""").jsonObject)
        assertEquals(BuilderPolicy(AutoApproveMode.On, 25, 0.2), set)
        val bad = BuilderPolicy.from(Json.parseToJsonElement("""{"builders":{"autoApprove":"yes","capacity":-1,"auditRate":3}}""").jsonObject)
        assertEquals(BuilderPolicy(), bad)
    }

    @Test
    fun aLapsedBuilderLosesTheRolesBundlesButKeepsEveryoneAndGrants() {
        val lapsed = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Builder, emptyList(), RoleWindow.LAPSED)
        val until = now + 12.days
        val active = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Builder, emptyList(), RoleWindow(true, until))

        assertEquals(setOf(Entitlement.PhotoEstimate), lapsed.granted.keys)
        assertEquals(Entitlement.entries.toSet(), active.granted.keys)
        assertEquals(
            EntitlementSource.FromRole(Role.Builder, "pro", until),
            active.granted.getValue(Entitlement.RecipeImport).single(),
        )
    }
}
