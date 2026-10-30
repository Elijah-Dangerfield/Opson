package com.opson.features.logger.impl

import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.foods.SuggestedFoodValues
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.foods.VerifyProblem
import com.opson.libraries.foods.VerifyValues
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * A Builder's Verify this food (`release.builders`): a sheet over the food's detail, after the
 * scanner with Also read the label on, or from Verify a food on the + sheet and the Foods tab.
 * Plain strings, no typeMap.
 */
@Serializable
data class CheckFoodRoute(val foodId: String, val name: String) : Route()

/** What the verify sheet draws, built by [CheckFoodStateBuilder]. */
data class CheckFoodState(
    val name: String,
    val stage: CheckStage = CheckStage.Photos(PackSide.Front),
    val front: MealPhoto? = null,
    val label: MealPhoto? = null,
    val canTakePhoto: Boolean = true,
    val cameraOff: Boolean = false,
    val busy: Boolean = false,
    val problem: CheckProblem? = null,
)

sealed interface CheckStage {
    /** Taking the photos; the buttons take [next]. */
    data class Photos(val next: PackSide) : CheckStage
    data object Reading : CheckStage

    /** The label photo shows no panel the phone could read, or too little of one. */
    data object Unreadable : CheckStage

    /**
     * The pack against what Opson has. [rows] are the four headline numbers when the mode shows
     * numbers and the pack differs; empty otherwise. [form] is "Something was wrong", when on.
     */
    data class Review(
        val matches: Boolean,
        val rows: List<CheckRowUi>,
        val perServing: Boolean,
        val form: VerifyFormUi?,
        val canSend: Boolean,
    ) : CheckStage

    data class Done(val outcome: CheckDone) : CheckStage
}

/** One headline number with its unit, null where a side has none. [differs] marks the rows that don't match. */
data class CheckRowUi(val nutrient: LabelNutrient, val pack: UnitAmount?, val opson: UnitAmount?, val differs: Boolean)

/**
 * Verified on the spot; sent but not counted (a server from before reviews could not find the
 * numbers); waiting for a reviewer; or filed as a report by a server from before reviews.
 */
enum class CheckDone { Confirmed, Unconfirmed, InReview, Corrected }

enum class CheckProblem { Offline, Failed, TooMany, Gone, NotBuilder, Unavailable }

/** How a part of the food was off. */
enum class FieldIssue { None, Wrong, Missing }

data class FieldIssueUi(val field: VerifyField, val issue: FieldIssue)

/**
 * "Something was wrong": each part of the food with how it was off, then the right values for the
 * parts marked, pre-filled from the label, and a note. [values] holds only the inputs the marked
 * parts ask for, in order. [numbers] is false in a mode that hides calories and macros: those
 * inputs are left out and the label's own figures go instead.
 */
data class VerifyFormUi(
    val fields: List<FieldIssueUi>,
    /** The label's serving the numbers are for; null is 100 g. */
    val basis: ReportBasis?,
    val numbers: Boolean,
    val values: List<VerifyInputUi>,
    val note: String,
)

/** One input of the right values: [key] is one of [VerifyForm]'s keys. */
data class VerifyInputUi(val key: String, val text: String)

sealed interface CheckFoodAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : CheckFoodAction
    data object TakePhotoTapped : CheckFoodAction
    data object ChooseFromLibraryTapped : CheckFoodAction
    data object SkipFrontTapped : CheckFoodAction
    data class CameraPermissionAnswered(val permission: CameraPermission) : CheckFoodAction
    data object OpenSettingsTapped : CheckFoodAction
    data class PhotoAnswered(val result: PhotoPickResult) : CheckFoodAction
    data class SomethingWrongChanged(val on: Boolean) : CheckFoodAction

    /** [issue] again on a part already marked so clears it. */
    data class IssuePicked(val field: VerifyField, val issue: FieldIssue) : CheckFoodAction
    data class ValueTyped(val key: String, val text: String) : CheckFoodAction
    data class NoteTyped(val text: String) : CheckFoodAction
    data object SendTapped : CheckFoodAction

    /** Back to the photos, both of them. */
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

