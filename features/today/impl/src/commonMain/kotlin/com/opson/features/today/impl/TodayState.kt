package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FastingStatus
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
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
    /** The hero card at the top, or none (No numbers, a planned day). */
    val summary: TodaySummary? = null,
    /** End after a set time ran out: said once, on the day it switched, or offered while the pause holds it. */
    val banner: TodayBanner? = null,
    /** A day ahead: "Planned meals count as logged". */
    val planning: Boolean = false,
    /** No numbers' "Meals today" (design sync 4, 13.5d): each meal done or not, and when. */
    val mealTiles: List<MealTile>? = null,
    val water: WaterRow? = null,
    val meals: List<MealEntry> = emptyList(),
    /** Close the day's button in the header. None on a planned day, an untracked one, or before anything is logged. */
    val closeDay: CloseDayButton? = null,
    /**
     * Closed with Close the day: it takes nothing until it is reopened, so the + buttons, select,
     * swipe and the menu's adding items go, and a line says so with Reopen.
     */
    val closed: Boolean = false,
    /** The meal that just changed, outlined for a moment and scrolled to. */
    val highlight: MealSlot? = null,
    /** "Added 2 foods to dinner" with Undo, for six seconds after the logger adds (3.9). */
    val added: AddedNotice? = null,
    /**
     * The day is marked untracked: one calm card stands in for the hero card, the counts, the
     * planning banner and Close the day. The meals stay, so logging still works.
     */
    val untracked: Boolean = false,
    val menu: DayMenu? = null,
    /** The fasting card (0.2), from [TodayFastingCard]; null when it does not show. */
    val fasting: FastingStatus? = null,
    /** `release.todaySummary`: the dots, the chevron, long-press to customise, the sticky bar. */
    val summaryTools: Boolean = false,
    /** The summary page left open, by its place in [TodaySummary.Energy.pages]. Remembered across days and restarts. */
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

/** The last page: a few vitamins and minerals as a percent of a typical day's, and See all. */
data class NutrientsPage(val rows: List<KeyNutrient>)

/**
 * "Sodium, of a 2,300 mg limit, 38%". [reference] is the amount the percent is of, formatted.
 * [notListed]: no food today lists it, so the row says so instead of a percent. [opensFoods]: a
 * food gave some, so a tap lists them.
 */
data class KeyNutrient(
    val micro: Micronutrient,
    val label: StringResource,
    val reference: String,
    val isLimit: Boolean,
    val display: Display,
    val notListed: Boolean = false,
    val opensFoods: Boolean = false,
)

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
        /** The last page, key vitamins and minerals; null on an empty day, or when the levels hide micros. */
        val nutrientsPage: NutrientsPage? = null,
        val singleGoal: Boolean = false,
        /** Design sync 4's page 1 (`release.goalsAndEnergy`, All numbers); null draws the design sync 2 page. */
        val card: EnergyCard? = null,
    ) : TodaySummary {
        /** The pages the card swipes through, in order; the dots count them. */
        val pages: List<SummaryPage>
            get() = listOfNotNull(SummaryPage.Energy, macrosPage?.let { SummaryPage.Macros }, nutrientsPage?.let { SummaryPage.Nutrients })
    }

    /**
     * Calories set to Soft (once Nutrients first, design sync 4, 13.5b): meals counted when the mode counts
     * them, each visible nutrient as a word and a bar, and energy as a word over a neutral bar.
     */
    data class Soft(
        val mealCount: MealCount?,
        val rows: List<MacroTile>,
        val energy: Display,
    ) : TodaySummary

    /** Calories hidden but some nutrient visible (2.5): "Your focus today". */
    data class Focus(val rows: List<MacroTile>) : TodaySummary

    /**
     * Later, before the reveal (design sync 4, 13.5c): when totals open ([opensAt] today, or the
     * weekly check), and how much is logged, as counts. Never an amount.
     */
    data class Locked(val opensAt: LocalTime, val weekly: Boolean, val meals: Int, val foods: Int) : TodaySummary

    /** Nothing numeric on show (2.4): how many meals so far, and when the next one usually is. */
    data class Counted(val done: Int, val next: MealSlot?, val nextAround: LocalTime?) : TodaySummary
}

data class MacroTile(val metric: Metric, val label: StringResource, val display: Display)

enum class SummaryPage { Energy, Macros, Nutrients }

data class MealCount(val done: Int, val total: Int)

/**
 * End after a set time ran out (design sync 4, 13.5): [from] gave way to [to]. [waiting] when the
 * switch shows more with the pause on, so the banner offers it instead of saying it happened.
 */
data class TodayBanner(val from: PresetId, val to: PresetId, val waiting: Boolean)

data class MealTile(val slot: MealSlot, val status: TileStatus)

