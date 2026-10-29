package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.foods.LabelCheck
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelChecks
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.LabelUnit
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * The form's rules, pure: a reading into fields, a keystroke into the model,
 * a fix into a figure, the fields back into [LabelFacts]. The view model
 * calls these and nothing else changes the model.
 */
object SubmitFoodForm {

    const val NAME = "name"
    const val BRAND = "brand"
    const val BARCODE = "barcode"
    const val SERVING_SIZE = "serving_size"
    const val SERVINGS_PER_CONTAINER = "servings_per_container"

    /** The nutrients the form always shows, in label order. The label's others follow under "More from the label". */
    val MAIN: List<LabelNutrient> = listOf(
        LabelNutrient.Energy, LabelNutrient.Protein, LabelNutrient.Carbs, LabelNutrient.Sugars,
        LabelNutrient.Fiber, LabelNutrient.Fat, LabelNutrient.SaturatedFat, LabelNutrient.Sodium,
    )

    /** Required whatever the mode: a food needs a name, and an amount to log it by. */
    val ALWAYS_REQUIRED: Set<String> = setOf(NAME, SERVING_SIZE)

    /**
     * The model's reading as fields. A label with only a per 100 g column and
     * no serving is taken as a 100 g serving, marked for a look. Per-serving
     * figures the label did not print are worked out from per 100 and
     * remembered as [SubmitModel.derived], so they follow a corrected serving size.
     */
    fun fromReading(model: SubmitModel, read: LabelParse.Read?, barcode: String?): SubmitModel {
        val facts = read?.facts ?: LabelFacts()
        val size = facts.servingSize ?: if (facts.per100.isNotEmpty()) 100.0 else null
        val sized = facts.copy(servingSize = size, servingUnit = facts.servingUnit ?: size?.let { ServingUnit.Gram })
        val nutrients = (sized.perServing.keys + sized.per100.keys)
        val texts = buildMap {
            sized.name?.let { put(NAME, it) }
            sized.brand?.let { put(BRAND, it) }
            (barcode ?: sized.barcode)?.let { put(BARCODE, it) }
            size?.let { put(SERVING_SIZE, amount(it)) }
            sized.servingsPerContainer?.let { put(SERVINGS_PER_CONTAINER, amount(it)) }
            nutrients.forEach { key -> sized.perServing(key)?.let { put(key.wire, amount(it, key.unit)) } }
        }
        val confidence = read?.confidence.orEmpty().let { c ->
            if (facts.servingSize == null && size != null) c + (SERVING_SIZE to 0.0) else c
        }
        return model.copy(
            parseId = read?.parseId,
            confidence = confidence,
            texts = texts,
            read = texts,
            servingUnit = sized.servingUnit ?: ServingUnit.Gram,
            servingLabel = facts.servingLabel,
            basis = facts.basis,
            per100 = sized.per100,
            derived = nutrients.filter { it !in sized.perServing && it in sized.per100 }.toSet(),
            extras = LabelNutrient.entries.filter { it in nutrients && it !in MAIN },
            kept = emptySet(),
        )
    }

    /**
     * The phone's own reading of the label (Builder mode) as fields, the same way as the
     * server's: each field carries the OCR confidence of the line behind it, so a hazy digit
     * asks for a look. There is no parse id; the server never saw this reading.
     */
    fun fromDraft(model: SubmitModel, draft: LabelDraft, barcode: String?): SubmitModel {
        val read = LabelParse.Read(
            parseId = "",
            facts = draft.facts,
            confidence = draft.fieldConfidence,
            checks = LabelChecks.check(draft.facts),
            readable = true,
        )
        return fromReading(model, read, barcode).copy(parseId = null)
    }

    /** Enough of the panel read on the phone to fill the form without the server: four of the five headline fields. */
    fun usable(draft: LabelDraft): Boolean = draft.layout.panelFound && draft.headlineFound >= MIN_HEADLINE_ON_PHONE

    private const val MIN_HEADLINE_ON_PHONE = 4

