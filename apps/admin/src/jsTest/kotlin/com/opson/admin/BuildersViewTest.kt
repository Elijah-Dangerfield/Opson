package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class BuildersViewTest {

    private val day = 86_400_000L

    private fun application(
        account: ApplicantSummaryDto = ApplicantSummaryDto(),
        shopping: String? = "weekly",
        region: String? = "uk",
        status: String = "pending",
    ) = BuilderApplicationDto(
        id = "a1",
        userId = "0b6f8a2c-0000-0000-0000-000000000000",
        status = status,
        submittedAtEpochMs = 0,
        photoRetention = true,
        shopping = shopping,
        region = region,
        via = "more",
        account = account,
    )

    @Test
    fun theTabCountsOnlyWhatIsWaiting() {
        assertEquals("Builders", buildersTabLabel(null))
        assertEquals("Builders", buildersTabLabel(0))
        assertEquals("Builders (3)", buildersTabLabel(3))
        assertEquals("1 application waiting", pendingHeadline(1))
        assertEquals("No applications waiting", pendingHeadline(0))
    }

    @Test
    fun anApplicantReadsAsTheAccountTheirRecordAndTheirAnswers() {
        val facts = applicantFacts(
            application(
                ApplicantSummaryDto(
                    email = "sam@example.com",
                    accountCreatedAtEpochMs = 0,
                    entries = 120,
                    daysLogged = 30,
                    lastLogDay = "2026-10-28",
                    submissionsApproved = 2,
                    reportsAccepted = 1,
                    reportsRejected = 1,
                    moderationActions = 1,
                    lastModerationReason = "spam",
                    earlierApplications = 1,
                    earlierDeclines = 1,
                ),
            ),
            now = 40 * day,
            date = { "Jan 1" },
        ).toMap()

        assertEquals("sam@example.com, 40 days old (since Jan 1)", facts["Account"])
        assertEquals("120 foods over 30 days, last on 2026-10-28", facts["Logging"])
        assertEquals("2 approved, 0 rejected, 0 waiting", facts["Foods sent"])
        assertEquals("1 accepted, 1 turned down, 0 open", facts["Reports"])
        assertEquals("1, all lifted or ended, last for spam", facts["Moderation"])
        assertEquals("1 earlier, 1 declined", facts["Before"])
        assertEquals("packaged food every week", facts["Shops"])
        assertEquals("United Kingdom", facts["Labels"])
    }

    @Test
    fun anActiveRestrictionAndAnOlderAppSayWhatTheyAre() {
        val facts = applicantFacts(
            application(ApplicantSummaryDto(moderationActions = 2, moderationActive = 1, membership = "left"), shopping = null, region = null),
            now = 0,
        ).toMap()

        assertEquals("1 in force of 2", facts["Moderation"])
        assertEquals("first application, was a Builder (left)", facts["Before"])
        assertEquals("not asked (an older app)", facts["Shops"])
        assertEquals("nothing logged", facts["Logging"])
    }

    @Test
    fun aNameFallsBackToTheEmailThenTheId() {
        assertEquals("Sam", applicantName(application(ApplicantSummaryDto(displayName = "Sam", email = "s@x"))))
        assertEquals("s@x", applicantName(application(ApplicantSummaryDto(displayName = " ", email = "s@x"))))
        assertEquals("0b6f8a2c", applicantName(application()))
    }

    @Test
    fun aDecisionSaysWhoWhenAndTheirNote() {
        val declined = application(status = "declined").copy(decidedBy = "elijah", decidedAtEpochMs = 1, note = "Not yet")
        assertEquals("declined by elijah on Jan 1 “Not yet”", decisionLine(declined) { "Jan 1" })
    }
}
