package com.opson.features.today.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource

/**
 * What the Today tab draws (design v3, 2.1 to 2.11), built by [TodayStateBuilder].
 * Every number in it is policy-resolved, and which cards exist is decided there
 * from the layout and the levels, never here or in the screen.
 */
data class TodayState(
    val loading: Boolean = true,
    val day: LocalDate,
    val dayLabel: RelativeDay = RelativeDay.Today,
    val canGoForward: Boolean = true,
    /** The hero card at the top, or none (Rhythm, Mindful, a planned day). */
    val summary: TodaySummary? = null,
    /** Sprint's "day 9 of 14". The other modes' banners are gone in v3. */
    val banner: TodayBanner? = null,
    /** A day ahead: "Planned meals count as logged". */
    val planning: Boolean = false,
    /** Counts that stay visible while totals are locked: "3 meals logged", "3 glasses". */
    val counts: Counts? = null,
    val mealTiles: List<MealTile>? = null,
    val rhythm: RhythmCard? = null,
    val safeFoods: List<String>? = null,
    val water: WaterRow? = null,
    val meals: List<MealEntry> = emptyList(),
    /** "What you had": Blind's per meal list, Rhythm's single line. */
    val whatYouHad: WhatYouHad? = null,
    /** The labeled "Close the day" card after the meals. None on a planned day. */
    val closeDay: CloseDayCard? = null,
    /** The meal that just changed, outlined for a moment and scrolled to. */
    val highlight: MealSlot? = null,
    /** "Added 2 foods to dinner" with Undo, for six seconds after the logger adds (3.9). */
    val added: AddedNotice? = null,
    /**
     * The day is marked untracked: one calm card stands in for the hero card, the counts, the
     * planning banner and Close the day. The meals stay, so logging still works.
     */
    val untracked: Boolean = false,
    /** Explorer's food bridge (2.8), until "I tried it" or "Not today". */
    val bridge: FoodBridgeCard? = null,
    val menu: DayMenu? = null,
)

/** [count] foods just went into [slot]. A count of foods, never an amount. */
data class AddedNotice(val slot: MealSlot, val count: Int)

/**
 * "A small step from [from]" / "You've had [to] before, and it has [shared] in it too." The
 * names are ready for the middle of a sentence ("rice pudding", but "Thai curry").
 */
data class FoodBridgeCard(val from: String, val to: String, val shared: String)

sealed interface TodaySummary {
    /**
     * Calories shown (2.1): eaten with the range as a dashed box, then the macros.
     * [empty] says "Nothing yet" instead of 0, and hides the macros.
     */
    data class Energy(
        val eaten: Display,
        val empty: Boolean,
        val range: Display,
        val rangeStart: Float,
        val rangeEnd: Float,
        val macros: List<MacroTile>,
        /** Expended so far from a connected health app (0.2): energy out, today only. */
        val expended: Display = Display.Hidden,
    ) : TodaySummary

    /** Calories softened (2.6): meals counted when the mode counts them, the lead macro, the rest as bars. */
    data class Soft(
        val mealCount: MealCount?,
        val lead: MacroTile?,
        val rows: List<MacroTile>,
        val energy: Display,
    ) : TodaySummary

    /** Calories hidden but some nutrient visible (2.5): "Your focus today". */
    data class Focus(val rows: List<MacroTile>) : TodaySummary

    /** Delayed and not yet open (2.3). */
    data class Locked(val title: StringResource, val body: StringResource) : TodaySummary

    /** Delayed and open tonight: "Your totals are open", linking to Day close. */
    data object Revealed : TodaySummary

    /** Nothing numeric on show (2.4): how many meals so far, and when the next one usually is. */
    data class Counted(val done: Int, val next: MealSlot?, val nextAround: LocalTime?) : TodaySummary

    /** Explorer (2.8): foods first tried this month. */
    data class NewFoods(val foods: List<NewFood>) : TodaySummary
}

data class MacroTile(val metric: Metric, val label: StringResource, val display: Display)

data class MealCount(val done: Int, val total: Int)

data class Counts(val meals: Int, val glasses: Int?)

data class TodayBanner(val text: StringResource, val args: List<Any> = emptyList())

data class MealTile(val slot: MealSlot, val status: TileStatus)

