package com.opson.features.logger.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.PhotoEstimate
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountUnits
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.round

/**
 * Photo estimate over the logger (docs/plans/screens/photo.md). Only Photo
 * on the + sheet and search's "Photograph it" open it, both behind `release.photo`.
 * [dayIso] and [slot] are where its "Add to {meal}" logs.
 */
@Serializable
data class PhotoEstimateRoute(val dayIso: String, val slot: MealSlot) : Route()

/** What the photo sheet draws. Built by [PhotoEstimateStateBuilder]; every energy value is policy-resolved. */
data class PhotoEstimateState(
    val slot: MealSlot = MealSlot.Dinner,
    val stage: PhotoStage = PhotoStage.Start,
    val note: String = "",
    /** The thumbnail, in memory only. */
    val photo: MealPhoto? = null,
    /** False where there is no camera, so only the library is offered. */
    val canTakePhoto: Boolean = true,
)

sealed interface PhotoStage {
    /** A note and the two ways to get a photo. */
    data object Start : PhotoStage

    data object Estimating : PhotoStage

    /** The v3 frame: editable lines and the footer. */
    data class Review(
        val rows: List<PhotoRowUi>,
        /** The stepper open under one line, or null. */
        val editor: PhotoAmountEditor?,
        /** The banner says the note was used. */
        val usedNote: Boolean,
        /** Null when there is nothing to add. */
        val footer: PhotoFooter?,
    ) : PhotoStage

    /** A search inside the sheet, to swap a line's food or add one it missed. */
    data class Swap(
        val replacing: Boolean,
        val query: String,
        /** Null while the catalog has not answered [query]. */
        val results: List<SwapResultUi>?,
        /** The catalog could not be reached: no results says so rather than "nothing by that name". */
        val offline: Boolean = false,
    ) : PhotoStage

    data object NothingFound : PhotoStage
    data object Unavailable : PhotoStage
    data object TooMany : PhotoStage
    data class Failed(val offline: Boolean) : PhotoStage
    data class CameraOff(val canAskAgain: Boolean) : PhotoStage
}

/** One estimate line: "Chicken breast, grilled", "about 120 g", "198". */
data class PhotoRowUi(
    val key: String,
    val name: String,
    val amount: String,
    /** A weight, which reads "about 120 g"; a household amount reads as it is. */
    val isWeight: Boolean,
    /** Bare, as the design draws a line: "198". */
    val calories: Display,
    /** False when the catalog had nothing for it: no energy, no stepper, but it can be swapped. */
    val matched: Boolean,
)

data class PhotoAmountEditor(
    val key: String,
    val amount: String,
    /** "cup", "tbsp", "grams". Grams are left out while calories are hidden and the food has a household measure. */
    val units: List<String>,
    val unitIndex: Int,
    val canLess: Boolean,
)

data class SwapResultUi(val id: String, val name: String, val subtitle: String?, val amount: String, val calories: Display)

/** "3 items · Estimate", "About 379 kcal": what Add writes. */
data class PhotoFooter(val count: Int, val calories: Display)

sealed interface PhotoEstimateAction {
    data class InputsArrived(val inputs: PhotoInputs) : PhotoEstimateAction
    data class PolicyArrived(val policy: VisibilityPolicy) : PhotoEstimateAction
    data class NoteTyped(val text: String) : PhotoEstimateAction
    data object TakePhotoTapped : PhotoEstimateAction
    data object ChooseFromLibraryTapped : PhotoEstimateAction
    data class CameraPermissionAnswered(val permission: CameraPermission) : PhotoEstimateAction
    data object AllowCameraTapped : PhotoEstimateAction
    data object OpenSettingsTapped : PhotoEstimateAction

    /** The camera or the picker answered. */
    data class PhotoAnswered(val result: PhotoPickResult, val fromCamera: Boolean) : PhotoEstimateAction

