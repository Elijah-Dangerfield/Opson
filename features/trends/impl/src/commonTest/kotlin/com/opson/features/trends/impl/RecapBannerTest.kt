package com.opson.features.trends.impl

import com.opson.features.trends.impl.recap.RecapBannerAction
import com.opson.features.trends.impl.recap.RecapBannerEvent
import com.opson.features.trends.impl.recap.RecapBannerStore
import com.opson.features.trends.impl.recap.RecapBannerViewModel
import com.opson.features.trends.impl.recap.RecapFixtures
import com.opson.features.trends.impl.recap.RecapViewModel
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.RecapsRelease
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.prompts.OptOut
import com.opson.libraries.prompts.OptOuts
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** "Your week is ready" on Today: once per week, gone once opened or put away, and its way out the first few times. */
class RecapBannerTest : CoroutineTest() {

    private val monday = RecapFixtures.today
    private val store = RecapBannerStore(MemoryCacheFactory())
    private val optOuts = CountingOptOuts()

    private fun vm(settings: Settings = Settings(), released: Boolean = true) = RecapBannerViewModel(
        store = store,
        settings = FakeSettingsRepository(settings),
        diary = FakeDiaryRepository(RecapFixtures.days()),
        calendar = object : LocalCalendar {
            override fun timeZone(): TimeZone = TimeZone.UTC

            override fun today(): LocalDate = monday
        },
        ticker = MinuteTicker { flowOf(Instant.parse("2026-10-26T09:00:00Z")) },
        recaps = RecapsRelease { released },
        optOuts = optOuts,
    )

    @Test
    fun theWeekJustOver_isOffered_andCountsAsOneShowingHoweverOftenItDraws() = runUnitTest {
        val first = vm()
        assertEquals(RecapFixtures.week, first.state.period)
        assertTrue(first.state.offersOptOut)
        vm()
        assertEquals(1, optOuts.shows[OptOut.RecapBanner], "the same week again is the same showing")
    }

    @Test
    fun lookingInOnAWeekStillGoing_leavesItsBanner_butOpeningOneThatsOverUsesItUp() = runUnitTest {
        fun recap(period: RecapPeriod) = RecapViewModel(
            kind = period.kind.wire,
            startIso = period.start.toString(),
            diary = FakeDiaryRepository(RecapFixtures.days()),
            weights = FakeWeightRepository(),
            settingsRepository = FakeSettingsRepository(),
            policySource = FakePolicySource(),
            calendar = object : LocalCalendar {
                override fun timeZone(): TimeZone = TimeZone.UTC
                override fun today(): LocalDate = monday
            },
            burnedRepository = FakeBurnedRepository(),
            goalsAndEnergy = goalsAndEnergyFlag(),
            checkInEnabled = checkInFlag(false),
            marks = DayMarks { flowOf(emptyList()) },
            banner = store,
            healthFlag = healthFlag(false),
        )
        recap(RecapPeriod(RecapKind.Week, monday))
        assertEquals(emptySet(), store.handled.first(), "this week is still going")
        recap(RecapFixtures.week)
        assertEquals(setOf(RecapFixtures.week.key), store.handled.first())
    }

    @Test
    fun openingIt_takesItAway_andOpensTheRecap() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<RecapBannerEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        vm.takeAction(RecapBannerAction.OpenTapped)
        assertEquals(listOf<RecapBannerEvent>(RecapBannerEvent.Open(RecapFixtures.week)), events)
        assertNull(vm.state.period)
        assertNull(vm().state.period, "once per week")
    }

    @Test
    fun notNow_takesItAway() = runUnitTest {
        val vm = vm()
        vm.takeAction(RecapBannerAction.NotNowTapped)
        assertNull(vm.state.period)
    }

    @Test
    fun ratherNot_turnsTheCardOff_andSaysWhereItComesBack() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<RecapBannerEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        vm.takeAction(RecapBannerAction.TurnOffTapped)
        assertEquals(listOf(OptOut.RecapBanner), optOuts.turnedOff)
        assertEquals(listOf<RecapBannerEvent>(RecapBannerEvent.TurnedOff), events)
    }

    @Test
    fun afterTheFirstFive_theWayOutIsOnlyInMore() = runUnitTest {
        optOuts.shows[OptOut.RecapBanner] = OptOuts.OFFERED_SHOWINGS
        assertFalse(vm().state.offersOptOut)
    }

    @Test
    fun nothingShows_withTheCardOff_orRecapsNotReleased() = runUnitTest {
        assertNull(vm(Settings(recapBanner = false)).state.period)
        assertNull(vm(released = false).state.period)
    }
}

/** Caches held in memory, each starting from its serializer's default. */
private class MemoryCacheFactory : CacheFactory {
    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache { defaultValue() }

    override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
        MemoryCache { serializer.read(null) }

    private class MemoryCache<T : Any>(private val default: suspend () -> T) : Cache<T> {
        private val value = MutableStateFlow<T?>(null)

        override val updates: Flow<T> = flow {
            get()
            emitAll(value.filterNotNull())
        }

        override suspend fun get(): T = value.value ?: default().also { value.value = it }

        override suspend fun set(value: T) {
            this.value.value = value
        }

        override suspend fun clear() {
            value.value = default()
        }

        override suspend fun update(transform: (T) -> T): T = transform(get()).also { set(it) }
    }
}

/** [OptOuts] in memory, counting as the real one does. */
private class CountingOptOuts : OptOuts {
    val shows = mutableMapOf<OptOut, Int>()
    val turnedOff = mutableListOf<OptOut>()

    override suspend fun showing(optOut: OptOut): Boolean {
        shows[optOut] = (shows[optOut] ?: 0) + 1
        return offered(optOut)
    }

    override suspend fun offered(optOut: OptOut): Boolean = (shows[optOut] ?: 0) in 1..OptOuts.OFFERED_SHOWINGS

    override suspend fun turnOff(optOut: OptOut) {
        turnedOff += optOut
    }
}
