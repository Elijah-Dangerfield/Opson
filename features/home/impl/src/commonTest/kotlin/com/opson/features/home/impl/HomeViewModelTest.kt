package com.opson.features.home.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Reference example for testing a SEAViewModel with the
 * :libraries:flowroutines:testing module.
 *
 * The recipe:
 *  - extend [CoroutineTest] — Main is a test dispatcher, so `viewModelScope`
 *    (Main.immediate) routes through the test scheduler and `takeAction`
 *    propagates to state in the same virtual tick
 *  - hand-roll a fake for each repository dependency (no mocking framework)
 *  - drive actions, assert on `vm.state`
 */
@OptIn(ExperimentalTime::class)
class HomeViewModelTest : CoroutineTest() {

    @Test
    fun loadsDisplayNameOnInit() = runUnitTest {
        val vm = HomeViewModel(FakeProfileRepository(authenticated("Ada")))

        assertEquals("Ada", vm.state.userName)
    }

    @Test
    fun refreshPicksUpChangedName() = runUnitTest {
        val repository = FakeProfileRepository(authenticated("Ada"))
        val vm = HomeViewModel(repository)

        repository.profile.value = authenticated("Grace")
        vm.takeAction(HomeAction.Refresh)

        assertEquals("Grace", vm.state.userName)
    }

    @Test
    fun fallbackProfileWithoutNameLeavesNameNull() = runUnitTest {
        val vm = HomeViewModel(FakeProfileRepository(Profile.Fallback(id = "local-id")))

        assertNull(vm.state.userName)
    }

    private fun authenticated(name: String) = Profile.Authenticated(
        id = "user-1",
        displayName = name,
        email = null,
        isAnonymous = true,
        createdAt = Instant.fromEpochSeconds(0),
    )
}

private class FakeProfileRepository(initial: Profile) : ProfileRepository {
    val profile = MutableStateFlow(initial)

    override suspend fun current(): Profile = profile.value
    override fun observe(): Flow<Profile> = profile
    override suspend fun update(displayName: String?): UpdateProfileOutcome {
        val current = profile.value
        if (current is Profile.Authenticated && displayName != null) {
            profile.value = current.copy(displayName = displayName)
            return UpdateProfileOutcome.Success(current.copy(displayName = displayName))
        }
        return UpdateProfileOutcome.NotSignedIn
    }
}
