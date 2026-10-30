package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodyFat
import com.opson.libraries.nutrition.BodyProfile
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.MicroReferences
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WaterDefaults
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
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
 * only change when the user changes them. The server merges a write into what it
 * has one level deep, so a new field is a new top-level key: [SettingsSerializer]
 * lifts the ones that live under [numbers] and [targets] (the goal style) out to the top.
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = SettingsSerializer::class)
data class Settings(
    /**
     * Preset, overrides, "Pause before showing more", when Later opens, End after a set time, and
     * when the user last changed them. Written for 0.1 phones too: see [NumbersSettingsSerializer].
     */
    @Serializable(with = NumbersSettingsSerializer::class)
    val numbers: NumbersSettings = NumbersSettings(),
    /**
     * When End after a set time last switched the mode, and from what, so Today says so on that
     * day and no other (design sync 4, 13.5). A top-level key: the server merges settings one
     * level deep.
     */
    val modeTimerEnded: ModeTimerEnded? = null,
    @Serializable(with = TargetsSerializer::class)
    val targets: Targets = Targets.Default,
    val macroMode: MacroMode = MacroMode.Ratios,
    /**
     * 0.1's key, never read: 0.1 synced the prototype's default for everyone who never saw a
     * question, so it says nothing about a choice. Kept so old JSON round-trips. See [exerciseRange].
     */
    @Serializable(with = ExerciseAdjustmentSerializer::class)
    val exercise: ExerciseAdjustment = ExerciseAdjustment.Default,
    /** Synced since 0.1 and never read by any build: Today's rings draw without it. Kept so old JSON round-trips. */
    val ringMode: RingMode = RingMode.Target,
    val trendsRange: TrendsRange = TrendsRange.Week,
    val trendsCards: TrendsCards = TrendsCards(),
    @Serializable(with = DayOfWeekSerializer::class)
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val waterGlasses: Int = WaterDefaults.GLASSES,
    val glassMl: Int = WaterDefaults.GLASS_ML,
    @Serializable(with = WaterUnitSerializer::class)
    val waterUnit: WaterUnit = WaterUnit.Ml,
    /**
     * Kilograms until something sets it, for everyone, so read it through [withRegionWeightUnit]
     * (the settings repository already does): until the person picks a unit, or onboarding or
     * Profile set their units, the phone's region decides.
     */
    @Serializable(with = WeightUnitSerializer::class)
    val weightUnit: WeightUnit = WeightUnit.Kg,
    /**
     * The person picked [weightUnit] on Display. Stored defaults are encoded like any other value,
     * so a stored "kg" can't say on its own whether anyone chose it. A top-level key, like the rest.
     */
    val weightUnitChosen: Boolean = false,
    /** Global, as the prototype. A day can override it through `Day.mealGroups`. */
    val mealGroups: Boolean = true,
    /**
     * The hunger and fullness questions' old switch. Never read: the questions are always asked
     * and always skippable (2026-10-29). Kept so old JSON round-trips.
     */
    val askTags: Boolean = true,
    /**
     * How we talk to you, once a setting. Never read: the voice is the brand's, and everything
     * speaks in [Tone.Plain] (2026-10-29). Kept so old JSON round-trips.
     */
    val tone: Tone = Tone.Plain,
    val bigDayEvent: EventLevel = EventLevel.Full,
    /**
     * Appearance: System, Light or Dark. There is one color scheme, Slate (design sync 4), so
     * 0.1's `palette` key is no longer read or written. A 0.1 phone still finds its own value on
     * the server, because the merge keeps keys a save leaves out.
     */
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
    /** When each meal usually happens: Today's "Usually around" and the meal reminder times. */
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
    /**
     * Foods starred on the amount sheet, as food ref keys (`f:<id>`, `c:<id>`), newest first.
     * The search options' Favorites tab lists them first. A top-level key, like the rest.
     */
    val favoriteFoods: List<String> = emptyList(),
    /**
     * Sizes of the person's own for a food ("Custom size…" on the amount sheet), by food ref key.
     * They show in that food's unit menu after its own portions.
     */
    val foodUnits: Map<String, List<FoodUnit>> = emptyMap(),
    /**
     * Foods taken off Recent from the food detail's ⋮, by food ref key, with when (epoch ms). A food
     * logged again after that comes back. A top-level key, because settings sync merges shallowly.
     */
    val hiddenRecentFoods: Map<String, Long> = emptyMap(),

    // Today's summary card (design sync 2, `release.todaySummary`). Legacy, no longer written:
    // the card's page and collapse live on each phone in `TodayCardViewStore`, so a swipe on one
    // phone can't race a mode change on another. Read only as Today's fallback until this
    // phone's store holds a value; kept while 0.1 clients still sync them.
    /** Legacy: whether the card was shrunk to one line. See above. */
    val todaySummaryCollapsed: Boolean = false,
    /** Legacy: the page last left open, 0 energy, 1 macros. See above. */
    val todaySummaryPage: Int = 0,
    /** Page 1's stats as `SummaryStat` wire keys, in order; null follows the onboarding goals. */
    val todaySummaryStats: List<String>? = null,
    /** Page 2, the macros page. */
    val todayMacrosPage: Boolean = true,
    /**
     * Close the day's check-in card's old switch. Never read: the check-in shows whenever
     * `release.checkIn` is on, and skipping it is the way to say no (2026-10-29). Kept so old JSON
     * round-trips.
     */
    val checkIn: Boolean = true,

    // Burned (design sync 4, `release.goalsAndEnergy`): where resting energy comes from, what the
    // calculation needs, and whether synced activity counts. Top-level keys, because the server
    // merges settings one level deep. Weight is the latest weigh-in, not a setting.
    @Serializable(with = RestingSourceSerializer::class)
    val restingSource: RestingSource = RestingSource.Calculate,
    /** "Enter my own", in kcal a day; kept when another source is picked, so switching back finds it. */
    val restingOwnKcal: Int? = null,
    /** "Add activity to Burned". Off, Burned is resting energy only. */
    val activityInBurned: Boolean = true,
    /**
     * "Count digesting food": the thermic effect of food, about a tenth of what is eaten
     * (`THERMIC_EFFECT_OF_FOOD_SHARE`), added to Burned. Off by default, as Cronometer has it.
     */
    val thermicEffectInBurned: Boolean = false,
    /**
     * The year of birth, which every calculation reads (age is the year's). Still written whenever
     * [birthDate] is, so a build that only knows the year keeps working; read the date through
     * [knownBirthDate], which drops it once an older build moved the year on its own.
     */
    val birthYear: Int? = null,
    /**
     * The full date of birth, from Profile or onboarding. Only Today's birthday line reads the day;
     * set it through [withBirthDate] so [birthYear] follows. A top-level key, like the rest.
     */
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    /**
     * How the person types their height, centimetres or feet and inches. Null until they pick
     * one; [heightUnitOr] says what to show until then. [heightCm] is centimetres either way.
     */
    @Serializable(with = HeightUnitSerializer::class)
    val heightUnit: HeightUnit? = null,
    @Serializable(with = BodySexSerializer::class)
    val bodySex: BodySex? = null,
    /**
     * Pregnant or breastfeeding, picked with sex. It adds that stage's energy to the estimate and
     * keeps weight change out of the plan ([BodyStage]). Never null, so going back to none reaches
     * the server rather than the merge keeping the old answer.
     */
    @Serializable(with = BodyStageSerializer::class)
    val bodyStage: BodyStage = BodyStage.None,
    /**
     * Body fat as a percentage, optional, typed on More › Profile; read it through [bodyFat]. Zero is
     * "not given" rather than null, because a null is left out of the write and the server's merge
     * would keep the old value, so clearing it on one phone would never reach the others. Nothing
     * calculates from it yet. A top-level key, like the rest of the profile.
     */
    val bodyFatPercent: Double = 0.0,
    /**
     * How active an ordinary day is, outside workouts: Burned's everyday movement whenever no
     * health app reads activity, so a deficit or surplus works without one. Picked on Resting
     * energy, Targets and onboarding. A top-level key, like the rest of Burned's settings.
     */
    @Serializable(with = EverydayActivitySerializer::class)
    val everydayActivity: EverydayActivity = EverydayActivity.Default,

    /**
     * The weight goal as builds before goals v2 read it: which way, the target and pace, where it
     * started, and the aim it last wrote to [targets] ([WeightGoal.drives]). [goals] is the source
     * now; [GoalBook] keeps this in step with it on every write. One top-level object, so the
     * server's one-level merge replaces it whole, and never null, so clearing it reaches the server.
     */
    @Serializable(with = WeightGoalSerializer::class)
    val weightGoal: WeightGoal = WeightGoal.None,
    /**
     * Every goal (docs/plans/goals-v2.md): weight, a set energy balance, burn, steps, tracking
     * every day, meals. Null until a build that knows goals saves them; read them through
     * [GoalBook.goals] ([allGoals]), which migrates [weightGoal] until then and folds back a
     * [weightGoal] an older build changed. A top-level key; written whole.
     */
    @Serializable(with = GoalsSerializer::class)
    val goals: List<Goal>? = null,
    /**
     * The last step of setting a goal: "I'm committed", or "Not now", and when. Nothing changes
     * with the answer; Close the day and celebrations may mention it. Null until that step is reached.
     */
    val planCommitment: PlanCommitment? = null,

    // More › Today screen (design sync 4, 13.6, `release.goalsAndEnergy`). Top-level keys, because
    // the server merges settings one level deep. Card pages are [todayMacrosPage].
    val todayCard: SummaryCardPreset = SummaryCardPreset.InOut,
    val todayMainNumber: MainNumberChoice = MainNumberChoice.Follow,
    /** The card's toggle, remembered per goal style ([mainNumberKey] to [MainNumberChoice.wire]). */
    val todayMainNumberByStyle: Map<String, String> = emptyMap(),
    /**
     * How each of the card's other figures was last read ([FlipFigure.key] to [Reading.wire]): a
     * tap on the macro row or a stat flips it, and the choice follows the person to their other
     * phones. A top-level key like the rest; builds that predate it skip it.
     */
    val todayReadings: Map<String, String> = emptyMap(),
    /** "Resting 1,610 · Active 570" under the Burned bar. */
    val todayBurnedBreakdown: Boolean = true,
    /** "3 of 4 meals" on the card. */
    val todayMealsCount: Boolean = false,
    /** The card's last page, a few vitamins and minerals and See all. Named for the link it replaced, so a stored choice carries over. */
    val todayNutrientsLink: Boolean = true,
    /** Rows in Today's list that can be turned off on Today alone. */
    val todayWater: Boolean = true,
    val todayWeight: Boolean = true,
    /** Close the day's button in Today's header, once a card at the end of the list. The ⋮ menu offers it either way. */
    val todayCloseDay: Boolean = true,

    // Vitamins and minerals (More › Targets › Vitamins and minerals). Top-level keys, because the
    // server merges settings one level deep, and never null, because a null is left out of the
    // write and the merge would keep the old value: empty is the default, and reaches the server.
    /** A reference the person set themselves, by `Micronutrient.key`, in its own unit. See [microReferences]. */
    val microTargets: Map<String, Double> = emptyMap(),
    /** The nutrients page on Today, as `Micronutrient.key`s in order; empty is [DEFAULT_TODAY_NUTRIENTS]. */
    val todayNutrients: List<String> = emptyList(),

    // Celebrations (`release.celebrations`): milestones for showing up, the streak on Today and
    // Your progress. Top-level keys, because the server merges settings one level deep.
    /**
     * Milestone celebrations, the streak on Today and the line after wrapping up a day. On by
     * default; the first celebration offers to turn it off, and More › Reminders and celebrations
     * turns it back on. Onboarding may read it.
     */
    val celebrationsEnabled: Boolean = true,
    /**
     * Whether celebrations say how many days ("4 days in a row"). Null until the person picks,
     * which follows the onboarding answer: off after numbers have been hard. Read it through
     * [celebrationStyle].
     */
    val celebrationCounts: Boolean? = null,
    /**
     * A second streak for a goal whose "met" is never eating less: tracking every day, meals, burn
     * or steps (`GoalStreaks.eligible`). Off until the person turns it on; onboarding asks when a
     * goal is set. The showing-up streak doesn't read it. A top-level key, synced with the rest.
     */
    val goalStreakEnabled: Boolean = false,
) {
    /** [birthDate] while it agrees with [birthYear]: an older build that changed only the year leaves the day unknown. */
    val knownBirthDate: LocalDate? get() = birthDate?.takeIf { it.year == birthYear }

    /** The date of birth, and the year every calculation reads with it. */
    fun withBirthDate(date: LocalDate): Settings = copy(birthDate = date, birthYear = date.year)

    /**
     * Whether [today] is their birthday. Someone born on 29 February has it on the 28th in a year
     * without one.
     */
    fun isBirthday(today: LocalDate): Boolean {
        val born = knownBirthDate ?: return false
        if (born.month == today.month && born.day == today.day) return true
        val leapDay = born.month == Month.FEBRUARY && born.day == LEAP_DAY
        return leapDay && today.month == Month.FEBRUARY && today.day == LEAP_DAY - 1 && !isLeapYear(today.year)
    }

    /**
     * How celebrations speak: [CelebrationStyle.Counts] with the day counts, [CelebrationStyle.Gentle]
     * with a kind line and no counts (the default after numbers have been hard), or
     * [CelebrationStyle.Off].
     */
    val celebrationStyle: CelebrationStyle
        get() = when {
            !celebrationsEnabled -> CelebrationStyle.Off
            celebrationCounts ?: !onboarding.hard -> CelebrationStyle.Counts
            else -> CelebrationStyle.Gentle
        }

    /** [bodyFatPercent] when one was given and makes sense. */
    val bodyFat: Double? get() = BodyFat.inRange(bodyFatPercent)

    /**
     * What the resting energy calculation is told: these details and [weightKg], on [today]. Age
     * is the birth year's; a [birthDate] that arrived without its year (written before
     * [withBirthDate] kept the two together) still counts, rather than leaving Burned unknown.
     */
    fun bodyProfile(weightKg: Double?, today: LocalDate): BodyProfile =
        BodyProfile(weightKg = weightKg, heightCm = heightCm, ageYears = (birthYear ?: birthDate?.year)?.let { today.year - it }, sex = bodySex)

    /**
     * Each vitamin and mineral's daily reference on [today]: by the age and sex Resting energy
     * collects, with the person's own numbers over it. Age is the birth year's, as Burned reads it.
     */
    fun microReferences(today: LocalDate): MicroReferences =
        MicroReferences.forPerson(bodySex, birthYear?.let { today.year - it }, ownMicroTargets)

    /** [microTargets] that name a tracked micronutrient; [MicroReferences] drops any that are not sensible. */
    val ownMicroTargets: Map<Micronutrient, Double>
        get() = microTargets.mapNotNull { (key, amount) -> Micronutrient.fromKey(key)?.let { it to amount } }.toMap()

    /** Sets [micro]'s own reference to [amount], or back to the suggested one when null. */
    fun withMicroTarget(micro: Micronutrient, amount: Double?): Settings =
        copy(microTargets = if (amount == null) microTargets - micro.key else microTargets + (micro.key to amount))

    /** What Today's nutrients page lists, in order: the person's picks, or [DEFAULT_TODAY_NUTRIENTS]. */
    val todayMicronutrients: List<Micronutrient>
        get() = todayNutrients.mapNotNull(Micronutrient::fromKey).distinct().take(TODAY_NUTRIENTS_MAX)
            .ifEmpty { DEFAULT_TODAY_NUTRIENTS }

    /**
     * Puts [micro] on Today's nutrients page, at the end, or takes it off. Adding a fifth does
     * nothing and taking off the last one does nothing: the page has room for four and is never empty.
     */
    fun withTodayNutrient(micro: Micronutrient, on: Boolean): Settings {
        val current = todayMicronutrients
        val next = when {
            on && micro !in current && current.size < TODAY_NUTRIENTS_MAX -> current + micro
            !on && micro in current && current.size > 1 -> current - micro
            else -> return this
        }
        return copy(todayNutrients = next.map { it.key })
    }

    /**
     * The height unit to show: the one picked, or else feet and inches for someone who weighs in
     * pounds (as height followed weight before it had its own unit), or else [regionDefault].
     */
    fun heightUnitOr(regionDefault: HeightUnit): HeightUnit =
        heightUnit ?: if (weightUnit == WeightUnit.Lb) HeightUnit.FtIn else regionDefault

    /**
     * These settings with [weightUnit] as the person should see it: what is stored once they picked
     * it ([weightUnitChosen]), once onboarding or Profile set their units ([heightUnit]), or when it
     * is not the stored default; before any of that, [regionDefault]. Height has always fallen back
     * to the region ([heightUnitOr]) and weight did not, so an American who never reached a unit
     * question saw 5'11" on Profile and kilograms on Weight, logged "170" as 170 kg, and resting
     * energy came out near 2,700 instead of 1,760. Nothing is written: the fallback is read, as
     * height's is.
     */
    fun withRegionWeightUnit(regionDefault: WeightUnit): Settings =
        if (weightUnitChosen || heightUnit != null || weightUnit != WeightUnit.Kg || weightUnit == regionDefault) {
            this
        } else {
            copy(weightUnit = regionDefault)
        }

    fun isFavorite(foodKey: String): Boolean = foodKey in favoriteFoods

    /** Stars [foodKey], or takes the star off. */
    fun toggleFavorite(foodKey: String): Settings =
        copy(favoriteFoods = if (isFavorite(foodKey)) favoriteFoods - foodKey else listOf(foodKey) + favoriteFoods)

    /** Takes [foodKey] off Recent as of [atEpochMs]; logging it after that brings it back. */
    fun hideFromRecent(foodKey: String, atEpochMs: Long): Settings =
        copy(hiddenRecentFoods = hiddenRecentFoods + (foodKey to atEpochMs))

    /** An entry of [foodKey] logged at [loggedAtEpochMs] stays off Recent: it was logged before the food was taken off. */
    fun hidesFromRecent(foodKey: String, loggedAtEpochMs: Long): Boolean =
        hiddenRecentFoods[foodKey]?.let { loggedAtEpochMs <= it } ?: false

    fun unitsFor(foodKey: String): List<FoodUnit> = foodUnits[foodKey].orEmpty()

    /** Adds [unit] to [foodKey]'s sizes, replacing one with the same name. */
    fun withUnit(foodKey: String, unit: FoodUnit): Settings {
        val kept = unitsFor(foodKey).filterNot { it.label.equals(unit.label, ignoreCase = true) }
        return copy(foodUnits = foodUnits + (foodKey to kept + unit))
    }

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

    companion object {
        private const val LEAP_DAY = 29

        private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        /** How many nutrients Today's page has room for. */
        const val TODAY_NUTRIENTS_MAX = 4

        /**
         * Fiber is on page 1 with the macros, and sugars and saturated fat are not tracked, so the
         * page starts with the minerals most people watch: sodium as a limit, then potassium,
         * calcium and iron.
         */
        val DEFAULT_TODAY_NUTRIENTS: List<Micronutrient> =
            listOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron)
    }
}

