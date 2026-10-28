package com.opson.libraries.ui.components

import androidx.compose.ui.window.PopupProperties

// A popup window is already as large as its content, which fills the screen.
internal actual fun windowCoveringPopupProperties() = PopupProperties(focusable = true)
