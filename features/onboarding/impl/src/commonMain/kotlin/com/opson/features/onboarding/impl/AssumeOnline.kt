package com.opson.features.onboarding.impl

import com.opson.libraries.core.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sign-in view models' default connection, for tests and previews: always online. The app injects the real one. */
internal object AssumeOnline : AppState {
    override val isOffline: StateFlow<Boolean> = MutableStateFlow(false)
}
