package com.opson.features.foods.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScanArea
import com.opson.libraries.ui.components.NoteBanner
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PageHeaderNavigation
import com.opson.libraries.ui.components.ScanFrame
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_title
import opson.libraries.resources.generated.resources.recipes_scan_allow
import opson.libraries.resources.generated.resources.recipes_scan_ask
import opson.libraries.resources.generated.resources.recipes_scan_camera_off
import opson.libraries.resources.generated.resources.recipes_scan_hint
import opson.libraries.resources.generated.resources.recipes_scan_not_recipe
import opson.libraries.resources.generated.resources.recipes_scan_settings
import opson.libraries.resources.generated.resources.recipes_scan_title
import opson.libraries.resources.generated.resources.recipes_scan_unavailable
import org.jetbrains.compose.resources.stringResource

/** What the scanner reads: a square in the middle, as a QR code is square. */
val RecipeScanArea = ScanArea(width = 0.7f, height = 0.7f)

/**
 * Scan a recipe code: a square viewfinder with its frame, the hint under it, and why the camera
 * can't run when it can't. [viewfinder] is the platform camera; previews pass a placeholder.
 */
@Composable
fun ScanRecipeScreen(
    state: ScanRecipeState,
    onAction: (ScanRecipeAction) -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            PageHeader(
                title = stringResource(Res.string.recipes_scan_title),
                backLabel = stringResource(Res.string.foods_title),
                onBack = { onAction(ScanRecipeAction.CloseTapped) },
                navigation = PageHeaderNavigation.Close,
            )
            when {
                state.cameraUnavailable -> NoteBanner(stringResource(Res.string.recipes_scan_unavailable))
                state.permission == CameraPermission.Granted -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(AppTheme.shapes.card)
                            .background(AppTheme.colors.ink.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        viewfinder(Modifier.fillMaxSize())
                        ScanFrame(modifier = Modifier.fillMaxSize(RecipeScanArea.width))
                    }
                    Text(stringResource(Res.string.recipes_scan_hint), typography = AppTheme.type.body, color = AppTheme.colors.muted)
                    if (state.notRecipe) NoteBanner(stringResource(Res.string.recipes_scan_not_recipe))
                }
                (state.permission as? CameraPermission.Denied)?.canAskAgain == false -> {
                    Text(stringResource(Res.string.recipes_scan_camera_off), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                    ButtonPrimary(onClick = { onAction(ScanRecipeAction.SettingsTapped) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.recipes_scan_settings))
                    }
                }
                else -> {
                    Text(stringResource(Res.string.recipes_scan_ask), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                    ButtonPrimary(onClick = { onAction(ScanRecipeAction.AllowTapped) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.recipes_scan_allow))
                    }
                }
            }
        }
    }
}
