package com.opson.libraries.nutrition.ui

import com.opson.system.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.PaceAssessment
import com.opson.libraries.nutrition.PaceTier
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.pace_above_limit
import opson.libraries.resources.generated.resources.pace_faster
import opson.libraries.resources.generated.resources.pace_gentle
import opson.libraries.resources.generated.resources.pace_info_body
import opson.libraries.resources.generated.resources.pace_info_cd
import opson.libraries.resources.generated.resources.pace_info_close
import opson.libraries.resources.generated.resources.pace_info_limit
import opson.libraries.resources.generated.resources.pace_info_limit_body
import opson.libraries.resources.generated.resources.pace_info_limit_estimated
import opson.libraries.resources.generated.resources.pace_info_limit_none
import opson.libraries.resources.generated.resources.pace_info_title
import opson.libraries.resources.generated.resources.pace_steady
import opson.libraries.resources.generated.resources.plan_goal_find_support
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * Goal views more than one feature draws (docs/plans/goals-v2.md): the note under a pace, its (i)
 * explanation, and the plain dialog goals ask their questions in. The goal flow and Targets both
 * use them, so a pace reads the same wherever it is set.
 */

/**
 * The note under a pace. Gentle and steady are a quiet line; faster than most guidance, or past the
 * fat-loss limit, a tinted card that says why in plain words, with Find support when [onFindSupport]
 * is given. Never a block. The (i) opens [PaceInfoDialog].
 */
@Composable
fun PaceNote(assessment: PaceAssessment, onInfo: () -> Unit, onFindSupport: (() -> Unit)?, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (assessment.tier) {
            PaceTier.Gentle -> Res.string.pace_gentle
            PaceTier.Steady -> Res.string.pace_steady
            PaceTier.Faster -> Res.string.pace_faster
            PaceTier.AboveFatLimit -> Res.string.pace_above_limit
        },
    )
    if (!assessment.needsCare) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            InfoButton(onInfo)
        }
        return
    }
    val warm = AppTheme.colors.nutrients.carbs
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(warm.container)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = if (onFindSupport != null) 0.dp else 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f).padding(top = 8.dp))
            InfoButton(onInfo)
        }
        onFindSupport?.let {
            ChevronLink(text = stringResource(Res.string.plan_goal_find_support), onClick = it, color = AppTheme.colors.ink)
        }
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.pace_info_cd)
    Icon(
        icon = AppIcons.Info,
        size = 18.dp,
        color = AppTheme.colors.muted,
        modifier = Modifier
            .minTouchTarget()
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
    )
}

/**
 * How fast is right: the usual guidance, and the fat-loss limit in plain words with its source.
 * [limitText] is the person's limit ("about 700 kcal"), already through the mode's policy, or null
 * where calories don't show or no limit could be worked out; [estimated] says the body fat behind
 * it is Opson's estimate; [canWorkOut] is false when no limit could be worked out at all.
 */
@Composable
fun PaceInfoDialog(limitText: String?, estimated: Boolean, canWorkOut: Boolean, onDismiss: () -> Unit) {
    GoalDialog(
        title = stringResource(Res.string.pace_info_title),
        lines = buildList {
            add(stringResource(Res.string.pace_info_body))
            add(stringResource(Res.string.pace_info_limit_body))
            limitText?.let { add(stringResource(Res.string.pace_info_limit, it)) }
            if (limitText != null && estimated) add(stringResource(Res.string.pace_info_limit_estimated))
            if (!canWorkOut) add(stringResource(Res.string.pace_info_limit_none))
        },
        buttons = listOf(GoalDialogButton(stringResource(Res.string.pace_info_close), onDismiss)),
        onDismiss = onDismiss,
    )
}

/** One of [GoalDialog]'s choices. The first is the one in the accent. */
data class GoalDialogButton(val text: String, val onClick: () -> Unit)

/**
 * The dialog goals ask in: a title, a few lines, and choices stacked so a long label reads whole.
 * Like More's log out: nothing pre-picked, and a tap outside is the same as the last choice.
 */
@Composable
fun GoalDialog(title: String, lines: List<String>, buttons: List<GoalDialogButton>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = title, typography = AppTheme.type.dayLabel, color = AppTheme.colors.ink, textAlign = TextAlign.Center)
            lines.forEach { Text(text = it, typography = AppTheme.type.body, color = AppTheme.colors.inkSoft, textAlign = TextAlign.Center) }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                buttons.forEachIndexed { index, button ->
                    FlatButton(
                        text = button.text,
                        onClick = button.onClick,
                        tone = if (index == 0) FlatButtonTone.AccentText else FlatButtonTone.Text,
                        centered = true,
                        typography = AppTheme.type.button,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PaceNotesPreview() = PreviewContent {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaceNote(PaceAssessment(PaceTier.Steady, 550, 0.006, null), onInfo = {}, onFindSupport = null)
        PaceNote(PaceAssessment(PaceTier.AboveFatLimit, 1100, 0.012, null), onInfo = {}, onFindSupport = {})
    }
}

@Preview
@Composable
private fun PaceInfoPreview() = PreviewContent {
    PaceInfoDialog(limitText = "about 1,420 kcal", estimated = true, canWorkOut = true, onDismiss = {})
}
