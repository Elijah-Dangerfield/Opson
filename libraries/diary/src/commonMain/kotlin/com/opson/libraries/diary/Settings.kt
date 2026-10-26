package com.opson.libraries.diary

import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WaterDefaults
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the user chooses, persisted on the device and mirrored to the
 * server as one JSON object (`user_settings.settings`).
 *
 * Defaults are the prototype's `DEFAULTS` (`docs/design/handoff/phone-logic.js`)
 * and the field list is `docs/plans/screens/shared.md` "Settings fields". A
 * field the prototype leaves undefined takes the value its code treats
 * undefined as (`mealGroups !== false` is on, `healthOpen !== false` is open,
 * `healthToday ?? (preset is Full)` is null here).
 *
 * Adding a field is non-breaking: missing keys decode to the default. Settings
 * only change when the user changes them.
 */
@Serializable
data class Settings(
    /** Preset, overrides, "Pause before showing more" and when the user last changed them. */
    @Serializable(with = NumbersSettingsSerializer::class)
    val numbers: NumbersSettings = NumbersSettings(),
    /** Set when Sprint is picked. Drives "day N of 14". */
    val sprintStartedOn: LocalDate? = null,
    @Serializable(with = TargetsSerializer::class)
    val targets: Targets = Targets.Default,
    val macroMode: MacroMode = MacroMode.Ratios,
    /**
     * 0.1's key, never read: 0.1 synced the prototype's default for everyone who never saw a
     * question, so it says nothing about a choice. Kept so old JSON round-trips. See [exerciseRange].
     */
    @Serializable(with = ExerciseAdjustmentSerializer::class)
    val exercise: ExerciseAdjustment = ExerciseAdjustment.Default,
    val ringMode: RingMode = RingMode.Target,
    val trendsRange: TrendsRange = TrendsRange.Week,
    val trendsCards: TrendsCards = TrendsCards(),
    @Serializable(with = DayOfWeekSerializer::class)
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val waterGlasses: Int = WaterDefaults.GLASSES,
    val glassMl: Int = WaterDefaults.GLASS_ML,
    @Serializable(with = WaterUnitSerializer::class)
    val waterUnit: WaterUnit = WaterUnit.Ml,
    @Serializable(with = WeightUnitSerializer::class)
    val weightUnit: WeightUnit = WeightUnit.Kg,
    /** Global, as the prototype. A day can override it through `Day.mealGroups`. */
    val mealGroups: Boolean = true,
    /** Hunger and fullness chips on the amount sheet. */
    val askTags: Boolean = true,
    val tone: Tone = Tone.Plain,
    val bigDayEvent: EventLevel = EventLevel.Full,
    val palette: PaletteChoice = PaletteChoice.Pine,
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
    val onboarding: OnboardingAnswers = OnboardingAnswers(),

    // 0.2: declared now so the serialized default exists. No UI in 0.1.
    val fasting: Boolean = false,
    val fastToday: Boolean = true,
    @Serializable(with = FastingPlanSerializer::class)
    val fastPlan: FastingPlan = FastingPlan.Default,
    /** Health card on Today; null follows the preset (on only in Full). */
    val healthToday: Boolean? = null,
    val healthRows: HealthRows = HealthRows(),
    val healthOpen: Boolean = true,
    val quiet: Boolean = true,
    val mealReminders: MealReminders = MealReminders(),
    /** When each meal usually happens: Today's "Usually around", Rhythm's "planned around", and the meal reminder times. */
    @Serializable(with = MealTimesSerializer::class)
    val mealTimes: MealTimes = MealTimes(),
    /**
     * The person picked a meal reminder level or turned a meal on themselves. 0.1 synced the
     * prototype's defaults ([remindersEvent] Subtle, three meals on) for everyone who never saw
     * the page, so until this is true those defaults are not acted on. See [mealReminderLevel].
     */
    val remindersChosen: Boolean = false,
    val waterReminders: Boolean = false,
    val newFoodEvent: EventLevel = EventLevel.Subtle,
    val recapEvent: EventLevel = EventLevel.Subtle,
    val remindersEvent: EventLevel = EventLevel.Subtle,
    /**
     * "Should exercise change your range?" (0.2, Watch and health apps). Null until the person
     * picks, which counts as No. Read it through [exerciseInRange].
     */
    @Serializable(with = ExerciseRangeSerializer::class)
    val exerciseRange: ExerciseAdjustment? = null,
    /**
     * The Logger's search options (0.2, `release.searchOptions`): how results are ordered,
     * which foods they come from, and whether the category tabs show. Top-level keys, because
     * settings sync merges shallowly. The tab picked is not kept; the Logger opens on All.
     */
    val searchSort: SearchSort = SearchSort.BestMatch,
    val searchDatabase: SearchDatabase = SearchDatabase.All,
    val searchTabs: Boolean = true,

    // Today's summary card (design sync 2, `release.todaySummary`). Top-level keys on purpose:
    // the server merges settings one level deep.
    /** The card shrunk to one line by choice, kept across days and restarts. */
    val todaySummaryCollapsed: Boolean = false,
    /** The page last left open: 0 energy, 1 macros. */
    val todaySummaryPage: Int = 0,
    /** Page 1's stats as `SummaryStat` wire keys, in order; null follows the onboarding goals. */
    val todaySummaryStats: List<String>? = null,
    /** Page 2, the macros page. */
    val todayMacrosPage: Boolean = true,
) {
    val waterGoalMl: Int get() = waterGlasses * glassMl

    /** How noticeable meal reminders are, as chosen: Off until the person has chosen ([remindersChosen]). */
    val mealReminderLevel: EventLevel get() = if (remindersChosen) remindersEvent else EventLevel.Off

    /**
     * How much watch activity may add to today's range. [ExerciseAdjustment.None] until the person
     * picks, and always for someone who said numbers have been hard, whatever is stored.
     */
    val exerciseInRange: ExerciseAdjustment
        get() = if (onboarding.hard) ExerciseAdjustment.None else exerciseRange ?: ExerciseAdjustment.None

    /** Same settings with different numbers. `changedAt` is whatever [next] carries. */
    fun withNumbers(next: NumbersSettings): Settings = copy(numbers = next)
}

