package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.Sleep
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.Icon
import opson.libraries.resources.generated.resources.today_checkin_energy
import opson.libraries.resources.generated.resources.today_checkin_energy_good
import opson.libraries.resources.generated.resources.today_checkin_energy_low
import opson.libraries.resources.generated.resources.today_checkin_energy_ok
import opson.libraries.resources.generated.resources.today_checkin_hunger
import opson.libraries.resources.generated.resources.today_checkin_hunger_often
import opson.libraries.resources.generated.resources.today_checkin_hunger_rarely
import opson.libraries.resources.generated.resources.today_checkin_hunger_some
import opson.libraries.resources.generated.resources.today_checkin_hunger_steady
import opson.libraries.resources.generated.resources.today_checkin_hunger_very
import opson.libraries.resources.generated.resources.today_checkin_mood
import opson.libraries.resources.generated.resources.today_checkin_mood_calm
import opson.libraries.resources.generated.resources.today_checkin_mood_happy
import opson.libraries.resources.generated.resources.today_checkin_mood_low
import opson.libraries.resources.generated.resources.today_checkin_mood_social
import opson.libraries.resources.generated.resources.today_checkin_mood_stressed
import opson.libraries.resources.generated.resources.today_checkin_mood_tired
import opson.libraries.resources.generated.resources.today_checkin_note
import opson.libraries.resources.generated.resources.today_checkin_sleep
import opson.libraries.resources.generated.resources.today_checkin_sleep_good
import opson.libraries.resources.generated.resources.today_checkin_sleep_ok
import opson.libraries.resources.generated.resources.today_checkin_sleep_poor
import opson.libraries.resources.generated.resources.today_checkin_title
import opson.libraries.resources.generated.resources.today_dayclose_above_goal
import opson.libraries.resources.generated.resources.today_dayclose_above_goal_other_day
import opson.libraries.resources.generated.resources.today_dayclose_below_goal
import opson.libraries.resources.generated.resources.today_dayclose_inside_goal
import opson.libraries.resources.generated.resources.today_dayclose_loading_cd
import opson.libraries.resources.generated.resources.today_dayclose_expended
import opson.libraries.resources.generated.resources.today_dayclose_support
import opson.libraries.resources.generated.resources.today_dayclose_tomorrow_goal
import opson.libraries.resources.generated.resources.today_dayclose_why_not
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.ui.components.chip.ChoiceChip
import opson.libraries.resources.generated.resources.today_dayclose_feeling_busy
import opson.libraries.resources.generated.resources.today_dayclose_feeling_good
import opson.libraries.resources.generated.resources.today_dayclose_feeling_hard
import opson.libraries.resources.generated.resources.today_dayclose_feeling_label
import opson.libraries.resources.generated.resources.today_dayclose_feeling_steady
import opson.libraries.resources.generated.resources.today_dayclose_feeling_tired
import org.jetbrains.compose.resources.StringResource
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.BigDayHeroCard
import com.opson.libraries.diary.ui.LabelValueCard
import com.opson.libraries.diary.ui.LoggedMealsCard
import com.opson.libraries.diary.ui.RangeSentence
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.components.InfoBanner
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import opson.libraries.resources.generated.resources.today_count_value
import opson.libraries.resources.generated.resources.today_dayclose_above
import opson.libraries.resources.generated.resources.today_dayclose_above_other_day
import opson.libraries.resources.generated.resources.today_dayclose_below
import opson.libraries.resources.generated.resources.today_dayclose_caption
import opson.libraries.resources.generated.resources.today_dayclose_inside
import opson.libraries.resources.generated.resources.today_dayclose_kcal
import opson.libraries.resources.generated.resources.today_dayclose_meals
import opson.libraries.resources.generated.resources.today_dayclose_revealed_body
import opson.libraries.resources.generated.resources.today_dayclose_revealed_title
import com.opson.features.today.impl.text
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.LabelledLineCard
import com.opson.libraries.diary.ui.DayTotalsCard
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_back
import opson.libraries.resources.generated.resources.today_dayclose_big_day
import opson.libraries.resources.generated.resources.today_dayclose_commit_can
import opson.libraries.resources.generated.resources.today_dayclose_commit_normal
import opson.libraries.resources.generated.resources.today_dayclose_committed
import opson.libraries.resources.generated.resources.today_dayclose_done
import opson.libraries.resources.generated.resources.today_dayclose_logged_that_day
import opson.libraries.resources.generated.resources.today_dayclose_logged_today
import opson.libraries.resources.generated.resources.today_dayclose_note_label
import opson.libraries.resources.generated.resources.today_dayclose_note_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_note_placeholder
import opson.libraries.resources.generated.resources.today_dayclose_soft_label
import opson.libraries.resources.generated.resources.today_dayclose_soft_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_tomorrow
import org.jetbrains.compose.resources.stringResource

