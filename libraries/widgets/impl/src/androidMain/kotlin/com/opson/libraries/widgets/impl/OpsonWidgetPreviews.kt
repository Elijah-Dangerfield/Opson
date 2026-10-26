package com.opson.libraries.widgets.impl

import androidx.compose.runtime.Composable
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.widgets.WidgetCopy
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotBuilder

/*
 * Glance previews of the real widget, from the same builder and fixtures as WidgetCard's Compose
 * previews (which are the design-sync screenshots). Glance can't read Compose resources, so the
 * copy here is the English the app would resolve.
 */

private val previewCopy = WidgetCopy(
    today = "Today",
    log = "Log food",
    noteLog = "Tap + to log",
    noteUntracked = "Not tracking today",
    meals = mapOf(
        MealSlot.Breakfast to WidgetCopy.MealCopy("Breakfast", "Breakfast, done", "Breakfast, not yet"),
        MealSlot.Lunch to WidgetCopy.MealCopy("Lunch", "Lunch, done", "Lunch, not yet"),
        MealSlot.Snack to WidgetCopy.MealCopy("Snack", "Snack, done", "Snack, not yet"),
        MealSlot.Dinner to WidgetCopy.MealCopy("Dinner", "Dinner, done", "Dinner, not yet"),
    ),
)

private fun previewSnapshot(preset: PresetId): WidgetSnapshot = WidgetSnapshotBuilder.build(
    WidgetFixtures.inputs(NumbersSettings(preset = preset)),
    WidgetFixtures.policy(preset),
    previewCopy,
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun OpsonWidgetFullPreview() {
    OpsonWidgetContent(previewSnapshot(PresetId.Full), WidgetFixtures.today, appName = "Opson", fallback = "Open Opson to start.")
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun OpsonWidgetBlindPreview() {
    OpsonWidgetContent(previewSnapshot(PresetId.Blind), WidgetFixtures.today, appName = "Opson", fallback = "Open Opson to start.")
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
private fun OpsonWidgetSmallFullPreview() {
    OpsonWidgetContent(previewSnapshot(PresetId.Full), WidgetFixtures.today, appName = "Opson", fallback = "Open Opson to start.")
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
private fun OpsonWidgetSmallBlindPreview() {
    OpsonWidgetContent(previewSnapshot(PresetId.Blind), WidgetFixtures.today, appName = "Opson", fallback = "Open Opson to start.")
}

/** Before the app has written anything, or after release.widgets went off. */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 110)
@Composable
private fun OpsonWidgetNoSnapshotPreview() {
    OpsonWidgetContent(snapshot = null, today = WidgetFixtures.today, appName = "Opson", fallback = "Open Opson to start.")
}
