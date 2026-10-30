package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.color
import com.opson.features.today.impl.text
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.BigDayHeroCard
import com.opson.libraries.diary.ui.DayTotalsCard
import com.opson.libraries.diary.ui.LabelValueCard
import com.opson.libraries.diary.ui.LabelledLineCard
import com.opson.libraries.diary.ui.LoggedMealsCard
import com.opson.libraries.diary.ui.RangeSentence
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.label
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.InfoBanner
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_checkin_energy
import opson.libraries.resources.generated.resources.today_checkin_hunger
import opson.libraries.resources.generated.resources.today_checkin_mood_level
import opson.libraries.resources.generated.resources.today_checkin_sleep
import opson.libraries.resources.generated.resources.today_checkin_title
import opson.libraries.resources.generated.resources.today_count_value
import opson.libraries.resources.generated.resources.today_dayclose_above
import opson.libraries.resources.generated.resources.today_dayclose_above_goal
import opson.libraries.resources.generated.resources.today_dayclose_above_goal_other_day
import opson.libraries.resources.generated.resources.today_dayclose_above_other_day
import opson.libraries.resources.generated.resources.today_dayclose_back
import opson.libraries.resources.generated.resources.today_dayclose_below
import opson.libraries.resources.generated.resources.today_dayclose_below_goal
import opson.libraries.resources.generated.resources.today_dayclose_big_day
import opson.libraries.resources.generated.resources.today_dayclose_caption
import opson.libraries.resources.generated.resources.today_dayclose_closed_caption
import opson.libraries.resources.generated.resources.today_dayclose_commit_can
import opson.libraries.resources.generated.resources.today_dayclose_commit_normal
import opson.libraries.resources.generated.resources.today_dayclose_committed
import opson.libraries.resources.generated.resources.today_dayclose_done
import opson.libraries.resources.generated.resources.today_dayclose_expended
import opson.libraries.resources.generated.resources.today_dayclose_feeling_label
import opson.libraries.resources.generated.resources.today_dayclose_inside
import opson.libraries.resources.generated.resources.today_dayclose_inside_goal
import opson.libraries.resources.generated.resources.today_dayclose_kcal
import opson.libraries.resources.generated.resources.today_dayclose_loading_cd
import opson.libraries.resources.generated.resources.today_dayclose_logged_that_day
import opson.libraries.resources.generated.resources.today_dayclose_logged_today
import opson.libraries.resources.generated.resources.today_dayclose_meals
import opson.libraries.resources.generated.resources.today_dayclose_note_label
import opson.libraries.resources.generated.resources.today_dayclose_note_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_note_placeholder
import opson.libraries.resources.generated.resources.today_dayclose_reopen
import opson.libraries.resources.generated.resources.today_dayclose_revealed_body
import opson.libraries.resources.generated.resources.today_dayclose_revealed_title
import opson.libraries.resources.generated.resources.today_dayclose_save
import opson.libraries.resources.generated.resources.today_dayclose_soft_label
import opson.libraries.resources.generated.resources.today_dayclose_soft_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_support
import opson.libraries.resources.generated.resources.today_dayclose_tomorrow
import opson.libraries.resources.generated.resources.today_dayclose_tomorrow_goal
import opson.libraries.resources.generated.resources.today_dayclose_why_not
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.opson.features.today.impl.KeyNutrient
import com.opson.features.today.impl.kindnote.CutBackLooks
import com.opson.features.today.impl.MacroTile
import com.opson.libraries.diary.ui.KeyNutrientCell
import com.opson.libraries.diary.ui.KeyNutrientsPage
import com.opson.libraries.diary.ui.MacrosPage
import com.opson.libraries.diary.ui.SummaryLabel
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
import com.opson.features.today.impl.Toast
import com.opson.features.today.impl.celebration.Celebrating
import com.opson.features.today.impl.celebration.CelebrationsSource
import com.opson.features.today.impl.celebration.nextName
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.MilestoneFamily
import opson.libraries.resources.generated.resources.celebrate_gentle_run
import opson.libraries.resources.generated.resources.celebrate_more_to
import opson.libraries.resources.generated.resources.celebrate_run
import opson.libraries.resources.generated.resources.celebrate_run_with_next
import opson.libraries.resources.generated.resources.celebrate_wrapped_toast
import opson.libraries.resources.generated.resources.celebrate_wrapped_toast_plain
import org.jetbrains.compose.resources.pluralStringResource
import opson.libraries.resources.generated.resources.today_dayclose_balance_burned
import opson.libraries.resources.generated.resources.today_dayclose_balance_burned_so_far
import opson.libraries.resources.generated.resources.today_dayclose_balance_eaten
import opson.libraries.resources.generated.resources.today_dayclose_balance_even
import opson.libraries.resources.generated.resources.today_dayclose_balance_over
import opson.libraries.resources.generated.resources.today_dayclose_balance_title
import opson.libraries.resources.generated.resources.today_dayclose_balance_under
import opson.libraries.resources.generated.resources.today_dayclose_checkin_empty
import opson.libraries.resources.generated.resources.today_dayclose_checkin_label
import opson.libraries.resources.generated.resources.today_dayclose_checkin_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_checkin_open_cd
import opson.libraries.resources.generated.resources.today_dayclose_checkin_pair
import opson.libraries.resources.generated.resources.today_dayclose_macros
import opson.libraries.resources.generated.resources.today_dayclose_nutrients
import opson.libraries.resources.generated.resources.today_dayclose_plan_deficit_beyond
import opson.libraries.resources.generated.resources.today_dayclose_plan_deficit_on
import opson.libraries.resources.generated.resources.today_dayclose_plan_deficit_opposite
import opson.libraries.resources.generated.resources.today_dayclose_plan_deficit_short
import opson.libraries.resources.generated.resources.today_dayclose_plan_surplus_beyond
import opson.libraries.resources.generated.resources.today_dayclose_plan_surplus_on
import opson.libraries.resources.generated.resources.today_dayclose_plan_surplus_opposite
import opson.libraries.resources.generated.resources.today_dayclose_plan_surplus_short
import opson.libraries.resources.generated.resources.today_dayclose_recap_all
import opson.libraries.resources.generated.resources.today_dayclose_recap_all_other_day
import opson.libraries.resources.generated.resources.today_dayclose_recap_none
import opson.libraries.resources.generated.resources.today_dayclose_recap_none_other_day
import opson.libraries.resources.generated.resources.today_dayclose_recap_some
import opson.libraries.resources.generated.resources.today_dayclose_recap_some_other_day
import opson.libraries.resources.generated.resources.today_nutrients_see_all
import opson.libraries.resources.generated.resources.trends_micro_not_listed
import opson.libraries.resources.generated.resources.trends_micro_of
import opson.libraries.resources.generated.resources.trends_micro_of_limit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Day close for one day. Done saves the note and the feeling and stamps the day closed, in one
 * write; Back leaves both alone. On a closed day Done saves them and keeps it closed, and Reopen
 * opens it to food again. The commitment buttons change only what this screen says.
 *
 * The check-in and the note are two optional sections, each the same data its own sheet edits:
 * the check-in row opens the check-in sheet, which saves on its own (closing never writes it),
 * and the note is the day's note, the one the Note sheet writes and All notes lists.
 */