/** What the person marked and typed under "Something was wrong". */
data class VerifyForm(
    val issues: Map<VerifyField, FieldIssue> = emptyMap(),
    val values: Map<String, String> = emptyMap(),
    val note: String = "",
) {
    fun issue(field: VerifyField): FieldIssue = issues[field] ?: FieldIssue.None
    val marked: List<VerifyField> get() = VerifyField.entries.filter { issue(it) != FieldIssue.None }
    val saysSomething: Boolean get() = marked.isNotEmpty() || note.isNotBlank()

    companion object {
        const val NAME = "name"
        const val BRAND = "brand"
        const val BARCODE = "barcode"
        const val SERVING = "serving"
        const val SERVING_GRAMS = "serving_grams"
        const val KCAL = "kcal"
        const val PROTEIN = "protein"
        const val CARBS = "carbs"
        const val FAT = "fat"
        val NUMBERS = setOf(KCAL, PROTEIN, CARBS, FAT, SERVING_GRAMS)
        val KEYS = listOf(NAME, BRAND, SERVING, SERVING_GRAMS, KCAL, PROTEIN, CARBS, FAT, BARCODE)

        /** The inputs a marked part asks for. Ingredients has none: the note and the photo say it. */
        fun keysFor(field: VerifyField): List<String> = when (field) {
            VerifyField.Calories -> listOf(KCAL)
            VerifyField.Macros -> listOf(PROTEIN, CARBS, FAT)
            VerifyField.Serving -> listOf(SERVING, SERVING_GRAMS)
            VerifyField.NameBrand -> listOf(NAME, BRAND)
            VerifyField.Ingredients -> emptyList()
            VerifyField.Barcode -> listOf(BARCODE)
        }
    }
}

/** Where the verification stands, as the view model keeps it. */
data class CheckModel(
    val name: String,
    val canTakePhoto: Boolean,
    val step: CheckStep = CheckStep.Photos(PackSide.Front),
    val front: MealPhoto? = null,
    val label: MealPhoto? = null,
    val somethingWrong: Boolean = false,
    val form: VerifyForm = VerifyForm(),
    /** The basis the form's numbers are for: the label's serving, or 100 g. */
    val basis: PackBasis = PackBasis.PER_100,
    val cameraOff: Boolean = false,
    val busy: Boolean = false,
    val problem: CheckProblem? = null,
)

/** The amount the right values are for: "1 bar" weighing 40 g, or 100 g. */
data class PackBasis(val label: String?, val grams: Double) {
    companion object {
        val PER_100 = PackBasis(null, 100.0)
    }
}

sealed interface CheckStep {
    data class Photos(val next: PackSide) : CheckStep
    data object Reading : CheckStep
    data object Unreadable : CheckStep
    data class Compared(val comparison: PackComparison) : CheckStep
    data class Done(val outcome: CheckDone) : CheckStep
}

object CheckFoodStateBuilder {
    private const val MAX_NOTE = 1_000
    private const val MAX_TEXT = 120
    private const val MAX_DIGITS = 8

    fun build(model: CheckModel, policy: VisibilityPolicy): CheckFoodState = CheckFoodState(
        name = model.name,
        stage = when (val step = model.step) {
            is CheckStep.Photos -> CheckStage.Photos(step.next)
            CheckStep.Reading -> CheckStage.Reading
            CheckStep.Unreadable -> CheckStage.Unreadable
            is CheckStep.Compared -> {
                val numbers = SubmitFoodStateBuilder.numbersShown(policy)
                CheckStage.Review(
                    matches = step.comparison.matches,
                    rows = if (numbers && !step.comparison.matches) rows(step.comparison) else emptyList(),
                    perServing = step.comparison.servingGrams != null,
                    form = if (model.somethingWrong) form(model, numbers) else null,
                    canSend = canSend(model),
                )
            }
            is CheckStep.Done -> CheckStage.Done(step.outcome)
        },
        front = model.front,
        label = model.label,
        canTakePhoto = model.canTakePhoto,
        cameraOff = model.cameraOff,
        busy = model.busy,
        problem = model.problem,
    )