/** The subset of [Settings] the `VisibilityPolicy` reads. */
fun Settings.numbers(): NumbersSettings = numbers

@Serializable
enum class MacroMode {
    /** Grams follow the energy target, so the split stays the same. */
    @SerialName("ratios")
    Ratios,

    /** Grams stay put when the energy target changes. */
    @SerialName("grams")
    Grams,
}

/** The third ring on Today's summary card. */
@Serializable
enum class RingMode {
    @SerialName("target")
    Target,

    @SerialName("remaining")
    Remaining,

    @SerialName("balance")
    Balance,
}

@Serializable
enum class TrendsRange(val days: Int) {
    @SerialName("week")
    Week(7),

    @SerialName("four_weeks")
    FourWeeks(28),

    @SerialName("three_months")
    ThreeMonths(90),
}

@Serializable
data class TrendsCards(
    val intake: Boolean = true,
    val recap: Boolean = true,
    val nutrients: Boolean = true,
    val expended: Boolean = true,
    val weight: Boolean = true,
)

/** How the Logger orders search results. */
@Serializable
enum class SearchSort {
    /** The server's ranking, your own matching foods first: what search did before options. */
    @SerialName("best_match")
    BestMatch,

    /** Foods you've logged first, last used first, then the rest. */
    @SerialName("most_recent")
    MostRecent,

    @SerialName("a_to_z")
    AToZ,
}

/** Where the Logger's All tab looks for foods. */
@Serializable
enum class SearchDatabase {
    @SerialName("all")
    All,

    @SerialName("usda")
    Usda,

    /** Only offered while the server searches Open Food Facts; otherwise read as [All]. */
    @SerialName("open_food_facts")
    OpenFoodFacts,

    /** Foods you made and foods you've logged. No server call. */
    @SerialName("my_foods")
    MyFoods,
}

/** Which empty-state and toast lines the app uses. */
@Serializable
enum class Tone {
    @SerialName("plain")
    Plain,

    @SerialName("warm")
    Warm,
}

/** How noticeable an event (a big day, a new food, the weekly recap, meal reminders) is. */
@Serializable
enum class EventLevel {
    @SerialName("off")
    Off,

