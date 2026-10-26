package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.diary.totals
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
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
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.nutrition.resolveRange
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

    fun build(
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
        highlight: MealSlot? = null,
        added: LoggedToMeal? = null,
        bridgeSkipped: Boolean = false,
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
            closeDay = if (future || untracked) null else CloseDayCard(closed = day.isClosed(today)),
            highlight = highlight,
            added = added?.takeIf { it.day == date }?.let { AddedNotice(it.slot, it.itemIds.size) },
            untracked = untracked,
            bridge = if (!future && !untracked && !bridgeSkipped && layout == TodayLayout.Explorer) {
                inputs.explorer?.bridge?.let(::bridgeCard)
            } else {
                null
            },
            menu = if (menuOpen) menu(inputs, clearArmed) else null,
        )
    }

    private fun bridgeCard(bridge: FoodBridge): FoodBridgeCard =
        FoodBridgeCard(from = inSentence(bridge.from), to = inSentence(bridge.to), shared = bridge.shared)

    /** "Rice pudding" reads "rice pudding" mid-sentence; "BBQ chips" stays as it is. */
    private fun inSentence(name: String): String =
        if (name.length < 2 || name[1].isUpperCase()) name else name.replaceFirstChar { it.lowercaseChar() }

    private fun summary(inputs: TodayInputs, policy: VisibilityPolicy, context: FormatContext): TodaySummary? {
        val layout = policy.preset.today
        if (layout == TodayLayout.Explorer) return explorer(inputs)
        if (layout == TodayLayout.Rhythm || layout == TodayLayout.Journal) return null

        val day = inputs.day
        val settings = inputs.settings
        val range = settings.targets.today(settings.exercise, 0, 0)
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
        val range = settings.targets.today(settings.exercise, 0, 0)
        val scaleMax = range.maxKcal * BAR_HEADROOM
        val empty = day.allItems.isEmpty() && day.ateNoDetails.isEmpty()
        return TodaySummary.Energy(
            eaten = policy.resolve(Metric.Calories, eaten, Surface.Today, context.copy(of = scaleMax, bare = true)),
            empty = empty,
            range = policy.resolveRange(Metric.Calories, range.minKcal.toDouble(), range.maxKcal.toDouble(), Surface.Today, context.copy(bare = true)),
            rangeStart = (range.minKcal / scaleMax).toFloat(),
            rangeEnd = (range.maxKcal / scaleMax).toFloat(),
            macros = if (empty) emptyList() else macroTiles(day, settings, policy, context, GoalStyle.Slash).filter { it.metric != Metric.Fiber },
        )
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
                        collapsed(inputs, policy, slot, usual = slot == next)
                    }
                } + listOfNotNull(loose)
            }
            TodayLayout.Explorer, TodayLayout.Journal -> {
                val next = nextEmpty(day)
                val slots = if (groupsOn) MealSlot.meals else listOf(MealSlot.Uncategorized)
                slots.map { slot ->
                    val items = if (groupsOn) day.items(slot) else day.allItems
                    if (items.isEmpty()) {
                        collapsed(inputs, policy, slot, usual = slot == next)
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

    private fun collapsed(inputs: TodayInputs, policy: VisibilityPolicy, slot: MealSlot, usual: Boolean): MealEntry.Collapsed {
        val before = inputs.dayBefore
        val yesterday = if (usual && slot.isMeal) {
            before.items(slot).takeIf { it.isNotEmpty() }?.let { items ->
                Yesterday(
                    foods = foods(items),
                    // Resolved as the day it would be added to, not the day it came from: in Delayed
                    // yesterday is open, but re-adding its dinner would tell today's hidden total.
                    calories = policy.resolve(Metric.Calories, items.totals().kcal.value, Surface.Today, inputs.day.formatContext(inputs.today)),
                )
            }
        } else {
            null
        }
        return MealEntry.Collapsed(
            slot = slot,
            around = inputs.settings.mealTimes.at(slot),
            usual = usual,
            yesterday = yesterday,
            offerSaved = usual && yesterday == null,
        )
    }

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
                    amount = item.amountText.orEmpty(),
                    precision = item.precision,
                    // The meal header carries the unit, so each item is a plain number (v3 2.1).
                    calories = policy.resolve(Metric.Calories, item.kcal, Surface.Today, context.copy(bare = true)),
                )
            },
            ateNoDetails = items.isEmpty() && slot in day.ateNoDetails,
            feel = listOfNotNull(feeling?.hunger?.label, feeling?.fullness?.label) + feeling?.moods.orEmpty().sortedBy { it.ordinal }.map { it.label },
            note = feeling?.note?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

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
