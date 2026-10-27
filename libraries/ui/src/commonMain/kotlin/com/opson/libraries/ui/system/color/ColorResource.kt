package com.opson.libraries.ui.system.color

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.opson.system.Dimension
import com.opson.system.Radii
import com.opson.system.VerticalSpacerD100
import kotlin.math.roundToInt

/** A color token. Screens read them from `AppTheme.colors`; the objects here are the few fixed ones. */
@Stable
sealed class ColorResource(val color: Color, val designSystemName: String) {
    object Unspecified : ColorResource(Color.Unspecified, "unspecified")

    object Black : ColorResource(Color(0xFF000000), "black")

    object White : ColorResource(Color(0xFFFFFFFF), "white")

    /**
     * A token built at runtime: the theme's colors and every [withAlpha]. Equal by value, so a
     * composable handed `onHero.withAlpha(0.7f)` on each recomposition can still skip.
     */
    class FromColor(color: Color, name: String) : ColorResource(color, name) {
        override fun equals(other: Any?): Boolean =
            other is FromColor && other.color == color && other.designSystemName == designSystemName

        override fun hashCode(): Int = 31 * color.hashCode() + designSystemName.hashCode()
    }

    val onColor: ColorResource
        get() {
            return if (color.luminance() > 0.4) Black else White
        }

    fun withAlpha(alpha: Float) = FromColor(this.color.copy(alpha = alpha), this.designSystemName + "_a_${alpha}")
}

@Composable
fun animateColorResourceAsState(
    targetValue: ColorResource,
    animationSpec: AnimationSpec<ColorResource> = spring(),
    label: String = "ColorAnimation",
    finishedListener: ((ColorResource) -> Unit)? = null,
): State<ColorResource> {
    val converter: TwoWayConverter<ColorResource, AnimationVector4D> = remember(targetValue) {
        val colorConverter = (Color.VectorConverter)(targetValue.color.colorSpace)
        TwoWayConverter(convertToVector = { token: ColorResource ->
            colorConverter.convertToVector(token.color)
        }, convertFromVector = { vector ->
            ColorResource.FromColor(
                color = colorConverter.convertFromVector(vector),
                name = targetValue.designSystemName
            )
        })
    }

    return animateValueAsState(
        targetValue = targetValue,
        typeConverter = converter,
        animationSpec = animationSpec,
        label = label,
        finishedListener = finishedListener
    )
}

@Composable
internal fun ColorCard(
    colorResource: ColorResource,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier.Companion.padding(Dimension.D100)
            .background(colorResource.color, shape = Radii.Card.shape)
            .height(150.dp)
            .width(120.dp)
            .clip(Radii.Card.shape),
        contentAlignment = Alignment.BottomCenter
    ) {


        Column {
            if (colorResource.color.luminance() > 0.5f) {
                HorizontalDivider(
                    color = Color.DarkGray,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(Dimension.D500)
            ) {

                Text(
                    text = title,
                    color = Color.Black
                )

                VerticalSpacerD100()

                Text(
                    text = description,
                    color = Color.Black
                )
            }
        }

    }
}

/**
 * Extension function to convert a Color object to a hexadecimal string representation.
 * Includes the alpha value by default but can be omitted.
 *
 * @param includeAlpha whether to include the alpha value in the hex string.
 * @return A hex string representation of the color (e.g., "#FFFFFFFF" or "#FFFFFF" if alpha is omitted).
 */
fun ColorResource.toHexString(includeAlpha: Boolean = true): String {
    val color = this.color

    // Handle unspecified color explicitly
    if (color == Color.Unspecified) return "unspecified"

    fun componentToHex(component: Float): String {
        val intVal = (component * 255f).coerceIn(0f, 255f).roundToInt()
        return intVal.toString(16).uppercase().padStart(2, '0')
    }

    val alpha = if (includeAlpha) componentToHex(color.alpha) else ""
    val red = componentToHex(color.red)
    val green = componentToHex(color.green)
    val blue = componentToHex(color.blue)
    return "#${alpha}${red}${green}${blue}"
}