    /** "Something was wrong" needs to say what; without it, a match or a Builder's word that it matches is enough. */
    fun canSend(model: CheckModel): Boolean =
        !model.busy && model.problem != CheckProblem.Gone && (!model.somethingWrong || model.form.saysSomething)

    private fun form(model: CheckModel, numbers: Boolean): VerifyFormUi {
        val keys = model.form.marked.flatMap(VerifyForm::keysFor).filter { numbers || it !in VerifyForm.NUMBERS || it == VerifyForm.SERVING_GRAMS }
        return VerifyFormUi(
            fields = VerifyField.entries.map { FieldIssueUi(it, model.form.issue(it)) },
            basis = model.basis.label?.let { label -> ReportBasis(label, kotlin.math.round(model.basis.grams).toLong()) },

            numbers = numbers,
            values = VerifyForm.KEYS.filter { it in keys }.map { VerifyInputUi(it, model.form.values[it].orEmpty()) },
            note = model.form.note,
        )
    }

    /** Per serving when the label printed one in grams, since that is the column a person reads; per 100 otherwise. */
    fun rows(comparison: PackComparison): List<CheckRowUi> {
        val scale = comparison.servingGrams?.let { it / 100.0 } ?: 1.0
        return CheckFoodCompare.HEADLINE.map { key ->
            fun shown(value: Double?) = value?.let { UnitAmount(SubmitFoodForm.amount(it * scale, key.unit), key.unit.label) }
            CheckRowUi(key, shown(comparison.pack[key]), shown(comparison.opson[key]), key in comparison.differing)
        }
    }

    /**
     * The form as it opens: what Opson has for the name, brand and barcode, and what the label
     * says for the serving and the numbers, for its serving when it printed one in grams.
     * [comparison] marks the parts that differ: wrong where Opson has a figure, missing where it
     * has none. A number the phone could not read is left for the person.
     */
    fun prefill(food: Food, draft: LabelDraft, comparison: PackComparison): Pair<VerifyForm, PackBasis> {
        val facts = draft.facts
        val basis = comparison.servingGrams?.let { PackBasis(facts.servingLabel?.trim()?.ifEmpty { null } ?: "${SubmitFoodForm.amount(it)} g", it) }
            ?: PackBasis.PER_100
        fun packValue(key: LabelNutrient): String? =
            comparison.pack[key]?.let { SubmitFoodForm.amount(it * basis.grams / 100.0, key.unit) }
        val values = buildMap {
            put(VerifyForm.NAME, food.name)
            food.brand?.let { put(VerifyForm.BRAND, it) }
            food.barcode?.let { put(VerifyForm.BARCODE, it) }
            facts.servingLabel?.let { put(VerifyForm.SERVING, it) }
            comparison.servingGrams?.let { put(VerifyForm.SERVING_GRAMS, SubmitFoodForm.amount(it)) }
            packValue(LabelNutrient.Energy)?.let { put(VerifyForm.KCAL, it) }
            packValue(LabelNutrient.Protein)?.let { put(VerifyForm.PROTEIN, it) }
            packValue(LabelNutrient.Carbs)?.let { put(VerifyForm.CARBS, it) }
            packValue(LabelNutrient.Fat)?.let { put(VerifyForm.FAT, it) }
        }
        fun issueOf(keys: List<LabelNutrient>): FieldIssue? {
            val off = keys.filter { it in comparison.differing && it in comparison.pack }
            if (off.isEmpty()) return null
            return if (off.all { it !in comparison.opson }) FieldIssue.Missing else FieldIssue.Wrong
        }
        val issues = buildMap {
            issueOf(listOf(LabelNutrient.Energy))?.let { put(VerifyField.Calories, it) }
            issueOf(listOf(LabelNutrient.Protein, LabelNutrient.Carbs, LabelNutrient.Fat))?.let { put(VerifyField.Macros, it) }
        }
        return VerifyForm(issues = issues, values = values) to basis
    }

