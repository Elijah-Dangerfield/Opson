package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.totals
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EnergyRange
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WaterProgress
import com.opson.libraries.nutrition.WeightTrend
import com.opson.libraries.nutrition.macroEnergy
import com.opson.libraries.nutrition.macroEnergyShares
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.share
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_all_food
import opson.libraries.resources.generated.resources.today_banner_sprint
import opson.libraries.resources.generated.resources.today_banner_sprint_done
import opson.libraries.resources.generated.resources.today_compact_range
import opson.libraries.resources.generated.resources.today_locked_day_body
import opson.libraries.resources.generated.resources.today_locked_day_body_warm
import opson.libraries.resources.generated.resources.today_locked_day_title
import opson.libraries.resources.generated.resources.today_locked_week_body
import opson.libraries.resources.generated.resources.today_locked_week_title
import opson.libraries.resources.generated.resources.today_macro_carbs
import opson.libraries.resources.generated.resources.today_macro_fat
import opson.libraries.resources.generated.resources.today_macro_fiber
import opson.libraries.resources.generated.resources.today_macro_protein
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * What Today holds itself rather than reads: the foods being selected and the panel's step, the
 * swiped row, and what was just removed. The view model keeps it; the builder only draws it.
 */
data class TodayUi(
    /** Null when not selecting. */
    val selected: Set<String>? = null,
    val step: SelectStep = SelectStep.Actions,
    val swipe: SwipeState = SwipeState(),
    val removed: RemovedNotice? = null,
)

/**
 * Today's state as a pure function of what it reads (design v3, 2.1 to 2.11), so previews and
 * the Blind leak test run the same mapping as the app.
 *
 * The hero card comes from the layout (`policy.preset.today`) and the calorie level: shown is
 * the eaten bar, softened is Fuel's floors-first card, delayed is the lock (or "your totals are
 * open" tonight), hidden with a nutrient visible is the focus card, and nothing visible is the
 * meal count. Nothing here asks which mode is on, except Sprint's banner copy.
 */
object TodayStateBuilder {
    /** How far ahead the header and the calendar go: one day, "plan, then hide". */
    const val MAX_DAYS_AHEAD = 1

    /** The eaten bar runs to 20% past the top of the range, so the range box sits inside it. */
    private const val BAR_HEADROOM = 1.2
    private const val SPRINT_DAYS = 14

    /** A sliver under this share of the bar is not drawn. */
    private const val MIN_PART = 0.01
    private const val NOON = 12
    private const val DAYS_PER_WEEK = 7.0
    private val TREND_WINDOW = 14.days

