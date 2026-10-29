package com.opson.features.builder.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelRegion
import com.opson.libraries.foods.ShoppingFrequency
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.checkbox.SquareCheck
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.builder_agree
import opson.libraries.resources.generated.resources.builder_apply_intro
import opson.libraries.resources.generated.resources.builder_apply_region
import opson.libraries.resources.generated.resources.builder_apply_region_sub
import opson.libraries.resources.generated.resources.builder_apply_send
import opson.libraries.resources.generated.resources.builder_apply_sending
import opson.libraries.resources.generated.resources.builder_apply_shopping
import opson.libraries.resources.generated.resources.builder_apply_title
import opson.libraries.resources.generated.resources.builder_apply_why
import opson.libraries.resources.generated.resources.builder_apply_why_hint
import opson.libraries.resources.generated.resources.builder_failed
import opson.libraries.resources.generated.resources.builder_offline
import opson.libraries.resources.generated.resources.builder_region_au_nz
import opson.libraries.resources.generated.resources.builder_region_ca
import opson.libraries.resources.generated.resources.builder_region_eu
import opson.libraries.resources.generated.resources.builder_region_other
import opson.libraries.resources.generated.resources.builder_region_uk
import opson.libraries.resources.generated.resources.builder_region_us
import opson.libraries.resources.generated.resources.builder_retention
import opson.libraries.resources.generated.resources.builder_rule_copy
import opson.libraries.resources.generated.resources.builder_rule_license
import opson.libraries.resources.generated.resources.builder_rule_one
import opson.libraries.resources.generated.resources.builder_rule_real
import opson.libraries.resources.generated.resources.builder_rule_removal
import opson.libraries.resources.generated.resources.builder_rules_hide
import opson.libraries.resources.generated.resources.builder_rules_show
import opson.libraries.resources.generated.resources.builder_rules_title
import opson.libraries.resources.generated.resources.builder_shopping_monthly
import opson.libraries.resources.generated.resources.builder_shopping_rarely
import opson.libraries.resources.generated.resources.builder_shopping_weekly
import opson.libraries.resources.generated.resources.builder_terms_link
import opson.libraries.resources.generated.resources.builder_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The application: how often they buy packaged food, where they shop, an optional line on why,
 * the rules (opened in place, so there's never a sheet on a sheet) and the photo consent. Send
 * waits for both answers and the rules.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuilderApplySheetContent(
    state: BuilderApplyState,
    onAction: (BuilderApplyAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.builder_apply_title), onClose = onClose)
        Text(stringResource(Res.string.builder_apply_intro), typography = AppTheme.type.body, color = AppTheme.colors.muted)

        Question(stringResource(Res.string.builder_apply_shopping)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoppingFrequency.entries.forEach { shopping ->
                    ChoiceChip(
                        label = stringResource(shopping.label),
                        selected = state.shopping == shopping,
                        onClick = { onAction(BuilderApplyAction.ShoppingPicked(shopping)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }

        Question(stringResource(Res.string.builder_apply_region), stringResource(Res.string.builder_apply_region_sub)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelRegion.entries.forEach { region ->
                    ChoiceChip(
                        label = stringResource(region.label),
                        selected = state.region == region,
                        onClick = { onAction(BuilderApplyAction.RegionPicked(region)) },
                        unselectedColor = AppTheme.colors.surface,
                    )
                }
            }
        }

        Question(stringResource(Res.string.builder_apply_why)) {
            TextArea(
                value = state.motivation,
                onValueChange = { onAction(BuilderApplyAction.MotivationTyped(it)) },
                placeholder = stringResource(Res.string.builder_apply_why_hint),
                minLines = 2,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            CheckRow(
                text = stringResource(Res.string.builder_agree),
                checked = state.agreed,
                onChange = { onAction(BuilderApplyAction.AgreeChanged(it)) },
            )
            FlatButton(
                text = stringResource(if (state.rulesOpen) Res.string.builder_rules_hide else Res.string.builder_rules_show),
                onClick = { onAction(BuilderApplyAction.RulesTapped) },
            )
            if (state.rulesOpen) BuilderRulesList()
            CheckRow(
                text = stringResource(Res.string.builder_retention),
                checked = state.retention,
                onChange = { onAction(BuilderApplyAction.RetentionChanged(it)) },
            )
        }

        state.problem?.let { NoteBanner(problemText(it)) }
        ButtonPrimary(
            onClick = { onAction(BuilderApplyAction.SendTapped) },
            enabled = state.canSend,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.sending) Res.string.builder_apply_sending else Res.string.builder_apply_send)) }
    }
}

/** The rules on their own, from the page's Read the rules. */
@Composable
fun BuilderRulesSheetContent(onTerms: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.builder_rules_title), onClose = onClose)
        BuilderRulesList()
        FlatButton(text = stringResource(Res.string.builder_terms_link), onClick = onTerms, icon = AppIcons.OpenInNew)
    }
}

/** The five rules, one line each, the same wherever they show. */
@Composable
fun BuilderRulesList(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        RULES.forEach { rule ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(icon = AppIcons.Check, size = 20.dp, color = AppTheme.colors.accent)
                Text(text = stringResource(rule), typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Question(title: String, detail: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Text(text = title, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        if (detail != null) Text(text = detail, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        content()
    }
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
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun problemText(problem: ApplyProblem): String = when (problem) {
    is ApplyProblem.Blocked -> if (problem.block == JoinBlock.Pending) stringResource(Res.string.builder_failed) else blockText(problem.block, until = null)
    ApplyProblem.Offline -> stringResource(Res.string.builder_offline)
    ApplyProblem.Failed -> stringResource(Res.string.builder_failed)
    ApplyProblem.Unavailable -> stringResource(Res.string.builder_unavailable)
}

private val RULES = listOf(
    Res.string.builder_rule_real,
    Res.string.builder_rule_copy,
    Res.string.builder_rule_license,
    Res.string.builder_rule_one,
    Res.string.builder_rule_removal,
)

private val ShoppingFrequency.label: StringResource
    get() = when (this) {
        ShoppingFrequency.Weekly -> Res.string.builder_shopping_weekly
        ShoppingFrequency.Monthly -> Res.string.builder_shopping_monthly
        ShoppingFrequency.Rarely -> Res.string.builder_shopping_rarely
    }

private val LabelRegion.label: StringResource
    get() = when (this) {
        LabelRegion.Us -> Res.string.builder_region_us
        LabelRegion.Canada -> Res.string.builder_region_ca
        LabelRegion.Uk -> Res.string.builder_region_uk
        LabelRegion.Eu -> Res.string.builder_region_eu
        LabelRegion.AustraliaNz -> Res.string.builder_region_au_nz
        LabelRegion.Other -> Res.string.builder_region_other
    }
