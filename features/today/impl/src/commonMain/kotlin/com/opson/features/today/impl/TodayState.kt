package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
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
    /** The fasting card (0.2), from [TodayFastingCard]; null when it does not show. */
    val fasting: FastingStatus? = null,
    /** `release.todaySummary`: the dots, the chevron, long-press to customise, the sticky bar. */
    val summaryTools: Boolean = false,
    /** The summary page left open: 0 energy, 1 macros. Remembered across days and restarts. */
    val summaryPage: Int = 0,
    /** The summary shrunk to one pinned line by choice. */
    val collapsed: Boolean = false,
    /** The summary as one line: the sticky bar for each page, and the collapsed line. */
    val compact: CompactSummary? = null,
    /** The day's latest weigh-in, through the weight level. */
    val weight: WeightRow? = null,
    /** `release.todaySelect`: long-press to select, drag between meals, swipe to delete. */
    val selectTools: Boolean = false,
    /** `release.foodActions`: each food row's ⋮, opening the food's actions (13.3d). */
    val foodMenus: Boolean = false,
    /** Foods being selected (13.3b); null when not selecting. */
    val selection: Selection? = null,
    /** Which food row is swiped open, and which has turned into its confirmation (2.13, 2.14). */
    val swipe: SwipeState = SwipeState(),
    /** "Removed banana" with Undo, for six seconds after a removal. */
    val removed: RemovedNotice? = null,
    /** "Moved 2 foods to dinner" with Undo, for six seconds after a move or a drop (13.3c). */
    val moved: MovedNotice? = null,
)

/** One piece of the eaten bar: a macro's share of the energy, or ([metric] null) the rest in one neutral piece. */
data class BarPart(val metric: Metric?, val weight: Float)

/** A stat on page 1 of the summary: its value is an amount through the policy, or a count. */
/** [label] is the stat's own, but "To your goal" for [SummaryStat.ToRange] when the day's goal is one number. */
data class StatTile(val stat: SummaryStat, val value: StatValue, val label: StringResource = stat.label)

sealed interface StatValue {
    data class Amount(val metric: Metric, val display: Display) : StatValue

    /** "3 of 4" meals, or glasses of water. */
    data class Count(val done: Int, val total: Int) : StatValue
}

/** Page 2 (2.1b): "of 1,245 kcal", the macros two by two, and each Show macro's share of the energy. */
data class MacrosPage(val eaten: Display, val rows: List<MacroTile>, val shares: List<MacroShare>)

data class MacroShare(val metric: Metric, val label: StringResource, val share: Display, val weight: Float)

/** The summary as one line (2.1c, 2.1d). [pages] are the sticky bar per page; [collapsed] joins page 1's lead to page 2's. */
data class CompactSummary(val pages: List<CompactLine>, val collapsed: CompactLine)

data class CompactLine(val parts: List<CompactPart>, val bar: CompactBar? = null)

/** The eaten bar under a compact line, only where calories are shown. */
data class CompactBar(val eaten: Display, val rangeStart: Float, val rangeEnd: Float, val parts: List<BarPart>)

sealed interface CompactPart {
    /** An amount, with [label] before it ("range 1,900 to 2,300", "Protein Partway"). */
    data class Amount(val metric: Metric, val display: Display, val label: StringResource? = null) : CompactPart

    /** The macros as the meal headers write them: "51P 162C 44F". */
    data class Macros(val parts: List<MacroPart>) : CompactPart

    /** "3 of 4 meals". */
    data class Meals(val done: Int, val total: Int) : CompactPart

    /** "3 meals logged", while totals are locked. */
    data class Logged(val meals: Int) : CompactPart
}

/** "This morning · 7:02 AM" and the weight, Soft or Shown; the row is absent when weight is Hide. */
data class WeightRow(val display: Display, val at: LocalTime, val morning: Boolean)

/**
 * Foods picked in select mode (13.3b): their [count], the first one's name for the card a drag
 * carries ("Apple + 1 more"), the meals they sit in, and which [step] the bottom bar is on. No
 * totals: the design's select mode shows none.
 */
data class Selection(
    val ids: Set<String>,
    val count: Int,
    val lead: String?,
    val slots: Set<MealSlot>,
    val step: SelectStep = SelectStep.Actions,
)

