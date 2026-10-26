package com.opson.integration.fasting

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClock
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.rawSync
import com.opson.integration.helpers.setFlagFor
import com.opson.libraries.nutrition.FastingPlan
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * **Fasting sync (0.2).** Fasts are an entity 0.1 does not know. A request
 * that `accepts` `fast` gets them in `changes.fasts`; a request without it, as
 * every 0.1 phone sends, gets a response with no `fasts` key at all, so its
 * strict decoder never meets one.
 */
class FastingSyncTest : IntegrationTest() {

    @Test
    fun aFastReachesTheUsersOtherPhone_andA01RequestNeverSeesTheKey() = integration {
        val user = randomUserId()
        setFlagFor(user, "release.fasting")
        val zone = TimeZone.currentSystemDefault()
        val yesterday = Clock.System.todayIn(zone).minus(1, DateTimeUnit.DAY)
        val windowClosed = LocalDateTime(yesterday, FastingPlan.Default.closes).toInstant(zone)
        val phone = client(user, clock = TestClock().apply { shiftTo(LocalDateTime(yesterday, LocalTime(21, 0)).toInstant(zone)) })

        phone.lifecycle.foreground()
        awaitUntil { phone.fastingRelease.isOn() }
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.settings.update { it.copy(fasting = true) }
        phone.settings.settings.awaitState { it.fasting } // the timer reads the settings flow, as the screens do
        phone.fasting.startDue()

        val fast = assertNotNull(phone.fasting.observeLatest().first(), "the evening's fast started")
        assertEquals(yesterday, fast.day)
        assertEquals(windowClosed, fast.startedAt, "dated at the window's close, not when it was noticed")
        phone.syncNow().getOrThrow()
        assertEquals(1L, server.countFor("fasts", user))

        val otherPhone = client(user)
        otherPhone.syncNow().getOrThrow()
        assertEquals(fast, otherPhone.fasting.observeLatest().first())

        val v01 = Json.parseToJsonElement(rawSync(user, """{"cursor":0,"mutations":[]}""").bodyAsText()).jsonObject
        val changes01 = v01.getValue("changes").jsonObject
        assertFalse("fasts" in changes01, "a 0.1 request gets no fasts key: $changes01")
        assertTrue("settings" in changes01, "but everything 0.1 knows still comes")

        val v02 = Json.parseToJsonElement(
            rawSync(user, """{"cursor":0,"mutations":[],"accepts":["fast"]}""").bodyAsText(),
        ).jsonObject
        val fasts = v02.getValue("changes").jsonObject.getValue("fasts").jsonArray
        assertEquals(listOf(yesterday.toString()), fasts.map { it.jsonObject.getValue("day").jsonPrimitive.content })
    }
}
