package com.opson.system

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.opson.libraries.ui.system.color.ColorResource

inline fun Modifier.thenIf(predicate: Boolean, factory: Modifier.() -> Modifier): Modifier {
    return if (predicate) then(factory(Modifier)) else this
}

inline fun <T : Any> Modifier.thenIfNotNull(
    value: T?,
    factory: Modifier.(T) -> Modifier
): Modifier {
    return if (value != null) then(factory(Modifier, value)) else this
}

fun Modifier.background(
    color: ColorResource,
): Modifier {
    return this.background(color.color)
}

fun Modifier.background(
    color: ColorResource,
    shape: Shape
): Modifier {
    return this.background(color.color, shape)
}
