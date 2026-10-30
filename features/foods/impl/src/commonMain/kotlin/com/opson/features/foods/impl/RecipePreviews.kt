package com.opson.features.foods.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import org.jetbrains.compose.ui.tooling.preview.Preview

// Recipes (release.recipes). Every state comes from the real builders over RecipeFixtures, the
// path the app takes. One theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.NoNumbers)

/** The Foods tab with the Recipes card: a shared chili and a copy of someone's oats. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRecipesPreview() = FoodsPreviewFrame { FoodsScreen(FoodsFixtures.foodsState(full, withRecent = false, recipesOn = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun FoodsRecipesEmptyPreview() = FoodsPreviewFrame { FoodsScreen(FoodsFixtures.foodsState(full, empty = true, recipesOn = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun RecipeNewPreview() = FoodsPreviewFrame { RecipeEditScreen(RecipeFixtures.editState(full, empty = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun RecipeEditPreview() = FoodsPreviewFrame { RecipeEditScreen(RecipeFixtures.editState(full, editing = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun RecipeEditDarkPreview() = FoodsPreviewFrame(dark = true) { RecipeEditScreen(RecipeFixtures.editState(full, editing = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun RecipeEditFullPreview() = FoodsPreviewFrame { RecipeEditScreen(RecipeFixtures.editState(full, editing = true).copy(canAddIngredient = false), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun RecipeEditBlindPreview() = FoodsPreviewFrame { RecipeEditScreen(RecipeFixtures.editState(blind, editing = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun RecipePreview() = FoodsPreviewFrame { RecipeScreen(RecipeFixtures.viewState(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun RecipeSharedDarkPreview() = FoodsPreviewFrame(dark = true) { RecipeScreen(RecipeFixtures.viewState(full, RecipeFixtures.sharedChili), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun RecipeBlindPreview() = FoodsPreviewFrame { RecipeScreen(RecipeFixtures.viewState(blind), onAction = {}) }

/** A copy saved from someone's shared recipe: it says whose it is. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipeSavedFromSomeonePreview() = FoodsPreviewFrame { RecipeScreen(RecipeFixtures.viewState(full, RecipeFixtures.recipes().last()), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipePublicPreview() = FoodsPreviewFrame { PublicRecipeScreen(RecipeFixtures.publicState(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipePublicDarkPreview() = FoodsPreviewFrame(dark = true) { PublicRecipeScreen(RecipeFixtures.publicState(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipePublicBlindPreview() = FoodsPreviewFrame { PublicRecipeScreen(RecipeFixtures.publicState(blind), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipePublicMinePreview() = FoodsPreviewFrame { PublicRecipeScreen(RecipeFixtures.publicState(full, mine = true), onAction = {}) }

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun RecipePublicSavedBeforePreview() = FoodsPreviewFrame {
    PublicRecipeScreen(RecipeFixtures.publicState(full).copy(savedId = "recipe-oats-copy"), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecipePublicNotSharedPreview() = FoodsPreviewFrame {
    PublicRecipeScreen(PublicRecipeState.Missing(PublicRecipeState.Missing.Why.NotShared), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecipePublicOfflinePreview() = FoodsPreviewFrame {
    PublicRecipeScreen(PublicRecipeState.Missing(PublicRecipeState.Missing.Why.Offline), onAction = {})
}

/** What becomes public, asked before anything is. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun RecipeShareConsentPreview() = FoodsPreviewFrame {
    RecipeShareSheetContent(RecipeFixtures.consentState(), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun RecipeShareConsentNamedDarkPreview() = FoodsPreviewFrame(dark = true) {
    RecipeShareSheetContent(RecipeFixtures.consentState(showName = true), onAction = {}, onClose = {})
}

/** No name of their own: no switch, and it says an Opson user. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun RecipeShareConsentNoNamePreview() = FoodsPreviewFrame {
    RecipeShareSheetContent(RecipeFixtures.consentState(displayName = null), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipeShareLinkPreview() = FoodsPreviewFrame {
    RecipeShareSheetContent(RecipeFixtures.sharedState(), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun RecipeShareLinkDarkPreview() = FoodsPreviewFrame(dark = true) {
    RecipeShareSheetContent(RecipeFixtures.sharedState(hidden = true), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun RecipeLogPreview() = FoodsPreviewFrame {
    LogRecipeSheetContent(RecipeFixtures.logState(full), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun RecipeLogBlindPreview() = FoodsPreviewFrame {
    LogRecipeSheetContent(RecipeFixtures.logState(blind), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun RecipeReportPreview() = FoodsPreviewFrame {
    ReportRecipeSheetContent(ReportRecipeState(name = RecipeFixtures.oats.name, reason = RecipeReportReason.Unsafe), onAction = {}, onClose = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecipeScanPreview() = FoodsPreviewFrame {
    ScanRecipeScreen(
        ScanRecipeState(permission = CameraPermission.Granted, notRecipe = true),
        onAction = {},
        viewfinder = { modifier -> Box(modifier.background(Color.DarkGray)) },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecipeScanAskPreview() = FoodsPreviewFrame {
    ScanRecipeScreen(ScanRecipeState(permission = CameraPermission.NotAsked), onAction = {}, viewfinder = {})
}
