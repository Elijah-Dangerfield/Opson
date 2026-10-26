package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.text.SingleLineField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_felt_fullness
import opson.libraries.resources.generated.resources.logger_felt_fullness_too
import opson.libraries.resources.generated.resources.logger_felt_hunger
import opson.libraries.resources.generated.resources.logger_felt_hunger_very
import opson.libraries.resources.generated.resources.logger_felt_mood
import opson.libraries.resources.generated.resources.logger_felt_note
import opson.libraries.resources.generated.resources.logger_felt_save
import opson.libraries.resources.generated.resources.logger_felt_skip
import opson.libraries.resources.generated.resources.logger_felt_subtitle
import opson.libraries.resources.generated.resources.logger_felt_title
import opson.libraries.resources.generated.resources.logger_hunger_bit
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The design's words, in its order. "A bit", "Very hungry" and "Too full" are the logger's own
// wording for the shared feelings; the design's "Tired" mood is not a Mood yet, so it is not offered.
private val FeltHunger: List<Pair<Hunger, StringResource>> = listOf(
    Hunger.NotHungry to Hunger.NotHungry.label,
    Hunger.Peckish to Res.string.logger_hunger_bit,
    Hunger.Hungry to Hunger.Hungry.label,
    Hunger.VeryHungry to Res.string.logger_felt_hunger_very,
)
private val FeltFullness: List<Pair<Fullness, StringResource>> = listOf(
    Fullness.StillHungry to Fullness.StillHungry.label,
    Fullness.Satisfied to Fullness.Satisfied.label,
    Fullness.Full to Fullness.Full.label,
    Fullness.VeryFull to Res.string.logger_felt_fullness_too,
)
private val FeltMoods = listOf(Mood.Calm, Mood.Rushed, Mood.Social, Mood.Stressed, Mood.Happy)

/**
 * How it felt: hunger before, fullness after, any moods, a note. Skip is as
 * big as Save, because skipping is a fine answer. Stateless.
 */
@Composable
fun HowItFeltSheetContent(
    state: HowItFeltState,
    onAction: (HowItFeltAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            SheetHeader(
                title = stringResource(Res.string.logger_felt_title, stringResource(state.slot.sentenceName)),
                subtitle = stringResource(Res.string.logger_felt_subtitle),
                onClose = { onAction(HowItFeltAction.CloseTapped) },
                titleTypography = AppTheme.type.dayLabel,
            )
            Choices(stringResource(Res.string.logger_felt_hunger)) {
                FeltHunger.forEach { (hunger, label) ->
                    ChoiceChip(stringResource(label), hunger == state.hunger, { onAction(HowItFeltAction.HungerTapped(hunger)) })
                }
            }
            Choices(stringResource(Res.string.logger_felt_fullness)) {
                FeltFullness.forEach { (fullness, label) ->
                    ChoiceChip(stringResource(label), fullness == state.fullness, { onAction(HowItFeltAction.FullnessTapped(fullness)) })
                }
            }
            Choices(stringResource(Res.string.logger_felt_mood)) {
                FeltMoods.forEach { mood ->
                    ChoiceChip(
                        label = stringResource(mood.label),
                        selected = mood in state.moods,
                        onClick = { onAction(HowItFeltAction.MoodTapped(mood)) },
                        role = Role.Checkbox,
                    )
                }
            }
            SingleLineField(
                value = state.note,
                onValueChange = { onAction(HowItFeltAction.NoteTyped(it)) },
                placeholder = stringResource(Res.string.logger_felt_note),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = AppTheme.spacing.row, bottom = AppTheme.spacing.page),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ButtonGhost(onClick = { onAction(HowItFeltAction.SkipTapped) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.logger_felt_skip))
            }
            ButtonPrimary(onClick = { onAction(HowItFeltAction.SaveTapped) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.logger_felt_save))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) { content() }
    }
}
