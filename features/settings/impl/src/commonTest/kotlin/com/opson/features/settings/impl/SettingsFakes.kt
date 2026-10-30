package com.opson.features.settings.impl

import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.ProviderSummaryEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncRejection
import com.opson.libraries.diary.TodaySummaryEnabled
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
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
import com.opson.libraries.navigation.WebLinkLauncher
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.WeeklyBudget
import com.opson.libraries.nutrition.WeightProjection
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.progressphotos.ProgressPhotosEnabled
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.widgets.WidgetsEnabled
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate

/*
 * Hand-rolled fakes for the settings view model tests (docs/practices/testing.md).
 */

class FakeSettingsRepository(initial: Settings = SettingsFixtures.settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    var writes = 0
        private set

    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
        val next = transform(state.value)
        if (next != state.value) writes++
        state.value = next
        return next
    }
}

/** Answers [outcome] for every request, and records every call. Writes nothing itself. */
class FakeNumbersChanger(var outcome: (NumbersChange) -> NumbersOutcome = { NumbersOutcome.Applied(fewer = false) }) :
    NumbersChanger {
    val requested = mutableListOf<NumbersChange>()
    val confirmed = mutableListOf<NumbersChange>()
    val shown = mutableListOf<NumbersChange>()
    val kept = mutableListOf<NumbersChange>()
    var pauseTurnedOn = 0
        private set

    /** Each End after a set time asked for, as weeks and the next preset; null for turned off. */
    val timers = mutableListOf<Pair<Int, PresetId>?>()

    override suspend fun request(change: NumbersChange): NumbersOutcome {
        requested += change
        return outcome(change)
    }

    override suspend fun confirm(change: NumbersChange) {
        confirmed += change
    }

    override suspend fun turnOnPause() {
        pauseTurnedOn++
    }

    override suspend fun setEndAfter(weeks: Int, then: PresetId) {
        timers += weeks to then
    }

    override suspend fun clearEndAfter() {
        timers += null
    }

    override suspend fun endTimedMode() = Unit

    override fun pauseShown(change: NumbersChange) {
        shown += change
    }

    override fun pauseKept(change: NumbersChange) {
        kept += change
    }
}

class FakePolicySource(initial: VisibilityPolicy) : VisibilityPolicySource {
    override val policy = MutableStateFlow(initial)
}

/** [hangs]: a sync that never answers, like a server that accepts the connection and goes quiet. */
class FakeDiarySync(var result: Result<Unit> = Result.success(Unit), private val hangs: Boolean = false) : DiarySync {
    var syncs = 0
        private set

    /** What is still queued after a sync, whatever it answered. */
    var pending = 0

    override val rejections: Flow<SyncRejection> = emptyFlow()

    override suspend fun pendingChanges(): Int = pending

    override suspend fun syncNow(): Result<Unit> {
        syncs++
        if (hangs) awaitCancellation()
        return result
    }
}

/** Fails if asked before a sync, so a test can prove the export waits for queued changes. */
class FakeDataExport(private val sync: FakeDiarySync, var result: Result<ByteArray>) : DataExport {
    var calls = 0
        private set
    var syncsBeforeFirstCall: Int? = null
        private set

    override suspend fun everything(): Result<ByteArray> {
        calls++
        if (syncsBeforeFirstCall == null) syncsBeforeFirstCall = sync.syncs
        return result
    }
}

/** Who is signed in, signing out and account deletion; everything else is out of scope for settings. */
class FakeAuthRepository(
    var deleteOutcome: DeleteAccountOutcome = DeleteAccountOutcome.Success,
    initial: AuthState = AuthState.Unauthenticated(),
) : AuthRepository {
    val state = MutableStateFlow(initial)
    var deletes = 0
        private set
    var signOuts = 0
        private set

    /** A sign-out that never finishes, like one still waiting on the network. */
    var signOutHangs = false

    override suspend fun deleteAccount(): DeleteAccountOutcome {
        deletes++
        return deleteOutcome
    }

    override suspend fun signOut() {
        signOuts++
        if (signOutHangs) awaitCancellation()
        state.value = AuthState.Unauthenticated(reason = AuthState.Unauthenticated.Reason.SignedOut)
    }

    override suspend fun current(): AuthState = state.value
    override fun observe(): Flow<AuthState> = state
    override suspend fun retry(): AuthState = unused()
    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome = unused()
    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome = unused()
    override suspend fun refreshSession(): RefreshOutcome = unused()
    override suspend fun resendVerificationEmail(email: String): ResendOutcome = unused()
    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome = unused()
    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome = unused()
    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome = unused()
    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome = unused()

    private fun unused(): Nothing = error("Settings never calls this")
}