sealed interface SelectStep {
    /** The bottom bar: Move, Copy to…, Delete. */
    data object Actions : SelectStep
    data class CopyTo(val day: CopyDay = CopyDay.Today, val slot: MealSlot? = null) : SelectStep
    data class MoveTo(val slot: MealSlot? = null) : SelectStep

    /** "Remove 3 foods?" in place of the actions. */
    data object ConfirmRemove : SelectStep
}

/** Where Copy to… can send foods, counted from the real today. */
enum class CopyDay(val offset: Int) { Yesterday(-1), Today(0), Tomorrow(1) }

/** [open]: the row with Delete showing. [confirming]: the row turned into "Remove banana from breakfast?". */
data class SwipeState(val open: String? = null, val confirming: String? = null)

/** What was just removed: one food by [name] in [slot], or [count] foods. Never an amount. */
data class RemovedNotice(val count: Int, val name: String?, val slot: MealSlot?)

/** [count] foods, or the one called [name], just moved to [slot]. Never an amount. */
data class MovedNotice(val count: Int, val name: String?, val slot: MealSlot)

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
     * [empty] says "Nothing yet" instead of 0, and hides the macros. [singleGoal] is a day whose
     * goal is one number (one number, a deficit or a surplus): "Your goal 2,100", and the bar
     * marks it with a line where a range has a box.
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
        /** The eaten fill split by macro (design sync 2). Empty on an empty day. */
        val bar: List<BarPart> = emptyList(),
        /** Page 1's stats (`release.todaySummary`); none on an empty day. */
        val stats: List<StatTile> = emptyList(),
        /** Page 2, the macros (`release.todaySummary`); null when off or nothing to show. */
        val macrosPage: MacrosPage? = null,
        val singleGoal: Boolean = false,
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

data class RhythmCard(
    val done: Int,
    val total: Int,
    val rows: List<RhythmRow>,
    /** The card under the checklist (0.2, `release.reminders`); none when the flag is off. */
    val reminder: RhythmReminder? = null,
)

/** v3 `today-rhythm.png`: "Dinner reminder / 7:00 PM, only if dinner isn't logged / Change times". */
sealed interface RhythmReminder {
    data class Next(val slot: MealSlot, val at: LocalTime) : RhythmReminder

