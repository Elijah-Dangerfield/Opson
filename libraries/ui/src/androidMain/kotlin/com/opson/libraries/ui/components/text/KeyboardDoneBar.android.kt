package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable

/** Every Android keyboard carries an action key, so there is no bar to add. */
@Composable
internal actual fun KeyboardDoneBar(active: Boolean, onDone: () -> Unit) = Unit
