package com.opson.features.home.impl.harness

import com.opson.features.home.impl.HomeAction
import com.opson.features.home.impl.HomeState
import com.opson.features.home.impl.HomeViewModel
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A minimal scenario harness for [HomeViewModel] — the pattern demo for
 * behaviour-driven VM tests, not a framework. Three pieces:
 *
 *  1. a builder ([homeScenario]) that wires the REAL view model with
 *     hand-rolled fakes;
 *  2. scenario verbs ([HomeScenario.renameUser], [HomeScenario.refresh]) that
 *     speak the user's language instead of repository plumbing; and
 *  3. an [HomeScenario.assertState] DSL whose vocabulary matches what the
 *     screen shows.
 *
 * When a feature grows enough flows that its tests keep re-wiring the same
 * fakes and re-deriving the same assertions, grow one of these next to it —
 * the test reads as a scenario ("rename, refresh, see the new name") and the
 * wiring noise lives here, written once.
 */
@OptIn(ExperimentalTime::class)
fun homeScenario(build: HomeScenarioBuilder.() -> Unit = {}): HomeScenario =
    HomeScenarioBuilder().apply(build).start()

@OptIn(ExperimentalTime::class)
class HomeScenarioBuilder internal constructor() {
    private var initialProfile: Profile = authenticatedProfile("Ada")

    /** The profile the fake repository resolves at boot. Default: "Ada". */
    fun profile(value: Profile) {
        initialProfile = value
    }

    /** Convenience for the common case — an authenticated user named [name]. */
    fun user(name: String) {
        initialProfile = authenticatedProfile(name)
    }

    internal fun start(): HomeScenario {
        val profiles = ScenarioProfileRepository(initialProfile)
        return HomeScenario(HomeViewModel(profiles), profiles)
    }
}

@OptIn(ExperimentalTime::class)
class HomeScenario internal constructor(
    val vm: HomeViewModel,
    private val profiles: ScenarioProfileRepository,
) {
    /** The profile's display name changes server-side (another device, a sync). */
    fun renameUser(name: String) {
        profiles.profile.value = authenticatedProfile(name)
    }

    /** The user signs out mid-session — the repository falls back. */
    fun signOut() {
        profiles.profile.value = Profile.Fallback(id = "local-id")
    }

    /** The user pulls to refresh. */
    fun refresh() {
        vm.takeAction(HomeAction.Refresh)
    }

    fun assertState(block: HomeStateAssertions.() -> Unit) {
        HomeStateAssertions(vm.state).block()
    }
}

class HomeStateAssertions internal constructor(private val state: HomeState) {
    fun userNameIs(expected: String) = assertEquals(expected, state.userName, "state.userName")
    fun userNameIsAbsent() = assertEquals(null, state.userName, "state.userName")
}

@OptIn(ExperimentalTime::class)
internal fun authenticatedProfile(name: String) = Profile.Authenticated(
    id = "user-1",
    displayName = name,
    email = null,
    isAnonymous = true,
    createdAt = Instant.fromEpochSeconds(0),
)

@OptIn(ExperimentalTime::class)
internal class ScenarioProfileRepository(initial: Profile) : ProfileRepository {
    val profile = MutableStateFlow(initial)

    override suspend fun current(): Profile = profile.value
    override fun observe(): Flow<Profile> = profile
    override suspend fun update(displayName: String?): UpdateProfileOutcome =
        UpdateProfileOutcome.NotSignedIn
}
