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
    /** Day close's check-in (design sync 2, `release.checkIn`), when any of it was answered. Only the user sees it. */
    val checkIn: CheckIn? = null,
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

    /**
     * Closed with Close the day: nothing can be added or changed until it is reopened
     * ([DayWrite.DayClosed]). Unlike [isClosed], a past day nobody closed stays open to logging.
     */
    val closedByUser: Boolean get() = closedAt != null

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

/**
 * A meal's note and how hungry it was. Only the user sees it. [fullness] and [moods] came from
 * the "How it felt" sheet the Note replaced: nothing asks for them now, but a row that has them
 * keeps them, so every write copies the stored feeling and changes only what it means to.
 */
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

/**
 * Day close's once-a-day check-in (design sync 2, 4.1): hunger through the day, energy, last
 * night's sleep and any moods. Every row is optional and one left blank stays blank. It replaces
 * the questions that used to follow each food; Patterns reads it as one entry per day.
 */
data class CheckIn(
    val hunger: DayHunger? = null,
    val energy: Energy? = null,
    val sleep: Sleep? = null,
    val moods: Set<CheckInMood> = emptySet(),
) {
    val isEmpty: Boolean get() = hunger == null && energy == null && sleep == null && moods.isEmpty()
}

/** How often hunger came up through the day, least to most. [wire] is what syncs. */
enum class DayHunger(val wire: String) {
    Rarely("rarely"),
    Some("some"),
    Steady("steady"),
    Often("often"),
    Very("very"),
    ;

    companion object {
        fun fromWire(value: String?): DayHunger? = entries.firstOrNull { it.wire == value }
    }
}

enum class Energy(val wire: String) {
    Low("low"),
    Ok("ok"),
    Good("good"),
    ;

    companion object {
        fun fromWire(value: String?): Energy? = entries.firstOrNull { it.wire == value }
    }
}

enum class Sleep(val wire: String) {
    Poor("poor"),
    Ok("ok"),
    Good("good"),
    ;

    companion object {
        fun fromWire(value: String?): Sleep? = entries.firstOrNull { it.wire == value }
    }
}

enum class CheckInMood(val wire: String) {
    Calm("calm"),
    Social("social"),
    Stressed("stressed"),
    Tired("tired"),
    Low("low"),
    Happy("happy"),
    ;

    companion object {
        fun fromWire(value: String?): CheckInMood? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * How hungry, one five-step scale from [Starving] to [Stuffed], in that order. [wire] is what syncs.
 *
 * The scale used to be four steps of hunger alone. [Starving], [Hungry] and [Neutral] keep the
 * wire values those steps were stored under, so rows written then still read, and app versions
 * from then still read these three. The old "peckish" reads as [Hungry].
 */
enum class Hunger(val wire: String) {
    Starving("very_hungry"),
    Hungry("hungry"),
    Neutral("not_hungry"),
    Full("full"),
    Stuffed("stuffed"),
    ;

    companion object {
        private const val OLD_PECKISH = "peckish"

        fun fromWire(value: String?): Hunger? = if (value == OLD_PECKISH) Hungry else entries.firstOrNull { it.wire == value }
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