    fun build(
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
        highlight: MealSlot? = null,
        added: LoggedToMeal? = null,
        bridgeSkipped: Boolean = false,
        ui: TodayUi = TodayUi(),
    ): TodayState {
        val day = inputs.day
        val date = day.date
        val today = inputs.today
        val layout = policy.preset.today
        val groupsOn = day.mealGroupsOn(inputs.settings)
        val future = date > today
        val untracked = day.untracked
        val context = day.formatContext(today)
        // An untracked day has no hero card to read it by: the untracked card stands in.
        val summary = if (future || untracked) null else summary(inputs, policy, context)
        val settings = inputs.settings
        val summaryOn = inputs.summaryOn
        val compact = if (summaryOn && !future && !untracked) compact(summary, inputs, policy, context) else null
        val pageCount = (summary as? TodaySummary.Energy)?.macrosPage?.let { 2 } ?: 1
        val selecting = ui.selected?.takeIf { inputs.selectOn }

        return TodayState(
            loading = false,
            day = date,
            dayLabel = RelativeDay.of(date, today),
            canGoForward = date < today.plus(DatePeriod(days = MAX_DAYS_AHEAD)),
            summary = summary,
            banner = banner(policy, inputs.settings, inputs.timeZone, today),
            planning = future && !untracked,
            counts = if (summary is TodaySummary.Locked) counts(day, inputs.settings, policy) else null,
            mealTiles = if (!future && layout == TodayLayout.Meals && groupsOn) {
                MealSlot.meals.map { MealTile(it, tileStatus(day, it, inputs)) }
            } else {
                null
            },
            rhythm = if (!future && layout == TodayLayout.Rhythm && groupsOn) rhythm(day, inputs) else null,
            safeFoods = if (!future && layout == TodayLayout.Explorer) inputs.explorer?.safeFoods.orEmpty() else null,
            water = if (!future && layout == TodayLayout.Full && summary !is TodaySummary.Locked) water(day, inputs.settings, policy, context) else null,
            meals = if (future) planned(inputs, policy, context, groupsOn) else meals(inputs, policy, context, layout, groupsOn),
            whatYouHad = if (future) null else whatYouHad(day, layout),
            // Nothing logged, nothing to close yet (design sync 2, 2.2); the ⋮ menu still offers it.
            closeDay = if (future || untracked || !day.hasLogged) null else CloseDayCard(closed = day.isClosed(today)),
            highlight = highlight,
            added = added?.takeIf { it.day == date }?.let { AddedNotice(it.slot, it.itemIds.size) },
            untracked = untracked,
            bridge = if (!future && !untracked && !bridgeSkipped && layout == TodayLayout.Explorer) {
                inputs.explorer?.bridge?.let(::bridgeCard)
            } else {
                null
            },
            menu = if (menuOpen) menu(inputs, clearArmed) else null,
            summaryTools = summaryOn,
            summaryPage = settings.todaySummaryPage.coerceIn(0, pageCount - 1),
            collapsed = compact != null && settings.todaySummaryCollapsed,
            compact = compact,
            weight = if (summaryOn && !future) weightRow(inputs, policy, context) else null,
            selectTools = inputs.selectOn,
            selection = selecting?.let { selection(it, day, policy, context, ui.step) },
            swipe = if (inputs.selectOn && selecting == null) ui.swipe else SwipeState(),
            removed = ui.removed?.takeIf { inputs.selectOn },
        )
    }

    private fun bridgeCard(bridge: FoodBridge): FoodBridgeCard =
        FoodBridgeCard(from = inSentence(bridge.from), to = inSentence(bridge.to), shared = bridge.shared)

    /** "Rice pudding" reads "rice pudding" mid-sentence; "BBQ chips" stays as it is. */
    internal fun inSentence(name: String): String =
        if (name.length < 2 || name[1].isUpperCase()) name else name.replaceFirstChar { it.lowercaseChar() }

    private fun summary(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): TodaySummary? {
        val layout = policy.preset.today
        if (layout == TodayLayout.Explorer) return explorer(inputs)
        if (layout == TodayLayout.Rhythm || layout == TodayLayout.Journal) return null

        val day = inputs.day
        val settings = inputs.settings
        val range = todayRange(settings, day, inputs.today, inputs.expended)
        val eaten = day.totals().kcal.known
        val delayed = policy.level(Metric.Calories) == Visibility.Delay
        val shown = policy.effectiveLevel(Metric.Calories, Surface.Today, context) == Visibility.Show
        val soft = policy.resolve(Metric.Calories, eaten, Surface.Today, context.copy(of = range.targetKcal.toDouble(), bare = true))

        return when {
            shown && delayed && day.date == inputs.today -> TodaySummary.Revealed
            shown -> energy(inputs, policy, context, eaten)
            delayed -> locked(policy, settings)
            soft is Display.Soft -> softCard(inputs, policy, context, soft)
            else -> macroTiles(day, settings, policy, context, GoalStyle.Default).takeIf { it.isNotEmpty() }?.let(TodaySummary::Focus)
                ?: counted(day, inputs.settings.mealTimes)
        }
    }

