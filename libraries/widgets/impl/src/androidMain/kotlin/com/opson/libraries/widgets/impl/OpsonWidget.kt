package com.opson.libraries.widgets.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.opson.libraries.widgets.WidgetColors
import com.opson.libraries.widgets.WidgetDay
import com.opson.libraries.widgets.WidgetEnergy
import com.opson.libraries.widgets.WidgetLinks
import com.opson.libraries.widgets.WidgetMeal
import com.opson.libraries.widgets.WidgetPalette
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSwatch
import com.opson.libraries.widgets.widgetColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The home screen widget on Android (Jetpack Glance), design v3 `widgets.png`. It draws the
 * snapshot the app wrote ([WidgetSnapshotStore]) and nothing else: Today and a +, then the
 * numbers card or the meal circles, whichever the snapshot holds. The visibility policy already
 * ran in shared Kotlin, so nothing here can show a number the mode hides.
 *
 * Laid out as `WidgetCard` (the Compose lookalike in `:libraries:widgets`) and the SwiftUI widget.
 * It picks the date's entry each time it draws, and the widget info asks for a redraw every 30
 * minutes, so it turns over at midnight with the app closed.
 *
 * Inert until [OpsonWidgetReceiver] is in the manifest, which waits for the 0.2 release.
 */
class OpsonWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.read(context)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val fallback = context.getString(R.string.widgets_android_fallback)
        provideContent {
            OpsonWidgetContent(snapshot = snapshot, today = today, appName = appName, fallback = fallback)
        }
    }

    internal companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

/** What the launcher talks to. Registered in the manifest only from the 0.2 release on. */
class OpsonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OpsonWidget()
}

/**
 * The widget for [snapshot] on [today]. Without a snapshot (the app has not written one yet, or
 * nobody is signed in) it shows the app's name, a line asking to open it, and the +.
 */
@Composable
internal fun OpsonWidgetContent(snapshot: WidgetSnapshot?, today: LocalDate, appName: String, fallback: String) {
    val context = LocalContext.current
    val small = LocalSize.current.width < OpsonWidget.MEDIUM.width
    val colors = snapshot?.colors ?: WidgetPalette.colors
    val day = snapshot?.dayFor(today)
    Box(modifier = GlanceModifier.fillMaxSize().appWidgetBackground().clickable(open(context, link = null))) {
        Image(
            provider = ImageProvider(R.drawable.widget_card),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colors.tint { it.background }),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(modifier = GlanceModifier.fillMaxSize().padding(Dimens.Padding)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = day?.title ?: appName,
                    style = TextStyle(color = colors.tint { it.ink }, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                LogButton(colors, label = snapshot?.logLabel, action = open(context, snapshot?.logLink ?: WidgetLinks.LOG))
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Body(day = day, colors = colors, small = small, fallback = fallback)
        }
    }
}

@Composable
private fun Body(day: WidgetDay?, colors: WidgetColors, small: Boolean, fallback: String) {
    val energy = day?.energy
    val note = if (day == null) fallback else day.note
    when {
        energy != null -> Energy(energy, colors, small)
        day != null && day.meals.isNotEmpty() -> Meals(day.meals, colors, small)
        note != null -> Text(
            text = note,
            style = TextStyle(color = colors.tint { it.muted }, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
    }
}

@Composable
private fun LogButton(colors: WidgetColors, label: String?, action: Action) {
    Box(modifier = GlanceModifier.size(Dimens.LogButton).clickable(action), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.widget_button),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colors.tint { it.accent }),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Image(
            provider = ImageProvider(R.drawable.widget_plus),
            contentDescription = label,
            colorFilter = ColorFilter.tint(colors.tint { it.onAccent }),
            modifier = GlanceModifier.size(Dimens.LogIcon),
        )
    }
}

@Composable
private fun Energy(energy: WidgetEnergy, colors: WidgetColors, small: Boolean) {
    val width = LocalSize.current.width - Dimens.Padding * 2
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.semantics { contentDescription = energy.label },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = energy.amount,
                style = TextStyle(color = colors.tint { it.ink }, fontSize = if (small) 22.sp else 30.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(Dimens.UnitGap))
            Text(
                text = energy.unit,
                style = TextStyle(color = colors.tint { it.muted }, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.padding(bottom = Dimens.UnitBaseline),
            )
        }
        Spacer(modifier = GlanceModifier.height(Dimens.BarGap))
        Bar(energy, colors, width)
    }
}

