package com.opson.features.settings.impl.support

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HeroBody
import com.opson.libraries.ui.components.HeroCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.PopoverMenu
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.settings_find_call
import opson.libraries.resources.generated.resources.settings_find_could_not_open
import opson.libraries.resources.generated.resources.settings_find_could_not_open_link
import opson.libraries.resources.generated.resources.settings_find_country_change
import opson.libraries.resources.generated.resources.settings_find_country_pick
import opson.libraries.resources.generated.resources.settings_find_crisis_call
import opson.libraries.resources.generated.resources.settings_find_crisis_sub
import opson.libraries.resources.generated.resources.settings_find_crisis_sub_keyword
import opson.libraries.resources.generated.resources.settings_find_crisis_text
import opson.libraries.resources.generated.resources.settings_find_crisis_title
import opson.libraries.resources.generated.resources.settings_find_ed_header
import opson.libraries.resources.generated.resources.settings_find_intro
import opson.libraries.resources.generated.resources.settings_find_no_country
import opson.libraries.resources.generated.resources.settings_find_open
import opson.libraries.resources.generated.resources.settings_find_other_countries
import opson.libraries.resources.generated.resources.settings_find_row_detail
import opson.libraries.resources.generated.resources.settings_find_row_title
import opson.libraries.resources.generated.resources.settings_find_text
import opson.libraries.resources.generated.resources.settings_find_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Find support (design sync 2, 10.3): the country, the crisis card on the hero colour, the
 * country's eating-disorder lines, then one line from each other country. Every tap goes
 * straight to the dialer, messages or the browser.
 */
@Composable
fun FindSupportScreen(
    state: FindSupportState,
    onAction: (FindSupportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_find_title),
            backLabel = stringResource(Res.string.common_back),
            onBack = { onAction(FindSupportAction.BackTapped) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(
                text = stringResource(Res.string.settings_find_intro),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            CountryPicker(state, onAction)
        }
        val crisis = state.crisis
        if (crisis != null) {
            CrisisCard(crisis, onAction)
        } else {
            EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset) {
                Text(
                    text = stringResource(Res.string.settings_find_no_country),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.ink,
                )
            }
        }
        state.couldNotOpen?.let { failed ->
            Text(
                text = stringResource(
                    if (failed.isLink) Res.string.settings_find_could_not_open_link else Res.string.settings_find_could_not_open,
                    failed.shown,
                ),
                typography = AppTheme.type.secondaryStrong,
                color = AppTheme.colors.ink,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.lines.isNotEmpty()) {
            LinesCard(
                header = stringResource(Res.string.settings_find_ed_header),
                lines = state.lines,
                withCountry = false,
                onAction = onAction,
            )
        }
        LinesCard(
            header = stringResource(state.country?.outside ?: Res.string.settings_find_other_countries),
            lines = state.elsewhere,
            withCountry = true,
            onAction = onAction,
        )
    }
}

/** The outlined country chip, and the menu of countries under it. */
@Composable
private fun CountryPicker(state: FindSupportState, onAction: (FindSupportAction) -> Unit) {
    val country = state.country?.let { stringResource(it.label) }
    val description = country?.let { stringResource(Res.string.settings_find_country_change, it) }
        ?: stringResource(Res.string.settings_find_country_pick)
    Box {
        Row(
            modifier = Modifier
                .minTouchTarget()
                .clip(AppTheme.shapes.inputLarge)
                .border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.inputLarge)
                .background(AppTheme.colors.surface)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                }
                .bounceClick(scaleDown = 0.97f) { onAction(FindSupportAction.CountryTapped) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon = AppIcons.Public, size = 20.dp, color = AppTheme.colors.ink)
            Text(
                text = country ?: stringResource(Res.string.settings_find_country_pick),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
            )
            Icon(icon = AppIcons.ExpandMore, size = 20.dp, color = AppTheme.colors.ink)
        }
        PopoverMenu(
            expanded = state.pickingCountry,
            onDismissRequest = { onAction(FindSupportAction.CountryPickerDismissed) },
            width = 240.dp,
        ) {
            SupportCountry.entries.forEach { option ->
                MenuItem(
                    label = stringResource(option.label),
                    icon = if (option == state.country) AppIcons.Check else null,
                    onClick = { onAction(FindSupportAction.CountryPicked(option)) },
                    showDivider = option != SupportCountry.entries.last(),
                )
            }
        }
    }
}

