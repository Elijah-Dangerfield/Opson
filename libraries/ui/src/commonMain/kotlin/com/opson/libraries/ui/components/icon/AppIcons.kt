package com.opson.libraries.ui.components.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.Edit as OutlinedEdit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.MealTone
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Every icon the screens use, named after the Material Symbols ligature in the handoff and drawn
 * from the Material Rounded set. Feature code reaches icons only through here, so swapping one
 * glyph (or vendoring a Symbols-only one later) is a one-line change.
 *
 * Entries are decorative (`contentDescription = null`). Give a standalone icon a description with
 * [described]; an icon next to a text label should stay decorative.
 *
 * Where Rounded has no exact match the closest glyph stands in, noted on the entry.
 */
@Suppress("TooManyFunctions")
object AppIcons {
    // Meals
    val BakeryDining get() = icon("bakery_dining", MaterialIcons.Rounded.BakeryDining)
    val LunchDining get() = icon("lunch_dining", MaterialIcons.Rounded.LunchDining)
    val DinnerDining get() = icon("dinner_dining", MaterialIcons.Rounded.DinnerDining)

    /** Symbols' `nutrition` (an apple) has no Rounded twin; Eco's leaf is the closest food-adjacent glyph. */
    val Nutrition get() = icon("nutrition", MaterialIcons.Rounded.Eco)
    val Restaurant get() = icon("restaurant", MaterialIcons.Rounded.Restaurant)

    // Logging
    val Add get() = icon("add", MaterialIcons.Rounded.Add)
    val Remove get() = icon("remove", MaterialIcons.Rounded.Remove)
    val AddCircle get() = icon("add_circle", MaterialIcons.Rounded.AddCircle)
    val ContentCopy get() = icon("content_copy", MaterialIcons.Rounded.ContentCopy)
    val FileCopy get() = icon("file_copy", MaterialIcons.Rounded.FileCopy)
    val Delete get() = icon("delete", MaterialIcons.Rounded.Delete)
    val Mood get() = icon("mood", MaterialIcons.Rounded.Mood)
    val ViewAgenda get() = icon("view_agenda", MaterialIcons.Rounded.ViewAgenda)

    /** Symbols' `monitoring` (a line chart) has no Rounded twin; Insights is the same chart with sparkles. */
    val Monitoring get() = icon("monitoring", MaterialIcons.Rounded.Insights)
    val WaterDrop get() = icon("water_drop", MaterialIcons.Rounded.WaterDrop)
    val LocalDrink get() = icon("local_drink", MaterialIcons.Rounded.LocalDrink)

    /** Symbols' `barcode_scanner` is not in the Rounded set; the QR scanner frame reads the same at 24dp. */
    val BarcodeScanner get() = icon("barcode_scanner", MaterialIcons.Rounded.QrCodeScanner)
    val PhotoCamera get() = icon("photo_camera", MaterialIcons.Rounded.PhotoCamera)
    val Mic get() = icon("mic", MaterialIcons.Rounded.Mic)
    val EditNote get() = icon("edit_note", MaterialIcons.Rounded.EditNote)
    val Edit get() = icon("edit", MaterialIcons.Rounded.Edit)
    val EditOutline get() = icon("edit_outline", MaterialIcons.Outlined.OutlinedEdit)
    val Bookmark get() = icon("bookmark", MaterialIcons.Rounded.Bookmark)
    val BookmarkAdd get() = icon("bookmark_add", MaterialIcons.Rounded.BookmarkAdd)
    val History get() = icon("history", MaterialIcons.Rounded.History)
    val Dialpad get() = icon("dialpad", MaterialIcons.Rounded.Dialpad)
    val MonitorWeight get() = icon("monitor_weight", MaterialIcons.Rounded.MonitorWeight)
    val Schedule get() = icon("schedule", MaterialIcons.Rounded.Schedule)
    val LockClock get() = icon("lock_clock", MaterialIcons.Rounded.LockClock)

    // Navigation and chrome
    val Close get() = icon("close", MaterialIcons.Rounded.Close)
    val Check get() = icon("check", MaterialIcons.Rounded.Check)
    val CheckCircle get() = icon("check_circle", MaterialIcons.Rounded.CheckCircle)

