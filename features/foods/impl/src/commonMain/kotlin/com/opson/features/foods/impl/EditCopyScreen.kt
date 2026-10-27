package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PageHeaderNavigation
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextInput
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_brand
import opson.libraries.resources.generated.resources.foods_copy_name
import opson.libraries.resources.generated.resources.foods_copy_save
import opson.libraries.resources.generated.resources.foods_copy_serving
import opson.libraries.resources.generated.resources.foods_copy_title
import opson.libraries.resources.generated.resources.foods_copy_use
import opson.libraries.resources.generated.resources.foods_copy_use_body_other
import opson.libraries.resources.generated.resources.foods_copy_use_body_today
import opson.libraries.resources.generated.resources.foods_copy_was
import opson.libraries.resources.generated.resources.foods_field_calories
import opson.libraries.resources.generated.resources.foods_field_carbs
import opson.libraries.resources.generated.resources.foods_field_fat
import opson.libraries.resources.generated.resources.foods_field_fiber
import opson.libraries.resources.generated.resources.foods_field_protein
import opson.libraries.resources.generated.resources.foods_food_per_serving
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.foods_unit_g
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Edit a copy (design sync 4, 13.3e): ✕, the title and Save; the name, brand and serving; the
 * numbers per serving that the mode shows, a changed one on the accent tint with "Was 190" under
 * its label; then Use for this entry. Stateless.
 */
@Composable
fun EditCopyScreen(
    state: EditCopyState,
    onAction: (EditCopyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
        ) {
            PageHeader(
                title = stringResource(Res.string.foods_copy_title),
                backLabel = stringResource(Res.string.foods_title),
                onBack = { onAction(EditCopyAction.CloseTapped) },
                navigation = PageHeaderNavigation.Close,
            ) {
                FlatButton(
                    text = stringResource(Res.string.foods_copy_save),
                    onClick = { onAction(EditCopyAction.SaveTapped) },
                    tone = FlatButtonTone.AccentText,
                    enabled = state.canSave,
                    minHeight = MinTouchTarget,
                    typography = AppTheme.type.button,
                )
            }
            if (state.loading) return@Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = AppTheme.spacing.page),
                verticalArrangement = Arrangement.spacedBy(FoodsDefaults.CardGap),
            ) {
                AboutCard(state, onAction)
                if (state.fields.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.foods_food_per_serving),
                        typography = AppTheme.type.sectionLabel,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                    NumbersCard(state, onAction)
                }
                UseCard(state, onAction)
            }
        }
    }
}

@Composable
private fun AboutCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    CopyCard {
        LabeledRow(stringResource(Res.string.foods_copy_name)) {
            TextInput(value = state.name, onValueChange = { onAction(EditCopyAction.NameTyped(it)) })
        }
        HorizontalDivider(color = AppTheme.colors.line)
        LabeledRow(stringResource(Res.string.foods_copy_brand)) {
            TextInput(value = state.brand, onValueChange = { onAction(EditCopyAction.BrandTyped(it)) })
        }
        HorizontalDivider(color = AppTheme.colors.line)
        // The logged amount is what one serving is; changing it is Change amount's job on Today.
        LabeledRow(stringResource(Res.string.foods_copy_serving)) {
            Text(text = state.serving, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun NumbersCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
        state.fields.forEachIndexed { index, field ->
            if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
            NumberRow(field, onAction)
        }
    }
}

@Composable
private fun NumberRow(field: CopyField, onAction: (EditCopyAction) -> Unit) {
    val label = stringResource(field.metric.copyLabel)
    // A changed number is tinted, so what differs from the food it came from is plain at a glance.
    val tag = if (field.metric == Metric.Calories) Modifier.testTag(Metric.Calories.testTag()) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (field.was != null) Modifier.background(AppTheme.colors.accentTint) else Modifier)
            .defaultMinSize(minHeight = RowHeight)
            .padding(horizontal = FoodsDefaults.CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            field.was?.let { was ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(Res.string.foods_copy_was), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.accent)
                    NutrientText(field.metric, was, typography = AppTheme.type.secondaryStrong.Bold.Tabular, color = AppTheme.colors.accent, maxLines = 1)
                }
            }
        }
        NumberField(
            value = field.text,
            onValueChange = { onAction(EditCopyAction.NutritionTyped(field.metric, it)) },
            unit = if (field.metric == Metric.Calories) null else stringResource(Res.string.foods_unit_g),
            textAlign = TextAlign.End,
            modifier = Modifier.width(FieldWidth).then(tag).semantics { contentDescription = label },
        )
    }
}

@Composable
private fun UseCard(state: EditCopyState, onAction: (EditCopyAction) -> Unit) {
    CopyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.foods_copy_use), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(
                    text = if (state.isToday && state.slot.isMeal) {
                        stringResource(Res.string.foods_copy_use_body_today, stringResource(state.slot.sentenceName))
                    } else {
                        stringResource(Res.string.foods_copy_use_body_other)
                    },
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            AppSwitch(checked = state.useForEntry, onCheckedChange = { onAction(EditCopyAction.UseForEntryToggled) })
        }
    }
}

@Composable
private fun CopyCard(content: @Composable () -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(FoodsDefaults.CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

/** A label on the left and its field on the right, as the design lays out Name, Brand and Serving. */
@Composable
private fun LabeledRow(label: String, field: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.width(LabelWidth))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { field() }
    }
}

private val Metric.copyLabel: StringResource
    get() = when (this) {
        Metric.Protein -> Res.string.foods_field_protein
        Metric.Carbs -> Res.string.foods_field_carbs
        Metric.Fat -> Res.string.foods_field_fat
        Metric.Fiber -> Res.string.foods_field_fiber
        else -> Res.string.foods_field_calories
    }

private val RowHeight = 64.dp
private val FieldWidth = 120.dp
private val LabelWidth = 96.dp