/** A meal on No numbers' timeline: logged at [Logged.time], marked eaten, or usually [Empty.around]. */
sealed interface TileStatus {
    data class Empty(val around: LocalTime) : TileStatus
    data class Logged(val time: LocalTime) : TileStatus
    data class AteNoDetails(val around: LocalTime) : TileStatus
}

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
        /** A Note on a meal with nothing logged yet still shows, under the row. */
        val note: MealNoteUi? = null,
    ) : MealEntry

    /** A planned meal on a day ahead: "Planned", its energy and foods. */
    data class Planned(override val slot: MealSlot, val calories: Display, val foods: String) : MealEntry

    /** A day ahead's empty meal: "Not planned" and Plan it. */
    data class NotPlanned(override val slot: MealSlot) : MealEntry
}

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
    /** Marked "ate, no details" (no longer offered; older marks still show): the card says so instead of listing items. */
    val ateNoDetails: Boolean,
    /** The meal's Note, under its foods. */
    val note: MealNoteUi? = null,
    /** Uncategorized with `release.todaySelect`: "Move to…" files its foods into a meal. */
    val fileable: Boolean = false,
)

/**
 * A meal's Note as Today shows it: the hunger level and the words, either one alone. Read from the
 * "How it felt" record the Note sheet writes; null when it holds neither.
 */
data class MealNoteUi(val hunger: StringResource?, val text: String?)

/** A food on a meal card. [precision] is null where only the household amount shows (No numbers). */
data class ItemRowUi(val entryId: String, val name: String, val amount: String, val precision: Precision?, val calories: Display)

/** [closed]: the day was closed with Close the day, so the button opens it to look back or reopen it. */
data class CloseDayButton(val closed: Boolean)

/** The ⋮ menu, grouped by what each item affects (2.11). */
data class DayMenu(val groups: List<List<DayMenuItem>>, val clearArmed: Boolean)

sealed interface DayMenuItem {
    data class CloseDay(val isToday: Boolean) : DayMenuItem

    /** In Close the day's place once the day is closed. */
    data class Reopen(val isToday: Boolean) : DayMenuItem
    data object Nutrients : DayMenuItem

    /** Every meal's Note, newest first. */
    data object Notes : DayMenuItem
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

    /** A row on the card's nutrients page: which foods gave it. */
    data class KeyNutrientTapped(val micro: Micronutrient) : TodayAction

    /** The summary card's "Energy ›" (design sync 4): Energy today. */
    data object EnergyTapped : TodayAction

    /** The main number's label: switches it, remembered for the goal style. */
    data object MainNumberToggled : TodayAction
    data class MealTileTapped(val slot: MealSlot) : TodayAction
    data class MealPlusTapped(val slot: MealSlot) : TodayAction
    data class PlanItTapped(val slot: MealSlot) : TodayAction
    data class ItemTapped(val entryId: String) : TodayAction

    /** A food row's ⋮ (`release.foodActions`). */
    data class ItemMenuTapped(val entryId: String) : TodayAction
    data object WaterPlusTapped : TodayAction
    data object CloseDayButtonTapped : TodayAction

    /** Reopen on the closed line or in the ⋮ menu: the day takes food again. */
    data object ReopenTapped : TodayAction
    data object HighlightFaded : TodayAction

    /** The logger added food (`RecentLogEvents.latest`) while Today showed [shown]. */
    data class LoggedArrived(val event: LoggedToMeal, val shown: LocalDate) : TodayAction
    data object UndoTapped : TodayAction

    /** [event]'s six seconds ran out. */
    data class UndoExpired(val event: LoggedToMeal) : TodayAction
    data object TrackAgainTapped : TodayAction

    /** "Switch to No numbers" on the banner, when End after a set time ran out with the pause on. */
    data class TimedSwitchTapped(val to: PresetId) : TodayAction

    // Fasting (0.2)
    data class FastingArrived(val status: FastingStatus?) : TodayAction
    data object FastingCardTapped : TodayAction
    data object EndFastTapped : TodayAction

    // The ⋮ menu
    data object DayMenuTapped : TodayAction
    data object MenuDismissed : TodayAction
    data object DayCloseMenuTapped : TodayAction
    data object DayNutrientsTapped : TodayAction
    data object NotesMenuTapped : TodayAction
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

    /** The foods that gave [micro] on [day]. */
    data class OpenNutrientFoods(val day: LocalDate, val micro: Micronutrient) : TodayEvent
    data object OpenNotes : TodayEvent
    data object OpenEnergyToday : TodayEvent
    data class OpenLogger(val day: LocalDate, val slot: MealSlot?) : TodayEvent
    data class OpenDayNote(val day: LocalDate) : TodayEvent
    data class OpenMealSheet(val day: LocalDate, val slot: MealSlot) : TodayEvent

    /** The food's actions (13.3d), from its row or its ⋮. */
    data class OpenItemSheet(val day: LocalDate, val entryId: String) : TodayEvent
    data class OpenWaterSheet(val day: LocalDate) : TodayEvent

    /** Something tried to add to [day], which is closed: "Today is closed. Reopen it to add more." */
    data class OpenDayClosed(val day: LocalDate) : TodayEvent
    data object OpenTodayCards : TodayEvent
    data object OpenFasting : TodayEvent
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
    /**
     * `release.goalsAndEnergy` (design sync 4): the summary card's presets and main number, the
     * list's own switches, and [burned] as what a set deficit or surplus aims at.
     */
    val goalsAndEnergyOn: Boolean = false,
    /** The day's Burned, with [goalsAndEnergyOn]; null while no resting source can say. */
    val burned: Burned? = null,
)

/**
 * A one-off message for the snackbar. [args] fill the message's `%1$s` slots; a
 * [StringResource] among them (a meal's sentence name) is resolved first.
 */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())
