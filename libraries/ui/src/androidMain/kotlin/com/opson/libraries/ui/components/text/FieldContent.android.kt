package com.opson.libraries.ui.components.text

import androidx.compose.ui.text.input.PlatformImeOptions

/** Android's autofill reads the semantics ([fieldContent]); its IME options have nothing to add. */
internal actual fun platformImeOptionsFor(content: FieldContent): PlatformImeOptions? = null