    /**
     * A keystroke. A nutrient typed is the person's word: its per 100 figure
     * is dropped so the serving check does not argue with them. A serving size
     * typed carries the worked-out nutrients with it.
     */
    fun typed(model: SubmitModel, key: String, text: String): SubmitModel {
        val clean = when {
            key == NAME || key == BRAND -> text.take(if (key == NAME) MAX_NAME else MAX_BRAND)
            key == BARCODE -> text.filter { it in '0'..'9' }.take(MAX_BARCODE)
            else -> sanitizeNumber(text)
        }
        val nutrient = LabelNutrient.fromWire(key)
        val next = model.copy(texts = model.texts + (key to clean))
        return when {
            nutrient != null -> next.copy(per100 = next.per100 - nutrient, derived = next.derived - nutrient)
            key == SERVING_SIZE -> rescale(next)
            else -> next
        }
    }

    /** The figure a check points to, written into its field. */
    fun fix(model: SubmitModel, check: LabelCheck): SubmitModel {
        val expected = check.expected ?: return model
        return when (check.code) {
            LabelCheckCode.EnergyMacrosMismatch, LabelCheckCode.EnergyLooksLikeKj ->
                typed(model, LabelNutrient.Energy.wire, amount(expected, LabelUnit.Kcal))
            LabelCheckCode.ServingMismatch -> {
                val field = check.fields.single()
                if (field == SERVING_SIZE) {
                    // The per-serving figures were right and the size was misread, so they stay as printed.
                    model.copy(texts = model.texts + (SERVING_SIZE to amount(expected)))
                } else {
                    val nutrient = LabelNutrient.fromWire(field) ?: return model
                    model.copy(texts = model.texts + (field to amount(expected, nutrient.unit)))
                }
            }
            LabelCheckCode.SodiumUnit -> typed(model, LabelNutrient.Sodium.wire, amount(expected, LabelUnit.Milligram))
            LabelCheckCode.MissingRequired, LabelCheckCode.ImpossibleValue, LabelCheckCode.EnergyUnitsDisagree -> model
        }
    }

    /** The fields as label facts, as the checks and the submission see them. */
    fun facts(model: SubmitModel): LabelFacts {
        val size = number(model.texts[SERVING_SIZE])
        return LabelFacts(
            name = model.texts[NAME]?.trim()?.ifEmpty { null },
            brand = model.texts[BRAND]?.trim()?.ifEmpty { null },
            barcode = model.texts[BARCODE]?.trim()?.ifEmpty { null },
            servingSize = size,
            servingUnit = model.servingUnit.takeIf { size != null },
            servingLabel = model.servingLabel,
            servingsPerContainer = number(model.texts[SERVINGS_PER_CONTAINER]),
            basis = model.basis,
            perServing = LabelNutrient.entries.mapNotNull { key -> number(model.texts[key.wire])?.let { key to it } }.toMap(),
            per100 = model.per100.filterKeys { it !in model.derived },
        )
    }

    /**
     * The person's own copy, saved to their foods: one serving as the label states it (its weight
     * is the serving when the serving is in grams), the main numbers, and the rest of the label per
     * serving, keyed the way a log entry keeps its micros.
     */
    fun customFood(model: SubmitModel): CustomFoodDraft {
        val facts = facts(model)
        val main = setOf(LabelNutrient.Energy, LabelNutrient.Protein, LabelNutrient.Carbs, LabelNutrient.Fat, LabelNutrient.Fiber)
        return CustomFoodDraft(
            name = facts.name.orEmpty(),
            servingAmount = facts.servingSize ?: 1.0,
            servingUnit = facts.servingUnit?.wire ?: UNIT_SERVING,
            brand = facts.brand,
            kcal = facts.perServing[LabelNutrient.Energy],
            protein = facts.perServing[LabelNutrient.Protein],
            carbs = facts.perServing[LabelNutrient.Carbs],
            fat = facts.perServing[LabelNutrient.Fat],
            fiber = facts.perServing[LabelNutrient.Fiber],
            micros = facts.perServing.filterKeys { it !in main }.mapKeys { it.key.wire },
        )
    }

    private const val UNIT_SERVING = "serving"

    /** The checks on the form as it stands. */
    fun checks(model: SubmitModel): List<LabelCheck> = LabelChecks.check(facts(model))

