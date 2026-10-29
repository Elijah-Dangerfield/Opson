package com.opson.features.today.impl.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerAnchor
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.StepSlider
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_checkin_clear
import opson.libraries.resources.generated.resources.today_checkin_clear_cd
import opson.libraries.resources.generated.resources.today_checkin_energy
import opson.libraries.resources.generated.resources.today_checkin_hunger
import opson.libraries.resources.generated.resources.today_checkin_hunger_now
import opson.libraries.resources.generated.resources.today_checkin_hunger_unanswered
import opson.libraries.resources.generated.resources.today_checkin_hunger_value
import opson.libraries.resources.generated.resources.today_checkin_mood
import opson.libraries.resources.generated.resources.today_checkin_mood_level
import opson.libraries.resources.generated.resources.today_checkin_note
import opson.libraries.resources.generated.resources.today_checkin_save
import opson.libraries.resources.generated.resources.today_checkin_sleep_add
import opson.libraries.resources.generated.resources.today_checkin_sleep_morning
import opson.libraries.resources.generated.resources.today_checkin_sleep_night_before
import opson.libraries.resources.generated.resources.today_checkin_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The check-in: hunger on the 1 to 10 slider, then energy and mood on five points, then the mood
 * tags. In the morning sleep comes first, as how last night went; later it is a link.
 */
@Composable
fun CheckInSheetContent(
    state: CheckInState,
    onAction: (CheckInAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.today_checkin_title),
            subtitle = stringResource(Res.string.today_checkin_note),
            onClose = { onAction(CheckInAction.CloseTapped) },
        )
        if (!state.loaded) return@Column
        val checkIn = state.checkIn
        val sleep = @Composable {
            ScaleRow(
                title = if (state.isToday) Res.string.today_checkin_sleep_morning else Res.string.today_checkin_sleep_night_before,
                options = Sleep.entries,
                picked = checkIn.sleep,
                label = { it.label },
                onTap = { onAction(CheckInAction.SleepTapped(it)) },
            )
        }
        if (state.sleepFirst) sleep()
        HungerRow(checkIn.hunger, now = state.isToday, onAction)
        ScaleRow(Res.string.today_checkin_energy, Energy.entries, checkIn.energy, { it.label }) { onAction(CheckInAction.EnergyTapped(it)) }
        ScaleRow(Res.string.today_checkin_mood_level, MoodLevel.entries, checkIn.mood, { it.label }) { onAction(CheckInAction.MoodTapped(it)) }
        when {
            state.sleepFirst -> Unit
            state.sleepShown -> sleep()
            else -> ChevronLink(text = stringResource(Res.string.today_checkin_sleep_add), onClick = { onAction(CheckInAction.AddSleepTapped) })
        }
        MoodTags(checkIn.moods, onAction)
        FlatButton(
            text = stringResource(Res.string.today_checkin_save),
            onClick = { onAction(CheckInAction.SaveTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "How hungry are you right now?", the step and its word, the slider, and the scale's ends and
 * middle under it so the numbers mean something before anything is picked.
 */
@Composable
private fun HungerRow(hunger: HungerLevel?, now: Boolean, onAction: (CheckInAction) -> Unit) {
    val title = stringResource(if (now) Res.string.today_checkin_hunger_now else Res.string.today_checkin_hunger)
    val word = hunger?.let { stringResource(Res.string.today_checkin_hunger_value, it.value, stringResource(it.label)) }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(title)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word ?: stringResource(Res.string.today_checkin_hunger_unanswered),
                typography = if (word == null) AppTheme.type.body else AppTheme.type.bodyBold,
                color = if (word == null) AppTheme.colors.muted else AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (hunger != null) ClearLink(title) { onAction(CheckInAction.HungerCleared) }
        }
        StepSlider(
            value = hunger?.value,
            onValueChange = { onAction(CheckInAction.HungerPicked(it)) },
            range = HungerLevel.MIN..HungerLevel.MAX,
            contentDescription = title,
            stateDescription = word,
        )
        Row(Modifier.fillMaxWidth()) {
            listOf(HungerAnchor.Ravenous to TextAlign.Start, HungerAnchor.Neutral to TextAlign.Center, HungerAnchor.Stuffed to TextAlign.End)
                .forEach { (anchor, align) ->
                    Text(
                        text = stringResource(anchor.label),
                        typography = AppTheme.type.secondary,
                        color = AppTheme.colors.muted,
                        textAlign = align,
                        modifier = Modifier.weight(1f),
                    )
                }
        }
    }
}

@Composable
private fun ClearLink(what: String, onClick: () -> Unit) {
    val description = stringResource(Res.string.today_checkin_clear_cd, what)
    Box(
        modifier = Modifier
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .defaultMinSize(minHeight = AppTheme.spacing.touchTarget, minWidth = AppTheme.spacing.touchTarget)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text = stringResource(Res.string.today_checkin_clear), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.accent)
    }
}

/** A 5-point row; tapping the picked one again clears it. */
@Composable
private fun <T : Any> ScaleRow(title: StringResource, options: List<T>, picked: T?, label: (T) -> StringResource, onTap: (T) -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodTags(picked: Set<CheckInMood>, onAction: (CheckInAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(Res.string.today_checkin_mood))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            CheckInMood.entries.forEach { mood ->
                ChoiceChip(
                    label = stringResource(mood.label),
                    selected = mood in picked,
                    onClick = { onAction(CheckInAction.MoodTagTapped(mood)) },
                    role = Role.Checkbox,
                    unselectedColor = AppTheme.colors.surface,
                )
            }
        }
    }
}