    private fun energy(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext, eaten: Double): TodaySummary.Energy {
        val day = inputs.day
        val settings = inputs.settings
        val range = todayRange(settings, day, inputs.today, inputs.expended)
        val scaleMax = range.maxKcal * BAR_HEADROOM
        val empty = day.allItems.isEmpty() && day.ateNoDetails.isEmpty()
        val summaryOn = inputs.summaryOn
        return TodaySummary.Energy(
            eaten = policy.resolve(Metric.Calories, eaten, Surface.Today, context.copy(of = scaleMax, bare = true)),
            empty = empty,
            range = policy.resolveRange(Metric.Calories, range.minKcal.toDouble(), range.maxKcal.toDouble(), Surface.Today, context.copy(bare = true)),
            rangeStart = (range.minKcal / scaleMax).toFloat(),
            rangeEnd = (range.maxKcal / scaleMax).toFloat(),
            macros = if (empty || summaryOn) {
                emptyList()
            } else {
                macroTiles(day, settings, policy, context, GoalStyle.Slash).filter { it.metric != Metric.Fiber }
            },
            expended = expended(inputs, policy, context),
            bar = if (empty) emptyList() else barParts(day, policy, context, eaten),
            stats = if (summaryOn && !empty) stats(inputs, policy, context, eaten, range) else emptyList(),
            macrosPage = if (summaryOn && !empty && settings.todayMacrosPage) macrosPage(day, settings, policy, context, eaten) else null,
        )
    }

    /** Protein, carbs and fat by their energy, end to end; a macro not on show and energy no macro explains are one neutral piece. */
    private fun barParts(day: Day, policy: VisibilityPolicy, context: FormatContext, eaten: Double): List<BarPart> {
        val totals = day.totals()
        val kcal = macroEnergy(totals.protein.known, totals.carbs.known, totals.fat.known)
        val total = maxOf(eaten, kcal.values.sum())
        if (total <= 0.0) return emptyList()
        val shown = kcal.filter { (metric, energy) -> energy > 0.0 && policy.effectiveLevel(metric, Surface.Today, context) == Visibility.Show }
        val rest = total - shown.values.sum()
        return shown.map { (metric, energy) -> BarPart(metric, (energy / total).toFloat()) } +
            listOfNotNull(BarPart(null, (rest / total).toFloat()).takeIf { rest / total > MIN_PART })
    }