sealed interface TileStatus {
    data class Empty(val around: LocalTime) : TileStatus
    data class Items(val count: Int, val time: LocalTime) : TileStatus
    data object AteNoDetails : TileStatus
}

data class RhythmCard(val done: Int, val total: Int, val rows: List<RhythmRow>)

/** [showActions] only on the next meal still to come, so the card is not a wall of buttons. */
data class RhythmRow(val slot: MealSlot, val status: RhythmStatus, val showActions: Boolean)

sealed interface RhythmStatus {
    data class Pending(val around: LocalTime) : RhythmStatus
    data class Logged(val time: LocalTime, val around: LocalTime) : RhythmStatus
    data class AteNoDetails(val around: LocalTime) : RhythmStatus
}

/** A food first logged this month; [seen] is "Today", "Sat" or "Oct 20". */
data class NewFood(val name: String, val seen: String)

/** [filled] of [total] glasses; no segments when the amount itself is hidden. */
data class WaterRow(val display: Display, val filled: Int, val total: Int, val showSegments: Boolean)

/** How one meal shows, chosen by the layout and whether it has anything in it. */
sealed interface MealEntry {
    val slot: MealSlot

    /** A meal with its items (Full layouts). */
    data class Full(val card: MealCard) : MealEntry {
        override val slot: MealSlot get() = card.slot
    }

    /**
     * An empty meal as one row. The next meal to come ([usual]) says "Usually around" and
     * offers yesterday's version, or Saved meals when there is no yesterday.
     */
    data class Collapsed(
        override val slot: MealSlot,
        val around: LocalTime,
        val usual: Boolean,
        val yesterday: Yesterday?,
        val offerSaved: Boolean,
    ) : MealEntry

    /** A meal as its foods in a line (Explorer), or how it felt (Mindful). */
    data class Compact(
        override val slot: MealSlot,
        val title: StringResource,
        val time: LocalTime?,
        val foods: String,
        val feel: FeelLine?,
        val askFeel: Boolean,
    ) : MealEntry

    /** A planned meal on a day ahead: "Planned", its energy and foods. */
    data class Planned(override val slot: MealSlot, val calories: Display, val foods: String) : MealEntry

    /** A day ahead's empty meal: "Not planned" and Plan it. */
    data class NotPlanned(override val slot: MealSlot) : MealEntry
}

/** Yesterday's version of an empty meal, to copy in one tap. */
data class Yesterday(val foods: String, val calories: Display)

/** How a meal felt, as chips: "Hungry → Satisfied", then moods. */
data class FeelLine(val hunger: StringResource?, val fullness: StringResource?, val moods: List<StringResource>)

data class MealCard(
    val slot: MealSlot,
    /** The slot's name, or "All food" when meal groups are off. */
    val title: StringResource,
    val time: LocalTime?,
    val calories: Display,
    val macros: List<MacroPart>,
    /** Compact (13P 80C 8F) when every macro shows, else the long form of the ones that do. */
    val macroStyle: MacroLineStyle,
    val items: List<ItemRowUi>,
    /** Marked "ate, no details": the card says so instead of listing items. */
    val ateNoDetails: Boolean,
    val feel: List<StringResource>,
    val note: String?,
)

data class ItemRowUi(val entryId: String, val name: String, val amount: String, val precision: Precision, val calories: Display)

sealed interface WhatYouHad {
    /** One labelled line per logged meal (Blind). */
    data class PerMeal(val rows: List<Pair<MealSlot, String>>) : WhatYouHad

    /** Every meal's foods in one paragraph (Rhythm). */
    data class Together(val text: String) : WhatYouHad
}

data class CloseDayCard(val closed: Boolean)

/** The ⋮ menu, grouped by what each item affects (2.11). */
data class DayMenu(val groups: List<List<DayMenuItem>>, val clearArmed: Boolean)

sealed interface DayMenuItem {
    data class CloseDay(val isToday: Boolean) : DayMenuItem
    data object Nutrients : DayMenuItem
    data object CopyYesterday : DayMenuItem
    data object CopyToTomorrow : DayMenuItem
    data object AddNote : DayMenuItem

    /** "Mark as untracked", or "Track this day again" once [marked]. */
    data class Untracked(val marked: Boolean) : DayMenuItem
    data object EditCards : DayMenuItem
    data object ClearDay : DayMenuItem
}

