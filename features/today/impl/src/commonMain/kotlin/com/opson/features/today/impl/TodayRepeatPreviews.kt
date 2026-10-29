package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// release.repeats on a food's sheet. Its own file so the Repeating work stays apart from Today's previews.

/** The peanut butter's ⋮ with Repeat this above Delete. */
@Preview(widthDp = 390)
@Composable
private fun FoodActionsRepeatPreview() = PreviewContent {
    val day = TodayFixtures.day()
    val state = ItemSheetStateBuilder.build(
        TodayFixtures.peanutButter,
        day,
        TodayFixtures.today,
        previewVisibilityPolicy(PresetId.Full),
        actionsOn = true,
        repeatsOn = true,
    )
    if (state != null) ItemSheetContent(state, onAction = {})
}

/** A food a repeat logged: Edit repeat in place of Repeat this. */
@Preview(widthDp = 390)
@Composable
private fun FoodActionsEditRepeatPreview() = PreviewContent(darkTheme = true) {
    val fixture = TodayFixtures.day()
    val day = fixture.copy(
        itemsBySlot = fixture.itemsBySlot.mapValues { (_, items) ->
            items.map { if (it.id == TodayFixtures.peanutButter) it.copy(repeatRuleId = "repeat-peanut-butter") else it }
        },
    )
    val state = ItemSheetStateBuilder.build(
        TodayFixtures.peanutButter,
        day,
        TodayFixtures.today,
        previewVisibilityPolicy(PresetId.Full),
        actionsOn = true,
        repeatsOn = true,
    )
    if (state != null) ItemSheetContent(state, onAction = {})
}
