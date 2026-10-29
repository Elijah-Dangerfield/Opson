package com.opson.features.builder.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.FillBar
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.dialog.BasicDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_add_food
import opson.libraries.resources.generated.resources.builder_agree
import opson.libraries.resources.generated.resources.builder_back
import opson.libraries.resources.generated.resources.builder_blocked_full
import opson.libraries.resources.generated.resources.builder_blocked_guest
import opson.libraries.resources.generated.resources.builder_blocked_removed
import opson.libraries.resources.generated.resources.builder_blocked_too_new
import opson.libraries.resources.generated.resources.builder_failed
import opson.libraries.resources.generated.resources.builder_get_body
import opson.libraries.resources.generated.resources.builder_get_title
import opson.libraries.resources.generated.resources.builder_how_add
import opson.libraries.resources.generated.resources.builder_how_check
import opson.libraries.resources.generated.resources.builder_how_report
import opson.libraries.resources.generated.resources.builder_how_title
import opson.libraries.resources.generated.resources.builder_intro_body
import opson.libraries.resources.generated.resources.builder_intro_title
import opson.libraries.resources.generated.resources.builder_join
import opson.libraries.resources.generated.resources.builder_joining
import opson.libraries.resources.generated.resources.builder_leave
import opson.libraries.resources.generated.resources.builder_leave_cancel
import opson.libraries.resources.generated.resources.builder_leave_confirm
import opson.libraries.resources.generated.resources.builder_leave_confirm_body
import opson.libraries.resources.generated.resources.builder_leave_confirm_title
import opson.libraries.resources.generated.resources.builder_left_body
import opson.libraries.resources.generated.resources.builder_left_title
import opson.libraries.resources.generated.resources.builder_loading_cd
import opson.libraries.resources.generated.resources.builder_offline
import opson.libraries.resources.generated.resources.builder_pro_ended
import opson.libraries.resources.generated.resources.builder_pro_until
import opson.libraries.resources.generated.resources.builder_progress
import opson.libraries.resources.generated.resources.builder_rejoin
import opson.libraries.resources.generated.resources.builder_removed_body
import opson.libraries.resources.generated.resources.builder_removed_title
import opson.libraries.resources.generated.resources.builder_retention
import opson.libraries.resources.generated.resources.builder_retention_on
import opson.libraries.resources.generated.resources.builder_retry
import opson.libraries.resources.generated.resources.builder_rule_copy
import opson.libraries.resources.generated.resources.builder_rule_license
import opson.libraries.resources.generated.resources.builder_rule_one
import opson.libraries.resources.generated.resources.builder_rule_real
import opson.libraries.resources.generated.resources.builder_rule_removal
import opson.libraries.resources.generated.resources.builder_rules_title
import opson.libraries.resources.generated.resources.builder_status_title
import opson.libraries.resources.generated.resources.builder_status_trusted
import opson.libraries.resources.generated.resources.builder_status_trusted_body
import opson.libraries.resources.generated.resources.builder_terms_link
import opson.libraries.resources.generated.resources.builder_tip_check
import opson.libraries.resources.generated.resources.builder_title
import opson.libraries.resources.generated.resources.builder_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * More › Builders: what the program is and its rules with Join, or, once joined, where the
 * person stands (accepted this month, when Pro runs to) with a way to add a food and to leave.
 * Plain words, no streaks or scores: the one number is how many count toward Pro.
 */
@Composable
fun BuilderProgramScreen(
    state: BuilderProgramState,
    onAction: (BuilderProgramAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page)
                .padding(bottom = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            PageHeader(
                title = stringResource(Res.string.builder_title),
                backLabel = stringResource(Res.string.builder_back),
                onBack = { onAction(BuilderProgramAction.BackTapped) },
            )
            when (val page = state.page) {
                null -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.builder_loading_cd))
                is BuilderPage.Join -> JoinPage(page, state, onAction)
                is BuilderPage.Member -> MemberPage(page, state, onAction)
                BuilderPage.Removed -> Card {
                    Title(stringResource(Res.string.builder_removed_title))
                    Body(stringResource(Res.string.builder_removed_body))
                }
                BuilderPage.Unavailable -> Card { Body(stringResource(Res.string.builder_unavailable)) }
            }
            state.problem?.let { problem ->
                Card {
                    Body(stringResource(if (problem == BuilderProblem.Offline) Res.string.builder_offline else Res.string.builder_failed))
                    FlatButton(text = stringResource(Res.string.builder_retry), onClick = { onAction(BuilderProgramAction.RetryTapped) })
                }
            }
        }
    }
    if (state.confirmingLeave) {
        BasicDialog(
            title = stringResource(Res.string.builder_leave_confirm_title),
            description = stringResource(Res.string.builder_leave_confirm_body),
            primaryButtonText = stringResource(Res.string.builder_leave_confirm),
            secondaryButtonText = stringResource(Res.string.builder_leave_cancel),
            onDismissRequest = { onAction(BuilderProgramAction.LeaveCancelled) },
            onPrimaryButtonClicked = { onAction(BuilderProgramAction.LeaveConfirmed) },
            onSecondaryButtonClicked = { onAction(BuilderProgramAction.LeaveCancelled) },
        )
    }
}

