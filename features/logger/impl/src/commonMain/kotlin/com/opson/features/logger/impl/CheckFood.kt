package com.opson.features.logger.impl

import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * A Builder's "Check against the pack" on a catalog food (`release.builders`): a sheet over the
 * food's detail, or after the scanner with Also read the label on. Plain strings, no typeMap.
 */
@Serializable
data class CheckFoodRoute(val foodId: String, val name: String) : Route()

/** What the check sheet draws, built by [CheckFoodStateBuilder]. */
data class CheckFoodState(
    val name: String,
    val stage: CheckStage = CheckStage.Intro,
    val photo: MealPhoto? = null,
    val canTakePhoto: Boolean = true,
    val cameraOff: Boolean = false,
    val busy: Boolean = false,
    val problem: CheckProblem? = null,
)

sealed interface CheckStage {
    data object Intro : CheckStage
    data object Reading : CheckStage

    /** The photo shows no panel the phone could read, or too little of one. */
    data object Unreadable : CheckStage

    data object Matches : CheckStage

    /** [rows] are the four headline numbers, pack against Opson, when the mode shows numbers; empty when it hides them. */
    data class Differs(val rows: List<CheckRowUi>, val perServing: Boolean) : CheckStage

    data class Done(val outcome: CheckDone) : CheckStage
}

/** One headline number with its unit, null where a side has none. [differs] marks the rows that don't match. */
data class CheckRowUi(val nutrient: LabelNutrient, val pack: UnitAmount?, val opson: UnitAmount?, val differs: Boolean)

enum class CheckDone { Confirmed, Unconfirmed, Corrected }

enum class CheckProblem { Offline, Failed, TooMany, Gone, NotBuilder, Unavailable }

sealed interface CheckFoodAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : CheckFoodAction
    data object TakePhotoTapped : CheckFoodAction
    data object ChooseFromLibraryTapped : CheckFoodAction
    data class CameraPermissionAnswered(val permission: CameraPermission) : CheckFoodAction
    data object OpenSettingsTapped : CheckFoodAction
    data class PhotoAnswered(val result: PhotoPickResult) : CheckFoodAction
    data object SendCheckTapped : CheckFoodAction
    data object SendCorrectionTapped : CheckFoodAction
    data object RetakeTapped : CheckFoodAction
    data object DoneTapped : CheckFoodAction
}

sealed interface CheckFoodEvent {
    data object LaunchCamera : CheckFoodEvent
    data object LaunchLibrary : CheckFoodEvent
    data object RequestCameraPermission : CheckFoodEvent
    data object Close : CheckFoodEvent
}

/** The pack against the catalog, per 100 g (or ml) as the catalog keeps it. */
data class PackComparison(
    val pack: Map<LabelNutrient, Double>,
    val opson: Map<LabelNutrient, Double>,
    val differing: Set<LabelNutrient>,
    /** The label's serving in grams, when it printed one: the rows are shown per serving then. */
    val servingGrams: Double?,
) {
    val matches: Boolean get() = differing.isEmpty()
}

object CheckFoodCompare {
    val HEADLINE = listOf(LabelNutrient.Energy, LabelNutrient.Protein, LabelNutrient.Carbs, LabelNutrient.Fat)

    /** Enough of the panel to compare: a panel, and three of energy, protein, carbs and fat. */
    private const val MIN_READ = 3

    /**
     * The headline numbers, compared within what label rounding allows: 10 kcal or 5% for
     * energy, 1 g or 10% for the macros (US labels round grams to the whole number). A number
     * the phone could not read counts as differing, so a check is never confirmed on a guess.
     * Null when there is too little of a panel to say anything.
     */
    fun compare(food: NutrientProfile, draft: LabelDraft): PackComparison? {
        if (!draft.layout.panelFound) return null
        val pack = HEADLINE.mapNotNull { key -> draft.facts.per100(key)?.let { key to it } }.toMap()
        if (pack.size < MIN_READ) return null
        val opson = HEADLINE.mapNotNull { key -> food.value(key)?.let { key to it } }.toMap()
        val differing = HEADLINE.filter { key ->
            val a = pack[key] ?: return@filter true
            val b = opson[key] ?: return@filter true
            val allowed = if (key == LabelNutrient.Energy) max(ENERGY_SLACK, ENERGY_SHARE * max(a, b)) else max(GRAM_SLACK, GRAM_SHARE * max(a, b))
            abs(a - b) > allowed
        }.toSet()
        val serving = draft.facts.servingSize?.takeIf { draft.facts.servingUnit == ServingUnit.Gram && it > 0 }
        return PackComparison(pack, opson, differing, serving)
    }

