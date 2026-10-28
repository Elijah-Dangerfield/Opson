package com.opson.libraries.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate

/** The two widget sizes both platforms offer, in the points an iPhone gives them. */
enum class WidgetSize(val width: Dp, val height: Dp) {
    Small(width = 158.dp, height = 158.dp),
    Medium(width = 338.dp, height = 158.dp),
}

/**
 * A home screen widget drawn in Compose, laid out as the Glance and SwiftUI widgets lay it out
 * (design v3 `widgets.png`): Today and a + on top, then the numbers card or the meal circles.
 * For the Widgets page, design sync and `CaloriesHiddenLeakTest`. It renders the [snapshot] and nothing
 * else, in the snapshot's own colours rather than the app theme, as the real widgets do.
 *
 * The amount carries `metric:calories` so the leak test sees it, as `NutrientValue` would tag it.
 */
@Composable
fun WidgetCard(
    snapshot: WidgetSnapshot,
    date: LocalDate,
    size: WidgetSize,
    modifier: Modifier = Modifier,
    dark: Boolean = AppTheme.colors.isDark,
) {
    val day = snapshot.dayFor(date) ?: return
    val swatch = if (dark) snapshot.colors.dark else snapshot.colors.light
    Column(
        modifier = modifier
            .widthIn(max = size.width)
            .fillMaxWidth()
            .height(size.height)
            .clip(WidgetDimens.CardShape)
            .background(widgetColor(swatch.background))
            .padding(WidgetDimens.Padding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = day.title, typography = AppTheme.type.cardTitle, color = swatch.ink.res(), modifier = Modifier.weight(1f))
            LogButton(label = snapshot.logLabel, swatch = swatch)
        }
        Spacer(Modifier.weight(1f))
        val energy = day.energy
        val note = day.note
        when {
            energy != null -> EnergyBlock(energy, swatch, small = size == WidgetSize.Small)
            day.meals.isNotEmpty() -> MealRow(day.meals, swatch, labels = size == WidgetSize.Medium)
            note != null -> Text(text = note, typography = AppTheme.type.bodyStrong, color = swatch.muted.res())
        }
    }
}

@Composable
private fun LogButton(label: String, swatch: WidgetSwatch) {
    Box(
        modifier = Modifier
            .size(WidgetDimens.LogButton)
            .clip(WidgetDimens.LogButtonShape)
            .background(widgetColor(swatch.accent))
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = AppIcons.Add, size = WidgetDimens.LogIcon, color = swatch.onAccent.res())
    }
}

@Composable
private fun EnergyBlock(energy: WidgetEnergy, swatch: WidgetSwatch, small: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WidgetDimens.BarGap)) {
        Row(
            modifier = Modifier.testTag("metric:${Metric.Calories.key}").semantics { contentDescription = energy.label },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(WidgetDimens.UnitGap),
        ) {
            Text(
                text = energy.amount,
                typography = if (small) AppTheme.type.figureLarge else AppTheme.type.figureDisplay,
                color = swatch.ink.res(),
            )
            Text(
                text = energy.unit,
                typography = AppTheme.type.bodyBold,
                color = swatch.muted.res(),
                modifier = Modifier.padding(bottom = WidgetDimens.UnitBaseline),
            )
        }
        EnergyBar(energy, swatch)
    }
}

/** The eaten bar with the range drawn as a dashed box over it, as Today's hero bar. */
@Composable
private fun EnergyBar(energy: WidgetEnergy, swatch: WidgetSwatch) {
    val track = widgetColor(swatch.track)
    val fill = widgetColor(swatch.bar)
    val outline = widgetColor(swatch.accent)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WidgetDimens.RangeHeight)
            .drawBehind {
                val barHeight = WidgetDimens.BarHeight.toPx()
                val top = (size.height - barHeight) / 2
                val radius = CornerRadius(barHeight / 2)
                drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
                if (energy.fraction > 0f) {
                    drawRoundRect(fill, topLeft = Offset(0f, top), size = Size(size.width * energy.fraction, barHeight), cornerRadius = radius)
                }
                val dash = WidgetDimens.Dash.toPx()
                val stroke = WidgetDimens.Stroke.toPx()
                val start = size.width * energy.rangeStart
                if (energy.rangeEnd > energy.rangeStart) {
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(start, stroke / 2),
                        size = Size(size.width * (energy.rangeEnd - energy.rangeStart), size.height - stroke),
                        cornerRadius = CornerRadius(WidgetDimens.RangeCorner.toPx()),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                    )
                } else if (start > 0f) {
                    // One number: a mark where it sits, as Today draws it.
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(start - WidgetDimens.Mark.toPx() / 2, 0f),
                        size = Size(WidgetDimens.Mark.toPx(), size.height),
                        cornerRadius = CornerRadius(WidgetDimens.Mark.toPx() / 2),
                    )
                }
            },
    )
}

@Composable
private fun MealRow(meals: List<WidgetMeal>, swatch: WidgetSwatch, labels: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        meals.forEach { meal ->
            val tone = swatch.meals[meal.slot] ?: WidgetTone(swatch.muted, swatch.track)
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = meal.description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WidgetDimens.LabelGap),
            ) {
                MealCircle(done = meal.done, tone = tone, size = if (labels) WidgetDimens.Circle else WidgetDimens.SmallCircle)
                if (labels) Text(text = meal.label, typography = AppTheme.type.bodyBold, color = swatch.ink.res())
            }
        }
    }
}

@Composable
private fun MealCircle(done: Boolean, tone: WidgetTone, size: Dp) {
    val content = widgetColor(tone.content)
    if (done) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(widgetColor(tone.container)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.Check, size = size / 2, color = content.res())
        }
    } else {
        Box(
            modifier = Modifier.size(size).drawBehind {
                val stroke = WidgetDimens.Stroke.toPx()
                val dash = WidgetDimens.Dash.toPx()
                drawCircle(
                    color = content,
                    radius = (this.size.minDimension - stroke) / 2,
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            },
        )
    }
}

/** The widget's own measurements, shared by Glance and SwiftUI by hand (they can't read these). */
internal object WidgetDimens {
    val CardShape = RoundedCornerShape(22.dp)
    val Padding = 16.dp
    val LogButton = 36.dp
    val LogButtonShape = RoundedCornerShape(10.dp)
    val LogIcon = 22.dp
    val UnitGap = 4.dp
    val UnitBaseline = 4.dp
    val BarGap = 10.dp
    val BarHeight = 8.dp
    val RangeHeight = 14.dp
    val RangeCorner = 4.dp
    val Stroke = 1.5.dp

    /** One number's mark on the bar, where a range has a box. */
    val Mark = 3.dp
    val Dash = 3.dp
    val Circle = 40.dp
    val SmallCircle = 26.dp
    val LabelGap = 6.dp
}

/** A snapshot colour for the design system's text and icons, which take a [ColorResource]. */
@Suppress("DEPRECATION")
private fun String.res(): ColorResource = widgetColor(this).res()

@Suppress("DEPRECATION")
private fun Color.res(): ColorResource = ColorResource.FromColor(this, "widget")
