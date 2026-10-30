package com.opson.features.builder.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.Banner
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.FillBar
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.PillTag
import com.opson.libraries.ui.components.dialog.BasicDialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_add_food
import opson.libraries.resources.generated.resources.builder_add_food_sub
import opson.libraries.resources.generated.resources.builder_applied_body
import opson.libraries.resources.generated.resources.builder_applied_title
import opson.libraries.resources.generated.resources.builder_apply
import opson.libraries.resources.generated.resources.builder_apply_again
import opson.libraries.resources.generated.resources.builder_back
import opson.libraries.resources.generated.resources.builder_blocked_declined
import opson.libraries.resources.generated.resources.builder_blocked_full
import opson.libraries.resources.generated.resources.builder_blocked_guest
import opson.libraries.resources.generated.resources.builder_blocked_removed
import opson.libraries.resources.generated.resources.builder_blocked_too_new
import opson.libraries.resources.generated.resources.builder_check_food
import opson.libraries.resources.generated.resources.builder_check_food_sub
import opson.libraries.resources.generated.resources.builder_declined_again_on
import opson.libraries.resources.generated.resources.builder_declined_body
import opson.libraries.resources.generated.resources.builder_declined_note
import opson.libraries.resources.generated.resources.builder_declined_title
import opson.libraries.resources.generated.resources.builder_failed
import opson.libraries.resources.generated.resources.builder_intro_body
import opson.libraries.resources.generated.resources.builder_intro_title
import opson.libraries.resources.generated.resources.builder_leave
import opson.libraries.resources.generated.resources.builder_leave_cancel
import opson.libraries.resources.generated.resources.builder_leave_confirm
import opson.libraries.resources.generated.resources.builder_leave_confirm_body
import opson.libraries.resources.generated.resources.builder_leave_confirm_title
import opson.libraries.resources.generated.resources.builder_loading_cd
import opson.libraries.resources.generated.resources.builder_offline
import opson.libraries.resources.generated.resources.builder_pro_ended
import opson.libraries.resources.generated.resources.builder_pro_progress
import opson.libraries.resources.generated.resources.builder_pro_title
import opson.libraries.resources.generated.resources.builder_pro_until
import opson.libraries.resources.generated.resources.builder_removed_body
import opson.libraries.resources.generated.resources.builder_removed_title
import opson.libraries.resources.generated.resources.builder_retention_on
import opson.libraries.resources.generated.resources.builder_retention_on_sub
import opson.libraries.resources.generated.resources.builder_retry
import opson.libraries.resources.generated.resources.builder_rules_row
import opson.libraries.resources.generated.resources.builder_rules_row_sub
import opson.libraries.resources.generated.resources.builder_stats_accepted
import opson.libraries.resources.generated.resources.builder_stats_checks
import opson.libraries.resources.generated.resources.builder_stats_foods
import opson.libraries.resources.generated.resources.builder_stats_percent
import opson.libraries.resources.generated.resources.builder_status_title
import opson.libraries.resources.generated.resources.builder_status_trusted
import opson.libraries.resources.generated.resources.builder_status_trusted_body
import opson.libraries.resources.generated.resources.builder_step_check
import opson.libraries.resources.generated.resources.builder_step_front
import opson.libraries.resources.generated.resources.builder_step_label
import opson.libraries.resources.generated.resources.builder_steps_title
import opson.libraries.resources.generated.resources.builder_title
import opson.libraries.resources.generated.resources.builder_unavailable
import opson.libraries.resources.generated.resources.builder_welcome_body
import opson.libraries.resources.generated.resources.builder_welcome_title
import opson.libraries.resources.generated.resources.builder_withdraw
import opson.libraries.resources.generated.resources.builder_withdraw_cancel
import opson.libraries.resources.generated.resources.builder_withdraw_confirm
import opson.libraries.resources.generated.resources.builder_withdraw_confirm_body
import opson.libraries.resources.generated.resources.builder_withdraw_confirm_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * More › Builders, one state at a time. Before applying: one sentence on what Builders do and
 * get, three steps in pictures, Apply, and the rules behind a row. Waiting, declined and removed
 * each say only that. A Builder gets a home to work from: their Pro, the two things to do, what
 * they've done, and the few settings, in that order.
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
                // Nothing to draw yet. Once a problem is known the card below says what happened,
                // so the spinner stops rather than turning forever beside "You're offline".
                null -> if (state.problem == null) {
                    DelayedLoadingIndicator(contentDescription = stringResource(Res.string.builder_loading_cd))
                }
                is BuilderPage.Intro -> IntroPage(page, onAction)
                is BuilderPage.Applied -> AppliedPage(page, state, onAction)
                is BuilderPage.Declined -> DeclinedPage(page, onAction)
                is BuilderPage.Home -> HomePage(page, state, onAction)
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
    state.confirming?.let { confirming -> Confirm(confirming, onAction) }
}