    /** Whether the person changed anything the model read. */
    fun edited(model: SubmitModel): Boolean = model.read.isNotEmpty() && model.texts.filterValues { it.isNotEmpty() } != model.read

    private fun rescale(model: SubmitModel): SubmitModel {
        val size = number(model.texts[SERVING_SIZE]) ?: return model
        val rescaled = model.derived.mapNotNull { key ->
            model.per100[key]?.let { key.wire to amount(it * size / 100.0, key.unit) }
        }
        return model.copy(texts = model.texts + rescaled)
    }

    fun number(text: String?): Double? = text?.trim()?.takeIf { it.isNotEmpty() && it != "." }?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Digits and one decimal point, a comma counting as one, as a decimal keypad types it. */
    fun sanitizeNumber(raw: String): String {
        val out = StringBuilder()
        var point = false
        for (char in raw) {
            when {
                char in '0'..'9' -> out.append(char)
                !point && (char == '.' || char == ',') -> {
                    out.append('.')
                    point = true
                }
            }
        }
        return out.toString().take(MAX_NUMBER)
    }

    /** A figure as a label would print it: whole kcal, up to two decimals for the rest, no trailing zeros. */
    fun amount(value: Double, unit: LabelUnit? = null): String {
        val decimals = if (unit == LabelUnit.Kcal) 0 else 2
        val factor = 10.0.pow(decimals)
        val rounded = round(value * factor) / factor
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString().trimEnd('0').trimEnd('.')
    }

    private const val MAX_NAME = 200
    private const val MAX_BRAND = 120
    private const val MAX_BARCODE = 14
    private const val MAX_NUMBER = 9
}

/**
 * [SubmitModel] to [SubmitFoodState], as a pure function of the model and the
 * policy.
 *
 * **Numbers and the mode.** The label's numbers are drawn, editable, only when
 * calories and every macro show on the logger; with [Metric.Micros] showing
 * too, the label's other nutrients follow. In any mode that keeps calories
 * out of sight the person can still add the food: the form keeps its name,
 * brand, barcode and serving (amounts of food, which every mode shows) and
 * says in words whether the label adds up, never by how much. What the model
 * read is submitted as it is, the checks travel with it, and a reviewer sees
 * the photos. Required numbers are only required where they can be seen.
 */
object SubmitFoodStateBuilder {

    /** Below this the model was unsure enough that the field asks for a look. */
    const val LOW_CONFIDENCE = 0.7