    /** The server's answer, with the catalog foods its matched lines name. */
    data class EstimateArrived(val estimate: PhotoEstimate, val foods: Map<String, Food>) : PhotoEstimateAction
    data class RowAmountTapped(val key: String) : PhotoEstimateAction
    data class LessTapped(val key: String) : PhotoEstimateAction
    data class MoreTapped(val key: String) : PhotoEstimateAction
    data class UnitPicked(val key: String, val index: Int) : PhotoEstimateAction
    data class RowRemoveTapped(val key: String) : PhotoEstimateAction
    data class RowNameTapped(val key: String) : PhotoEstimateAction
    data object AddMissedTapped : PhotoEstimateAction
    data class SwapTyped(val text: String) : PhotoEstimateAction
    data class SwapResultsArrived(val query: String, val foods: List<Food>, val offline: Boolean = false) : PhotoEstimateAction
    data class SwapPicked(val id: String) : PhotoEstimateAction
    data object TryAgainTapped : PhotoEstimateAction
    data object AnotherPhotoTapped : PhotoEstimateAction
    data object DescribeInsteadTapped : PhotoEstimateAction
    data object BackTapped : PhotoEstimateAction
    data object AddToMealTapped : PhotoEstimateAction
}

sealed interface PhotoEstimateEvent {
    data object LaunchCamera : PhotoEstimateEvent
    data object LaunchLibrary : PhotoEstimateEvent
    data object RequestCameraPermission : PhotoEstimateEvent

    /** Close this sheet and open the logger on Describe it. */
    data class OpenDescribe(val day: LocalDate, val slot: MealSlot) : PhotoEstimateEvent

    /** Written: close this sheet. Today takes it from `RecentLogEvents`. */
    data class Logged(val day: LocalDate, val committed: Committed) : PhotoEstimateEvent

    /** The day is closed (Close the day, maybe on another phone): nothing was logged. */
    data class DayClosed(val day: LocalDate) : PhotoEstimateEvent
    data object Dismiss : PhotoEstimateEvent
}

/** What the sheet reads: the day, for its policy context. */
data class PhotoInputs(val today: LocalDate, val day: Day)

/** Where the sheet has got to, before any of it is drawn. */
sealed interface PhotoStep {
    data object Start : PhotoStep
    data object Estimating : PhotoStep
    data object Review : PhotoStep
    data object NothingFound : PhotoStep
    data object Unavailable : PhotoStep
    data object TooMany : PhotoStep
    data class Failed(val offline: Boolean) : PhotoStep
    data class CameraOff(val canAskAgain: Boolean) : PhotoStep
}

/** The swap search: [replacing] is the line being swapped, or null when adding one. */
data class PhotoSwap(val replacing: String?, val query: String = "", val results: SwapAnswer? = null)

/** The catalog's answer for [query]. [offline]: it could not be reached, so an empty answer is not a miss. */
data class SwapAnswer(val query: String, val foods: List<Food>, val offline: Boolean = false)

/** Everything the photo sheet holds; [PhotoEstimateStateBuilder] draws it. */
data class PhotoModel(
    val slot: MealSlot,
    val step: PhotoStep = PhotoStep.Start,
    val note: String = "",
    val photo: MealPhoto? = null,
    val rows: List<PhotoRow> = emptyList(),
    val editing: String? = null,
    val swap: PhotoSwap? = null,
    /** The note was sent with the photo. */
    val usedNote: Boolean = false,
    val canTakePhoto: Boolean = true,
)

/**
 * One line of the estimate. [food] and [unit] are set when the catalog
 * matched it, and then the amount is the food's own; otherwise [parsed] is
 * all there is and the line logs as the server read it.
 */
data class PhotoRow(
    val key: String,
    val food: Food?,
    val unit: AmountUnit?,
    val quantity: Double,
    val parsed: ParsedItem? = null,
) {
    val name: String get() = food?.name ?: parsed?.name.orEmpty()

    val editable: Boolean get() = food != null && unit != null

    /**
     * Logged as Describe, always an estimate. A `photo` method would need a
     * server migration and would break a 0.1 phone pulling the item; the
     * telemetry still says photo.
     */
    fun draft(): LogDraft = if (food != null && unit != null) {
        food.draftFor(unit, quantity, Precision.Estimate, LogMethod.Describe)
    } else {
        checkNotNull(parsed) { "a line is a food or a parsed item" }.toLogDraft()
    }

    fun toLogPick(): LogPick = LogPick(
        key = key,
        ref = food?.let { FoodRef.Catalog(it.id) },
        draft = draft(),
        source = PickSource.Photo,
        food = food,
    )
}