sealed interface TodayAction {
    data class InputsArrived(val inputs: TodayInputs) : TodayAction
    data class PolicyArrived(val policy: VisibilityPolicy) : TodayAction
    data object PreviousDayTapped : TodayAction
    data object NextDayTapped : TodayAction
    data object DayLabelTapped : TodayAction
    data object CalendarTapped : TodayAction
    data object NutrientsTapped : TodayAction
    data object RevealedTapped : TodayAction
    data class MealTileTapped(val slot: MealSlot) : TodayAction
    data class MealPlusTapped(val slot: MealSlot) : TodayAction
    data class PlanItTapped(val slot: MealSlot) : TodayAction
    data class CopyYesterdayMealTapped(val slot: MealSlot) : TodayAction
    data class SavedMealsTapped(val slot: MealSlot) : TodayAction
    data class FeelPromptTapped(val slot: MealSlot) : TodayAction
    data class ItemTapped(val entryId: String) : TodayAction
    data object WaterPlusTapped : TodayAction
    data class AlreadyAteTapped(val slot: MealSlot) : TodayAction
    data class RhythmLogTapped(val slot: MealSlot) : TodayAction
    data object CloseDayCardTapped : TodayAction
    data object HighlightFaded : TodayAction

    /** The logger added food (`RecentLogEvents.latest`) while Today showed [shown]. */
    data class LoggedArrived(val event: LoggedToMeal, val shown: LocalDate) : TodayAction
    data object UndoTapped : TodayAction

    /** [event]'s six seconds ran out. */
    data class UndoExpired(val event: LoggedToMeal) : TodayAction
    data object TrackAgainTapped : TodayAction
    data object BridgeTriedTapped : TodayAction
    data object BridgeNotTodayTapped : TodayAction

    // The ⋮ menu
    data object DayMenuTapped : TodayAction
    data object MenuDismissed : TodayAction
    data object DayCloseMenuTapped : TodayAction
    data object DayNutrientsTapped : TodayAction
    data object CopyYesterdayTapped : TodayAction
    data object CopyToTomorrowTapped : TodayAction
    data object DayNoteTapped : TodayAction
    data object UntrackedMenuTapped : TodayAction
    data object EditCardsTapped : TodayAction
    data object ClearDayTapped : TodayAction
}

sealed interface TodayEvent {
    data class OpenCalendar(val day: LocalDate) : TodayEvent
    data class OpenDayClose(val day: LocalDate) : TodayEvent
    data class OpenNutrients(val day: LocalDate) : TodayEvent
    data class OpenLogger(val day: LocalDate, val slot: MealSlot?) : TodayEvent

    /** The logger with [foodId] already picked (the food bridge's "I tried it"). */
    data class OpenLoggerWithFood(val day: LocalDate, val foodId: String, val isCustom: Boolean) : TodayEvent
    data class OpenLog(val day: LocalDate, val slot: MealSlot, val tab: LogTab) : TodayEvent
    data class OpenDayNote(val day: LocalDate) : TodayEvent
    data class OpenMealSheet(val day: LocalDate, val slot: MealSlot) : TodayEvent
    data class OpenMealFeel(val day: LocalDate, val slot: MealSlot) : TodayEvent
    data class OpenItemSheet(val day: LocalDate, val entryId: String) : TodayEvent
    data class OpenWaterSheet(val day: LocalDate) : TodayEvent
    data object OpenTodayCards : TodayEvent
    data class OpenPause(val change: NumbersChange) : TodayEvent
    data class ShowToast(val toast: Toast) : TodayEvent
}

/** Everything Today reads, gathered so the builder is a pure function of it. */
data class TodayInputs(
    val today: LocalDate,
    val timeZone: TimeZone,
    val day: Day,
    /** The day before [day], for "yesterday's breakfast". */
    val dayBefore: Day,
    val settings: Settings,
    /** Only collected in the Explorer layout. */
    val explorer: ExplorerStats? = null,
    /** Today's energy from a connected health app; null when not connected or `release.health` is off. */
    val expended: EnergyExpended? = null,
)

/**
 * A one-off message for the snackbar. [args] fill the message's `%1$s` slots; a
 * [StringResource] among them (a meal's sentence name) is resolved first.
 */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())