    /** Meal reminders are off: "Meal reminders / Off / Set up". */
    data object Off : RhythmReminder
}

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
     * An empty meal as one row. The next meal to come ([usual]) says "Usually around". Copying
     * yesterday's version lives in the add sheet now (design sync 2, 2.2).
     */
    data class Collapsed(
        override val slot: MealSlot,
        val around: LocalTime,
        val usual: Boolean,
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
    val note: String?,
    /** Uncategorized with `release.todaySelect`: "Move to…" files its foods into a meal. */
    val fileable: Boolean = false,
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
    /** The day's title: the calendar drops from it. */
    data object DayLabelTapped : TodayAction
    data object NutrientsTapped : TodayAction
    data object RevealedTapped : TodayAction
    data class MealTileTapped(val slot: MealSlot) : TodayAction
    data class MealPlusTapped(val slot: MealSlot) : TodayAction
    data class PlanItTapped(val slot: MealSlot) : TodayAction
    data class FeelPromptTapped(val slot: MealSlot) : TodayAction
    data class ItemTapped(val entryId: String) : TodayAction

    /** A food row's ⋮ (`release.foodActions`). */
    data class ItemMenuTapped(val entryId: String) : TodayAction
    data object WaterPlusTapped : TodayAction
    data class AlreadyAteTapped(val slot: MealSlot) : TodayAction

    /** Change times or Set up, on the Rhythm card's reminder line. */
    data object RemindersTapped : TodayAction
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

    // Fasting (0.2)
    data class FastingArrived(val status: FastingStatus?) : TodayAction
    data object FastingCardTapped : TodayAction
    data object EndFastTapped : TodayAction

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

    // The summary card (release.todaySummary)
    data class SummaryPageShown(val page: Int) : TodayAction
    data object CollapseTapped : TodayAction
    data object ExpandTapped : TodayAction
    data object SummaryLongPressed : TodayAction
    data object WeightTapped : TodayAction

    // Select foods, drag them between meals, swipe to delete (release.todaySelect)
    data class FoodLongPressed(val entryId: String) : TodayAction
    data class FoodToggled(val entryId: String) : TodayAction
    data class MealHeaderToggled(val slot: MealSlot) : TodayAction
    data object SelectAllTapped : TodayAction
    data object SelectionClosed : TodayAction

    /** A row's handle was grabbed; a food not yet picked is picked, so it moves with the rest. */
    data class DragStarted(val entryId: String) : TodayAction

    /** The picked foods were let go over [slot]'s card. */
    data class FoodsDropped(val slot: MealSlot) : TodayAction

    /** A row's "Move to another meal" accessibility action: pick it and open Move. */
    data class MoveRowRequested(val entryId: String) : TodayAction
    data object MovedUndoTapped : TodayAction

    /** The move's six seconds ran out; [token] tells this move from a later one. */
    data class MovedUndoExpired(val token: Long) : TodayAction
    data object CopyToTapped : TodayAction
    data class CopyDayPicked(val day: CopyDay) : TodayAction
    data class CopySlotPicked(val slot: MealSlot) : TodayAction
    data object CopyConfirmed : TodayAction
    data object MoveTapped : TodayAction
    data class MoveSlotPicked(val slot: MealSlot) : TodayAction
    data object MoveConfirmed : TodayAction
    data object RemoveSelectedTapped : TodayAction
    data object RemoveSelectedConfirmed : TodayAction
    data object SelectStepBack : TodayAction
    data class MoveUncategorizedTapped(val ids: List<String>) : TodayAction
    data class RowSwipedOpen(val entryId: String) : TodayAction
    data object RowClosed : TodayAction
    data class DeleteRequested(val entryId: String) : TodayAction
    data object RowRemoveKept : TodayAction
    data class RowRemoveConfirmed(val entryId: String) : TodayAction
    data object ListScrolled : TodayAction
    data object RemovedUndoTapped : TodayAction

    /** The removal's six seconds ran out; [token] tells this removal from a later one. */
    data class RemovedUndoExpired(val token: Long) : TodayAction
}

sealed interface TodayEvent {
    data class OpenCalendar(val day: LocalDate) : TodayEvent
    data class OpenDayClose(val day: LocalDate) : TodayEvent
    data class OpenNutrients(val day: LocalDate) : TodayEvent
    data class OpenLogger(val day: LocalDate, val slot: MealSlot?) : TodayEvent

    /** The logger with [foodId] already picked (the food bridge's "I tried it"). */
    data class OpenLoggerWithFood(val day: LocalDate, val foodId: String, val isCustom: Boolean) : TodayEvent
    data class OpenDayNote(val day: LocalDate) : TodayEvent
    data class OpenMealSheet(val day: LocalDate, val slot: MealSlot) : TodayEvent
    data class OpenMealFeel(val day: LocalDate, val slot: MealSlot) : TodayEvent

    /** The food's actions (13.3d), from its row or its ⋮. */
    data class OpenItemSheet(val day: LocalDate, val entryId: String) : TodayEvent
    data class OpenWaterSheet(val day: LocalDate) : TodayEvent
    data object OpenTodayCards : TodayEvent
    data object OpenFasting : TodayEvent
    data object OpenReminders : TodayEvent
    data object OpenWeight : TodayEvent
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
    /** `release.reminders`: the Rhythm card's reminder line. */
    val remindersOn: Boolean = false,
    /** Today's energy from a connected health app; null when not connected or `release.health` is off. */
    val expended: EnergyExpended? = null,
    /** `release.todaySummary`. */
    val summaryOn: Boolean = false,
    /** `release.todaySelect`. */
    val selectOn: Boolean = false,
    /** `release.foodActions`. */
    val foodActionsOn: Boolean = false,
    /** Recent weigh-ins, oldest first, for the Weight row and its trend word. Only read with [summaryOn]. */
    val weighIns: List<WeighIn> = emptyList(),
)

/**
 * A one-off message for the snackbar. [args] fill the message's `%1$s` slots; a
 * [StringResource] among them (a meal's sentence name) is resolved first.
 */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())
