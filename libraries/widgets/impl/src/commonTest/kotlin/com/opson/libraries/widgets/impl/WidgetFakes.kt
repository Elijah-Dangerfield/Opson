package com.opson.libraries.widgets.impl

import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.LinkEmailIdentityOutcome
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.RefreshOutcome
import com.opson.libraries.identity.auth.ResendOutcome
import com.opson.libraries.identity.auth.SendResetOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.SignUpOutcome
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventBus
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import com.opson.libraries.widgets.WidgetStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/* Hand-rolled fakes for the widget tests (docs/practices/testing.md). */

/** Records what reached the platform. */
class FakePublisher : WidgetPublisher {
    val published = mutableListOf<WidgetSnapshot>()
    var clears = 0
        private set

    override suspend fun publish(snapshot: WidgetSnapshot) {
        published += snapshot
    }

    override suspend fun clear() {
        clears++
    }
}

/** Snapshots on demand, and whether anyone is collecting. */
class FakeSnapshotSource : WidgetSnapshotSource {
    val snapshots = MutableSharedFlow<WidgetSnapshot>(replay = 1)
    var collections = 0
        private set

    override fun observe(): Flow<WidgetSnapshot> = snapshots.onStart { collections++ }
}

class FakeSettingsRepository(initial: Settings, initiallyKnown: Boolean = true) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val known = MutableStateFlow(initiallyKnown)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = known

    /** What [isKnownFor] answers; by default the same as [isKnown]. */
    var knownFor: (userId: String?) -> Boolean = { known.value }

    override suspend fun isKnownFor(userId: String?): Boolean = knownFor(userId)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

/** Who is signed in; only [observe] is real. Starts signed in, as most tests want. */
class FakeAuth(initial: AuthState = AuthState.Authenticated(userId = "u1", isAnonymous = false, email = null)) : AuthRepository {
    val state = MutableStateFlow(initial)

    override suspend fun current(): AuthState = state.value
    override fun observe(): Flow<AuthState> = state
    override suspend fun retry(): AuthState = state.value
    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = unused()
    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = unused()
    override suspend fun refreshSession(): RefreshOutcome = unused()
    override suspend fun resendVerificationEmail(email: String): ResendOutcome = unused()
    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = unused()
    override suspend fun signOut() = unused()
    override suspend fun deleteAccount(): DeleteAccountOutcome = unused()
    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = unused()
    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = unused()
    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome = unused()

    private fun unused(): Nothing = error("Widgets only observe auth")
}

/** The app's own record; only whether someone finished onboarding matters here. */
class FakeAppCache(initial: AppData = AppData()) : AppCache {
    val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state

    override suspend fun get(): AppData = state.value

    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }

    override suspend fun update(transform: (AppData) -> AppData): AppData = transform(state.value).also { state.value = it }
}

class FakePolicySource(initial: VisibilityPolicy) : VisibilityPolicySource {
    val state = MutableStateFlow(initial)
    override val policy: StateFlow<VisibilityPolicy> = state
}

/** Days by date. Only what the widgets read is real; [reads] counts it. */
class FakeDiary : DiaryRepository {
    val days = MutableStateFlow<Map<LocalDate, Day>>(emptyMap())
    var reads = 0
        private set

    override fun observeDay(date: LocalDate): Flow<Day> = days.onStart { reads++ }.map { it[date] ?: Day.empty(date) }

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> = unused()
    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = unused()
    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = unused()
    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> = unused()
    override suspend fun updateItem(item: LogItem): DayWrite<Unit> = unused()
    override suspend fun removeItem(id: String): DayWrite<Unit> = unused()
    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = unused()
    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = unused()
    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = unused()
    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = unused()
    /** Water added, by day; a day in [closed] refuses it. */
    val waterAdded = mutableListOf<Pair<LocalDate, Int>>()
    val closed = mutableSetOf<LocalDate>()

    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> {
        if (day in closed) return DayWrite.DayClosed(day)
        waterAdded += day to ml
        days.value = days.value + (day to (days.value[day] ?: Day.empty(day)).let { it.copy(waterMl = it.waterMl + ml) })
        return DayWrite.Written(Unit)
    }
    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = unused()
    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = unused()
    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = unused()
    override suspend fun reopenDay(day: LocalDate) = unused()
    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = unused()
    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = unused()
    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = unused()
    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = unused()
    override fun recentFoods(limit: Int): Flow<List<LogItem>> = unused()
    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = unused()
    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = unused()

    private fun unused(): Nothing = error("Widgets never call this")
}

/** The latest fast, as a test sets it. */
class FakeFastingRepository(latest: Fast? = null) : FastingRepository {
    val latest = MutableStateFlow(latest)
    override fun observeLatest(): Flow<Fast?> = latest
    override suspend fun startDue() = Unit
    override suspend fun end() = Unit
    override suspend fun endForTimerOff() = Unit
}

/** The day Today shows. */
class FakeSelectedDay(initial: LocalDate) : SelectedDay {
    private val state = MutableStateFlow(initial)
    override val date: StateFlow<LocalDate> = state
    override fun select(day: LocalDate) {
        state.value = day
    }
}

class FixedCalendar(var date: LocalDate) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = date
}

class FakeEventBus : AppEventBus {
    val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    override fun dispatch(event: AppEvent) {
        events.tryEmit(event)
    }
    override fun eventStream(): Flow<AppEvent> = events
    override fun liveEventStream(): Flow<AppEvent> = events
}

/** The resource's key, so a test never depends on English copy. */
object KeyStrings : WidgetStrings {
    override suspend fun get(resource: StringResource, vararg args: Any): String =
        if (args.isEmpty()) resource.key else "${resource.key}(${args.joinToString()})"

    override suspend fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
        "${resource.key}[$quantity](${args.joinToString()})"
}
