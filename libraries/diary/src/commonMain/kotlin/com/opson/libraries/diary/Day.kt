package com.opson.libraries.diary

import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Everything logged on one date. Built from the items, the day record and the
 * feelings; a date nobody touched is [Day.empty], so screens never branch on
 * "no row yet".
 *
 * Totals are computed here, never stored: a stored total is a second source of
 * truth that drifts.
 */
data class Day(
    val date: LocalDate,
    /** Every slot is a key, each list oldest first. */
    val itemsBySlot: Map<MealSlot, List<LogItem>> = emptyItems(),
    val waterMl: Int = 0,
    val closedAt: Instant? = null,
    val note: String? = null,
    /** Slots the user marked "ate, no details". */
    val ateNoDetails: Set<MealSlot> = emptySet(),
    /** This day's meal-groups choice; null follows [Settings.mealGroups]. */
    val mealGroups: Boolean? = null,
    val feelings: Map<MealSlot, MealFeeling> = emptyMap(),
    /** Marked untracked (a holiday): Trends leaves a gap instead of reading it as a low day. */
    val untracked: Boolean = false,
    /** Day close's "How did today feel?", when answered. Only the user sees it. */
    val feeling: DayFeeling? = null,
) {
    val allItems: List<LogItem> get() = MealSlot.entries.flatMap { items(it) }

    fun items(slot: MealSlot): List<LogItem> = itemsBySlot[slot].orEmpty()

    /** A meal counts as done when it has items or was marked "ate, no details". */
    fun isMealDone(slot: MealSlot): Boolean = items(slot).isNotEmpty() || slot in ateNoDetails

    /** How many of the four meals are done. Uncategorized is not a meal. */
    val doneMealCount: Int get() = MealSlot.meals.count(::isMealDone)

    /** Anything at all for the calendar dot: an item or an "ate, no details". */
    val hasLogged: Boolean get() = itemsBySlot.values.any { it.isNotEmpty() } || ateNoDetails.isNotEmpty()

    /**
     * Whether Trends reads this day at all: something logged, and not marked
     * [untracked]. An untracked day is a gap in every chart and average, never
     * a low day.
     */
    val countsInTrends: Boolean get() = hasLogged && !untracked

    /**
     * The day's totals, or one slot's. A slot marked "ate, no details" adds one
     * unknown, so a day with a mystery lunch never reads as complete.
     */
    fun totals(slot: MealSlot? = null): Totals {
        val slots = slot?.let(::listOf) ?: MealSlot.entries
        return slots.fold(Totals.Empty) { acc, s ->
            acc + items(s).totals().plusUnknown(if (s in ateNoDetails) 1 else 0)
        }
    }

    fun mealGroupsOn(settings: Settings): Boolean = mealGroups ?: settings.mealGroups

    /** Closed by the user, or simply in the past. Either one reveals delayed numbers. */
    fun isClosed(today: LocalDate): Boolean = closedAt != null || date < today

    /** The policy context for a number on this day. */
    fun formatContext(
        today: LocalDate,
        of: Double? = null,
        bare: Boolean = false,
        waterUnit: WaterUnit = WaterUnit.Ml,
        weightUnit: WeightUnit = WeightUnit.Kg,
    ): FormatContext = FormatContext(
        of = of,
        bare = bare,
        unit = waterUnit,
        weightUnit = weightUnit,
        day = date,
        dayClosed = isClosed(today),
    )

    companion object {
        fun empty(date: LocalDate): Day = Day(date)

        private fun emptyItems(): Map<MealSlot, List<LogItem>> = MealSlot.entries.associateWith { emptyList() }
    }
}

/** "How it felt" for one meal. Only the user sees it. */
data class MealFeeling(
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
    val moods: Set<Mood> = emptySet(),
    val note: String? = null,
) {
    val isEmpty: Boolean get() = hunger == null && fullness == null && moods.isEmpty() && note.isNullOrBlank()
}

/** Day close's optional "How did today feel?", in the design's order (4.5). [wire] is what syncs. */
enum class DayFeeling(val wire: String) {
    Steady("steady"),
    Busy("busy"),
    Tired("tired"),
    Good("good"),
    Hard("hard"),
    ;

    companion object {
        fun fromWire(value: String?): DayFeeling? = entries.firstOrNull { it.wire == value }
    }
}

/** Hunger before eating, in the Meal sheet's order. [wire] is what syncs. */
enum class Hunger(val wire: String) {
    VeryHungry("very_hungry"),
    Hungry("hungry"),
    Peckish("peckish"),
    NotHungry("not_hungry"),
    ;

    companion object {
        fun fromWire(value: String?): Hunger? = entries.firstOrNull { it.wire == value }
    }
}

/** Fullness after eating, in the Meal sheet's order. */
enum class Fullness(val wire: String) {
    StillHungry("still_hungry"),
    Satisfied("satisfied"),
    Full("full"),
    VeryFull("very_full"),
    ;

    companion object {
        fun fromWire(value: String?): Fullness? = entries.firstOrNull { it.wire == value }
    }
}

enum class Mood(val wire: String) {
    Calm("calm"),
    Happy("happy"),
    Social("social"),
    Rushed("rushed"),
    Stressed("stressed"),
    Anxious("anxious"),
    Low("low"),
    ;

    companion object {
        fun fromWire(value: String): Mood? = entries.firstOrNull { it.wire == value }
    }
}
