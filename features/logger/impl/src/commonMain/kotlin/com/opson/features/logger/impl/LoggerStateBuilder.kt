package com.opson.features.logger.impl

import com.opson.features.logger.impl.FoodPicks.toPick
import com.opson.features.logger.impl.FoodPicks.toRow
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.amountTextFor
import com.opson.libraries.foods.chipLabel
import com.opson.libraries.foods.nutrientsFor
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay

/** What the person has done on the sheet so far, as opposed to what it read. */
data class LoggerChoices(
    val slot: MealSlot,
    val pane: LoggerPane = LoggerPane.Actions,
    val describeText: String = "",
    val describeStatus: DescribeStatus = DescribeStatus.Idle,
    /** Describe's lines as the person has corrected them. */
    val review: DescribeReview = DescribeReview(),
    val number: String = "",
    /** `release.barcode`, read once when the sheet opens. */
    val scanAvailable: Boolean = false,
    /** `release.photo`, read once when the sheet opens. */
    val photoAvailable: Boolean = false,
    /** `release.voice`, read once when the sheet opens. */
    val voiceEnabled: Boolean = false,
    /** `release.fasting`, read once when the sheet opens. */
    val fastingReleased: Boolean = false,
    val voice: VoiceCapture = VoiceCapture.Idle(),
    /** How tall the sheet is; it only ever grows. */
    val height: SheetHeight = SheetHeight.Partial,
    /**
     * The meal was given (a meal's own +) or picked from the dropdown, so the picker's answer
     * from this person's logging times no longer moves it.
     */
    val slotChosen: Boolean = true,
    /** The day's item ids when it was first read; anything logged since reads as added (✓). */
    val seen: Set<String>? = null,
)

/**
 * The add sheet's state as a pure function of what it reads and what the person has done, so
 * previews and the Blind leak test run the same mapping as the app. Every number goes through
 * [VisibilityPolicy] on [Surface.Logger].
 */
object LoggerStateBuilder {

    fun build(inputs: LoggerInputs, policy: VisibilityPolicy, choices: LoggerChoices): LoggerState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val slot = effectiveSlot(inputs, choices.slot)
        val context = inputs.day.formatContext(inputs.today)
        val bare = inputs.day.formatContext(inputs.today, bare = true)
        val canNumber = policy.level(Metric.Calories) != Visibility.Hide
        return LoggerState(
            loading = false,
            day = RelativeDay.of(inputs.day.date, inputs.today),
            slot = slot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            pane = choices.pane,
            height = choices.height,
            actions = actions(inputs, policy, slot, choices),
            recent = recentRows(inputs, policy, slot, choices, bare),
            describe = DescribeUi(
                text = choices.describeText,
                status = choices.describeStatus,
                lines = describedLines(choices.review, policy, bare),
                total = describeTotal(choices.review, policy, context),
                find = choices.review.find?.let { describeFind(it, choices.review, policy, bare) },
                canNumber = canNumber,
                number = if (canNumber) choices.number else "",
                // A spoken description becomes the same text and the same lines as a typed one.
                voice = VoiceStateBuilder.build(choices.voiceEnabled, choices.voice),
            ),
            saved = SavedUi(
                meals = inputs.savedMeals.map { meal ->
                    SavedMealUi(
                        id = meal.id,
                        name = meal.name,
                        foods = meal.items.joinToString(", ") { shortName(it.name) },
                        calories = policy.resolve(Metric.Calories, meal.totals.kcal.value, Surface.Logger, context),
                    )
                },
            ),
        )
    }

    /** The slot a log lands in: [slot], or Uncategorized when the day has meal groups off. */
    fun effectiveSlot(inputs: LoggerInputs, slot: MealSlot): MealSlot =
        if (inputs.day.mealGroupsOn(inputs.settings)) slot else MealSlot.Uncategorized

    /** Recent's foods as they log: the view model finds a tapped row's draft here, so the state never carries one. */
    fun recentPicks(inputs: LoggerInputs): List<FoodPick> =
        inputs.recent.mapNotNull { it.toPick(PickSource.Recent) }

    private fun actions(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot, choices: LoggerChoices): List<AddAction> = buildList {
        add(AddAction.SearchFood)
        if (choices.scanAvailable) add(AddAction.ScanBarcode)
        if (choices.photoAvailable) add(AddAction.Photo)
        add(AddAction.SayOrType(voice = choices.voiceEnabled))
        add(AddAction.SavedMeals)
        if (inputs.dayBefore.items(slot).isNotEmpty()) add(AddAction.CopyMeal(slot))
        add(AddAction.Water)
        add(AddAction.WeighIn)
        add(AddAction.Note)
        if (slot.isMeal) add(AddAction.AteNoDetails)
        if (inputs.settings.fastingTimerOn(choices.fastingReleased)) add(AddAction.Fasting)
    }

    private fun recentRows(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot, choices: LoggerChoices, bare: FormatContext): List<FoodRowUi> {
        val added = FoodPicks.addedSince(inputs.day, slot, choices.seen)
        return recentPicks(inputs).map { it.toRow(policy, bare, added = it.key in added) }
    }

    private fun describedLines(review: DescribeReview, policy: VisibilityPolicy, bare: FormatContext): List<DescribedLine> {
        // Calories hidden: amounts are counted in household measures, never weighed out.
        val householdOnly = policy.level(Metric.Calories) == Visibility.Hide
        return review.rows.map { row ->
            val draft = row.draft()
            val units = row.food?.let { DescribeRows.units(it, householdOnly) }.orEmpty()
            DescribedLine(
                key = row.key,
                name = row.name,
                amount = draft.amountLabel,
                calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare),
                matched = row.food != null,
                canLess = DescribeRows.canLess(row),
                units = units.map { it.chipLabel },
                unitIndex = units.indexOf(row.unit),
                alternatives = row.alternatives.map { it.name },
            )
        }
    }

    /** Add writes exactly the lines. */
    private fun describeTotal(review: DescribeReview, policy: VisibilityPolicy, context: FormatContext): DescribeTotal? {
        if (review.rows.isEmpty()) return null
        val drafts = review.rows.map { it.draft() }
        return DescribeTotal(count = drafts.size, calories = policy.resolve(Metric.Calories, drafts.totalKcal(), Surface.Logger, context))
    }

    private fun describeFind(find: DescribeFind, review: DescribeReview, policy: VisibilityPolicy, bare: FormatContext): DescribeFindUi {
        val query = find.query.trim()
        val answer = find.answer?.takeIf { it.query == query }
        val serving = FoodAmount.Servings(1.0)
        return DescribeFindUi(
            replacing = find.replacing?.let { key -> review.rows.firstOrNull { it.key == key }?.name },
            query = find.query,
            results = when {
                query.isEmpty() -> emptyList()
                answer == null -> null
                else -> answer.foods.map { food ->
                    SwapResultUi(
                        id = food.id,
                        name = food.name,
                        subtitle = food.brand,
                        amount = HouseholdAmount.tidy(food.amountTextFor(serving)),
                        calories = policy.resolve(Metric.Calories, food.nutrientsFor(serving).kcal, Surface.Logger, bare),
                    )
                }
            },
        )
    }
}
