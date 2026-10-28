package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine

/**
 * A food a + logs as it stands: [draft] is the amount it goes in at, the person's last one when
 * they have logged it before ([usual] when that amount is a habit), else one serving.
 */
data class FoodPick(
    val key: String,
    val ref: FoodRef,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
    val subtitle: String? = null,
    val usual: Boolean = false,
) {
    fun toLogPick(): LogPick = LogPick(key = key, ref = ref, draft = draft, source = source, food = food)
}

/**
 * A row with a +: in search, and under Recent on the add sheet. [key] is unique per food, so
 * the same food reads as added in every section.
 */
data class FoodRowUi(
    val key: String,
    val ref: FoodRef,
    val source: PickSource,
    val name: String,
    /** "Jasmine rice" for "Jasmine rice, cooked": what the Undo snackbar names. */
    val shortName: String,
    /** The brand (cuisine, once the catalog has it), or null. */
    val subtitle: String?,
    /** "Your usual": the amount is the one this person keeps logging. */
    val usual: Boolean,
    val amount: String,
    val macros: List<MacroPart>,
    /** Bare, as a row draws it: "205". */
    val calories: Display,
    /** Logged to this meal since the screen opened (✓), or picked for a meal being put together. */
    val added: Boolean,
    /** Where the food came from; null with the search options off, or for a source with no badge. */
    val badge: SourceBadge? = null,
    /** Tiers 1 to 3: someone accountable stands behind the numbers. Never set for the person's own foods. */
    val verified: Boolean = false,
)

/** The foods a person has logged before, by food key, and the ones they keep coming back to. */
data class LoggedBefore(val last: Map<String, LogItem> = emptyMap(), val habits: Set<String> = emptySet()) {
    companion object {
        /** [history] newest first; [frequent] the foods logged most lately. */
        fun of(history: List<LogItem>, frequent: List<LogItem>): LoggedBefore = LoggedBefore(
            last = history.mapNotNull { item -> item.foodRef()?.let { it.key to item } }.distinctBy { it.first }.toMap(),
            habits = frequent.mapNotNullTo(HashSet()) { it.foodRef()?.key },
        )
    }
}

/**
 * What a one-tap + has logged that the day has not shown back yet. A second tap queued behind
 * the first runs before the day is read again, so the row has no ✓ to take it back out yet and
 * would log the food twice; while its key is here a tap is ignored. Once the day shows the
 * items, [addedSince][FoodPicks.addedSince] has them and the ✓ takes over.
 */
internal class UnshownLogs {
    private val byKey = mutableMapOf<String, List<String>>()

    operator fun contains(key: String): Boolean = key in byKey

    fun logged(key: String, itemIds: List<String>) {
        if (itemIds.isNotEmpty()) byKey[key] = itemIds
    }

    /** The day was read again: whatever it shows is no longer waiting. */
    fun shown(day: Day) {
        val there = day.allItems.mapTo(HashSet()) { it.id }
        byKey.values.removeAll { ids -> ids.any { it in there } }
    }
}

internal object FoodPicks {

    /** A food from the diary, at the amount it was logged: what Usual and Recent list. */
    fun LogItem.toPick(source: PickSource, before: LoggedBefore = LoggedBefore()): FoodPick? {
        val ref = foodRef() ?: return null
        return FoodPick(key = ref.key, ref = ref, draft = againDraft().copy(method = source.methodFor(ref)), source = source, usual = ref.key in before.habits)
    }

    /** A catalog food at the person's last amount of it, else one serving. */
    fun Food.toPick(before: LoggedBefore = LoggedBefore()): FoodPick {
        val ref = FoodRef.Catalog(id)
        val draft = before.last[ref.key]?.againDraft() ?: toLogDraft(FoodAmount.Servings(1.0))
        return FoodPick(
            key = ref.key,
            ref = ref,
            draft = draft.copy(method = PickSource.Search.methodFor(ref)),
            source = PickSource.Search,
            food = this,
            subtitle = brand,
            usual = ref.key in before.habits,
        )
    }

    fun CustomFood.toPick(before: LoggedBefore = LoggedBefore()): FoodPick {
        val ref = FoodRef.Custom(id)
        val draft = before.last[ref.key]?.againDraft()?.copy(method = PickSource.Search.methodFor(ref)) ?: draftFor(initialQuantity)
        return FoodPick(key = ref.key, ref = ref, draft = draft, source = PickSource.Search, subtitle = brand, usual = ref.key in before.habits)
    }

    fun FoodPick.matches(query: String): Boolean =
        draft.name.contains(query, ignoreCase = true) || subtitle?.contains(query, ignoreCase = true) == true

    /**
     * The foods logged to [slot] on [day] since the screen opened: whatever is there now and was
     * not in [seen]. Null [seen] is before the day was first read, so nothing is new yet.
     */
    fun addedSince(day: Day, slot: MealSlot, seen: Set<String>?): Map<String, List<String>> {
        if (seen == null) return emptyMap()
        return day.items(slot)
            .filter { it.id !in seen }
            .mapNotNull { item -> item.foodRef()?.key?.let { it to item.id } }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * [known] is null with the search options off, which draws no badges, except NEVO's: RIVM
     * allows its foods only with its name beside them.
     */
    fun FoodPick.toRow(policy: VisibilityPolicy, bare: FormatContext, added: Boolean, known: Map<String, Food>? = null): FoodRowUi {
        val catalogFood = if (known != null && ref is FoodRef.Catalog) food ?: known[ref.id] else null
        return FoodRowUi(
            key = key,
            ref = ref,
            source = source,
            name = draft.name,
            shortName = shortName(draft.name),
            subtitle = subtitle,
            usual = usual,
            amount = draft.amountLabel,
            macros = if (draft.hasMacros) {
                policy.macroLine(draft.amounts(), Surface.Logger, bare, MacroLineStyle.Compact)
            } else {
                emptyList()
            },
            calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare),
            added = added,
            badge = when {
                (food ?: catalogFood)?.source == FoodSource.Nevo -> SourceBadge.Nevo
                known == null -> null
                ref is FoodRef.Custom -> SourceBadge.YourFood
                else -> catalogFood?.let { SourceBadge.of(it.source) }
            },
            verified = catalogFood?.verified == true,
        )
    }
}