    @SerialName("subtle")
    Subtle,

    @SerialName("full")
    Full,
}

/**
 * The brand palette. Kept here as a key so this module does not depend on
 * `:libraries:ui`; the shell maps it to the theme's palette.
 */
@Serializable
enum class PaletteChoice {
    @SerialName("pine")
    Pine,

    @SerialName("espresso")
    Espresso,

    @SerialName("clay")
    Clay,

    @SerialName("plum")
    Plum,
}

@Serializable
enum class ThemeMode {
    @SerialName("system")
    System,

    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark,
}

@Serializable
enum class TextSize {
    @SerialName("normal")
    Normal,

    /** 19 sp base instead of 16. */
    @SerialName("large")
    Large,
}

/** Which rows the Health card shows. Prototype `healthRows` defaults. */
@Serializable
data class HealthRows(
    val steps: Boolean = true,
    val heartRate: Boolean = true,
    val restingHeartRate: Boolean = true,
    val hrv: Boolean = false,
    val sleep: Boolean = true,
    val vo2Max: Boolean = false,
    val workouts: Boolean = true,
)

/** Prototype `mealRem`. */
@Serializable
data class MealReminders(
    val breakfast: Boolean = true,
    val lunch: Boolean = true,
    val snack: Boolean = false,
    val dinner: Boolean = true,
) {
    /** Uncategorized is not a meal, so it never has a reminder. */
    fun isOn(slot: MealSlot): Boolean = when (slot) {
        MealSlot.Breakfast -> breakfast
        MealSlot.Lunch -> lunch
        MealSlot.Snack -> snack
        MealSlot.Dinner -> dinner
        MealSlot.Uncategorized -> false
    }

    fun with(slot: MealSlot, on: Boolean): MealReminders = when (slot) {
        MealSlot.Breakfast -> copy(breakfast = on)
        MealSlot.Lunch -> copy(lunch = on)
        MealSlot.Snack -> copy(snack = on)
        MealSlot.Dinner -> copy(dinner = on)
        MealSlot.Uncategorized -> this
    }

    val countOn: Int get() = MealSlot.meals.count(::isOn)
}

/**
 * When each meal usually happens. The defaults are the times Today used before they were a
 * setting, so nothing on Today moved when they became one. Serialized as `"08:00"` per meal by
 * [MealTimesSerializer]; a missing or unreadable time falls back to that meal's default.
 */
data class MealTimes(
    val breakfast: LocalTime = DEFAULT_BREAKFAST,
    val lunch: LocalTime = DEFAULT_LUNCH,
    val snack: LocalTime = DEFAULT_SNACK,
    val dinner: LocalTime = DEFAULT_DINNER,
) {
    /** Uncategorized has no time of its own; it reads as lunch, the middle of the day. */
    fun at(slot: MealSlot): LocalTime = when (slot) {
        MealSlot.Breakfast -> breakfast
        MealSlot.Lunch, MealSlot.Uncategorized -> lunch
        MealSlot.Snack -> snack
        MealSlot.Dinner -> dinner
    }

    fun with(slot: MealSlot, time: LocalTime): MealTimes = when (slot) {
        MealSlot.Breakfast -> copy(breakfast = time)
        MealSlot.Lunch -> copy(lunch = time)
        MealSlot.Snack -> copy(snack = time)
        MealSlot.Dinner -> copy(dinner = time)
        MealSlot.Uncategorized -> this
    }

    companion object {
        val DEFAULT_BREAKFAST = LocalTime(8, 0)
        val DEFAULT_LUNCH = LocalTime(12, 30)
        val DEFAULT_SNACK = LocalTime(15, 30)
        val DEFAULT_DINNER = LocalTime(19, 0)
    }
}

/**
 * The four onboarding questions, as the option keys onboarding defines. Every
 * one is skippable, so empty is a real answer. Never sent to telemetry.
 */
@Serializable
data class OnboardingAnswers(
    val why: List<String> = emptyList(),
    val feel: String? = null,
    val notice: List<String> = emptyList(),
    /** "Numbers or tracking have been hard for me before". Hides the Full option. */
    val hard: Boolean = false,
)
