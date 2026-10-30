package com.opson.libraries.ui.components.text

import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** What a field needs from [rememberFieldIme]: its keyboard actions, and a modifier that tracks its focus. */
internal class FieldIme(val actions: KeyboardActions, val modifier: Modifier)

/**
 * The keyboard handling every shared text field gets: [dismissingKeyboardActions] for its return
 * key, and a [KeyboardDoneBar] while it is focused on a keyboard with no way out of its own: a
 * number pad on iOS, or a multi-line field, whose return key types a new line.
 *
 * Put [FieldIme.modifier] on the field itself, ahead of the text field's own focus target, so it
 * sees the field's focus.
 */
@Composable
internal fun rememberFieldIme(keyboardActions: KeyboardActions, keyboardType: KeyboardType, singleLine: Boolean): FieldIme {
    val actions = dismissingKeyboardActions(keyboardActions)
    var focused by remember { mutableStateOf(false) }
    KeyboardDoneBar(active = focused && (!singleLine || keyboardType.lacksReturnKeyOnIos), onDone = actions::done)
    val modifier = remember { Modifier.onFocusChanged { focused = it.isFocused } }
    return FieldIme(actions, modifier)
}

/**
 * [actions] with every return key ending in a way out of the keyboard. Done, Go, Search and Send
 * run the caller's handler (if any) and then drop focus; Next moves to the next field, or drops
 * focus when there is nothing to move to, unless the caller handles it. Focus search wraps, so
 * Next on a form's last field goes back to its first: give the last field Done.
 *
 * Compose's own fallback for Done hides the keyboard controller, which on iOS leaves the field
 * focused and the keyboard up. Dropping focus is what ends the input session there.
 */
@Composable
private fun dismissingKeyboardActions(actions: KeyboardActions): KeyboardActions {
    val focusManager = LocalFocusManager.current
    fun finish(handler: (KeyboardActionScope.() -> Unit)?): KeyboardActionScope.() -> Unit = {
        handler?.invoke(this)
        focusManager.clearFocus()
    }
    return KeyboardActions(
        onDone = finish(actions.onDone),
        onGo = finish(actions.onGo),
        onSearch = finish(actions.onSearch),
        onSend = finish(actions.onSend),
        onNext = actions.onNext ?: { focusManager.moveFocusOrFinish(FocusDirection.Next) },
        onPrevious = actions.onPrevious ?: { focusManager.moveFocusOrFinish(FocusDirection.Previous) },
    )
}

private fun FocusManager.moveFocusOrFinish(direction: FocusDirection) {
    if (!moveFocus(direction)) clearFocus()
}

/**
 * The keyboards iOS draws without a return key, so nothing on them can close them.
 */
private val KeyboardType.lacksReturnKeyOnIos: Boolean
    get() = this == KeyboardType.Number ||
        this == KeyboardType.Decimal ||
        this == KeyboardType.Phone ||
        this == KeyboardType.NumberPassword

/**
 * The Done bar's tap, run as the field's Done key: the caller's handler, then focus dropped. The
 * same on a field whose key says Next, since the bar offers only Done.
 */
private fun KeyboardActions.done() {
    onDone?.invoke(DoneBarScope)
}

/**
 * The return key's [imeAction] run through these actions, for a field built on a `TextFieldState`,
 * which hands every action to one handler.
 */
internal fun KeyboardActions.perform(imeAction: ImeAction) {
    val handler = when (imeAction) {
        ImeAction.Done -> onDone
        ImeAction.Go -> onGo
        ImeAction.Next -> onNext
        ImeAction.Previous -> onPrevious
        ImeAction.Search -> onSearch
        ImeAction.Send -> onSend
        else -> null
    }
    handler?.invoke(DoneBarScope)
}

private object DoneBarScope : KeyboardActionScope {
    override fun defaultKeyboardAction(imeAction: ImeAction) = Unit
}

/**
 * While [active], a bar with a Done button rides on top of the iOS keyboard, for keyboards and
 * fields that otherwise offer no way to close it (the number pads, a multi-line note). Tapping
 * Done calls [onDone]. Nothing on Android, where every keyboard has an action key.
 *
 * Drawn as the text input view's `inputAccessoryView`, not as Compose content over the keyboard,
 * so it is part of the keyboard's frame: the scroll that keeps the focused field above the
 * keyboard, and `WindowInsets.ime`, both make room for it.
 */
@Composable
internal expect fun KeyboardDoneBar(active: Boolean, onDone: () -> Unit)