@Inject
class DayCloseViewModel(
    @Assisted private val day: LocalDate,
    private val diary: DiaryRepository,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val health: HealthRepository,
    private val healthFlag: HealthReleaseFlag,
    private val checkInEnabled: CheckInEnabled,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
    private val burnedRepository: BurnedRepository,
    private val promptEvents: PromptEvents = PromptEvents.None,
    private val celebrations: CelebrationsSource = CelebrationsSource.None,
    private val goals: GoalRepository = GoalRepository.None,
    /** Each "Why not cut back?" is noted on the phone for the hard-stretch check. */
    private val cutBackLooks: CutBackLooks = CutBackLooks.None,
) : SEAViewModel<DayCloseState, DayCloseEvent, DayCloseAction>(initialStateArg = DayCloseState()) {

    private var model = Model()
    private var leaving = false

    private data class Model(
        val inputs: DayCloseInputs? = null,
        val policy: VisibilityPolicy? = null,
        val note: String = "",
        val feeling: DayFeeling? = null,
        val prefilled: Boolean = false,
        val committed: Boolean = false,
        val celebrating: Celebrating? = null,
    )

    init {
        // Read once when the page opens, like every release flag.
        val checkInOn = checkInEnabled()
        viewModelScope.launch {
            // The goal card joins once the goals answer, so the page never waits on a week of readings.
            val energy = combine(expended(), burned(), goals.wrapUp(day).onStart { emit(emptyList()) }) { expended, burned, wraps -> Triple(expended, burned, wraps) }
            combine(diary.observeDay(day), settingsRepository.settings, policySource.policy, energy) { d, settings, policy, (expended, burned, wraps) ->
                DayCloseAction.InputsArrived(DayCloseInputs(calendar.today(), d, settings, expended, checkInOn = checkInOn, burned = burned, goals = wraps), policy)
            }.collect(::takeAction)
        }
        viewModelScope.launch { celebrations.observe().collect { takeAction(DayCloseAction.CelebratingArrived(it)) } }
    }

    /** Today's energy, so the range here is the one Today showed. With `release.health` off the store is never touched. */
    private fun expended(): Flow<EnergyExpended?> {
        val today = calendar.today()
        return if (healthFlag() && day == today) health.energy(today, calendar.timeZone()) else flowOf(null)
    }

    /** The day's Burned, what a set deficit or surplus aims at. With `release.goalsAndEnergy` off nothing reads it. */
    private fun burned(): Flow<Burned?> = if (goalsAndEnergy()) burnedRepository.burned(day) else flowOf(null)

    override suspend fun handleAction(action: DayCloseAction) {
        when (action) {
            is DayCloseAction.InputsArrived -> {
                // A future day has nothing to close; only a deep link gets here.
                if (day > action.inputs.today) {
                    sendEvent(DayCloseEvent.Close)
                    return
                }
                val note = if (model.prefilled) model.note else action.inputs.day.note.orEmpty()
                val feeling = if (model.prefilled) model.feeling else action.inputs.day.feeling
                action.rebuild { copy(inputs = action.inputs, policy = action.policy, note = note, feeling = feeling, prefilled = true) }
            }
            is DayCloseAction.CelebratingArrived -> action.rebuild { copy(celebrating = action.celebrating) }
            is DayCloseAction.NoteTyped -> action.rebuild { copy(note = action.text) }
            is DayCloseAction.FeelingTapped -> action.rebuild { copy(feeling = action.feeling.takeIf { it != feeling }) }
            DayCloseAction.CheckInTapped -> if (state.checkIn != null) sendEvent(DayCloseEvent.OpenCheckIn(day))
            DayCloseAction.NutrientsTapped -> sendEvent(DayCloseEvent.OpenNutrients(day))
            DayCloseAction.WhyNotCutBackTapped -> {
                cutBackLooks.record()
                sendEvent(DayCloseEvent.OpenWhyNotCutBack)
            }
            DayCloseAction.SupportTapped -> sendEvent(DayCloseEvent.OpenSupport)
            DayCloseAction.CommitmentTapped -> action.rebuild { copy(committed = true) }
            DayCloseAction.DoneTapped -> {
                // Done shows while the day loads; before it is read the note field is still empty, and
                // saving that would wipe the note already there. Once leaving, a second tap does nothing.
                if (model.inputs == null || leaving) return
                leaving = true
                val stored = model.inputs?.day
                val reflection = DayReflection(
                    note = model.note.trim().ifEmpty { null },
                    // Only where it was asked: a feeling picked under another mode is left as it was.
                    feeling = if (state.feeling != null) model.feeling else stored?.feeling,
                )
                // Read before the write lands, so it says where the run stood and whether a milestone was waiting.
                val wrapped = if (day == calendar.today()) wrappedToast(model.celebrating, celebrations.pending()) else null
                diary.closeDay(day, reflection)
                promptEvents.emit(PromptEvent.DayClosed)
                wrapped?.let { sendEvent(DayCloseEvent.ShowToast(it)) }
                sendEvent(DayCloseEvent.Close)
            }
            DayCloseAction.ReopenTapped -> {
                // Save sits beside Reopen; tapped while leaving, it would close the day again.
                if (leaving) return
                leaving = true
                diary.reopenDay(day)
                sendEvent(DayCloseEvent.Close)
            }
            DayCloseAction.BackTapped -> sendEvent(DayCloseEvent.Close)
        }
    }

    private suspend fun DayCloseAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs ?: return
        val policy = current.policy ?: return
        updateState { DayCloseStateBuilder.build(inputs, policy, current.note, current.committed, current.feeling, current.celebrating) }
    }

    internal companion object {
        /**
         * What Today says once today is wrapped up: "Day wrapped up. That's 4 in a row." Nothing
         * when celebrations are off, or when a celebration is about to play (one was [waiting],
         * or this wrap-up reaches a milestone), so the two never say the same thing twice.
         */
        fun wrappedToast(celebrating: Celebrating?, waiting: Milestone?): Toast? {
            val progress = celebrating?.progress ?: return null
            if (waiting != null) return null
            val run = if (progress.todayCounted) progress.current else progress.current + 1
            val reaches = Milestone.entries.any { milestone ->
                milestone !in progress.reached && when (milestone.family) {
                    MilestoneFamily.WrapUp -> true
                    MilestoneFamily.Streak -> milestone.threshold == run
                    MilestoneFamily.CheckIn -> false
                }
            }
            return when {
                reaches -> null
                celebrating.style == CelebrationStyle.Counts && run >= 2 -> Toast(Res.string.celebrate_wrapped_toast, listOf(run))
                else -> Toast(Res.string.celebrate_wrapped_toast_plain)
            }
        }
    }
}

