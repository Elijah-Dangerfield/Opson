package com.opson.libraries.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.PopupProperties

// Left to the default, an iOS popup is laid out inside the safe area, and a tap on the status bar
// or the home indicator strip would miss it.
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun windowCoveringPopupProperties() = PopupProperties(focusable = true, usePlatformInsets = false)
