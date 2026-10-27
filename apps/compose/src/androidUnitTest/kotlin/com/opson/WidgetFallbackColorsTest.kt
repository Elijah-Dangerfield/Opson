package com.opson

import com.opson.libraries.widgets.WidgetPalette
import com.opson.libraries.widgets.WidgetSwatch
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS widget draws `WidgetColors.fallback` until the app writes a snapshot: the placeholder,
 * a widget added before sign-in, and after sign-out. It is a hand copy of the app's theme in
 * Swift, so it drifted once: the app moved to Slate and the widget kept drawing the old teal Pine.
 */
class WidgetFallbackColorsTest {

    @Test
    fun theSwiftFallbackIsTheAppsOwnTheme() {
        val swift = File("../ios/OpsonWidgets/TodayWidgetView.swift").readText()
        val fallback = swift.substringAfter("static let fallback = WidgetColors(").substringBefore("\n    )\n")

        assertEquals(WidgetPalette.colors.light.tokens(), swiftSwatch(fallback, "light"))
        assertEquals(WidgetPalette.colors.dark.tokens(), swiftSwatch(fallback, "dark"))
    }

    private fun WidgetSwatch.tokens() = mapOf(
        "background" to background, "ink" to ink, "muted" to muted, "track" to track,
        "bar" to bar, "accent" to accent, "onAccent" to onAccent,
    )

    private fun swiftSwatch(fallback: String, scheme: String): Map<String, String> {
        val block = fallback.substringAfter("$scheme: WidgetSwatch(").substringBefore(")")
        return Regex("""(\w+): "(#[0-9A-F]{6})"""").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
