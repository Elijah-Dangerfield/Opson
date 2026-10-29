package com.opson.libraries.widgets.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.opson.AppData
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetJson
import com.opson.libraries.widgets.WidgetLinks
import com.opson.libraries.widgets.WidgetLogOpen
import com.opson.libraries.widgets.WidgetOpen
import com.opson.libraries.widgets.WidgetTodayOpen
import com.opson.libraries.widgets.WidgetSnapshot
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The snapshot source against fake repositories, and what a widget's + asks the app for. */
class WidgetSourceAndOpenerTest : CoroutineTest() {

    private val today = WidgetFixtures.today
    private val diary = FakeDiary()
    private val calendar = FixedCalendar(today)
    private val bus = FakeEventBus()
    private val settings = FakeSettingsRepository(WidgetFixtures.settings())
    private val policies = FakePolicySource(WidgetFixtures.policy(PresetId.Full))
    private var now: Instant = WidgetFixtures.noon
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private lateinit var appScope: AppCoroutineScope

    private fun source() = RealWidgetSnapshotSource(
        settingsRepository = settings,
        diary = diary,
        policies = policies,
        calendar = calendar,
        clock = clock,
        appEvents = AppEvents(bus),
        strings = KeyStrings,
        burnedRepository = FakeBurnedRepository(),
        goalsAndEnergy = goalsAndEnergyFlag(),
        fastingRepository = fasts,
        fastingRelease = { fastingOn },
    )

    private val auth = FakeAuth()
    private val fasts = FakeFastingRepository()
    private var fastingOn = true
    private val selectedDay = FakeSelectedDay(LocalDate(2026, 10, 20))

    private val appCache = FakeAppCache(AppData(hasUserOnboarded = true))

    private fun opener(): WidgetOpener = WidgetOpener(diary, settings, calendar, clock, appCache, { auth }, selectedDay, appScope)

    private fun test(block: suspend TestScope.() -> Unit) = runUnitTest {
        appScope = AppCoroutineScope(dispatchers)
        try {
            block()
        } finally {
            appScope.cancel()
        }
    }

    /** Collects the source into a list for the rest of the test. */
    private fun collect(): List<WidgetSnapshot> {
        val seen = mutableListOf<WidgetSnapshot>()
        appScope.launch { source().observe().collect { seen += it } }
        return seen
    }

    @Test
    fun nothingIsReadUntilSomeoneCollects() = test {
        source()
        assertEquals(0, diary.reads)
    }

    @Test
    fun todaysDayInTheMode_isTheSnapshot() = test {
        diary.days.value = mapOf(today to WidgetFixtures.day)
        val seen = collect()

        val energy = assertNotNull(seen.last().days.first().energy)
        assertEquals("1,245", energy.amount)
        assertEquals("widgets_today", seen.last().days.first().title)
    }

    @Test
    fun aLog_aModeChange_andAMealGroupsChange_eachRebuildIt() = test {
        val seen = collect()
        assertEquals("0", seen.last().days.first().energy?.amount)

        diary.days.value = mapOf(today to WidgetFixtures.day)
        assertEquals("1,245", seen.last().days.first().energy?.amount)

        policies.state.value = WidgetFixtures.policy(PresetId.NoNumbers)
        assertNull(seen.last().days.first().energy)
        assertEquals(4, seen.last().days.first().meals.size)

        settings.update { it.copy(mealGroups = false) }
        assertEquals(4, seen.size)
    }

    @Test
    fun anUnchangedSnapshot_isNotEmittedAgain() = test {
        val seen = collect()
        settings.update { it.copy(askTags = !it.askTags) }
        assertEquals(1, seen.size)
    }

    @Test
    fun settingsThatAreNotTheUsersYet_buildNothing() = test {
        settings.known.value = false
        val seen = collect()
        assertTrue(seen.isEmpty())

        settings.known.value = true
        assertEquals(1, seen.size)
    }

    @Test
    fun aForegroundOnANewDay_movesToThatDay() = test {
        val seen = collect()
        calendar.date = LocalDate(2026, 10, 26)
        bus.dispatch(AppEvent.OnForeground(isColdBoot = false))

        assertEquals("2026-10-26", seen.last().days.first().day)
    }

    @Test
    fun theLogLink_opensTheLoggerOnTheNextMeal_today() = test {
        diary.days.value = mapOf(today to WidgetFixtures.day)
        now = LocalDateTime(today, LocalTime(12, 30)).toInstant(WidgetFixtures.timeZone)
        val opener = opener()

        assertTrue(opener.opened(WidgetLinks.LOG))

        assertEquals(WidgetLogOpen(MealSlot.Dinner, today), opener.requests.first())
    }

    @Test
    fun theLogLink_withEverythingDone_leavesTheSlotToTheLogger() = test {
        diary.days.value = mapOf(today to Day.empty(today).copy(ateNoDetails = MealSlot.meals.toSet()))
        val opener = opener()

        opener.opened(WidgetLinks.LOG)

        assertEquals(WidgetLogOpen(null, today), opener.requests.first())
    }

    @Test
    fun anyOtherUrl_isNotTheWidgets() = test {
        val opener = opener()
        assertFalse(opener.opened("opson://login-callback#access_token=x"))
        assertFalse(opener.opened("opson://auth/confirmed"))
    }