/**
 * The amount rules for estimate lines, pure so they can be tested on their
 * own. [householdOnly] is true while calories are hidden: a weight is a
 * number too, so a food with a household measure is counted in it.
 */
object PhotoRows {

    /** A line from the server's item and, when it matched, the catalog food. */
    fun fromEstimate(key: String, item: ParsedItem, food: Food?, householdOnly: Boolean): PhotoRow {
        if (food == null) return PhotoRow(key = key, food = null, unit = null, quantity = item.quantity, parsed = item)
        val (unit, quantity) = initialAmount(food, item, householdOnly)
        return PhotoRow(key = key, food = food, unit = unit, quantity = quantity, parsed = item)
    }

    /** A food picked from the swap search, in place of [row] (keeping its weight where it can) or as a new line. */
    fun picked(key: String, food: Food, row: PhotoRow?, householdOnly: Boolean): PhotoRow {
        val grams = row?.draft()?.grams
        val (unit, quantity) = if (grams != null && grams > 0) fromGrams(food, grams, householdOnly) else firstUnit(food, householdOnly)
        return PhotoRow(key = key, food = food, unit = unit, quantity = quantity)
    }

    /** The units the stepper offers for [food]. */
    fun units(food: Food, householdOnly: Boolean): List<AmountUnit> {
        val all = food.amountUnits()
        return if (householdOnly && food.portions.isNotEmpty()) all.filterNot { it == AmountUnit.Grams } else all
    }

    /** A line counted in grams moves to the food's household measure once calories are hidden. */
    fun forPolicy(row: PhotoRow, householdOnly: Boolean): PhotoRow {
        val food = row.food ?: return row
        val unit = row.unit ?: return row
        if (!householdOnly || unit != AmountUnit.Grams || food.portions.isEmpty()) return row
        val to = units(food, householdOnly = true).first()
        return row.copy(unit = to, quantity = PortionAmount.convert(row.quantity, unit, to))
    }

    fun less(row: PhotoRow): PhotoRow = row.unit?.let { row.copy(quantity = PortionAmount.less(it, row.quantity)) } ?: row

    fun more(row: PhotoRow): PhotoRow = row.unit?.let { row.copy(quantity = PortionAmount.more(it, row.quantity)) } ?: row

    fun unitPicked(row: PhotoRow, index: Int, householdOnly: Boolean): PhotoRow {
        val food = row.food ?: return row
        val from = row.unit ?: return row
        val to = units(food, householdOnly).getOrNull(index) ?: return row
        return row.copy(unit = to, quantity = PortionAmount.convert(row.quantity, from, to))
    }

    /**
     * The portion the server matched, counted to the nearest half; else the
     * server's weight; else one of the food's first unit.
     */
    private fun initialAmount(food: Food, item: ParsedItem, householdOnly: Boolean): Pair<AmountUnit, Double> {
        val portion = food.portions.firstOrNull { it.label.equals(item.portionLabel, ignoreCase = true) }
        val grams = item.grams?.takeIf { it > 0 }
        return when {
            portion != null && grams != null -> AmountUnit.OfPortion(portion) to halves(grams / portion.grams)
            portion != null -> AmountUnit.OfPortion(portion) to max(HALF, item.quantity)
            grams != null -> fromGrams(food, grams, householdOnly)
            else -> firstUnit(food, householdOnly)
        }
    }

    private fun fromGrams(food: Food, grams: Double, householdOnly: Boolean): Pair<AmountUnit, Double> {
        if (householdOnly && food.portions.isNotEmpty()) {
            val to = units(food, householdOnly = true).first()
            return to to PortionAmount.convert(grams, AmountUnit.Grams, to)
        }
        return AmountUnit.Grams to max(1.0, round(grams))
    }

    private fun firstUnit(food: Food, householdOnly: Boolean): Pair<AmountUnit, Double> {
        val unit = units(food, householdOnly).first()
        return unit to PortionAmount.initial(unit)
    }

    private fun halves(value: Double): Double = max(HALF, round(value * 2) / 2)

    private const val HALF = 0.5
}
