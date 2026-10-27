package com.opson.features.qa.impl

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.thenIf
import org.jetbrains.compose.resources.StringResource

/*
 * The QA menu's frame, drawn to match a More page. Settings keeps its own copies internal to
 * :features:settings:impl, which this module may not depend on (only apps depend on an impl), and
 * a dev tool is not worth promoting them to :libraries:ui for. If More's pages change look, this
 * can lag behind without harm.
 */

@Composable
internal fun QaPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.page)
                .padding(bottom = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun QaCard(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/** A row as More draws one: optional muted icon, title and subtitle, then whatever trails. */
@Composable
internal fun QaRow(
    title: String,
    icon: IconResource? = null,
    subtitle: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .thenIf(onClick != null) {
                    semantics { role = Role.Button }.bounceClick(scaleDown = 0.98f, onClick = onClick!!)
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon = icon, size = 24.dp, color = AppTheme.colors.muted)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                if (subtitle != null) {
                    Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
                }
            }
            trailing?.invoke(this)
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

@Composable
internal fun QaSectionLabel(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.secondarySmallStrong,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** A one-off message for the snackbar. */
data class QaToast(val message: StringResource, val args: List<Any> = emptyList())
