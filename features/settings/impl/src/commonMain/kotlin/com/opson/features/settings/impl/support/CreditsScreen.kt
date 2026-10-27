package com.opson.features.settings.impl.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_back
import opson.libraries.resources.generated.resources.credits_font
import opson.libraries.resources.generated.resources.credits_font_detail
import opson.libraries.resources.generated.resources.credits_font_header
import opson.libraries.resources.generated.resources.credits_food_header
import opson.libraries.resources.generated.resources.credits_food_note
import opson.libraries.resources.generated.resources.credits_intro
import opson.libraries.resources.generated.resources.credits_libraries_header
import opson.libraries.resources.generated.resources.credits_library_detail
import opson.libraries.resources.generated.resources.credits_license_hide
import opson.libraries.resources.generated.resources.credits_license_open
import opson.libraries.resources.generated.resources.credits_license_show
import opson.libraries.resources.generated.resources.credits_licenses_header
import opson.libraries.resources.generated.resources.credits_nevo
import opson.libraries.resources.generated.resources.credits_off
import opson.libraries.resources.generated.resources.credits_off_detail
import opson.libraries.resources.generated.resources.credits_usda
import opson.libraries.resources.generated.resources.credits_usda_detail
import opson.libraries.resources.generated.resources.logger_source_nevo
import opson.libraries.resources.generated.resources.settings_about_credits
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Credits and licenses, from Support's About card: food data, the typeface, every open-source
 * library, then each licence's full text behind Show. A lazy list, because the library list
 * runs to hundreds of rows.
 */
@Composable
fun CreditsScreen(
    state: CreditsState,
    onAction: (CreditsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        ) {
            item(key = "header") {
                SettingsHeader(
                    title = stringResource(Res.string.settings_about_credits),
                    backLabel = stringResource(Res.string.common_back),
                    onBack = { onAction(CreditsAction.BackTapped) },
                )
            }
            item(key = "intro") {
                Text(
                    text = stringResource(Res.string.credits_intro),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.inkSoft,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            sectionHeader("food", { stringResource(Res.string.credits_food_header) })
            item(key = "food") { FoodDataCard(onAction) }
            sectionHeader("font", { stringResource(Res.string.credits_font_header) })
            item(key = "font") {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(Res.string.credits_font),
                        subtitle = stringResource(Res.string.credits_font_detail),
                        icon = null,
                    )
                }
            }
            if (state.libraries.isNotEmpty()) {
                sectionHeader("libraries", { stringResource(Res.string.credits_libraries_header) })
                itemsIndexed(state.libraries, key = { _, library -> library.id }, contentType = { _, _ -> "library" }) { index, library ->
                    Segment(index, state.libraries.size) {
                        LibraryRow(library)
                    }
                }
                sectionHeader("licenses", { stringResource(Res.string.credits_licenses_header) })
                itemsIndexed(state.licenses, key = { _, license -> "license-${license.id}" }, contentType = { _, _ -> "license" }) { index, license ->
                    Segment(index, state.licenses.size) {
                        LicenseRow(license, open = license.id == state.openLicense, onClick = { onAction(CreditsAction.LicenseTapped(license.id)) })
                    }
                }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, text: @Composable () -> String) {
    item(key = "$key-header", contentType = "header") {
        Text(
            text = text(),
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(start = 4.dp, top = SettingsDefaults.CardGap * 2, bottom = 8.dp),
        )
    }
}

@Composable
private fun FoodDataCard(onAction: (CreditsAction) -> Unit) {
    SettingsCard {
        Text(
            text = stringResource(Res.string.credits_food_note),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        SettingsRow(
            title = stringResource(Res.string.credits_usda),
            subtitle = stringResource(Res.string.credits_usda_detail),
            icon = null,
            chevron = true,
            showDivider = true,
            onClick = { onAction(CreditsAction.LinkTapped(USDA_URL)) },
        )
        SettingsRow(
            title = stringResource(Res.string.credits_off),
            subtitle = stringResource(Res.string.credits_off_detail),
            icon = null,
            chevron = true,
            showDivider = true,
            onClick = { onAction(CreditsAction.LinkTapped(ODBL_URL)) },
        )
        SettingsRow(
            title = stringResource(Res.string.credits_nevo),
            // RIVM's citation, word for word, shared with the logger's source line.
            subtitle = stringResource(Res.string.logger_source_nevo),
            icon = null,
            chevron = true,
            onClick = { onAction(CreditsAction.LinkTapped(NEVO_URL)) },
        )
    }
}

/** One row of a list that runs across many lazy items but reads as one card. */
@Composable
private fun Segment(index: Int, count: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(segmentShape(index, count))
            .background(AppTheme.colors.surface)
            .padding(horizontal = SettingsDefaults.CardPadding),
    ) {
        if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
        content()
    }
}

// AppTheme.shapes.card's radius, so the run of segments reads as the cards around it.
private val SegmentRadius = 22.dp

private fun segmentShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(SegmentRadius)
    index == 0 -> RoundedCornerShape(topStart = SegmentRadius, topEnd = SegmentRadius)
    index == count - 1 -> RoundedCornerShape(bottomStart = SegmentRadius, bottomEnd = SegmentRadius)
    else -> RectangleShape
}

@Composable
private fun LibraryRow(library: CreditedLibrary) {
    val licenses = library.licenses.joinToString()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = library.name, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(
            text = library.version?.let { stringResource(Res.string.credits_library_detail, it, licenses) } ?: licenses,
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun LicenseRow(license: CreditedLicense, open: Boolean, onClick: () -> Unit) {
    val action = when {
        license.text == null -> Res.string.credits_license_open
        open -> Res.string.credits_license_hide
        else -> Res.string.credits_license_show
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { role = Role.Button }
                .bounceClick(scaleDown = 0.98f, onClick = onClick)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = license.name, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(text = stringResource(action), typography = AppTheme.type.bodyBold, color = AppTheme.colors.accent)
        }
        if (open && license.text != null) {
            Text(
                text = license.text,
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.inkSoft,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private val PreviewCredits = CreditsState(
    libraries = listOf(
        CreditedLibrary("io.ktor:ktor-client-core", "Ktor", "3.3.1", listOf("Apache License 2.0")),
        CreditedLibrary("io.github.jan-tennert.supabase:supabase-kt", "supabase-kt", "3.2.4", listOf("MIT License")),
        CreditedLibrary("io.sentry:sentry-cocoa", "Sentry Cocoa", "8.57.3", listOf("MIT License")),
    ),
    licenses = listOf(
        CreditedLicense(CreditsStateBuilder.FONT_LICENSE_ID, "SIL Open Font License 1.1", "Copyright 2020-2024 The Atkinson Hyperlegible Next Project Authors.", OFL_URL),
        CreditedLicense("MIT", "MIT License", "Permission is hereby granted, free of charge, to any person obtaining a copy of this software…", null),
        CreditedLicense("ASDKL", "Android Software Development Kit License", null, "https://developer.android.com/studio/terms.html"),
    ),
    openLicense = "MIT",
)

@Preview
@Composable
private fun SupportCreditsPreview() {
    SettingsPreview {
        CreditsScreen(PreviewCredits, onAction = {})
    }
}

@Preview
@Composable
private fun SupportCreditsDarkPreview() {
    SettingsPreview(dark = true) {
        CreditsScreen(PreviewCredits, onAction = {})
    }
}