@Composable
private fun JoinPage(page: BuilderPage.Join, state: BuilderProgramState, onAction: (BuilderProgramAction) -> Unit) {
    Card {
        if (page.rejoin) {
            Title(stringResource(Res.string.builder_left_title))
            Body(stringResource(Res.string.builder_left_body))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToneIcon(icon = AppIcons.Science, tone = AppTheme.colors.nutrients.fiber)
                Title(stringResource(Res.string.builder_intro_title), Modifier.weight(1f))
            }
            Body(stringResource(Res.string.builder_intro_body))
        }
    }
    Card {
        Title(stringResource(Res.string.builder_get_title))
        Body(stringResource(Res.string.builder_get_body))
    }
    Card {
        Title(stringResource(Res.string.builder_how_title))
        Body(stringResource(Res.string.builder_how_add))
        Body(stringResource(Res.string.builder_how_check))
        Body(stringResource(Res.string.builder_how_report))
    }
    Card {
        Title(stringResource(Res.string.builder_rules_title))
        listOf(
            Res.string.builder_rule_real,
            Res.string.builder_rule_copy,
            Res.string.builder_rule_license,
            Res.string.builder_rule_one,
            Res.string.builder_rule_removal,
        ).forEach { Body(stringResource(it)) }
        FlatButton(text = stringResource(Res.string.builder_terms_link), onClick = { onAction(BuilderProgramAction.TermsTapped) })
    }
    val block = page.block
    Card {
        if (block != null) {
            Body(blockText(block, page.until))
        } else {
            CheckRow(
                text = stringResource(Res.string.builder_agree),
                checked = state.agreed,
                onChange = { onAction(BuilderProgramAction.AgreeChanged(it)) },
            )
            CheckRow(
                text = stringResource(Res.string.builder_retention),
                checked = state.retention,
                onChange = { onAction(BuilderProgramAction.RetentionChanged(it)) },
            )
            ButtonPrimary(
                onClick = { onAction(BuilderProgramAction.JoinTapped) },
                enabled = state.agreed && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            state.busy -> Res.string.builder_joining
                            page.rejoin -> Res.string.builder_rejoin
                            else -> Res.string.builder_join
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun MemberPage(page: BuilderPage.Member, state: BuilderProgramState, onAction: (BuilderProgramAction) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIcon(icon = AppIcons.Science, tone = AppTheme.colors.nutrients.fiber)
            Title(
                stringResource(if (page.trusted) Res.string.builder_status_trusted else Res.string.builder_status_title),
                Modifier.weight(1f),
            )
        }
        if (page.trusted) Body(stringResource(Res.string.builder_status_trusted_body))
        Body(stringResource(Res.string.builder_progress, page.accepted.coerceAtMost(page.needed), page.needed))
        FillBar(
            fraction = if (page.needed == 0) 1f else page.accepted.toFloat() / page.needed,
            color = AppTheme.colors.accent,
            modifier = Modifier.fillMaxWidth(),
        )
        page.proUntil?.let { date ->
            Body(stringResource(if (page.proActive) Res.string.builder_pro_until else Res.string.builder_pro_ended, date))
        }
    }
    Card {
        ButtonPrimary(onClick = { onAction(BuilderProgramAction.AddFoodTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.builder_add_food))
        }
        Body(stringResource(Res.string.builder_tip_check))
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Body(stringResource(Res.string.builder_retention_on), Modifier.weight(1f))
            AppSwitch(
                checked = page.retention,
                onCheckedChange = { onAction(BuilderProgramAction.RetentionChanged(it)) },
                enabled = !state.busy,
            )
        }
        FlatButton(text = stringResource(Res.string.builder_terms_link), onClick = { onAction(BuilderProgramAction.TermsTapped) })
        FlatButton(
            text = stringResource(Res.string.builder_leave),
            onClick = { onAction(BuilderProgramAction.LeaveTapped) },
            tone = FlatButtonTone.Outlined,
            enabled = !state.busy,
        )
    }
}

@Composable
private fun blockText(block: JoinBlock, until: String?): String = when (block) {
    JoinBlock.Guest -> stringResource(Res.string.builder_blocked_guest)
    JoinBlock.TooNew -> stringResource(Res.string.builder_blocked_too_new, until.orEmpty())
    JoinBlock.Removed -> stringResource(Res.string.builder_blocked_removed)
    JoinBlock.Full -> stringResource(Res.string.builder_blocked_full)
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SquareCheck(checked = checked, size = 24.dp)
        Body(text, Modifier.weight(1f))
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        content = content,
    )
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = modifier)
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.muted, modifier = modifier)
}

private val CardGap = 12.dp
private val CardInset = androidx.compose.foundation.layout.PaddingValues(16.dp)
