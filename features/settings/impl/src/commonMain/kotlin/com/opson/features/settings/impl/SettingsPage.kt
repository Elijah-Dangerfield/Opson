package com.opson.features.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.thenIf
import org.jetbrains.compose.resources.StringResource

/**
 * The frame every More page shares: the page colour, system-bar insets, page padding, the
 * section gap, and one scroll for the whole page. Design v3 sets cards 16 dp from the edge.
 */
@Composable
internal fun SettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page)
                .padding(bottom = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(SettingsDefaults.CardGap),
            content = content,
        )
    }
}

/** A pushed settings page's top app bar, lined up with [SettingsPage]'s padding. */
@Composable
internal fun SettingsHeader(title: String, backLabel: String, onBack: () -> Unit) {
    PageHeader(title = title, backLabel = backLabel, onBack = onBack)
}

/** A card of [SettingsRow]s. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        modifier = modifier,
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = SettingsDefaults.CardPadding),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/**
 * A More row (design v3): an accent icon, a title with an optional subtitle, then trailing text
 * or a chevron. The whole row is the tap target; a row with no [onClick] is plain text.
 */
@Composable
internal fun SettingsRow(
    title: String,
    icon: IconResource?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    chevron: Boolean = false,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = SettingsDefaults.RowMinHeight)
                .thenIf(onClick != null) {
                    semantics { role = Role.Button }.bounceClick(scaleDown = 0.98f, onClick = onClick!!)
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsDefaults.IconGap),
        ) {
            if (icon != null) Icon(icon = icon, size = 24.dp, color = AppTheme.colors.accent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                if (subtitle != null) {
                    Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                }
            }
            if (trailingText != null) {
                Text(text = trailingText, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            trailing?.invoke(this)
            if (chevron) ListRowChevron()
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** A small label above a card ("Starting point", "Each number"). */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        typography = AppTheme.type.secondarySmallStrong,
        color = AppTheme.colors.muted,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
    )
}

internal object SettingsDefaults {
    val CardGap: Dp = 12.dp
    val CardPadding: Dp = 16.dp
    val CardInset = PaddingValues(16.dp)
    val RowMinHeight: Dp = 56.dp
    val IconGap: Dp = 14.dp

    /** A standard Android dialog's corners. */
    val DialogShape = RoundedCornerShape(28.dp)
}

/** A one-off message for the snackbar. Every settings view model's toast event carries one. */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())

/** One theme per screen preview (every preview is also a 390 dp design-sync screenshot). */
@Composable
internal fun SettingsPreview(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark) { content() }
}
