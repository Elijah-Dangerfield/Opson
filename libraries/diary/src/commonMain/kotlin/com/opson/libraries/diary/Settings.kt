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
    val waterReminders: Boolean = false,
    val newFoodEvent: EventLevel = EventLevel.Subtle,
    val recapEvent: EventLevel = EventLevel.Subtle,
    val remindersEvent: EventLevel = EventLevel.Subtle,
) {
    val waterGoalMl: Int get() = waterGlasses * glassMl

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
)

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
