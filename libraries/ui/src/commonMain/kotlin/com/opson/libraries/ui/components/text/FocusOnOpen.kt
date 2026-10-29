package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.opson.libraries.core.Catching
import kotlinx.coroutines.delay

/**
 * Puts the keyboard up in [focusRequester]'s field once the screen or sheet holding it has come
 * in, for a place someone only opens to type into: Weigh-in, a note, the activity search. Call it
 * where the field is composed, after anything that holds the field back (a loading state), with
 * the field carrying `Modifier.focusRequester(focusRequester)`.
 *
 * It waits a frame, so the field is attached and a request cannot throw, and then the length of
 * an enter animation, so the keyboard does not slide up while a screen is still sliding in or a
 * sheet is still rising. On iOS the two fighting each other is a visible jump; on Android a sheet's
 * window may not have focus yet, which is also why it asks the keyboard to show and does not rely
 * on focus alone.
 *
 * Once per visit: [rememberSaveable], so coming back to the screen from one pushed over it, or a
 * rotation, leaves the keyboard as the person left it. Nothing in a preview, where there is no
 * window to focus.
 */
@Composable
fun FocusOnOpen(focusRequester: FocusRequester, enabled: Boolean = true) {
    val inPreview = LocalInspectionMode.current
    val keyboard = LocalSoftwareKeyboardController.current
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled || asked || inPreview) return@LaunchedEffect
        withFrameNanos { }
        delay(FOCUS_SETTLE_MILLIS)
        asked = true
        if (Catching { focusRequester.requestFocus() }.isSuccess) keyboard?.show()
    }
}

/** About the length of a screen's push and a sheet rising, which both run on the default spring. */
private const val FOCUS_SETTLE_MILLIS = 300L