/**
 * Day close for one day. Done saves the note and stamps the day closed; Back
 * leaves both alone. The commitment buttons change only what this screen says.
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
    private val checkIns: DayCheckIns,
    private val checkInEnabled: CheckInEnabled,
) : SEAViewModel<DayCloseState, DayCloseEvent, DayCloseAction>(initialStateArg = DayCloseState()) {

    private var model = Model()

    private data class Model(
        val inputs: DayCloseInputs? = null,
        val policy: VisibilityPolicy? = null,
        val note: String = "",
        val feeling: DayFeeling? = null,
        val checkIn: CheckIn? = null,
        val prefilled: Boolean = false,
        val committed: Boolean = false,
    )

    init {
        viewModelScope.launch {
            combine(diary.observeDay(day), settingsRepository.settings, policySource.policy, expended()) { d, settings, policy, expended ->
                val checkInOn = checkInEnabled() && settings.checkIn
                DayCloseAction.InputsArrived(DayCloseInputs(calendar.today(), d, settings, expended, checkInOn = checkInOn), policy)
            }.collect(::takeAction)
        }
    }

    /** Today's energy, so the range here is the one Today showed. With `release.health` off the store is never touched. */
    private fun expended(): Flow<EnergyExpended?> {
        val today = calendar.today()
        return if (healthFlag() && day == today) health.energy(today, calendar.timeZone()) else flowOf(null)
    }

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
                val checkIn = if (model.prefilled) model.checkIn else action.inputs.day.checkIn
                action.rebuild {
                    copy(inputs = action.inputs, policy = action.policy, note = note, feeling = feeling, checkIn = checkIn, prefilled = true)
                }
            }
            is DayCloseAction.NoteTyped -> action.rebuild { copy(note = action.text) }
            is DayCloseAction.FeelingTapped -> action.rebuild { copy(feeling = action.feeling.takeIf { it != feeling }) }
            is DayCloseAction.CheckInHungerTapped -> action.checkIn { it.copy(hunger = action.hunger.takeIf { h -> h != it.hunger }) }
            is DayCloseAction.CheckInEnergyTapped -> action.checkIn { it.copy(energy = action.energy.takeIf { e -> e != it.energy }) }
            is DayCloseAction.CheckInSleepTapped -> action.checkIn { it.copy(sleep = action.sleep.takeIf { s -> s != it.sleep }) }
            is DayCloseAction.CheckInMoodTapped -> action.checkIn {
                it.copy(moods = if (action.mood in it.moods) it.moods - action.mood else it.moods + action.mood)
            }
            DayCloseAction.WhyNotCutBackTapped -> sendEvent(DayCloseEvent.OpenWhyNotCutBack)
            DayCloseAction.SupportTapped -> sendEvent(DayCloseEvent.OpenSupport)
            DayCloseAction.CommitmentTapped -> action.rebuild { copy(committed = true) }
            DayCloseAction.DoneTapped -> {
                diary.setDayNote(day, model.note.trim().ifEmpty { null })
                // Only where it was asked: a feeling picked under another mode is left as it was.
                if (state.feeling != null && model.feeling != model.inputs?.day?.feeling) diary.setDayFeeling(day, model.feeling)
                // Saved on Close the day only, and only where it was asked.
                if (state.checkIn != null && model.checkIn != model.inputs?.day?.checkIn) checkIns.setCheckIn(day, model.checkIn)
                diary.closeDay(day)
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
        updateState { DayCloseStateBuilder.build(inputs, policy, current.note, current.committed, current.feeling, current.checkIn) }
    }

    private suspend fun DayCloseAction.checkIn(change: (CheckIn) -> CheckIn) =
        rebuild { copy(checkIn = change(checkIn ?: CheckIn()).takeUnless { it.isEmpty }) }
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
                ExpendedRow(state.expended)
                state.loggedMeals?.let { done ->
                    LoggedMealsCard(
                        label = stringResource(if (state.isToday) Res.string.today_dayclose_logged_today else Res.string.today_dayclose_logged_that_day),
                        meals = MealSlot.meals.map { it to stringResource(it.label) },
                        done = done,
                    )
                }
                state.feeling?.let { choice -> Feeling(choice, onAction) }
                state.checkIn?.let { checkIn -> CheckInCard(checkIn, onAction) }
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
                    text = stringResource(Res.string.today_dayclose_done),
                    onClick = { onAction(DayCloseAction.DoneTapped) },
                    tone = FlatButtonTone.Accent,
                    centered = true,
                    minHeight = FlatButtonDefaults.TallMinHeight,
                    typography = AppTheme.type.button,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = stringResource(Res.string.today_dayclose_caption), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
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