    /** A value as it is typed: numbers keep digits and one decimal point, the rest is free text. */
    fun typed(key: String, text: String): String = if (key in VerifyForm.NUMBERS) {
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
        val point = cleaned.indexOfFirst { it == '.' || it == ',' }
        if (point < 0) cleaned.take(MAX_DIGITS) else (cleaned.substring(0, point + 1) + cleaned.substring(point + 1).filter(Char::isDigit)).take(MAX_DIGITS)
    } else {
        text.take(MAX_TEXT)
    }

    fun note(text: String): String = text.take(MAX_NOTE)

    /**
     * What goes to the server: the marked parts and only the values they ask for. In a mode that
     * hides numbers the label's own figures go, since the person never saw a field to fix them in.
     */
    fun problem(model: CheckModel): VerifyProblem? {
        if (!model.somethingWrong) return null
        val form = model.form
        val keys = form.marked.flatMap(VerifyForm::keysFor).toSet()
        fun text(key: String) = form.values[key]?.trim()?.ifEmpty { null }?.takeIf { key in keys }
        fun number(key: String) = text(key)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }
        val values = VerifyValues(
            name = text(VerifyForm.NAME),
            brand = text(VerifyForm.BRAND),
            barcode = text(VerifyForm.BARCODE),
            basisLabel = model.basis.label ?: "100 g",
            basisGrams = model.basis.grams,
            kcal = number(VerifyForm.KCAL),
            protein = number(VerifyForm.PROTEIN),
            carbs = number(VerifyForm.CARBS),
            fat = number(VerifyForm.FAT),
            servingLabel = text(VerifyForm.SERVING),
            servingGrams = number(VerifyForm.SERVING_GRAMS),
        )
        return VerifyProblem(
            wrong = form.marked.filter { form.issue(it) == FieldIssue.Wrong }.toSet(),
            missing = form.marked.filter { form.issue(it) == FieldIssue.Missing }.toSet(),
            values = values.takeUnless { it.isEmpty },
            note = form.note.trim().ifEmpty { null },
        )
    }

    /**
     * The same problem as Report a problem files it, for a server from before verifications were
     * reviewed: what was wrong as report reasons, the values as suggested numbers.
     */
    fun reasonsOf(problem: VerifyProblem): List<FoodReportReason> = buildList {
        val numbers = setOf(VerifyField.Calories, VerifyField.Macros)
        if (problem.wrong.any { it in numbers }) add(FoodReportReason.WrongNumbers)
        if (problem.missing.isNotEmpty()) add(FoodReportReason.MissingInfo)
        if (VerifyField.Serving in problem.wrong) add(FoodReportReason.WrongServing)
        if (VerifyField.NameBrand in problem.wrong) add(FoodReportReason.WrongName)
        if (isEmpty()) add(FoodReportReason.Other)
    }

    fun suggestedOf(problem: VerifyProblem): SuggestedFoodValues? = problem.values?.let {
        SuggestedFoodValues(
            basisLabel = it.basisLabel,
            basisGrams = it.basisGrams,
            kcal = it.kcal,
            protein = it.protein,
            carbs = it.carbs,
            fat = it.fat,
            servingLabel = it.servingLabel,
            servingGrams = it.servingGrams,
        )
    }?.takeUnless { it.isEmpty }

    fun problemOf(result: FoodCheckResult): CheckProblem? = when (result) {
        FoodCheckResult.Confirmed, is FoodCheckResult.Unconfirmed, FoodCheckResult.InReview -> null
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