    private fun NutrientProfile.value(key: LabelNutrient): Double? = when (key) {
        LabelNutrient.Energy -> kcal
        LabelNutrient.Protein -> protein
        LabelNutrient.Carbs -> carbs
        LabelNutrient.Fat -> fat
        else -> null
    }

    private const val ENERGY_SLACK = 10.0
    private const val ENERGY_SHARE = 0.05
    private const val GRAM_SLACK = 1.0
    private const val GRAM_SHARE = 0.10
}

/** Where the check stands, as the view model keeps it. */
data class CheckModel(
    val name: String,
    val canTakePhoto: Boolean,
    val step: CheckStep = CheckStep.Intro,
    val photo: MealPhoto? = null,
    val cameraOff: Boolean = false,
    val busy: Boolean = false,
    val problem: CheckProblem? = null,
)

sealed interface CheckStep {
    data object Intro : CheckStep
    data object Reading : CheckStep
    data object Unreadable : CheckStep
    data class Compared(val comparison: PackComparison) : CheckStep
    data class Done(val outcome: CheckDone) : CheckStep
}

object CheckFoodStateBuilder {
    fun build(model: CheckModel, policy: VisibilityPolicy): CheckFoodState = CheckFoodState(
        name = model.name,
        stage = when (val step = model.step) {
            CheckStep.Intro -> CheckStage.Intro
            CheckStep.Reading -> CheckStage.Reading
            CheckStep.Unreadable -> CheckStage.Unreadable
            is CheckStep.Compared -> if (step.comparison.matches) {
                CheckStage.Matches
            } else {
                CheckStage.Differs(
                    rows = if (SubmitFoodStateBuilder.numbersShown(policy)) rows(step.comparison) else emptyList(),
                    perServing = step.comparison.servingGrams != null,
                )
            }
            is CheckStep.Done -> CheckStage.Done(step.outcome)
        },
        photo = model.photo,
        canTakePhoto = model.canTakePhoto,
        cameraOff = model.cameraOff,
        busy = model.busy,
        problem = model.problem,
    )

    /** Per serving when the label printed one in grams, since that is the column a person reads; per 100 otherwise. */
    fun rows(comparison: PackComparison): List<CheckRowUi> {
        val scale = comparison.servingGrams?.let { it / 100.0 } ?: 1.0
        return CheckFoodCompare.HEADLINE.map { key ->
            fun shown(value: Double?) = value?.let { UnitAmount(SubmitFoodForm.amount(it * scale, key.unit), key.unit.label) }
            CheckRowUi(key, shown(comparison.pack[key]), shown(comparison.opson[key]), key in comparison.differing)
        }
    }

    fun problemOf(result: FoodCheckResult): CheckProblem? = when (result) {
        FoodCheckResult.Confirmed, is FoodCheckResult.Unconfirmed -> null
        FoodCheckResult.NotBuilder -> CheckProblem.NotBuilder
        FoodCheckResult.TooMany -> CheckProblem.TooMany
        FoodCheckResult.Gone -> CheckProblem.Gone
        is FoodCheckResult.Failed -> if (result.offline) CheckProblem.Offline else CheckProblem.Failed
    }

    fun problemOf(result: FoodReportResult): CheckProblem? = when (result) {
        is FoodReportResult.Sent -> null
        FoodReportResult.TooMany -> CheckProblem.TooMany
        FoodReportResult.Gone -> CheckProblem.Gone
        is FoodReportResult.Failed -> if (result.offline) CheckProblem.Offline else CheckProblem.Failed
    }
}