@Composable
fun DayCloseScreen(
    state: DayCloseState,
    onAction: (DayCloseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                PageHeader(
                    title = stringResource(state.title),
                    subtitle = state.day?.let { DateCopy.longWeekdayMonthDay(it) },
                    backLabel = stringResource(Res.string.today_dayclose_back, state.backLabel.text()),
                    onBack = { onAction(DayCloseAction.BackTapped) },
                )
                if (state.loading) {
                    // Waits on the day and, with release.health, on the health store's first reading.
                    DelayedLoadingIndicator(contentDescription = stringResource(Res.string.today_dayclose_loading_cd))
                    return@Column
                }
                state.recap?.let { RecapText(it, state.isToday) }
                state.streak?.let { StreakLineCard(it) }
                if (state.revealNote) {
                    InfoBanner(
                        icon = AppIcons.Lock,
                        title = stringResource(Res.string.today_dayclose_revealed_title),
                        body = stringResource(Res.string.today_dayclose_revealed_body),
                    )
                }
                state.totals?.let { totals -> Totals(totals, state.isToday) }
                state.soft?.let { soft ->
                    LabelledLineCard(
                        label = stringResource(if (state.isToday) Res.string.today_dayclose_soft_label else Res.string.today_dayclose_soft_label_other_day),
                    ) {
                        NutrientText(metric = Metric.Calories, display = soft, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink)
                    }
                }
                state.bigDay?.let { bigDay ->
                    BigDayHeroCard(
                        body = stringResource(Res.string.today_dayclose_big_day),
                        tomorrow = bigDay.tomorrow,
                        tomorrowPhrase = {
                            stringResource(if (bigDay.singleGoal) Res.string.today_dayclose_tomorrow_goal else Res.string.today_dayclose_tomorrow, it)
                        },
                        commitLabels = if (bigDay.commitment == Commitment.Offered) {
                            listOf(stringResource(Res.string.today_dayclose_commit_normal), stringResource(Res.string.today_dayclose_commit_can))
                        } else {
                            emptyList()
                        },
                        onCommit = { onAction(DayCloseAction.CommitmentTapped) },
                        committedText = if (bigDay.commitment == Commitment.Committed) stringResource(Res.string.today_dayclose_committed) else null,
                        whyNotLabel = stringResource(Res.string.today_dayclose_why_not),
                        onWhyNot = { onAction(DayCloseAction.WhyNotCutBackTapped) },
                        supportLabel = stringResource(Res.string.today_dayclose_support),
                        onSupport = { onAction(DayCloseAction.SupportTapped) },
                    )
                }
                state.mealsLine?.let { line ->
                    LabelValueCard(
                        label = stringResource(Res.string.today_dayclose_meals),
                        value = stringResource(Res.string.today_count_value, line.done, line.total),
                    )
                }
                state.goal?.let { GoalWrapCardView(it) }
                state.balance?.let { BalanceCard(it) }
                state.macros?.let { MacrosCard(it) }
                state.micros?.let { micros -> MicrosCard(micros, onSeeAll = { onAction(DayCloseAction.NutrientsTapped) }) }
                ExpendedRow(state.expended)
                state.loggedMeals?.let { done ->
                    LoggedMealsCard(
                        label = stringResource(if (state.isToday) Res.string.today_dayclose_logged_today else Res.string.today_dayclose_logged_that_day),
                        meals = MealSlot.meals.map { it to stringResource(it.label) },
                        done = done,
                    )
                }
                state.feeling?.let { choice -> Feeling(choice, onAction) }
                state.checkIn?.let { checkIn ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                        SectionLabel(
                            stringResource(if (state.isToday) Res.string.today_dayclose_checkin_label else Res.string.today_dayclose_checkin_label_other_day),
                        )
                        CheckInEntry(checkIn, onClick = { onAction(DayCloseAction.CheckInTapped) })
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                    SectionLabel(
                        stringResource(if (state.isToday) Res.string.today_dayclose_note_label else Res.string.today_dayclose_note_label_other_day),
                    )
                    TextArea(
                        value = state.note,
                        onValueChange = { onAction(DayCloseAction.NoteTyped(it)) },
                        placeholder = stringResource(Res.string.today_dayclose_note_placeholder),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FlatButton(
                    text = stringResource(if (state.closed) Res.string.today_dayclose_save else Res.string.today_dayclose_done),
                    onClick = { onAction(DayCloseAction.DoneTapped) },
                    tone = FlatButtonTone.Accent,
                    centered = true,
                    minHeight = FlatButtonDefaults.TallMinHeight,
                    typography = AppTheme.type.button,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.closed) {
                    FlatButton(
                        text = stringResource(Res.string.today_dayclose_reopen),
                        onClick = { onAction(DayCloseAction.ReopenTapped) },
                        tone = FlatButtonTone.Soft,
                        centered = true,
                        minHeight = FlatButtonDefaults.TallMinHeight,
                        typography = AppTheme.type.button,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(if (state.closed) Res.string.today_dayclose_closed_caption else Res.string.today_dayclose_caption),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
        }
    }
}

/** The run so far, once today is wrapped up: quiet, one line, and no number in the gentle style. */
@Composable
private fun StreakLineCard(line: StreakLine) {
    val text = when (line) {
        is StreakLine.Counts -> {
            val run = pluralStringResource(Res.plurals.celebrate_run, line.days, line.days)
            val next = line.next
            val toNext = line.toNext
            if (next != null && toNext != null) {
                val more = pluralStringResource(Res.plurals.celebrate_more_to, toNext, toNext, stringResource(next.nextName))
                stringResource(Res.string.celebrate_run_with_next, run, more)
            } else {
                run
            }
        }
        StreakLine.Gentle -> stringResource(Res.string.celebrate_gentle_run)
    }
    TodayCard(contentPadding = PaddingValues(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Icon(icon = AppIcons.AutoAwesome, size = 20.dp, color = AppTheme.colors.accent)
            Text(text = text, typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Feeling(choice: FeelingChoice, onAction: (DayCloseAction) -> Unit) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SectionLabel(stringResource(Res.string.today_dayclose_feeling_label))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            choice.options.forEach { feeling ->
                ChoiceChip(
                    label = stringResource(feeling.label),
                    selected = feeling == choice.picked,
                    onClick = { onAction(DayCloseAction.FeelingTapped(feeling)) },
                    role = Role.RadioButton,
                    unselectedColor = AppTheme.colors.surface,
                )
            }
        }
    }
}

/** "Expended, from your watch  2,310 kcal": energy out today, nothing at all when hidden. */
@Composable
private fun ExpendedRow(expended: Display) {
    NutrientVisible(expended) {
        TodayCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon = AppIcons.Watch, size = 22.dp, color = AppTheme.colors.muted)
                Text(
                    text = stringResource(Res.string.today_dayclose_expended),
                    typography = AppTheme.type.bodyBold,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                NutrientText(Metric.EnergyOut, expended, typography = AppTheme.type.bodyBold.ExtraBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
    }
}

/** The line under the title: how much of the day was logged, never an amount. */
@Composable
private fun RecapText(recap: RecapLine, isToday: Boolean) {
    val text = when (recap) {
        RecapLine.AllMeals -> if (isToday) Res.string.today_dayclose_recap_all else Res.string.today_dayclose_recap_all_other_day
        RecapLine.SomeLogged -> if (isToday) Res.string.today_dayclose_recap_some else Res.string.today_dayclose_recap_some_other_day
        RecapLine.NothingLogged -> if (isToday) Res.string.today_dayclose_recap_none else Res.string.today_dayclose_recap_none_other_day
    }
    Text(text = stringResource(text), typography = AppTheme.type.body, color = AppTheme.colors.ink)
}

/**
 * Eaten and burned side by side, then how far apart in a sentence, then how that sits with the
 * deficit or surplus the goal aims for. Only built when every amount in it is shown.
 */
@Composable
private fun BalanceCard(balance: EnergyBalance) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        SectionLabel(stringResource(Res.string.today_dayclose_balance_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            BalanceFigure(stringResource(Res.string.today_dayclose_balance_eaten), Metric.Calories, balance.eaten, Modifier.weight(1f))
            BalanceFigure(
                stringResource(if (balance.soFar) Res.string.today_dayclose_balance_burned_so_far else Res.string.today_dayclose_balance_burned),
                Metric.EnergyOut,
                balance.burned,
                Modifier.weight(1f),
            )
        }
        when (balance.direction) {
            BalanceDirection.Even -> Text(text = stringResource(Res.string.today_dayclose_balance_even), typography = AppTheme.type.body, color = AppTheme.colors.ink)
            BalanceDirection.Under, BalanceDirection.Over -> NutrientPhrase(
                metric = Metric.Calories,
                display = balance.difference,
                phrase = {
                    stringResource(if (balance.direction == BalanceDirection.Under) Res.string.today_dayclose_balance_under else Res.string.today_dayclose_balance_over, it)
                },
                typography = AppTheme.type.body.Tabular,
                color = AppTheme.colors.ink,
            )
        }
        balance.plan?.let { plan ->
            NutrientPhrase(
                metric = Metric.Calories,
                display = plan.planned,
                phrase = { stringResource(plan.text(), it) },
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
            )
        }
    }
}

private fun BalancePlan.text(): StringResource = when (kind) {
    PlanKind.Deficit -> when (result) {
        PlanResult.OnPlan -> Res.string.today_dayclose_plan_deficit_on
        PlanResult.Beyond -> Res.string.today_dayclose_plan_deficit_beyond
        PlanResult.Short -> Res.string.today_dayclose_plan_deficit_short
        PlanResult.Opposite -> Res.string.today_dayclose_plan_deficit_opposite
    }
    PlanKind.Surplus -> when (result) {
        PlanResult.OnPlan -> Res.string.today_dayclose_plan_surplus_on
        PlanResult.Beyond -> Res.string.today_dayclose_plan_surplus_beyond
        PlanResult.Short -> Res.string.today_dayclose_plan_surplus_short
        PlanResult.Opposite -> Res.string.today_dayclose_plan_surplus_opposite
    }
}

@Composable
private fun BalanceFigure(label: String, metric: Metric, display: Display, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SummaryLabel(label)
        NutrientText(metric, display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/** The macros two by two, each against its target with its bar, as Today's macros page draws them. */
@Composable
private fun MacrosCard(macros: List<MacroTile>) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page)) {
        MacrosPage(
            title = stringResource(Res.string.today_dayclose_macros),
            ofLine = {},
            macros = macros.map { SummaryMacro(it.metric, stringResource(it.label), it.display, it.metric.color()) },
            shares = emptyList(),
        )
    }
}

/** Today's vitamins and minerals, each against its reference, with See all for the rest. */
@Composable
private fun MicrosCard(micros: List<KeyNutrient>, onSeeAll: () -> Unit) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page)) {
        KeyNutrientsPage(
            title = stringResource(Res.string.today_dayclose_nutrients),
            seeAllLabel = stringResource(Res.string.today_nutrients_see_all),
            onSeeAll = onSeeAll,
            rows = micros.map { row ->
                KeyNutrientCell(
                    label = stringResource(row.label),
                    reference = stringResource(if (row.isLimit) Res.string.trends_micro_of_limit else Res.string.trends_micro_of, row.reference),
                    display = row.display,
                    notListed = if (row.notListed) stringResource(Res.string.trends_micro_not_listed) else null,
                )
            },
        )
    }
}