    fun numbersShown(policy: VisibilityPolicy): Boolean =
        listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat).all {
            policy.effectiveLevel(it, Surface.Logger) == Visibility.Show
        }

    fun microsShown(policy: VisibilityPolicy): Boolean = policy.effectiveLevel(Metric.Micros, Surface.Logger) == Visibility.Show

    fun build(model: SubmitModel, policy: VisibilityPolicy): SubmitFoodState = SubmitFoodState(
        slot = model.slot,
        stage = stage(model, policy),
        front = model.front,
        label = model.label,
        canTakePhoto = model.canTakePhoto,
        photoFailed = model.photoFailed.takeIf { model.step == SubmitStep.Photos },
        builderMode = model.builderMode,
        ingredients = model.ingredients,
        readingOnPhone = model.readingOnPhone && model.step == SubmitStep.Reading,
    )

    private fun stage(model: SubmitModel, policy: VisibilityPolicy): SubmitStage = when (val step = model.step) {
        SubmitStep.Photos -> SubmitStage.Photos(canRead = model.front != null && model.label != null)
        SubmitStep.Reading -> SubmitStage.Reading
        SubmitStep.Review -> SubmitStage.Review(form(model, policy))
        SubmitStep.Saving -> SubmitStage.Saving
        SubmitStep.NoLabel -> SubmitStage.NoLabel
        SubmitStep.TooMany -> SubmitStage.TooMany
        is SubmitStep.ReadFailed -> SubmitStage.ReadFailed(step.offline)
        is SubmitStep.CameraOff -> SubmitStage.CameraOff(step.canAskAgain)
        is SubmitStep.SendFailed -> SubmitStage.SendFailed(step.offline, step.tooMany, step.restricted)
    }

    /** What may be submitted: name and serving always, the required numbers and possible amounts where they are seen. */
    fun canSubmit(model: SubmitModel, policy: VisibilityPolicy): Boolean = blockers(model, policy).isEmpty() &&
        model.front != null && model.label != null

    private fun blockers(model: SubmitModel, policy: VisibilityPolicy): List<String> {
        val shown = numbersShown(policy)
        return SubmitFoodForm.checks(model)
            .filter { it.code.blocking }
            .flatMap { check -> check.fields.filter { shown || (check.code == LabelCheckCode.MissingRequired && it in SubmitFoodForm.ALWAYS_REQUIRED) } }
            .distinct()
    }

    fun form(model: SubmitModel, policy: VisibilityPolicy): SubmitForm {
        val shown = numbersShown(policy)
        val checks = SubmitFoodForm.checks(model)
        val missing = checks.filter { it.code == LabelCheckCode.MissingRequired }.flatMap { it.fields }.toSet()
        val open = checks.filter { it.code != LabelCheckCode.MissingRequired && (it.code to it.fields.first()) !in model.kept }
        val flagged = open.flatMap { it.fields }.toSet()

        fun field(key: String, requiredHere: Boolean = true) = FormField(
            key = key,
            text = model.texts[key].orEmpty(),
            needsLook = (model.confidence[key] ?: 1.0) < LOW_CONFIDENCE && model.texts[key] == model.read[key] && key in model.read,
            flagged = shown && key in flagged,
            missing = requiredHere && key in missing,
        )

        fun nutrient(key: LabelNutrient) = NutrientField(key, field(key.wire), key.unit.symbol)

        val numbers = if (shown) {
            FormNumbers(
                main = SubmitFoodForm.MAIN.map(::nutrient),
                more = if (microsShown(policy)) model.extras.map(::nutrient) else emptyList(),
            )
        } else {
            null
        }
        val hidden = if (shown) {
            null
        } else {
            HiddenNumbers(
                addsUp = checks.none { it.code != LabelCheckCode.MissingRequired },
                incomplete = missing.any { it !in SubmitFoodForm.ALWAYS_REQUIRED },
            )
        }
        return SubmitForm(
            name = field(SubmitFoodForm.NAME),
            brand = field(SubmitFoodForm.BRAND),
            barcode = field(SubmitFoodForm.BARCODE),
            barcodeLocked = model.barcodeLocked,
            servingSize = field(SubmitFoodForm.SERVING_SIZE),
            servingUnit = model.servingUnit,
            servingLabel = model.servingLabel,
            servingsPerContainer = field(SubmitFoodForm.SERVINGS_PER_CONTAINER),
            numbers = numbers,
            hidden = hidden,
            checks = if (shown) checks.filter { it.code != LabelCheckCode.MissingRequired }.map { row(it, model) } else emptyList(),
            notice = model.notice,
            canSubmit = canSubmit(model, policy),
            missing = blockers(model, policy),
        )
    }

    private fun row(check: LabelCheck, model: SubmitModel): CheckRow {
        val unit = model.servingUnit.wire
        val field = check.fields.first()
        val nutrient = LabelNutrient.fromWire(field)
        fun withUnit(value: Double?): String? = value?.let {
            when {
                field == SubmitFoodForm.SERVING_SIZE -> "${SubmitFoodForm.amount(it)} $unit"
                nutrient != null -> "${SubmitFoodForm.amount(it, nutrient.unit)} ${nutrient.unit.symbol}"
                else -> SubmitFoodForm.amount(it)
            }
        }
        val kept = (check.code to field) in model.kept
        return when (check.code) {
            LabelCheckCode.ImpossibleValue -> CheckRow(check.code, check.fields, difference = null, fix = null, keep = null, kept = false)
            LabelCheckCode.EnergyMacrosMismatch -> CheckRow(
                code = check.code,
                fields = check.fields,
                difference = withUnit(abs((check.actual ?: 0.0) - (check.expected ?: 0.0))),
                fix = withUnit(check.expected),
                keep = withUnit(check.actual),
                kept = kept,
            )
            else -> CheckRow(check.code, check.fields, difference = null, fix = withUnit(check.expected), keep = withUnit(check.actual), kept = kept)
        }
    }
}