@Composable
private fun IntroPage(page: BuilderPage.Intro, onAction: (BuilderProgramAction) -> Unit) {
    Hero(icon = AppIcons.Science, title = stringResource(Res.string.builder_intro_title), body = stringResource(Res.string.builder_intro_body))
    Card {
        Label(stringResource(Res.string.builder_steps_title))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Step(1, AppIcons.PhotoCamera, Res.string.builder_step_front, Modifier.weight(1f))
            Step(2, AppIcons.FormatListBulleted, Res.string.builder_step_label, Modifier.weight(1f))
            Step(3, AppIcons.CheckCircleOutline, Res.string.builder_step_check, Modifier.weight(1f))
        }
    }
    val block = page.block
    if (block != null) {
        Banner(blockText(block, page.until))
    } else {
        ButtonPrimary(onClick = { onAction(BuilderProgramAction.ApplyTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (page.returning) Res.string.builder_apply_again else Res.string.builder_apply))
        }
    }
    RulesRow(onAction)
}

@Composable
private fun AppliedPage(page: BuilderPage.Applied, state: BuilderProgramState, onAction: (BuilderProgramAction) -> Unit) {
    Hero(
        icon = AppIcons.CheckCircle,
        title = stringResource(Res.string.builder_applied_title),
        body = stringResource(Res.string.builder_applied_body, page.sentOn),
    )
    RulesRow(onAction)
    FlatButton(
        text = stringResource(Res.string.builder_withdraw),
        onClick = { onAction(BuilderProgramAction.WithdrawTapped) },
        tone = FlatButtonTone.Outlined,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeclinedPage(page: BuilderPage.Declined, onAction: (BuilderProgramAction) -> Unit) {
    Card {
        Title(stringResource(Res.string.builder_declined_title))
        Body(stringResource(Res.string.builder_declined_body))
        page.note?.let { note ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.inputLarge)
                    .background(AppTheme.colors.soft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Label(stringResource(Res.string.builder_declined_note))
                Text(text = note, typography = AppTheme.type.body, color = AppTheme.colors.ink)
            }
        }
        page.againOn?.let { Body(stringResource(Res.string.builder_declined_again_on, it)) }
    }
    if (page.againOn == null) {
        ButtonPrimary(onClick = { onAction(BuilderProgramAction.ApplyTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.builder_apply_again))
        }
    }
    RulesRow(onAction)
}

@Composable
private fun HomePage(page: BuilderPage.Home, state: BuilderProgramState, onAction: (BuilderProgramAction) -> Unit) {
    if (page.welcome) {
        Hero(icon = AppIcons.AutoAwesome, title = stringResource(Res.string.builder_welcome_title), body = stringResource(Res.string.builder_welcome_body))
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToneIcon(icon = AppIcons.WorkspacePremium, tone = AppTheme.colors.nutrients.fiber)
            Column(Modifier.weight(1f)) {
                Label(stringResource(if (page.trusted) Res.string.builder_status_trusted else Res.string.builder_status_title))
                Title(stringResource(Res.string.builder_pro_title))
            }
        }
        Text(
            text = stringResource(Res.string.builder_pro_progress, page.accepted.coerceAtMost(page.needed), page.needed),
            typography = AppTheme.type.figureLarge,
            color = AppTheme.colors.ink,
        )
        FillBar(
            fraction = if (page.needed == 0) 1f else (page.accepted.toFloat() / page.needed).coerceIn(0f, 1f),
            color = AppTheme.colors.accent,
            modifier = Modifier.fillMaxWidth(),
        )
        page.proUntil?.let { date ->
            Body(
                if (page.proActive) {
                    stringResource(Res.string.builder_pro_until, date)
                } else {
                    stringResource(Res.string.builder_pro_ended, date, page.needed)
                },
            )
        }
        if (page.trusted) Body(stringResource(Res.string.builder_status_trusted_body))
    }
    EdgeCard(edgeToEdge = false, contentPadding = RowsInset, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ActionRow(Res.string.builder_add_food, Res.string.builder_add_food_sub, AppIcons.PostAdd) { onAction(BuilderProgramAction.AddFoodTapped) }
        HorizontalDivider(color = AppTheme.colors.line)
        ActionRow(Res.string.builder_check_food, Res.string.builder_check_food_sub, AppIcons.BarcodeScanner) {
            onAction(BuilderProgramAction.CheckFoodTapped)
        }
    }
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(page.foodsAdded.toString(), Res.string.builder_stats_foods, Modifier.weight(1f))
            Stat(page.checks.toString(), Res.string.builder_stats_checks, Modifier.weight(1f))
            Stat(page.acceptedPercent?.let { stringResource(Res.string.builder_stats_percent, it) } ?: NO_FIGURE, Res.string.builder_stats_accepted, Modifier.weight(1f))
        }
    }
    EdgeCard(edgeToEdge = false, contentPadding = RowsInset, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.builder_retention_on), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                Text(stringResource(Res.string.builder_retention_on_sub), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
            }
            AppSwitch(
                checked = page.retention,
                onCheckedChange = { onAction(BuilderProgramAction.RetentionChanged(it)) },
                enabled = !state.busy,
            )
        }
        HorizontalDivider(color = AppTheme.colors.line)
        ListRow(
            title = stringResource(Res.string.builder_rules_row),
            icon = AppIcons.Article,
            onClick = { onAction(BuilderProgramAction.RulesTapped) },
            trailing = { ListRowChevron() },
        )
    }
    FlatButton(
        text = stringResource(Res.string.builder_leave),
        onClick = { onAction(BuilderProgramAction.LeaveTapped) },
        tone = FlatButtonTone.Outlined,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Confirm(confirming: BuilderConfirm, onAction: (BuilderProgramAction) -> Unit) {
    val leave = confirming == BuilderConfirm.Leave
    BasicDialog(
        title = stringResource(if (leave) Res.string.builder_leave_confirm_title else Res.string.builder_withdraw_confirm_title),
        description = stringResource(if (leave) Res.string.builder_leave_confirm_body else Res.string.builder_withdraw_confirm_body),
        primaryButtonText = stringResource(if (leave) Res.string.builder_leave_confirm else Res.string.builder_withdraw_confirm),
        secondaryButtonText = stringResource(if (leave) Res.string.builder_leave_cancel else Res.string.builder_withdraw_cancel),
        onDismissRequest = { onAction(BuilderProgramAction.ConfirmCancelled) },
        onPrimaryButtonClicked = { onAction(BuilderProgramAction.Confirmed) },
        onSecondaryButtonClicked = { onAction(BuilderProgramAction.ConfirmCancelled) },
    )
}

/** The page's lead: an icon, one line in large type and one short line under it. */
@Composable
private fun Hero(icon: IconResource, title: String, body: String) {
    Card {
        ToneIcon(icon = icon, tone = AppTheme.colors.nutrients.fiber, size = ToneIconSize.Large)
        Text(text = title, typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
        Text(text = body, typography = AppTheme.type.lead, color = AppTheme.colors.muted)
    }
}

/** One of the three steps: a numbered picture and a few words under it. */
@Composable
private fun Step(number: Int, icon: IconResource, label: StringResource, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Box(
                modifier = Modifier
                    .size(StepCircle)
                    .clip(CircleShape)
                    .background(AppTheme.colors.accentTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon = icon, size = 30.dp, color = AppTheme.colors.accent)
            }
            PillTag(label = number.toString(), color = AppTheme.colors.surface, modifier = Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = stringResource(label),
            typography = AppTheme.type.secondarySmallStrong,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Stat(value: String, label: StringResource, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink)
        Text(text = stringResource(label), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionRow(title: StringResource, subtitle: StringResource, icon: IconResource, onClick: () -> Unit) {
    ListRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        icon = icon,
        onClick = onClick,
        trailing = { ListRowChevron() },
    )
}

/** The rules, a row away rather than on the page. */
@Composable
private fun RulesRow(onAction: (BuilderProgramAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = RowsInset) {
        ListRow(
            title = stringResource(Res.string.builder_rules_row),
            subtitle = stringResource(Res.string.builder_rules_row_sub),
            icon = AppIcons.Article,
            onClick = { onAction(BuilderProgramAction.RulesTapped) },
            trailing = { ListRowChevron() },
        )
    }
}

@Composable
internal fun blockText(block: JoinBlock, until: String?): String = when (block) {
    JoinBlock.Guest -> stringResource(Res.string.builder_blocked_guest)
    JoinBlock.TooNew -> stringResource(Res.string.builder_blocked_too_new, until.orEmpty())
    JoinBlock.Removed -> stringResource(Res.string.builder_blocked_removed)
    JoinBlock.Full, JoinBlock.Pending -> stringResource(Res.string.builder_blocked_full)
    JoinBlock.Declined -> stringResource(Res.string.builder_blocked_declined)
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        content = content,
    )
}

@Composable
internal fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = modifier)
}

@Composable
internal fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.muted, modifier = modifier)
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted, modifier = modifier)
}

private val CardGap = 12.dp
private val CardInset = PaddingValues(16.dp)
private val RowsInset = PaddingValues(horizontal = 16.dp, vertical = 2.dp)
private val StepCircle = 64.dp

/** A stat with nothing behind it yet. A plain hyphen, never a dash. */
private const val NO_FIGURE = "-"