/** `release.reminders`, on or off. */
fun remindersFlag(on: Boolean = false): RemindersEnabled = RemindersEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("reminders" to on))
    },
)

/** `release.progressPhotos`, on or off. */
fun photosFlag(on: Boolean = false): ProgressPhotosEnabled = ProgressPhotosEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("progressPhotos" to on))
    },
)

/** Burned and the resting choices, as a test sets them. Defaults to design sync 4's 2,180. */
class FakeBurnedRepository(
    burned: Burned? = SettingsFixtures.burned,
    choices: RestingChoices = RestingChoices(1610, emptySet(), 1560, HealthSource.HealthConnect, 72.0),
) : BurnedRepository {
    val burnedFlow = MutableStateFlow(burned)
    val choicesFlow = MutableStateFlow(choices)

    override fun burned(day: LocalDate): Flow<Burned?> = burnedFlow

    override fun restingChoices(): Flow<RestingChoices> = choicesFlow
}

/** `release.goalsAndEnergy`, on or off. */
fun goalsAndEnergyFlag(on: Boolean = false): GoalsAndEnergyEnabled = GoalsAndEnergyEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("goalsAndEnergy" to on))
    },
)

/** `release.todaySummary`, on or off. */
fun todaySummaryFlag(on: Boolean = false): TodaySummaryEnabled = TodaySummaryEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("todaySummary" to on))
    },
)

/** `release.findSupport`, on or off. */
fun findSupportFlag(on: Boolean = false): FindSupportEnabled = FindSupportEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("findSupport" to on))
    },
)

/** `release.onboardingSetup`, on or off: the weight goal and its row on Targets. */
fun weightGoalsFlag(on: Boolean = false): OnboardingSetupEnabled = OnboardingSetupEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("onboardingSetup" to on))
    },
)

/** `release.providerSummary`, on or off. */
fun providerSummaryFlag(on: Boolean = false): ProviderSummaryEnabled = ProviderSummaryEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("providerSummary" to on))
    },
)

/** The dialer, messages and browser: records every URL, and opens it unless [opens] is false. */
class FakeLinks(var opens: Boolean = true) : WebLinkLauncher {
    val opened = mutableListOf<String>()

    override fun open(url: String): Result<Unit> {
        opened += url
        return if (opens) Result.success(Unit) else Result.failure(IllegalStateException("No handler available"))
    }
}

/** `release.widgets`, on or off. */
fun widgetsFlag(on: Boolean = false): WidgetsEnabled = WidgetsEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("widgets" to on))
    },
)

/**
 * Notification permission as the system would answer: [status] until asked, then [answer] once
 * the prompt is shown. Counts prompts, so a test can prove nothing asked at the wrong moment.
 */
class FakeNotificationPermissions(
    var status: NotificationPermission = NotificationPermission.NotAsked,
    private val answer: NotificationPermission = NotificationPermission.Granted,
) : NotificationPermissions {
    var prompts = 0
        private set
    var settingsOpened = 0
        private set

    override suspend fun status(): NotificationPermission = status

    override suspend fun request(): NotificationPermission {
        if (status == NotificationPermission.NotAsked) {
            prompts++
            status = answer
        }
        return status
    }

    override fun openSettings() {
        settingsOpened++
    }
}

/** Every event [vm] sends from now on, in order. */
fun <E : Any> TestScope.eventsOf(vm: SEAViewModel<*, E, *>): List<E> {
    val events = mutableListOf<E>()
    backgroundScope.launch { vm.eventFlow.collect { events += it } }
    return events
}

/** The goal repository as Targets reads it: a recalculation to offer and the latest weigh-in, both settable. */
class FakeGoalRepository(recalculation: Recalculation? = null, weightKg: Double? = null) : GoalRepository {
    val recalculationFlow = MutableStateFlow(recalculation)
    val inputsFlow = MutableStateFlow<BaselineInputs?>(weightKg?.let { GoalFixtures.inputs.copy(weightKg = it) })

    override fun inputs(): Flow<BaselineInputs?> = inputsFlow
    override fun recalculation(): Flow<Recalculation?> = recalculationFlow
    override suspend fun accept(recalculation: Recalculation) = Unit
    override suspend fun keep(recalculation: Recalculation) = Unit
    override fun today(): Flow<List<GoalToday>> = MutableStateFlow(emptyList())
    override fun days(from: LocalDate, to: LocalDate): Flow<Map<String, List<GoalDay>>> = MutableStateFlow(emptyMap())
    override fun week(): Flow<WeeklyBudget?> = MutableStateFlow(null)
    override fun projection(): Flow<WeightProjection?> = MutableStateFlow(null)
}