    /** Page 1's stats, in the person's order (or the goals' suggestion), the ones the levels hide left out. */
    private fun stats(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext, eaten: Double, range: EnergyRange): List<StatTile> {
        val offered = SummaryStats.offered(policy)
        return SummaryStats.chosen(inputs.settings)
            .filter { it in offered && SummaryStats.allowed(it, policy) }
            .mapNotNull { stat -> statValue(stat, inputs, policy, context, eaten, range)?.let { StatTile(stat, it) } }
            .take(SummaryStats.MAX)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun statValue(
        stat: SummaryStat,
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        context: FormatContext,
        eaten: Double,
        range: EnergyRange,
    ): StatValue? {
        val day = inputs.day
        val energy = context.copy(of = null, bare = false)
        val value: StatValue = when (stat) {
            SummaryStat.Expended -> StatValue.Amount(Metric.EnergyOut, expended(inputs, policy, context))
            SummaryStat.DeficitSoFar -> {
                val out = inputs.expended?.totalKcal?.takeIf { day.date == inputs.today } ?: return null
                StatValue.Amount(Metric.EnergyOut, policy.resolve(Metric.EnergyOut, (out - eaten).coerceAtLeast(0.0), Surface.Today, energy))
            }
            SummaryStat.ToRange -> {
                if (policy.effectiveLevel(Metric.Remaining, Surface.Today, context) != Visibility.Show) return null
                val gap = range.minKcal - eaten
                if (gap >= 1.0) {
                    StatValue.Amount(Metric.Calories, policy.resolve(Metric.Calories, gap, Surface.Today, energy))
                } else {
                    val bounds = energy.copy(eaten = eaten, min = range.minKcal.toDouble(), max = range.maxKcal.toDouble())
                    StatValue.Amount(Metric.Remaining, policy.resolve(Metric.Remaining, gap, Surface.Today, bounds))
                }
            }
            SummaryStat.Meals -> StatValue.Count(day.doneMealCount, MealSlot.meals.size)
            SummaryStat.Water -> {
                if (policy.level(Metric.Water) == Visibility.Hide) return null
                val glasses = WaterProgress.glasses(day.waterMl.toDouble(), inputs.settings.glassMl, inputs.settings.waterGlasses)
                StatValue.Count(glasses.filled, glasses.total)
            }
            SummaryStat.Protein, SummaryStat.Carbs, SummaryStat.Fat, SummaryStat.Fiber -> {
                val metric = requireNotNull(stat.metric)
                val (grams, of) = macroAmount(day, inputs.settings, metric)
                StatValue.Amount(metric, policy.resolve(metric, grams, Surface.Today, context.copy(of = of, goal = GoalStyle.None)))
            }
        }
        return value.takeUnless { it is StatValue.Amount && it.display is Display.Hidden }
    }

    private fun macroAmount(day: Day, settings: Settings, metric: Metric): Pair<Double, Double> {
        val totals = day.totals()
        val targets = settings.targets
        return when (metric) {
            Metric.Protein -> totals.protein.known to targets.proteinG.toDouble()
            Metric.Carbs -> totals.carbs.known to targets.carbsG.toDouble()
            Metric.Fat -> totals.fat.known to targets.fatG.toDouble()
            else -> totals.fiber.known to targets.fiberG.toDouble()
        }
    }

    /** Page 2: "of 1,245 kcal", the four macros against their targets, and each shown macro's share of the energy. */
    private fun macrosPage(day: Day, settings: Settings, policy: VisibilityPolicy, context: FormatContext, eaten: Double): MacrosPage? {
        val rows = macroTiles(day, settings, policy, context, GoalStyle.Slash)
        if (rows.isEmpty()) return null
        val totals = day.totals()
        val shares = macroEnergyShares(totals.protein.known, totals.carbs.known, totals.fat.known) {
            policy.effectiveLevel(it, Surface.Today, context) == Visibility.Show
        }.map { (metric, percent) ->
            MacroShare(metric, macroLabel(metric), policy.share(metric, percent, Surface.Today, context), percent.toFloat())
        }.filter { it.share !is Display.Hidden && it.weight > 0f }
        return MacrosPage(
            eaten = policy.resolve(Metric.Calories, eaten, Surface.Today, context.copy(of = null, bare = false)),
            rows = rows,
            shares = shares,
        )
    }

    /**
     * The summary as one line (2.1c, 2.1d). Energy: the figure and the range, then the macros as
     * the meal headers write them; softened: the meals and the lead macro; focus: each word;
     * locked: how many meals; everywhere else the meal count alone, so a hidden number never
     * lands here.
     */
    private fun compact(summary: TodaySummary?, inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): CompactSummary {
        val day = inputs.day
        val meals = CompactLine(listOf(CompactPart.Meals(day.doneMealCount, MealSlot.meals.size)))
        val single = when (summary) {
            is TodaySummary.Energy -> return energyCompact(summary, inputs, policy, context)
            is TodaySummary.Soft -> CompactLine(
                listOfNotNull(
                    summary.mealCount?.let { CompactPart.Meals(it.done, it.total) },
                    summary.lead?.let { CompactPart.Amount(it.metric, it.display, it.label) },
                ),
            ).takeIf { it.parts.isNotEmpty() } ?: meals
            is TodaySummary.Focus -> CompactLine(summary.rows.map { CompactPart.Amount(it.metric, it.display, it.label) })
            is TodaySummary.Locked -> CompactLine(listOf(CompactPart.Logged(day.doneMealCount)))
            else -> meals
        }
        return CompactSummary(pages = listOf(single), collapsed = single)
    }

    private fun energyCompact(summary: TodaySummary.Energy, inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): CompactSummary {
        val totals = inputs.day.totals()
        val eaten = policy.resolve(Metric.Calories, totals.kcal.known, Surface.Today, context.copy(of = null, bare = false))
        val bar = CompactBar(summary.eaten, summary.rangeStart, summary.rangeEnd, summary.bar)
        val lead = CompactPart.Amount(Metric.Calories, eaten)
        val energyLine = CompactLine(listOf(lead, CompactPart.Amount(Metric.Calories, summary.range, Res.string.today_compact_range)), bar)
        val macros = policy.macroLine(
            NutrientAmounts(kcal = totals.kcal.value, protein = totals.protein.known, carbs = totals.carbs.known, fat = totals.fat.known),
            Surface.Today,
            context,
            MacroLineStyle.Compact,
        )
        val macrosLine = if (summary.macrosPage != null && macros.isNotEmpty()) CompactLine(listOf(CompactPart.Macros(macros)), bar) else null
        return CompactSummary(
            pages = listOfNotNull(energyLine, macrosLine),
            collapsed = CompactLine(listOfNotNull(lead, macrosLine?.parts?.first()), bar),
        )
    }

    /** The day's latest weigh-in through the weight level, with the two weeks' slope for the soft word. */
    private fun weightRow(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): WeightRow? {
        val tz = inputs.timeZone
        val latest = inputs.weighIns
            .filter { it.measuredAt.toLocalDateTime(tz).date == inputs.day.date }
            .maxByOrNull { it.measuredAt } ?: return null
        val window = inputs.weighIns.filter { it.measuredAt <= latest.measuredAt && latest.measuredAt - it.measuredAt <= TREND_WINDOW }
        val display = policy.resolve(
            Metric.Weight,
            latest.kg,
            Surface.Today,
            context.copy(of = null, weightUnit = inputs.settings.weightUnit, trendKgPerWeek = slope(window)),
        )
        if (display is Display.Hidden) return null
        val time = latest.measuredAt.toLocalDateTime(tz).time
        return WeightRow(display, time, morning = time.hour < NOON)
    }

    /** Kilograms a week along the smoothed line, or null under two readings. */
    private fun slope(weighIns: List<WeighIn>): Double? {
        if (weighIns.size < 2) return null
        val span = weighIns.last().measuredAt - weighIns.first().measuredAt
        if (span <= Duration.ZERO) return null
        val ema = WeightTrend.ema(weighIns.map { it.kg })
        return (ema.last() - ema.first()) / (span / 1.days) * DAYS_PER_WEEK
    }

    /** The picked foods' count and totals; in Blind only the count survives. */
    private fun selection(ids: Set<String>, day: Day, policy: VisibilityPolicy, context: FormatContext, step: SelectStep): Selection {
        val items = day.allItems.filter { it.id in ids }
        val totals = items.totals()
        return Selection(
            ids = items.mapTo(LinkedHashSet()) { it.id },
            count = items.size,
            calories = policy.resolve(Metric.Calories, totals.kcal.value, Surface.Today, context.copy(of = null, bare = false)),
            macros = listOf(
                Metric.Protein to totals.protein.value,
                Metric.Carbs to totals.carbs.value,
                Metric.Fat to totals.fat.value,
                Metric.Fiber to totals.fiber.value,
            ).map { (metric, grams) ->
                MacroTile(metric, macroLabel(metric), policy.resolve(metric, grams, Surface.Today, context.copy(of = null, goal = GoalStyle.None)))
            }.filter { it.display !is Display.Hidden },
            step = step,
        )
    }

    /** "Expended so far" says so far, so it is today's alone; energy out hides at anything short of Show. */
    private fun expended(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): Display =
        if (inputs.day.date != inputs.today) {
            Display.Hidden
        } else {
            policy.resolve(Metric.EnergyOut, inputs.expended?.totalKcal, Surface.Today, context.copy(of = null, bare = false))
        }

    private fun softCard(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext, energy: Display): TodaySummary.Soft {
        val macros = macroTiles(inputs.day, inputs.settings, policy, context, GoalStyle.Of)
        val lead = macros.firstOrNull { it.display is Display.Shown }
        return TodaySummary.Soft(
            mealCount = if (policy.preset.mealCount) MealCount(inputs.day.doneMealCount, MealSlot.meals.size) else null,
            lead = lead,
            rows = macros - listOfNotNull(lead).toSet(),
            energy = energy,
        )
    }

    private fun locked(policy: VisibilityPolicy, settings: Settings): TodaySummary.Locked =
        if (policy.preset.revealOn == RevealOn.Week) {
            TodaySummary.Locked(Res.string.today_locked_week_title, Res.string.today_locked_week_body)
        } else {
            TodaySummary.Locked(
                title = Res.string.today_locked_day_title,
                body = if (settings.tone == Tone.Warm) Res.string.today_locked_day_body_warm else Res.string.today_locked_day_body,
            )
        }

    private fun counted(day: Day, times: MealTimes): TodaySummary.Counted {
        val next = MealSlot.meals.firstOrNull { !day.isMealDone(it) }
        return TodaySummary.Counted(done = day.doneMealCount, next = next, nextAround = next?.let(times::at))
    }

    private fun explorer(inputs: TodayInputs): TodaySummary.NewFoods = TodaySummary.NewFoods(
        inputs.explorer?.newFoods.orEmpty().map { NewFood(it.name, ExplorerStatsBuilder.seenLabel(it.firstLogged, inputs.today)) },
    )

    private fun macroTiles(day: Day, settings: Settings, policy: VisibilityPolicy, context: FormatContext, goal: GoalStyle): List<MacroTile> {
        val totals = day.totals()
        val targets = settings.targets
        return listOf(
            Triple(Metric.Protein, totals.protein.known, targets.proteinG),
            Triple(Metric.Carbs, totals.carbs.known, targets.carbsG),
            Triple(Metric.Fat, totals.fat.known, targets.fatG),
            Triple(Metric.Fiber, totals.fiber.known, targets.fiberG),
        ).map { (metric, grams, of) ->
            MacroTile(metric, macroLabel(metric), policy.resolve(metric, grams, Surface.Today, context.copy(of = of.toDouble(), goal = goal)))
        }.filter { it.display !is Display.Hidden }
    }

    fun macroLabel(metric: Metric): StringResource = when (metric) {
        Metric.Protein -> Res.string.today_macro_protein
        Metric.Carbs -> Res.string.today_macro_carbs
        Metric.Fat -> Res.string.today_macro_fat
        else -> Res.string.today_macro_fiber
    }

    private fun counts(day: Day, settings: Settings, policy: VisibilityPolicy): Counts = Counts(
        meals = day.doneMealCount,
        glasses = if (policy.level(Metric.Water) != Visibility.Hide) {
            WaterProgress.glasses(day.waterMl.toDouble(), settings.glassMl, settings.waterGlasses).filled
        } else {
            null
        },
    )

    /** Sprint's banner, keyed by the mode as the presets' copy is; the levels play no part. */
    private fun banner(policy: VisibilityPolicy, settings: Settings, timeZone: TimeZone, today: LocalDate): TodayBanner? {
        if (policy.preset.id != PresetId.Sprint) return null
        val start = settings.sprintStartedOn ?: settings.numbers.changedAt?.toLocalDateTime(timeZone)?.date ?: today
        val dayNumber = start.daysUntil(today) + 1
        return if (dayNumber > SPRINT_DAYS) {
            TodayBanner(Res.string.today_banner_sprint_done)
        } else {
            TodayBanner(Res.string.today_banner_sprint, listOf(dayNumber.coerceAtLeast(1), DateCopy.monthDay(start.plus(DatePeriod(days = SPRINT_DAYS)))))
        }
    }

    private fun tileStatus(day: Day, slot: MealSlot, inputs: TodayInputs): TileStatus {
        val items = day.items(slot)
        return when {
            items.isNotEmpty() -> TileStatus.Items(items.size, items.first().localTime(inputs.timeZone))
            slot in day.ateNoDetails -> TileStatus.AteNoDetails
            else -> TileStatus.Empty(inputs.settings.mealTimes.at(slot))
        }
    }

    private fun rhythm(day: Day, inputs: TodayInputs): RhythmCard {
        val next = MealSlot.meals.firstOrNull { !day.isMealDone(it) }
        val times = inputs.settings.mealTimes
        val rows = MealSlot.meals.map { slot ->
            val items = day.items(slot)
            val around = times.at(slot)
            val status = when {
                items.isNotEmpty() -> RhythmStatus.Logged(items.first().localTime(inputs.timeZone), around)
                slot in day.ateNoDetails -> RhythmStatus.AteNoDetails(around)
                else -> RhythmStatus.Pending(around)
            }
            RhythmRow(slot, status, showActions = slot == next)
        }
        return RhythmCard(
            done = day.doneMealCount,
            total = MealSlot.meals.size,
            rows = rows,
            reminder = rhythmReminder(day, next, inputs),
        )
    }

    /**
     * v3 `today-rhythm.png`: the next meal's reminder under the checklist, with Change times. Only
     * today, only with `release.reminders` on, and only while a meal is still to come; "Off" with
     * Set up while meal reminders are off. A next meal whose own switch is off gets no card.
     */
    private fun rhythmReminder(day: Day, next: MealSlot?, inputs: TodayInputs): RhythmReminder? {
        if (!inputs.remindersOn || day.date != inputs.today || next == null) return null
        val settings = inputs.settings
        return when {
            settings.mealReminderLevel == EventLevel.Off -> RhythmReminder.Off
            settings.mealReminders.isOn(next) -> RhythmReminder.Next(next, settings.mealTimes.at(next))
            else -> null
        }
    }

    private fun water(day: Day, settings: Settings, policy: VisibilityPolicy, context: FormatContext): WaterRow? {
        val display = policy.resolve(
            Metric.Water,
            day.waterMl.toDouble(),
            Surface.Today,
            context.copy(of = settings.waterGoalMl.toDouble(), unit = settings.waterUnit),
        )
        // v3 draws the water card only with the amount on show; a softened or hidden water level has no card.
        if (display !is Display.Shown) return null
        val glasses = WaterProgress.glasses(day.waterMl.toDouble(), settings.glassMl, settings.waterGlasses)
        return WaterRow(display = display, filled = glasses.filled, total = glasses.total, showSegments = true)
    }

    private fun meals(
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        context: FormatContext,
        layout: TodayLayout,
        groupsOn: Boolean,
    ): List<MealEntry> {
        val day = inputs.day
        return when (layout) {
            TodayLayout.Meals, TodayLayout.Rhythm -> emptyList()
            TodayLayout.Full -> if (!groupsOn) {
                listOf(MealEntry.Full(mealCard(inputs, policy, context, MealSlot.Uncategorized, day.allItems, Res.string.today_all_food)))
            } else {
                val next = nextEmpty(day)
                val loose = day.items(MealSlot.Uncategorized)
                    .takeIf { it.isNotEmpty() }
                    ?.let { MealEntry.Full(mealCard(inputs, policy, context, MealSlot.Uncategorized, it, MealSlot.Uncategorized.label)) }
                MealSlot.meals.map { slot ->
                    if (day.items(slot).isNotEmpty() || slot in day.ateNoDetails) {
                        MealEntry.Full(mealCard(inputs, policy, context, slot, day.items(slot), slot.label))
                    } else {
                        collapsed(inputs, slot, usual = slot == next)
                    }
                } + listOfNotNull(loose)
            }
            TodayLayout.Explorer, TodayLayout.Journal -> {
                val next = nextEmpty(day)
                val slots = if (groupsOn) MealSlot.meals else listOf(MealSlot.Uncategorized)
                slots.map { slot ->
                    val items = if (groupsOn) day.items(slot) else day.allItems
                    if (items.isEmpty()) {
                        collapsed(inputs, slot, usual = slot == next)
                    } else {
                        val feeling = day.feelings[slot]
                        val feel = feeling?.let {
                            FeelLine(it.hunger?.label, it.fullness?.label, it.moods.sortedBy { mood -> mood.ordinal }.map { mood -> mood.label })
                        }?.takeIf { it.hunger != null || it.fullness != null || it.moods.isNotEmpty() }
                        MealEntry.Compact(
                            slot = slot,
                            title = if (groupsOn) slot.label else Res.string.today_all_food,
                            time = items.first().localTime(inputs.timeZone),
                            foods = foods(items),
                            feel = if (layout == TodayLayout.Journal) feel else null,
                            askFeel = layout == TodayLayout.Journal && feel == null,
                        )
                    }
                }
            }
        }
    }

    /** The meal to offer first: the first one with nothing in it. */
    private fun nextEmpty(day: Day): MealSlot? = MealSlot.meals.firstOrNull { !day.isMealDone(it) }

    private fun collapsed(inputs: TodayInputs, slot: MealSlot, usual: Boolean): MealEntry.Collapsed =
        MealEntry.Collapsed(slot = slot, around = inputs.settings.mealTimes.at(slot), usual = usual)

    private fun planned(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext, groupsOn: Boolean): List<MealEntry> {
        val day = inputs.day
        val slots = if (groupsOn) MealSlot.meals else listOf(MealSlot.Uncategorized)
        return slots.map { slot ->
            val items = if (groupsOn) day.items(slot) else day.allItems
            if (items.isEmpty()) {
                MealEntry.NotPlanned(slot)
            } else {
                MealEntry.Planned(slot, policy.resolve(Metric.Calories, items.totals().kcal.value, Surface.Today, context), foods(items))
            }
        }
    }

    private fun whatYouHad(day: Day, layout: TodayLayout): WhatYouHad? {
        val logged = MealSlot.entries.mapNotNull { slot -> day.items(slot).takeIf { it.isNotEmpty() }?.let { slot to foods(it) } }
        if (logged.isEmpty()) return null
        return when (layout) {
            TodayLayout.Meals -> WhatYouHad.PerMeal(logged)
            TodayLayout.Rhythm -> WhatYouHad.Together(logged.joinToString(MEAL_SEPARATOR) { it.second })
            else -> null
        }
    }

    private fun mealCard(
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        context: FormatContext,
        slot: MealSlot,
        items: List<LogItem>,
        title: StringResource,
    ): MealCard {
        val day = inputs.day
        val totals = items.totals()
        val hasMacros = items.any { it.protein != null || it.carbs != null || it.fat != null }
        val allMacrosShow = listOf(Metric.Protein, Metric.Carbs, Metric.Fat).all { policy.effectiveLevel(it, Surface.Today, context) == Visibility.Show }
        val style = if (allMacrosShow) MacroLineStyle.Compact else MacroLineStyle.Long
        val feeling = day.feelings[slot]
        return MealCard(
            slot = slot,
            title = title,
            time = items.firstOrNull()?.localTime(inputs.timeZone),
            calories = if (items.isEmpty()) Display.Hidden else policy.resolve(Metric.Calories, totals.kcal.value, Surface.Today, context),
            macros = if (hasMacros) {
                policy.macroLine(
                    NutrientAmounts(kcal = totals.kcal.value, protein = totals.protein.known, carbs = totals.carbs.known, fat = totals.fat.known),
                    Surface.Today,
                    context,
                    style,
                )
            } else {
                emptyList()
            },
            macroStyle = style,
            items = items.map { item ->
                ItemRowUi(
                    entryId = item.id,
                    name = item.name,
                    amount = item.amountLabel,
                    precision = item.precision,
                    // The meal header carries the unit, so each item is a plain number (v3 2.1).
                    calories = policy.resolve(Metric.Calories, item.kcal, Surface.Today, context.copy(bare = true)),
                )
            },
            ateNoDetails = items.isEmpty() && slot in day.ateNoDetails,
            note = feeling?.note?.trim()?.takeIf { it.isNotEmpty() },
            fileable = inputs.selectOn && slot == MealSlot.Uncategorized && items.isNotEmpty() && groupsOn(inputs),
        )
    }

    private fun groupsOn(inputs: TodayInputs): Boolean = inputs.day.mealGroupsOn(inputs.settings)

    private fun menu(inputs: TodayInputs, clearArmed: Boolean): DayMenu {
        val date = inputs.day.date
        val today = inputs.today
        val past = date <= today
        val untracked = inputs.day.untracked
        val groups = listOf(
            listOfNotNull(if (past && !untracked) DayMenuItem.CloseDay(isToday = date == today) else null, DayMenuItem.Nutrients),
            listOfNotNull(
                DayMenuItem.CopyYesterday,
                if (past) DayMenuItem.CopyToTomorrow else null,
                DayMenuItem.AddNote,
                DayMenuItem.Untracked(marked = untracked),
            ),
            listOf(DayMenuItem.EditCards, DayMenuItem.ClearDay),
        )
        return DayMenu(groups = groups, clearArmed = clearArmed)
    }

    /** "Oat porridge with milk, banana, coffee with milk": names in a sentence, the first capitalised. */
    fun foods(items: List<LogItem>): String = items.mapIndexed { index, item ->
        val name = item.name.trim()
        if (index == 0 || name.length < 2 || name[1].isUpperCase()) name else name.replaceFirstChar { it.lowercaseChar() }
    }.joinToString(", ")

    private const val MEAL_SEPARATOR = " · "

    private fun LogItem.localTime(timeZone: TimeZone): LocalTime = loggedAt.toLocalDateTime(timeZone).time
}
