package com.opson.features.home.impl

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.displayNameOrNull
import me.tatarka.inject.annotations.Inject

@Inject
class HomeViewModel(
    private val profileRepository: ProfileRepository,
) : SEAViewModel<HomeState, HomeEvent, HomeAction>(
    initialStateArg = HomeState()
) {

    init {
        takeAction(HomeAction.Load)
    }

    override suspend fun handleAction(action: HomeAction) {
        when (action) {
            is HomeAction.Load -> action.loadProfile()
            is HomeAction.Refresh -> action.loadProfile()
        }
    }

    private suspend fun HomeAction.loadProfile() {
        val profile = profileRepository.current()
        updateState { it.copy(userName = profile.displayNameOrNull) }
    }
}

data class HomeState(
    val userName: String? = null,
)

sealed interface HomeEvent

sealed interface HomeAction {
    data object Load : HomeAction
    data object Refresh : HomeAction
}