/**
 * The check-in (4.1): hunger through the day, energy and last night's sleep as segmented rows,
 * then moods as chips. Every row optional; tapping the picked one again clears it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(checkIn: CheckIn, onAction: (DayCloseAction) -> Unit) {
    TodayCard(contentPadding = PaddingValues(AppTheme.spacing.page), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(text = stringResource(Res.string.today_checkin_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(text = stringResource(Res.string.today_checkin_note), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        CheckInRow(Res.string.today_checkin_hunger, DayHunger.entries, checkIn.hunger, { it.label }) { onAction(DayCloseAction.CheckInHungerTapped(it)) }
        CheckInRow(Res.string.today_checkin_energy, Energy.entries, checkIn.energy, { it.label }) { onAction(DayCloseAction.CheckInEnergyTapped(it)) }
        CheckInRow(Res.string.today_checkin_sleep, Sleep.entries, checkIn.sleep, { it.label }) { onAction(DayCloseAction.CheckInSleepTapped(it)) }
        SectionLabel(stringResource(Res.string.today_checkin_mood))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            CheckInMood.entries.forEach { mood ->
                ChoiceChip(
                    label = stringResource(mood.label),
                    selected = mood in checkIn.moods,
                    onClick = { onAction(DayCloseAction.CheckInMoodTapped(mood)) },
                    role = Role.Checkbox,
                    unselectedColor = AppTheme.colors.surface,
                )
            }
        }
    }
}

@Composable
private fun <T : Any> CheckInRow(title: StringResource, options: List<T>, picked: T?, label: (T) -> StringResource, onTap: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(title))
        SegmentedControl<T?>(
            options = options,
            selected = picked,
            onSelect = { it?.let(onTap) },
            label = { option -> option?.let { stringResource(label(it)) }.orEmpty() },
        )
    }
}

private val DayHunger.label: StringResource
    get() = when (this) {
        DayHunger.Rarely -> Res.string.today_checkin_hunger_rarely
        DayHunger.Some -> Res.string.today_checkin_hunger_some
        DayHunger.Steady -> Res.string.today_checkin_hunger_steady
        DayHunger.Often -> Res.string.today_checkin_hunger_often
        DayHunger.Very -> Res.string.today_checkin_hunger_very
    }

private val Energy.label: StringResource
    get() = when (this) {
        Energy.Low -> Res.string.today_checkin_energy_low
        Energy.Ok -> Res.string.today_checkin_energy_ok
        Energy.Good -> Res.string.today_checkin_energy_good
    }

private val Sleep.label: StringResource
    get() = when (this) {
        Sleep.Poor -> Res.string.today_checkin_sleep_poor
        Sleep.Ok -> Res.string.today_checkin_sleep_ok
        Sleep.Good -> Res.string.today_checkin_sleep_good
    }

private val CheckInMood.label: StringResource
    get() = when (this) {
        CheckInMood.Calm -> Res.string.today_checkin_mood_calm
        CheckInMood.Social -> Res.string.today_checkin_mood_social
        CheckInMood.Stressed -> Res.string.today_checkin_mood_stressed
        CheckInMood.Tired -> Res.string.today_checkin_mood_tired
        CheckInMood.Low -> Res.string.today_checkin_mood_low
        CheckInMood.Happy -> Res.string.today_checkin_mood_happy
    }

private val DayFeeling.label: StringResource
    get() = when (this) {
        DayFeeling.Steady -> Res.string.today_dayclose_feeling_steady
        DayFeeling.Busy -> Res.string.today_dayclose_feeling_busy
        DayFeeling.Tired -> Res.string.today_dayclose_feeling_tired
        DayFeeling.Good -> Res.string.today_dayclose_feeling_good
        DayFeeling.Hard -> Res.string.today_dayclose_feeling_hard
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
        macros = totals.macros.map { SummaryMacro(it.metric, stringResource(it.label), it.display, it.metric.dayCloseColor()) },
    )
}

@Composable
private fun Metric.dayCloseColor(): ColorResource = when (this) {
    Metric.Protein -> AppTheme.colors.nutrients.protein.content
    Metric.Carbs -> AppTheme.colors.nutrients.carbs.content
    Metric.Fat -> AppTheme.colors.nutrients.fat.content
    else -> AppTheme.colors.nutrients.fiber.content
}
