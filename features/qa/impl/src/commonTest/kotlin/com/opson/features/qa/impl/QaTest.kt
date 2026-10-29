package com.opson.features.qa.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRefresher
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.config.ConfigOverride
import com.opson.libraries.config.ConfigOverrideRepository
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.foods.db.FoodCacheEntity
import com.opson.libraries.identity.IdentityConfig
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderTestFire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The QA menu: who may see it, what its rows say, and that each tool does exactly its one thing.
 * Not covered: the clipboard and the routes the events become (the entry point).
 */
class QaTest : CoroutineTest() {

    // --- The gate ---

    @Test
    fun debugAlwaysGetsIt_andTheStoreChannelNever() {
        assertTrue(QaGate.allowed(isDebug = true, releaseChannel = "store", sandboxReceipt = false))
        assertFalse(QaGate.allowed(isDebug = false, releaseChannel = "store", sandboxReceipt = true))
    }

    @Test
    fun aBetaBinaryPromotedToTheAppStore_losesIt() {
        assertTrue(QaGate.allowed(isDebug = false, releaseChannel = "beta", sandboxReceipt = true), "TestFlight")
        assertFalse(QaGate.allowed(isDebug = false, releaseChannel = "beta", sandboxReceipt = false), "same binary from the App Store")
        assertTrue(QaGate.allowed(isDebug = false, releaseChannel = "beta", sandboxReceipt = null), "Android can't tell")
        assertTrue(QaGate.allowed(isDebug = false, releaseChannel = "dev", sandboxReceipt = null))
    }

    // --- What the rows say ---

    @Test
    fun theSupabaseProject_isTheUrlsFirstLabel() {
        assertEquals("gfmstgbrdwqepmeycyid", QaStateBuilder.supabaseProject("https://gfmstgbrdwqepmeycyid.supabase.co"))
        assertNull(QaStateBuilder.supabaseProject(""))
    }

    @Test
    fun identity_withNoSession_saysSo_andOffersNothingToCopy() {
        val rows = QaStateBuilder.identity(null, serverUrl = "", supabaseUrl = "")
        assertTrue(rows.none { it.copyable })
    }

    @Test
    fun identity_signedIn_copiesTheUserId() {
        val rows = QaStateBuilder.identity(AuthState.Authenticated("u1", isAnonymous = false, email = "a@b.c"), "https://api", "https://p.supabase.co")
        assertEquals(listOf("u1", "a@b.c", "https://api", "p"), rows.filter { it.copyable }.map { it.value })
    }

    @Test
    fun typedOverrides_parseToTheirType_orNotAtAll() {
        assertEquals(42, QaStateBuilder.parse(QaConfigKind.Int, " 42 "))
        assertEquals(1.5, QaStateBuilder.parse(QaConfigKind.Double, "1.5"))
        assertNull(QaStateBuilder.parse(QaConfigKind.Long, "soon"))
        assertNull(QaStateBuilder.parse(QaConfigKind.ReadOnly, "anything"))
    }

    // --- The tools ---

    @Test
    fun configRows_showTheLiveValue_andWhichAreOverridden() = runUnitTest {
        val f = Fixture()
        val vm = f.vm()

        assertEquals(QaConfigRow("Check-in", "release.checkIn", "release", "false", QaConfigKind.Flag, overridden = false), vm.state.config.single())

        vm.takeAction(QaAction.FlagToggled("release.checkIn", on = true))

        assertEquals("true", vm.state.config.single().value)
        assertTrue(vm.state.config.single().overridden)
    }

    @Test
    fun firingAReminder_firesTodaysNudgeForThatMeal() = runUnitTest {
        val f = Fixture()
        val vm = f.vm()
        val events = eventsOf(vm)

        vm.takeAction(QaAction.FireTapped(QaReminder.Lunch))

        assertEquals(listOf<ReminderKey>(ReminderKey.Meal(f.calendar.today(), MealSlot.Lunch)), f.fire.fired)
        assertEquals(1, events.filterIsInstance<QaEvent.ShowToast>().size)
    }

    @Test
    fun theCheckIn_turnsOnItsFlag_thenOpensTodaysCheckIn() = runUnitTest {
        val f = Fixture()
        val vm = f.vm()
        val events = eventsOf(vm)

        vm.takeAction(QaAction.CheckInTapped)

        assertTrue(f.overrides.paths().contains("release.checkIn"))
        assertEquals(listOf<QaEvent>(QaEvent.OpenCheckIn(f.calendar.today().toString())), events)
    }

