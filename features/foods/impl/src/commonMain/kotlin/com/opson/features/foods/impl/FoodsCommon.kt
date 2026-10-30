package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.thenIf
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_unverified
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.opson.libraries.ui.components.VerifiedMark
import opson.libraries.resources.generated.resources.food_check_verified
import opson.libraries.resources.generated.resources.food_check_verified_cd

/** A one-off message for the snackbar. Every Foods view model's toast event carries one. */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())

/**
 * A food's name cut at the first comma or bracket, for a saved meal's list of
 * foods: "Greek yogurt, plain" is "Greek yogurt". The logger's helper of the
 * same name, which feature modules cannot share.
 */
internal fun shortName(name: String): String {
    val cut = name.indexOfFirst { it == ',' || it == '(' || it == '[' }
    val short = (if (cut > 0) name.substring(0, cut) else name).trim()
    return short.ifEmpty { name.trim() }
}

/**
 * A Foods card (design v3): surface, the card radius, rows inset 14 from its sides, and an
 * optional section label inside it above a hairline ("Saved meals").
 */
@Composable
internal fun FoodsCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = FoodsDefaults.CardRowInset),
        verticalArrangement = Arrangement.Top,
    ) {
        if (label != null) FoodsCardLabel(label)
        content()
    }
}

/** A Foods card's section label and the hairline under it. */
@Composable
internal fun FoodsCardLabel(label: String) {
    Text(
        text = label,
        typography = AppTheme.type.sectionLabel,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
    )
    HorizontalDivider(color = AppTheme.colors.line)
}

/**
 * A row on a Foods card: the bold name, a muted line under it, the bare calories, and a round
 * button (the + on the tab, the X on Create a meal). Tapping the name runs [onClick] when there
 * is one; the button runs [onButton].
 */
@Composable
internal fun FoodsRow(
    name: String,
    sub: String?,
    calories: Display,
    buttonIcon: IconResource,
    buttonDescription: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    buttonContainer: ColorResource = AppTheme.colors.accentTint,
    buttonContent: ColorResource = AppTheme.colors.accent,
    showDivider: Boolean = true,
    /** Sent to Opson's food database and not checked yet. */
    unverified: Boolean = false,
    /** A person checked it against its package. */
    verified: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FoodsDefaults.RowMinHeight)
                // The whole row is the target, so it is as tall as the row and never under 48 dp.
                .thenIf(onClick != null) { semantics { role = Role.Button }.bounceClick(scaleDown = 0.98f, onClick = onClick!!) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) {
                    Text(text = sub, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (unverified) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icon = AppIcons.Schedule, size = 12.dp, color = AppTheme.colors.muted)
                        Text(text = stringResource(Res.string.foods_unverified), typography = AppTheme.type.captionStrong, color = AppTheme.colors.muted, maxLines = 1)
                    }
                } else if (verified) {
                    VerifiedMark(
                        text = stringResource(Res.string.food_check_verified),
                        description = stringResource(Res.string.food_check_verified_cd),
                        iconSize = 12.dp,
                    )
                }
            }
            NutrientText(
                metric = Metric.Calories,
                display = calories,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
            RoundIconButton(
                icon = buttonIcon.described(buttonDescription),
                onClick = onButton,
                containerColor = buttonContainer,
                contentColor = buttonContent,
                touchTarget = AppTheme.spacing.touchTarget,
            )
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** A muted line on a Foods card, for "nothing here yet". */
@Composable
internal fun FoodsCardNote(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(vertical = 14.dp),
    )
}

internal object FoodsDefaults {
    val CardGap: Dp = 12.dp
    val CardPadding: Dp = 16.dp
    val CardRowInset: Dp = 14.dp
    val RowMinHeight: Dp = 60.dp
}

/** One theme per screen preview (every preview is also a 390 dp design-sync screenshot). */
@Composable
internal fun FoodsPreviewFrame(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark) { content() }
}