/** The commitment step's answer: [committed] for "I'm committed", false for "Not now", [on] that day. */
@Serializable
data class PlanCommitment(val on: LocalDate, val committed: Boolean)

/** End after a set time switched from [from] [on] that day. */
@Serializable
data class ModeTimerEnded(
    val on: LocalDate,
    @Serializable(with = PresetKeySerializer::class)
    val from: PresetId,
)

/** A size someone saved for a food: "my bowl" is 420 g. Grams are what make it convertible. */
@Serializable
data class FoodUnit(val label: String, val grams: Double)

@Serializable
enum class MacroMode {
    /** Grams follow the energy target, so the split stays the same. */
    @SerialName("ratios")
    Ratios,

    /** Grams stay put when the energy target changes. */
    @SerialName("grams")
    Grams,
}

/** [Settings.ringMode]'s values. */
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
    /** Hunger and the Notes summary, read from the meal notes. */
    val journal: Boolean = true,
    /** The on-track calendar: each day of the month against the person's own plan, or logged and not. */
    val calendar: Boolean = true,
    /** The running total: eaten against burned, added up over a few weeks, with what that might mean on the scale. */
    val runningTotal: Boolean = true,
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

/** The voice [Settings.tone] once picked. Only [Plain] is spoken now. */
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
) {
    /** "Working with a provider" was one of the reasons for tracking. Support offers the provider summary first. */
    val workingWithProvider: Boolean get() = WORKING_WITH_PROVIDER in why

    companion object {
        /** Onboarding's `Why.WorkingWithProvider` key, as [why] stores it. */
        const val WORKING_WITH_PROVIDER = "working_with_provider"
    }
}