    @Test
    fun resetOnboarding_onlyClearsTheFlag() = runUnitTest {
        val f = Fixture()
        f.appCache.set(AppData(hasUserOnboarded = true, installId = "install"))

        f.vm().takeAction(QaAction.ResetOnboardingTapped)

        assertEquals(AppData(hasUserOnboarded = false, installId = "install"), f.appCache.get())
    }

    @Test
    fun clearCaches_keepsFavorites_andRefetchesConfig() = runUnitTest {
        val f = Fixture()

        f.vm().takeAction(QaAction.ClearCachesTapped)

        assertEquals(1, f.foodCache.clears)
        assertEquals(1, f.refreshes)
    }

    @Test
    fun clearOverrides_clearsThemAll() = runUnitTest {
        val f = Fixture()
        val vm = f.vm()
        vm.takeAction(QaAction.FlagToggled("release.checkIn", on = true))

        vm.takeAction(QaAction.ClearOverridesTapped)

        assertTrue(f.overrides.paths().isEmpty())
        assertEquals("false", vm.state.config.single().value)
    }

    /** A config whose values follow the overrides, as the real repository merges them. */
    private class Fixture {
        val calendar = FakeCalendar()
        val overrides = FakeOverrides()
        val appCache = FakeAppCache()
        val fire = FakeTestFire()
        val foodCache = FakeFoodCache()
        var refreshes = 0

        private val live = object : AppConfigMap() {
            override val map: Map<String, *> get() = merged(overrides.state.value)
        }
        private val repository = object : AppConfigRepository {
            override fun config(): AppConfigMap = live
            override fun configStream(): Flow<AppConfigMap> = overrides.state.map { list ->
                object : AppConfigMap() {
                    override val map: Map<String, *> = merged(list)
                }
            }
        }

        fun vm() = QaViewModel(
            build = QaStateBuilder.BuildFacts("0.2.0", 702, "beta", "abc", "main", "iOS"),
            auth = FakeAuthRepository(),
            identityConfig = object : IdentityConfig {
                override val supabaseUrl = "https://p.supabase.co"
                override val supabasePublishableKey = "publishable"
            },
            networkConfig = object : NetworkConfig {
                override val baseUrl = "https://api"
            },
            configValues = setOf<QaConfigValue>(CheckInEnabled(live)),
            config = AppConfigFlow(repository),
            overrides = overrides,
            refresher = AppConfigRefresher { refreshes++ },
            appCache = appCache,
            testFire = fire,
            checkInEnabled = CheckInEnabled(live),
            calendar = calendar,
            foodCache = foodCache,
        )

        private fun merged(list: List<ConfigOverride<Any>>): Map<String, *> {
            val checkIn = list.lastOrNull { it.path == "release.checkIn" }?.value ?: false
            return mapOf("release" to mapOf("checkIn" to checkIn))
        }
    }

    private class FakeOverrides : ConfigOverrideRepository {
        val state = MutableStateFlow<List<ConfigOverride<Any>>>(emptyList())
        fun paths() = state.value.map { it.path }
        override fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>> = state
        override suspend fun addOverride(override: ConfigOverride<Any>) {
            state.value = state.value.filterNot { it.path == override.path } + override
        }
        override suspend fun clearAll() {
            state.value = emptyList()
        }
    }

    private class FakeAppCache : AppCache {
        private val state = MutableStateFlow(AppData())
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) {
            state.value = value
        }
        override suspend fun clear() {
            state.value = AppData()
        }
    }

    private class FakeTestFire : ReminderTestFire {
        val fired = mutableListOf<ReminderKey>()
        override suspend fun fire(key: ReminderKey): NotificationPermission {
            fired += key
            return NotificationPermission.Granted
        }
    }

    /** Only the QA menu's clear is driven. */
    private class FakeFoodCache : FoodCacheDao {
        var clears = 0
        override suspend fun deleteAllButFavorites() {
            clears++
        }
        override suspend fun upsert(row: FoodCacheEntity) = unused()
        override suspend fun get(id: String): FoodCacheEntity? = unused()
        override suspend fun getByBarcode(barcode: String): FoodCacheEntity? = unused()
        override suspend fun search(pattern: String, limit: Int): List<FoodCacheEntity> = unused()
        override fun observeFavorites(): Flow<List<FoodCacheEntity>> = emptyFlow()
        override fun observeRecent(limit: Int): Flow<List<FoodCacheEntity>> = emptyFlow()
        override suspend fun deleteAll() = unused()
        private fun unused(): Nothing = error("The QA menu never calls this")
    }
}