/** "In crisis right now" on the hero colour, with Call and Text. */
@Composable
private fun CrisisCard(crisis: CrisisLine, onAction: (FindSupportAction) -> Unit) {
    HeroCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.settings_find_crisis_title),
            typography = AppTheme.type.heroTitle,
            color = AppTheme.colors.onHero,
        )
        HeroBody(
            crisis.textKeyword?.let { stringResource(Res.string.settings_find_crisis_sub_keyword, it, crisis.text.shown) }
                ?: stringResource(Res.string.settings_find_crisis_sub),
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            CrisisButton(
                text = stringResource(Res.string.settings_find_crisis_call, crisis.call.shown),
                icon = AppIcons.Call,
                container = AppTheme.colors.onHero,
                content = AppTheme.colors.hero,
                onClick = { onAction(FindSupportAction.CrisisCallTapped) },
                modifier = Modifier.weight(1f),
            )
            CrisisButton(
                text = stringResource(Res.string.settings_find_crisis_text, crisis.text.shown),
                icon = AppIcons.Sms,
                container = AppTheme.colors.heroWash,
                content = AppTheme.colors.onHero,
                onClick = { onAction(FindSupportAction.CrisisTextTapped) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CrisisButton(
    text: String,
    icon: IconResource,
    container: ColorResource,
    content: ColorResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(container)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon = icon, size = 22.dp, color = content)
        Text(text = text, typography = AppTheme.type.bodyBold.ExtraBold, color = content, maxLines = 1)
    }
}

/** A card of lines under a small header, each with its Call, Text or Open button. */
@Composable
private fun LinesCard(
    header: String,
    lines: List<SupportLine>,
    withCountry: Boolean,
    onAction: (FindSupportAction) -> Unit,
) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(start = SettingsDefaults.CardPadding, end = SettingsDefaults.CardPadding, top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = header,
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        lines.forEach { line ->
            HorizontalDivider(color = AppTheme.colors.line)
            LineRow(line, withCountry, onClick = { onAction(FindSupportAction.LineTapped(line)) })
        }
    }
}

@Composable
private fun LineRow(line: SupportLine, withCountry: Boolean, onClick: () -> Unit) {
    val name = stringResource(line.name)
    val about = stringResource(line.about, *line.aboutArgs.toTypedArray())
    val title = if (withCountry) stringResource(Res.string.settings_find_row_title, name, stringResource(line.country.label)) else name
    val detail = when (line.reach) {
        is Reach.Call -> if (withCountry) line.shown else stringResource(Res.string.settings_find_row_detail, about, line.shown)
        is Reach.Text, is Reach.Web -> about
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(text = detail, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        val (label, icon) = when (line.reach) {
            is Reach.Call -> stringResource(Res.string.settings_find_call) to AppIcons.Call
            is Reach.Text -> stringResource(Res.string.settings_find_text) to AppIcons.Sms
            is Reach.Web -> stringResource(Res.string.settings_find_open) to AppIcons.OpenInNew
        }
        ReachButton(text = label, icon = icon, onClick = onClick)
    }
}

/** The tinted pill at the end of a line: Call, Text or Open. */
@Composable
private fun ReachButton(text: String, icon: IconResource, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(AppTheme.shapes.pill)
            .background(AppTheme.colors.accentTint)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon = icon, size = 20.dp, color = AppTheme.colors.accent)
        Text(text = text, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.accent)
    }
}

@Preview
@Composable
private fun SupportFindUkDarkPreview() {
    SettingsPreview(dark = true) {
        FindSupportScreen(FindSupportStateBuilder.build(SupportCountry.UnitedKingdom), onAction = {})
    }
}

/** A region with no list: the page asks for a country and lists every one. */
@Preview
@Composable
private fun SupportFindNoCountryPreview() {
    SettingsPreview {
        FindSupportScreen(FindSupportStateBuilder.build(null), onAction = {})
    }
}

/** The phone couldn't open the dialer: the number, said plainly. */
@Preview
@Composable
private fun SupportFindCouldNotOpenPreview() {
    SettingsPreview {
        FindSupportScreen(
            FindSupportStateBuilder.build(SupportCountry.Australia).copy(couldNotOpen = CouldNotOpen("13 11 14", isLink = false)),
            onAction = {},
        )
    }
}
