package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModerationViewTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    @Test
    fun standingSaysWhatThePersonCanStillDo() {
        assertEquals("good standing", standingSentence(null, now))
        assertEquals(
            "restricted: logs food, can't submit foods, reports or corrections, until lifted",
            standingSentence(StandingDto("restriction", "contributions"), now),
        )
        assertEquals(
            "suspended: every call answers with the blocked screen, 2 more days",
            standingSentence(StandingDto("suspension", "full", untilEpochMs = now + day + 1), now),
        )
        assertEquals(
            "banned: every call answers with the blocked screen, 1 more day",
            standingSentence(StandingDto("ban", "full", untilEpochMs = now + 1), now),
        )
    }

    @Test
    fun theFormBuildsTheRequestOrSaysWhatIsMissing() {
        val ok = assertIs<Built.Ok>(moderateBody("restriction", "fake_data", "  same photo  ", "", markDevices = true, rollback = false, now))
        assertEquals(ModerateBody("restriction", "fake_data", "same photo", null, markDevices = true), ok.body)

        val suspended = assertIs<Built.Ok>(moderateBody("suspension", "spam", "", "7", markDevices = false, rollback = false, now))
        assertEquals(now + 7 * day, suspended.body.untilEpochMs)
        assertNull(suspended.body.note)

        assertIs<Built.Invalid>(moderateBody("suspension", "spam", "", "", markDevices = false, rollback = false, now))
        assertIs<Built.Invalid>(moderateBody("ban", "spam", "", "two", markDevices = false, rollback = false, now))
        assertIs<Built.Invalid>(moderateBody("ban", "spam", "", "0", markDevices = false, rollback = false, now))
    }

    @Test
    fun aBanOrARollbackAsksForTheEnvironmentsName() {
        assertTrue(actionWarning(ModerateBody("ban", "spam"))!!.contains("blocks the app"))
        assertTrue(actionWarning(ModerateBody("restriction", "spam", rollback = true))!!.contains("unpublishes"))
        assertNull(actionWarning(ModerateBody("restriction", "spam")))
    }

    @Test
    fun statusesAndCountsReadPlainly() {
        val action = ModerationActionDto(
            id = "a", kind = "suspension", scope = "full", reason = "spam", createdAtEpochMs = 0, createdBy = "elijah",
            untilEpochMs = now - 1, active = false,
        )
        assertEquals("ended", actionStatus(action, now))
        assertEquals("lifted by sam", actionStatus(action.copy(liftedAtEpochMs = 5, liftedBy = "sam"), now))
        assertEquals("in force", actionStatus(action.copy(untilEpochMs = null), now))
        assertEquals("Restrict contributions", kindLabel("restriction"))
        assertEquals("ban evasion", reasonLabel("ban_evasion"))
        assertEquals("to be written on its next contribution", remoteStateLabel("pending"))
        assertEquals(
            "3 accepted, 1 rolled back, 0 waiting; contributed from 1 device",
            contributionSentence(ContributionSummaryDto(accepted = 3, rolledBack = 1, pending = 0, devices = 1)),
        )
        assertEquals(
            "Rolled back: reports 1 undone, 0 waiting rejected, 1 replaced since (check those foods); submissions 2 undone, 1 waiting rejected.",
            rollbackSentence(listOf(RevertedDto("submission", 2, 1), RevertedDto("report", 1, 0, skipped = 1))),
        )
    }
}
