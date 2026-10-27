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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.outlined.Sms as OutlinedSms
import androidx.compose.material.icons.outlined.Delete as OutlinedDelete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Support
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.Edit as OutlinedEdit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.LocalDining
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock as OutlinedLock
import androidx.compose.material.icons.outlined.Visibility as OutlinedVisibility
import androidx.compose.material.icons.outlined.VisibilityOff as OutlinedVisibilityOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.outlined.Info as OutlinedInfo
import com.opson.libraries.ui.components.icon.icons.Fruit as FruitGlyph
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bedtime
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ArrowDropDown
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

    /** Crossed fork and knife: Foods' "Create a meal". */
    val LocalDining get() = icon("local_dining", MaterialIcons.Rounded.LocalDining)

    // Food tiles (design sync 2): a picture for the kind of food on a row
    val RiceBowl get() = icon("rice_bowl", MaterialIcons.Rounded.RiceBowl)
    val Leaf get() = icon("eco", MaterialIcons.Rounded.Eco)

    /** No fruit in the Rounded set; drawn to match ([FruitGlyph]). */
    val Fruit get() = icon("fruit", FruitGlyph)
    val Icecream get() = icon("icecream", MaterialIcons.Rounded.Icecream)
    val SetMeal get() = icon("set_meal", MaterialIcons.Rounded.SetMeal)
    val Egg get() = icon("egg", MaterialIcons.Rounded.Egg)
    val Bread get() = icon("breakfast_dining", MaterialIcons.Rounded.BreakfastDining)
    val SoupKitchen get() = icon("soup_kitchen", MaterialIcons.Rounded.SoupKitchen)
    val LocalCafe get() = icon("local_cafe", MaterialIcons.Rounded.LocalCafe)

    // Logging
    val Add get() = icon("add", MaterialIcons.Rounded.Add)
    val Remove get() = icon("remove", MaterialIcons.Rounded.Remove)
    val AddCircle get() = icon("add_circle", MaterialIcons.Rounded.AddCircle)
    val ContentCopy get() = icon("content_copy", MaterialIcons.Rounded.ContentCopy)
    val Delete get() = icon("delete", MaterialIcons.Rounded.Delete)
    val Mood get() = icon("mood", MaterialIcons.Rounded.Mood)
    val ViewAgenda get() = icon("view_agenda", MaterialIcons.Rounded.ViewAgenda)

    val WaterDrop get() = icon("water_drop", MaterialIcons.Rounded.WaterDrop)
    val LocalDrink get() = icon("local_drink", MaterialIcons.Rounded.LocalDrink)

    /** Symbols' `barcode_scanner` is not in the Rounded set; the QR scanner frame reads the same at 24dp. */
    val BarcodeScanner get() = icon("barcode_scanner", MaterialIcons.Rounded.QrCodeScanner)
    val PhotoCamera get() = icon("photo_camera", MaterialIcons.Rounded.PhotoCamera)
    val PhotoLibrary get() = icon("photo_library", MaterialIcons.Rounded.PhotoLibrary)
    val Mic get() = icon("mic", MaterialIcons.Rounded.Mic)

    /** Stop listening: the mic button while it is on. */
    val Stop get() = icon("stop", MaterialIcons.Rounded.Stop)
    val EditNote get() = icon("edit_note", MaterialIcons.Rounded.EditNote)

    /** A page with a +: Foods' "Add a food". */
    val PostAdd get() = icon("post_add", MaterialIcons.Rounded.PostAdd)
    val Edit get() = icon("edit", MaterialIcons.Rounded.Edit)
    val EditOutline get() = icon("edit_outline", MaterialIcons.Outlined.OutlinedEdit)
    val Bookmark get() = icon("bookmark", MaterialIcons.Rounded.Bookmark)
    val BookmarkAdd get() = icon("bookmark_add", MaterialIcons.Rounded.BookmarkAdd)
    val History get() = icon("history", MaterialIcons.Rounded.History)
    val Dialpad get() = icon("dialpad", MaterialIcons.Rounded.Dialpad)
    val MonitorWeight get() = icon("monitor_weight", MaterialIcons.Rounded.MonitorWeight)
    val Schedule get() = icon("schedule", MaterialIcons.Rounded.Schedule)
    val LockClock get() = icon("lock_clock", MaterialIcons.Rounded.LockClock)

    /** A fast that is running, on the fasting status and Today's card. */
    val HourglassTop get() = icon("hourglass_top", MaterialIcons.Rounded.HourglassTop)

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
    val ArrowBack get() = icon("arrow_back", MaterialIcons.AutoMirrored.Rounded.ArrowBack)
    val ExpandMore get() = icon("expand_more", MaterialIcons.Rounded.ExpandMore)
    val ExpandLess get() = icon("expand_less", MaterialIcons.Rounded.ExpandLess)

    /** The handle a row is dragged by (Edit Today's cards). */
    val DragIndicator get() = icon("drag_indicator", MaterialIcons.Rounded.DragIndicator)
    val SelectAll get() = icon("select_all", MaterialIcons.Rounded.SelectAll)

    /** "Suggested from your goals" (Edit Today's cards). */
    val AutoAwesome get() = icon("auto_awesome", MaterialIcons.Rounded.AutoAwesome)
    val OpenInNew get() = icon("open_in_new", MaterialIcons.AutoMirrored.Rounded.OpenInNew)
    val SwapHoriz get() = icon("swap_horiz", MaterialIcons.Rounded.SwapHoriz)
    val Tune get() = icon("tune", MaterialIcons.Rounded.Tune)
    val Search get() = icon("search", MaterialIcons.Rounded.Search)
    val SearchOff get() = icon("search_off", MaterialIcons.Rounded.SearchOff)
    val Cancel get() = icon("cancel", MaterialIcons.Rounded.HighlightOff)
    val Settings get() = icon("settings", MaterialIcons.Rounded.Settings)
    val CalendarToday get() = icon("calendar_today", MaterialIcons.Rounded.CalendarToday)
    val CalendarMonth get() = icon("calendar_month", MaterialIcons.Rounded.CalendarMonth)
    val Insights get() = icon("insights", MaterialIcons.Rounded.Insights)
    val VisibilityOff get() = icon("visibility_off", MaterialIcons.Rounded.VisibilityOff)


    /** The outlined eye and eye-off, as design sync 2 draws the mode preview's lines. */
    val VisibilityOutline get() = icon("visibility_outline", MaterialIcons.Outlined.OutlinedVisibility)
    val VisibilityOffOutline get() = icon("visibility_off_outline", MaterialIcons.Outlined.OutlinedVisibilityOff)

    /** A softened number (a word or a fill bar): Symbols' `blur_on`, a field of dots. */
    val BlurOn get() = icon("blur_on", MaterialIcons.Rounded.BlurOn)

    // Account
    val Mail get() = icon("mail", MaterialIcons.Outlined.Email)

    /** The outlined lock, for a reassurance line rather than a locked control. */
    val LockOutlined get() = icon("lock_outlined", MaterialIcons.Outlined.OutlinedLock)

    // More and settings
    val Palette get() = icon("palette", MaterialIcons.Rounded.Palette)
    /** More's Reminders and celebrations row (v3: an outlined bell). */
    val Notifications get() = icon("notifications", MaterialIcons.Outlined.Notifications)
    val WaterDropOutline get() = icon("water_drop_outline", MaterialIcons.Outlined.WaterDrop)
    val Support get() = icon("support", MaterialIcons.Rounded.Support)

    val WorkspacePremium get() = icon("workspace_premium", MaterialIcons.Outlined.WorkspacePremium)
    val Download get() = icon("download", MaterialIcons.Rounded.Download)

    // Goal styles
    /** A range, drawn as a ruler. */
    val Straighten get() = icon("straighten", MaterialIcons.Rounded.Straighten)
    val TrendingDown get() = icon("trending_down", MaterialIcons.AutoMirrored.Rounded.TrendingDown)
    val EventRepeat get() = icon("event_repeat", MaterialIcons.Rounded.EventRepeat)

    /** A calendar with a check: Coast's weekly check on Trends. */
    val EventAvailable get() = icon("event_available", MaterialIcons.Rounded.EventAvailable)

    // Misc
    val Star get() = icon("star", MaterialIcons.Rounded.Star)

    /** The amount sheet's star before the food is saved. */
    val StarOutline get() = icon("star_outline", MaterialIcons.Rounded.StarOutline)

    /** An outlined "i", for a note that explains rather than warns. */
    val Info get() = icon("info", MaterialIcons.Outlined.OutlinedInfo)
    val Favorite get() = icon("favorite", MaterialIcons.Rounded.Favorite)
    val Science get() = icon("science", MaterialIcons.Rounded.Science)
    val FormatListBulleted get() = icon("format_list_bulleted", MaterialIcons.AutoMirrored.Rounded.FormatListBulleted)

    /** A day marked untracked (a holiday), as the Day menu draws it (v3 2.11). */
    val BeachAccess get() = icon("beach_access", MaterialIcons.Rounded.BeachAccess)

    /** Explorer's food bridge: a small step from one food to the next (v3 2.8). */
    val Route get() = icon("route", MaterialIcons.Rounded.Route)

    // Health
    val Bedtime get() = icon("bedtime", MaterialIcons.Rounded.Bedtime)

    /** Watch and health apps (More) and Expended so far (Today). */
    val Watch get() = icon("watch", MaterialIcons.Rounded.Watch)

    /** Widgets (More), as v3's "widgets" glyph. */
    val Widgets get() = icon("widgets", MaterialIcons.Rounded.Widgets)

    // Find support (design sync 2)
    /** Call a line: the phone handset. */
    val Call get() = icon("call", MaterialIcons.Rounded.Call)

    /** Text a line: the outlined message bubble with dots. */
    val Sms get() = icon("sms", MaterialIcons.Outlined.OutlinedSms)

    /** The country picker's globe. */
    val Public get() = icon("public", MaterialIcons.Rounded.Public)

    /** "Find support near you" and "Talk to someone": the headset. */
    val SupportAgent get() = icon("support_agent", MaterialIcons.Rounded.SupportAgent)

    /** Delete everything, outlined as design sync 2 draws it. */
    val DeleteOutline get() = icon("delete_outline", MaterialIcons.Outlined.OutlinedDelete)

    /** Targets on More: concentric rings (design sync 2). */
    val TrackChanges get() = icon("track_changes", MaterialIcons.Rounded.TrackChanges)

    // Progress photos (design sync 2, 8.4 and 8.5)
    /** Two frames split down the middle: progress photos' Compare. */
    val Compare get() = icon("compare", MaterialIcons.Rounded.Compare)

    /** The front and back camera: the capture screen's switch. */
    val Cameraswitch get() = icon("cameraswitch", MaterialIcons.Rounded.Cameraswitch)

    /** A filled down triangle: Compare's date pickers. */
    val ArrowDropDown get() = icon("arrow_drop_down", MaterialIcons.Rounded.ArrowDropDown)

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
            BakeryDining, LunchDining, DinnerDining, Nutrition, Restaurant, LocalDining, RiceBowl, Leaf,
            Fruit, Icecream, SetMeal, Egg, Bread, SoupKitchen, LocalCafe, StarOutline, Info, Add, Remove,
            AddCircle, ContentCopy, Delete, Mood, ViewAgenda, CheckCircleOutline, MoreVert, EventNote, Lock,
            DashboardCustomize, WaterDrop, LocalDrink, BarcodeScanner, PhotoCamera, PhotoLibrary, Mic, Stop,
            EditNote, PostAdd, Edit, Bookmark, BookmarkAdd, EditOutline, History, Dialpad, MonitorWeight,
            Schedule, LockClock, HourglassTop, Close, Check, CheckCircle, RadioButtonUnchecked, MoreHoriz,
            ChevronLeft, ChevronRight, SwapHoriz, Tune, Search, SearchOff, Cancel, ArrowBack, ExpandMore,
            ExpandLess, DragIndicator, SelectAll, AutoAwesome, Settings, CalendarToday, CalendarMonth,
            Insights, VisibilityOff, VisibilityOutline, VisibilityOffOutline, BlurOn, Mail, LockOutlined,
            OpenInNew, Palette, Notifications, WaterDropOutline, Support, WorkspacePremium, Download,
            Straighten, TrendingDown, EventRepeat, EventAvailable, Star, Favorite, Science,
            FormatListBulleted, BeachAccess, Route, Bedtime, Watch, Widgets, Call, Sms, Public, SupportAgent,
            DeleteOutline, TrackChanges, Compare, Cameraswitch, ArrowDropDown,
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