    /** `check_circle` at FILL 0: the open close-day button on Today. */
    val CheckCircleOutline get() = icon("check_circle_outline", MaterialIcons.Rounded.CheckCircleOutline)
    val RadioButtonUnchecked get() = icon("radio_button_unchecked", MaterialIcons.Rounded.RadioButtonUnchecked)
    val MoreHoriz get() = icon("more_horiz", MaterialIcons.Rounded.MoreHoriz)
    val MoreVert get() = icon("more_vert", MaterialIcons.Rounded.MoreVert)
    val EventNote get() = icon("event_note", MaterialIcons.AutoMirrored.Rounded.EventNote)
    val Lock get() = icon("lock", MaterialIcons.Rounded.Lock)
    val DashboardCustomize get() = icon("dashboard_customize", MaterialIcons.Rounded.DashboardCustomize)
    val ChevronLeft get() = icon("chevron_left", MaterialIcons.Rounded.ChevronLeft)
    val ChevronRight get() = icon("chevron_right", MaterialIcons.Rounded.ChevronRight)
    val SwapHoriz get() = icon("swap_horiz", MaterialIcons.Rounded.SwapHoriz)
    val Tune get() = icon("tune", MaterialIcons.Rounded.Tune)
    val Search get() = icon("search", MaterialIcons.Rounded.Search)
    val SearchOff get() = icon("search_off", MaterialIcons.Rounded.SearchOff)
    val Cancel get() = icon("cancel", MaterialIcons.Rounded.HighlightOff)
    val ArrowBack get() = icon("arrow_back", MaterialIcons.AutoMirrored.Rounded.ArrowBack)
    val ExpandMore get() = icon("expand_more", MaterialIcons.Rounded.ExpandMore)
    val Settings get() = icon("settings", MaterialIcons.Rounded.Settings)
    val CalendarToday get() = icon("calendar_today", MaterialIcons.Rounded.CalendarToday)
    val CalendarMonth get() = icon("calendar_month", MaterialIcons.Rounded.CalendarMonth)
    val Insights get() = icon("insights", MaterialIcons.Rounded.Insights)
    val VisibilityOff get() = icon("visibility_off", MaterialIcons.Rounded.VisibilityOff)

    // Misc
    val Star get() = icon("star", MaterialIcons.Rounded.Star)
    val Favorite get() = icon("favorite", MaterialIcons.Rounded.Favorite)
    val Bolt get() = icon("bolt", MaterialIcons.Rounded.Bolt)
    val Science get() = icon("science", MaterialIcons.Rounded.Science)
    val FormatListBulleted get() = icon("format_list_bulleted", MaterialIcons.AutoMirrored.Rounded.FormatListBulleted)

    // Health
    val Bedtime get() = icon("bedtime", MaterialIcons.Rounded.Bedtime)
    val DirectionsWalk get() = icon("directions_walk", MaterialIcons.AutoMirrored.Rounded.DirectionsWalk)
    val Hiking get() = icon("hiking", MaterialIcons.Rounded.Hiking)
    val MonitorHeart get() = icon("monitor_heart", MaterialIcons.Rounded.MonitorHeart)

    /** The meal's tile icon. Uncategorized uses the list glyph. */
    fun meal(tone: MealTone): IconResource = when (tone) {
        MealTone.Breakfast -> BakeryDining
        MealTone.Lunch -> LunchDining
        MealTone.Snack -> Nutrition
        MealTone.Dinner -> DinnerDining
        MealTone.Uncategorized -> FormatListBulleted
    }

    /** Every entry, for the gallery. */
    internal val all: List<IconResource>
        get() = listOf(
            BakeryDining, LunchDining, DinnerDining, Nutrition, Restaurant,
            Add, Remove, AddCircle, ContentCopy, FileCopy, Delete, Mood, ViewAgenda, Monitoring, CheckCircleOutline, MoreVert, EventNote, Lock, DashboardCustomize, WaterDrop, LocalDrink, BarcodeScanner, PhotoCamera, Mic, EditNote, Edit,
            Bookmark, BookmarkAdd, EditOutline, History, Dialpad, MonitorWeight, Schedule, LockClock,
            Close, Check, CheckCircle, RadioButtonUnchecked, MoreHoriz, ChevronLeft, ChevronRight, SwapHoriz, Tune, Search,
            SearchOff, Cancel, ArrowBack, ExpandMore,
            Settings, CalendarToday, CalendarMonth, Insights, VisibilityOff,
            Star, Favorite, Bolt, Science, FormatListBulleted,
            Bedtime, DirectionsWalk, Hiking, MonitorHeart,
        )

    private fun icon(name: String, vector: ImageVector) = IconResource(
        imageVector = vector,
        contentDescription = null,
        identifier = name,
    )
}

/** The same icon with an accessibility label, for icons that stand alone. */
fun IconResource.described(contentDescription: String): IconResource =
    copy(contentDescription = contentDescription)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconGrid(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcons.all.forEach { icon ->
            Column(
                modifier = Modifier.width(72.dp).padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon = icon, size = IconSize.Small, color = AppTheme.colors.ink)
                Text(
                    text = icon.identifier.orEmpty(),
                    typography = AppTheme.type.caption,
                    color = AppTheme.colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(widthDp = 420, heightDp = 640)
@Composable
private fun AppIconsPreview() {
    PreviewContent(contentPadding = PaddingValues(16.dp)) {
        AppIconGrid()
    }
}

@Preview(widthDp = 420, heightDp = 640)
@Composable
private fun AppIconsDarkPreview() {
    PreviewContent(darkTheme = true, contentPadding = PaddingValues(16.dp)) {
        AppIconGrid()
    }
}