    @Test
    fun aModeWithMealGroupsOff_logsWithoutASlot() = test {
        settings.update { it.copy(mealGroups = false, numbers = NumbersSettings(preset = PresetId.NoNumbers)) }
        val opener = opener()

        opener.opened(WidgetLinks.LOG)

        assertEquals(WidgetLogOpen(null, today), opener.requests.first())
    }

    /** The + is on the "Open Opson" card too. Signed out, it opens the app, never a Logger nobody owns. */
    @Test
    fun theLogLink_signedOut_opensNothing() = test {
        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        val opener = opener()
        val asked = mutableListOf<WidgetOpen>()
        appScope.launch { opener.requests.collect { asked += it } }

        assertTrue(opener.opened(WidgetLinks.LOG), "still the widget's link, so it goes no further")
        runCurrent()

        assertTrue(asked.isEmpty())
    }

    /** Offline on an hour-old token, the app still shows Today, and the + still lands on the Logger. */
    @Test
    fun theLogLink_onASessionThatCannotRefreshOffline_stillOpensTheLogger() = test {
        auth.state.value = AuthState.Unauthenticated()
        val opener = opener()

        opener.opened(WidgetLinks.LOG)

        assertEquals(today, (opener.requests.first() as WidgetLogOpen).day)
    }

    @Test
    fun theLogLink_onAFreshInstall_opensNothing() = test {
        appCache.state.value = AppData(hasUserOnboarded = false)
        auth.state.value = AuthState.Unauthenticated()
        val opener = opener()
        val asked = mutableListOf<WidgetOpen>()
        appScope.launch { opener.requests.collect { asked += it } }

        opener.opened(WidgetLinks.LOG)
        runCurrent()

        assertTrue(asked.isEmpty())
    }

    /**
     * The owner's report: food logged, the widget still said 0. The whole Kotlin side end to end,
     * the real source into the real refresher: what reaches the platform after a log is today's
     * day, under today's date, with the amount eaten.
     */
    @Test
    fun aLogAfterLaunch_reachesThePlatform_asTodaysAmount() = test {
        val publisher = FakePublisher()
        WidgetRefresher(settings, source(), publisher, appCache, { auth }, appScope)
        assertEquals("0", publisher.published.last().dayFor(today)?.energy?.amount, "launch, nothing logged yet")

        diary.days.value = mapOf(today to WidgetFixtures.day)
        advanceTimeBy(SETTLE_MS)

        val written = assertNotNull(WidgetJson.decode(WidgetJson.encode(publisher.published.last())))
        assertEquals(today.toString(), written.days.first().day)
        assertEquals("1,245", written.dayFor(today)?.energy?.amount)
        assertEquals(2, publisher.published.size, "one publish for the launch, one for the log")
    }

    @Test
    fun theFastingTimer_isInTheSnapshot_onlyWhileItIsOnAndOffered() = test {
        settings.update { it.copy(fasting = true) }
        val seen = collect()
        assertNotNull(seen.last().fasting, "on, in All numbers")

        settings.update { it.copy(numbers = NumbersSettings(preset = PresetId.NoNumbers)) }
        assertNull(seen.last().fasting, "fasting is only offered in All numbers")
    }

    @Test
    fun theFastingTimer_withTheReleaseOff_isNeverRead() = test {
        fastingOn = false
        settings.update { it.copy(fasting = true) }
        val seen = collect()
        assertNull(seen.last().fasting)
    }

    @Test
    fun aMealRow_opensTheLoggerOnItsOwnMeal() = test {
        val opener = opener()

        assertTrue(opener.opened(WidgetLinks.log(MealSlot.Lunch)))

        assertEquals(WidgetLogOpen(MealSlot.Lunch, today), opener.requests.first())
    }

    @Test
    fun theBodyOfAWidget_opensToday_onToday() = test {
        val opener = opener()

        assertTrue(opener.opened(WidgetLinks.TODAY))

        assertEquals(WidgetTodayOpen, opener.requests.first())
        assertEquals(today, selectedDay.date.value, "Today shows the day the widget showed")
    }

    @Test
    fun theWaterAdd_putsOneGlassOnToday_thenOpensToday() = test {
        settings.update { it.copy(glassMl = 330) }
        val opener = opener()

        assertTrue(opener.opened(WidgetLinks.WATER_ADD))

        assertEquals(WidgetTodayOpen, opener.requests.first())
        assertEquals(listOf(today to 330), diary.waterAdded)
    }

    @Test
    fun theWaterAdd_onAClosedDay_writesNothing_andStillOpensToday() = test {
        diary.closed += today
        val opener = opener()

        opener.opened(WidgetLinks.WATER_ADD)

        assertEquals(WidgetTodayOpen, opener.requests.first())
        assertTrue(diary.waterAdded.isEmpty())
    }

    @Test
    fun theWaterAdd_signedOut_writesNothing() = test {
        auth.state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
        val opener = opener()
        val asked = mutableListOf<WidgetOpen>()
        appScope.launch { opener.requests.collect { asked += it } }

        assertTrue(opener.opened(WidgetLinks.WATER_ADD))
        runCurrent()

        assertTrue(diary.waterAdded.isEmpty())
        assertTrue(asked.isEmpty())
    }

    private companion object {
        /** Past WidgetRefresher's settle. */
        const val SETTLE_MS = 300L
    }
}