/** The eaten bar and the dashed range box (or one number's mark), positioned in dp because Glance has no fractions. */
@Composable
private fun Bar(energy: WidgetEnergy, colors: WidgetColors, width: Dp) {
    Box(modifier = GlanceModifier.width(width).height(Dimens.RangeHeight), contentAlignment = Alignment.CenterStart) {
        Image(
            provider = ImageProvider(R.drawable.widget_bar),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colors.tint { it.track }),
            modifier = GlanceModifier.width(width).height(Dimens.BarHeight),
        )
        if (energy.fraction > 0f) {
            Image(
                provider = ImageProvider(R.drawable.widget_bar),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colors.tint { it.bar }),
                modifier = GlanceModifier.width(width * energy.fraction).height(Dimens.BarHeight),
            )
        }
        val rangeWidth = width * (energy.rangeEnd - energy.rangeStart)
        if (rangeWidth > 0.dp) {
            Row {
                Spacer(modifier = GlanceModifier.width(width * energy.rangeStart))
                Image(
                    provider = ImageProvider(R.drawable.widget_range),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(colors.tint { it.accent }),
                    modifier = GlanceModifier.width(rangeWidth).height(Dimens.RangeHeight),
                )
            }
        } else if (energy.rangeStart > 0f) {
            // One number: a solid mark where it sits, as Today draws it.
            Row {
                Spacer(modifier = GlanceModifier.width(width * energy.rangeStart - Dimens.Mark / 2))
                Image(
                    provider = ImageProvider(R.drawable.widget_bar),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(colors.tint { it.accent }),
                    modifier = GlanceModifier.width(Dimens.Mark).height(Dimens.RangeHeight),
                )
            }
        }
    }
}

@Composable
private fun Meals(meals: List<WidgetMeal>, colors: WidgetColors, small: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        meals.forEach { meal ->
            Column(
                modifier = GlanceModifier.defaultWeight().semantics { contentDescription = meal.description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MealCircle(meal, colors, size = if (small) Dimens.SmallCircle else Dimens.Circle)
                if (!small) {
                    Spacer(modifier = GlanceModifier.height(Dimens.LabelGap))
                    Text(
                        text = meal.label,
                        style = TextStyle(color = colors.tint { it.ink }, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCircle(meal: WidgetMeal, colors: WidgetColors, size: Dp) {
    val content = colors.tint { it.meals[meal.slot]?.content ?: it.muted }
    Box(modifier = GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        if (meal.done) {
            Image(
                provider = ImageProvider(R.drawable.widget_circle),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colors.tint { it.meals[meal.slot]?.container ?: it.track }),
                modifier = GlanceModifier.fillMaxSize(),
            )
            Image(
                provider = ImageProvider(R.drawable.widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.size(size / 2),
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.widget_ring),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(content),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }
}

/** A day and a night colour for one token, so the widget follows the system's appearance. */
private fun WidgetColors.tint(pick: (WidgetSwatch) -> String): ColorProvider =
    ColorProvider(day = widgetColor(pick(light)), night = widgetColor(pick(dark)))

/**
 * Opens the app, the way a launcher icon does, carrying [link] when there is one. MainActivity
 * hands the link to `WidgetOpens` and clears it; SINGLE_TOP and CLEAR_TOP land it on the running
 * activity rather than a second one, as a tapped reminder does.
 */
private fun open(context: Context, link: String?): Action {
    val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (link != null) intent.data = Uri.parse(link)
    return actionStartActivity(intent)
}

/** The same measurements as `WidgetCard`'s, which Glance can't read from common code. */
private object Dimens {
    /** One number's mark on the bar, where a range has a box. */
    val Mark = 3.dp
    val Padding = 16.dp
    val LogButton = 36.dp
    val LogIcon = 22.dp
    val UnitGap = 4.dp
    val UnitBaseline = 4.dp
    val BarGap = 10.dp
    val BarHeight = 8.dp
    val RangeHeight = 14.dp
    val Circle = 40.dp
    val SmallCircle = 26.dp
    val LabelGap = 6.dp
}
