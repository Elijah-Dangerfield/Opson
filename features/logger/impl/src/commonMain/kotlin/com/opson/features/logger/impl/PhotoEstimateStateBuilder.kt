package com.opson.features.logger.impl

import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountTextFor
import com.opson.libraries.foods.chipLabel
import com.opson.libraries.foods.nutrientsFor
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy

/**
 * The photo sheet's state as a pure function of [PhotoModel], so previews and
 * the Blind leak test run the same mapping as the app. Energy goes through
 * [VisibilityPolicy] on [Surface.Logger], with the day's context so a delayed
 * mode holds back today as everywhere else.
 */
object PhotoEstimateStateBuilder {

    /** Calories hidden: amounts are counted in household measures, never weighed out. */
    fun householdOnly(policy: VisibilityPolicy): Boolean = policy.level(Metric.Calories) == Visibility.Hide

    fun build(model: PhotoModel, inputs: PhotoInputs?, policy: VisibilityPolicy): PhotoEstimateState = PhotoEstimateState(
        slot = model.slot,
        stage = stage(model, inputs, policy),
        note = model.note,
        photo = model.photo,
        canTakePhoto = model.canTakePhoto,
    )

    private fun stage(model: PhotoModel, inputs: PhotoInputs?, policy: VisibilityPolicy): PhotoStage = when (val step = model.step) {
        PhotoStep.Start -> PhotoStage.Start
        PhotoStep.Estimating -> PhotoStage.Estimating
        PhotoStep.Review -> model.swap?.let { swap(it, inputs, policy) } ?: review(model, inputs, policy)
        PhotoStep.NothingFound -> PhotoStage.NothingFound
        PhotoStep.Unavailable -> PhotoStage.Unavailable
        PhotoStep.TooMany -> PhotoStage.TooMany
        is PhotoStep.Failed -> PhotoStage.Failed(step.offline)
        is PhotoStep.CameraOff -> PhotoStage.CameraOff(step.canAskAgain)
    }

    private fun review(model: PhotoModel, inputs: PhotoInputs?, policy: VisibilityPolicy): PhotoStage.Review {
        val bare = inputs?.day?.formatContext(inputs.today, bare = true) ?: FormatContext(bare = true)
        val context = inputs?.day?.formatContext(inputs.today) ?: FormatContext()
        val householdOnly = householdOnly(policy)
        val rows = model.rows.map { row ->
            val draft = row.draft()
            val unit = row.unit
            PhotoRowUi(
                key = row.key,
                name = row.name,
                amount = draft.amountLabel,
                isWeight = unit == AmountUnit.Grams || (unit == null && row.parsed?.unit == GRAMS),
                calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare),
                matched = row.food != null,
            )
        }
        val editor = model.rows.firstOrNull { it.key == model.editing && it.editable }?.let { row ->
            val food = checkNotNull(row.food)
            val unit = checkNotNull(row.unit)
            val units = PhotoRows.units(food, householdOnly)
            PhotoAmountEditor(
                key = row.key,
                amount = row.draft().amountLabel,
                units = units.map { it.chipLabel },
                unitIndex = units.indexOf(unit).coerceAtLeast(0),
                canLess = PortionAmount.canLess(unit, row.quantity),
            )
        }
        val drafts = model.rows.map { it.draft() }
        return PhotoStage.Review(
            rows = rows,
            editor = editor,
            usedNote = model.usedNote,
            footer = if (drafts.isEmpty()) {
                null
            } else {
                PhotoFooter(count = drafts.size, calories = policy.resolve(Metric.Calories, drafts.totalKcal(), Surface.Logger, context))
            },
        )
    }

    private fun swap(swap: PhotoSwap, inputs: PhotoInputs?, policy: VisibilityPolicy): PhotoStage.Swap {
        val bare = inputs?.day?.formatContext(inputs.today, bare = true) ?: FormatContext(bare = true)
        val query = swap.query.trim()
        val answer = swap.results?.takeIf { it.query == query }
        return PhotoStage.Swap(
            replacing = swap.replacing != null,
            query = swap.query,
            results = when {
                query.isEmpty() -> emptyList()
                answer == null -> null
                else -> answer.foods.map { it.toResult(policy, bare) }
            },
        )
    }

    private fun Food.toResult(policy: VisibilityPolicy, bare: FormatContext): SwapResultUi {
        val serving = FoodAmount.Servings(1.0)
        return SwapResultUi(
            id = id,
            name = name,
            subtitle = brand,
            amount = HouseholdAmount.tidy(amountTextFor(serving)),
            calories = policy.resolve(Metric.Calories, nutrientsFor(serving).kcal, Surface.Logger, bare),
        )
    }

    /** The server's wire unit for grams on a Describe item. */
    private const val GRAMS = "g"
}
