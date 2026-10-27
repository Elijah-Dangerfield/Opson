package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class UsersViewTest {

    @Test
    fun theGrafanaLinkPicksTheDatabaseOfTheConnectedEnvironment() {
        assertEquals(
            "https://cards.grafana.net/d/opson-user/opson-user-detail?var-env=opson-prod-db&var-user=abc",
            userDetailDashboardUrl("abc", isProd = true),
        )
        assertEquals(
            "https://cards.grafana.net/d/opson-user/opson-user-detail?var-env=opson-db&var-user=abc",
            userDetailDashboardUrl("abc", isProd = false),
        )
    }

    @Test
    fun theSentryLinkSearchesByTheAccountId() {
        assertEquals(
            "https://elijah-dangerfield.sentry.io/issues/?query=user.id%3Aabc&statsPeriod=90d",
            sentryIssuesUrl("abc"),
        )
    }

    @Test
    fun runsLabels() {
        assertEquals("ios 0.2.0 (52)", runsLabel("ios", "0.2.0", 52))
        assertEquals("android 0.1.0", runsLabel("android", "0.1.0", 0))
        assertEquals("—", runsLabel(null, null, null))
    }

    @Test
    fun aFirstSearchThatFailsSaysSoInsteadOfLoadingForever() {
        assertEquals("Loading…", userListNote(results = null, searchFailed = false))
        assertEquals("Couldn't load accounts. Search to try again.", userListNote(results = null, searchFailed = true))
        assertEquals("No account matches.", userListNote(results = emptyList(), searchFailed = false))
    }
}
