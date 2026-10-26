package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.FoodKind
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A food's tile. The catalog has no categories to pick an icon from, so every
 * food gets the fork and knife, tinted by a stable hash of [seed] (the food's
 * key or id) over the four nutrient tints. The same food is the same colour on
 * every screen and every launch, and neighbouring rows usually differ.
 */
@Composable
fun FoodIconTile(
    seed: String,
    modifier: Modifier = Modifier,
    size: IconTileSize = IconTileSize.Medium,
) {
    IconTile(icon = AppIcons.Restaurant, tone = foodTone(seed), modifier = modifier, size = size)
}

/**
 * A food's tile as design sync 2 draws it: a picture of the [kind] of food, tinted per kind
 * (grains and fruit carbs orange, greens and desserts fibre brown, dairy and drinks fat blue,
 * plates, fish and eggs protein pink). The tints only tell tiles apart; none means good or bad.
 */
@Composable
fun FoodIconTile(
    kind: FoodKind,
    modifier: Modifier = Modifier,
    size: IconTileSize = IconTileSize.Medium,
) {
    IconTile(icon = kind.icon, tone = kindTone(kind), modifier = modifier, size = size)
}

/** The glyph for a kind of food. */
val FoodKind.icon: IconResource
    get() = when (this) {
        FoodKind.Grain -> AppIcons.RiceBowl
        FoodKind.Salad -> AppIcons.Leaf
        FoodKind.Fruit -> AppIcons.Fruit
        FoodKind.Dessert, FoodKind.Dairy -> AppIcons.Icecream
        FoodKind.Fish -> AppIcons.SetMeal
        FoodKind.Egg -> AppIcons.Egg
        FoodKind.Bread -> AppIcons.Bread
        FoodKind.Soup -> AppIcons.SoupKitchen
        FoodKind.Drink -> AppIcons.LocalCafe
        FoodKind.Plate -> AppIcons.DinnerDining
    }

@Composable
private fun kindTone(kind: FoodKind): ToneColors {
    val nutrients = AppTheme.colors.nutrients
    return when (kind) {
        FoodKind.Grain, FoodKind.Fruit, FoodKind.Bread -> nutrients.carbs
        FoodKind.Salad, FoodKind.Dessert, FoodKind.Soup -> nutrients.fiber
        FoodKind.Dairy, FoodKind.Drink -> nutrients.fat
        FoodKind.Plate, FoodKind.Fish, FoodKind.Egg -> nutrients.protein
    }
}

/** The tint [FoodIconTile] uses for [seed], for anything drawn around it (the favorites' round tile). */
@Composable
fun foodTone(seed: String): ToneColors {
    val nutrients = AppTheme.colors.nutrients
    return when (FoodTint.indexOf(seed)) {
        0 -> nutrients.protein
        1 -> nutrients.carbs
        2 -> nutrients.fat
        else -> nutrients.fiber
    }
}

/** Pure so it can be tested: the same seed must land on the same tint on Android and iOS. */
internal object FoodTint {
    private const val TINTS = 4
    private const val PRIME = 31

    // Spelled out rather than String.hashCode(), which nothing promises is the same on every platform.
    fun indexOf(seed: String): Int {
        var hash = 0
        for (c in seed) hash = PRIME * hash + c.code
        return ((hash % TINTS) + TINTS) % TINTS
    }
}

@Preview
@Composable
private fun FoodIconTilePreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("f:banana", "f:rice", "f:yogurt", "c:granola", "f:adobo").forEach { FoodIconTile(seed = it) }
        }
    }
}

@Preview
@Composable
private fun FoodKindTilePreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodKind.entries.forEach { FoodIconTile(kind = it) }
        }
    }
}