/**
 * The way into the check-in: its title, then what the day's check-in holds ("Hunger 4 · Energy
 * Good") or an invitation when it holds nothing. A tap opens the sheet.
 */
@Composable
private fun CheckInEntry(checkIn: CheckIn, onClick: () -> Unit) {
    val answers = buildList {
        checkIn.hunger?.let { add(stringResource(Res.string.today_dayclose_checkin_pair, stringResource(Res.string.today_checkin_hunger), it.value.toString())) }
        checkIn.energy?.let { add(stringResource(Res.string.today_dayclose_checkin_pair, stringResource(Res.string.today_checkin_energy), stringResource(it.label))) }
        checkIn.mood?.let { add(stringResource(Res.string.today_dayclose_checkin_pair, stringResource(Res.string.today_checkin_mood_level), stringResource(it.label))) }
        checkIn.sleep?.let { add(stringResource(Res.string.today_dayclose_checkin_pair, stringResource(Res.string.today_checkin_sleep), stringResource(it.label))) }
    }
    val description = stringResource(Res.string.today_dayclose_checkin_open_cd)
    TodayCard(
        modifier = Modifier.semantics { onClick(label = description) { onClick(); true } },
        contentPadding = PaddingValues(AppTheme.spacing.page),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Icon(icon = AppIcons.Mood, size = 22.dp, color = AppTheme.colors.muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(Res.string.today_checkin_title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(
                    text = answers.joinToString(" · ").ifEmpty { stringResource(Res.string.today_dayclose_checkin_empty) },
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            Icon(icon = AppIcons.ChevronRight, size = 22.dp, color = AppTheme.colors.muted)
        }
    }
}
@Composable
private fun Totals(totals: DayTotals, isToday: Boolean) {
    DayTotalsCard(
        eaten = totals.eaten,
        unitLabel = stringResource(Res.string.today_dayclose_kcal),
        rangeLine = {
            when (val line = totals.rangeLine) {
                is RangeLine.Inside -> RangeSentence(line.range) {
                    stringResource(if (totals.singleGoal) Res.string.today_dayclose_inside_goal else Res.string.today_dayclose_inside, it)
                }
                is RangeLine.Below -> RangeSentence(line.range) {
                    stringResource(if (totals.singleGoal) Res.string.today_dayclose_below_goal else Res.string.today_dayclose_below, it)
                }
                RangeLine.Above -> Text(
                    text = stringResource(
                        when {
                            totals.singleGoal && isToday -> Res.string.today_dayclose_above_goal
                            totals.singleGoal -> Res.string.today_dayclose_above_goal_other_day
                            isToday -> Res.string.today_dayclose_above
                            else -> Res.string.today_dayclose_above_other_day
                        },
                    ),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.ink,
                )
            }
        },
        macros = emptyList(),
    )
}

