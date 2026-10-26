package com.opson.leaktest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.PreviewAppState
import com.opson.libraries.ui.system.LocalAppState
import com.opson.libraries.ui.system.LocalBuildInfo
import com.opson.libraries.ui.system.LocalClock
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import kotlin.time.Clock

/**
 * The handoff's `LEAK`: a number followed by a calorie unit, or a number followed by "left". The
 * prototype scans `textContent` plus every input's value, so a number split across nodes ("190"
 * then "kcal") still matches once the text is joined.
 */
internal val LEAK = Regex("""\d[\d,.]*\s*(kcal|cal\b|calories)|\d[\d,]*\s*left\b""", RegexOption.IGNORE_CASE)

/** Long enough for enter transitions and short delays, and the same every run. */
private const val SETTLE_MILLIS = 1_000L

/**
 * Renders [surface] in [policy] the way the app would: Pine, light, normal text, the policy
 * provided at the root. `LocalInspectionMode` is on because that is what freezes loops and lays
 * sheets out expanded. `PreviewContextConfigurationEffect` hands Compose resources the Android
 * context the way a preview does; today's surfaces resolve their strings without it, but a
 * resource read that needs it would otherwise throw here rather than render.
 */
@OptIn(ExperimentalResourceApi::class)
internal fun ComposeContentTestRule.render(surface: LeakSurface, policy: VisibilityPolicy) {
    // Held clock, as in PreviewScreenshotTest: an animation that ignores LocalInspectionMode
    // would otherwise keep the rule waiting for an idle that never comes.
    mainClock.autoAdvance = false
    setContent {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAppState provides PreviewAppState,
            LocalBuildInfo provides BuildInfo,
            LocalClock provides Clock.fixed(NOW),
            LocalVisibilityPolicy provides policy,
        ) {
            PreviewContextConfigurationEffect()
            AppTheme(darkTheme = false) {
                Page { surface.content(policy) }
            }
        }
    }
    mainClock.advanceTimeBy(SETTLE_MILLIS)
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() }
}

/** A node tagged with one of [CALORIE_METRICS], and the text it shows. */
internal data class Tagged(val metric: Metric, val text: String) {
    override fun toString() = "${metric.testTag()} showing \"$text\""
}

/** A [LEAK] match in the joined text, and the nodes it spans. */
internal data class Hit(val value: String, val nodes: List<String>) {
    override fun toString() = "\"$value\" in ${nodes.joinToString(" + ") { "\"$it\"" }}"
}

internal class LeakScan(val tagged: List<Tagged>, val hits: List<Hit>) {
    val isEmpty: Boolean get() = tagged.isEmpty() && hits.isEmpty()
}

/**
 * Scans everything composed, every window included, on the unmerged tree so a tag on a child
 * inside a merged row is still seen.
 */
internal fun ComposeContentTestRule.scan(): LeakScan {
    val tagged = CALORIE_METRICS.flatMap { metric ->
        onAllNodes(hasTestTag(metric.testTag()), useUnmergedTree = true).fetchSemanticsNodes()
            .map { Tagged(metric, it.texts().joinToString(" ")) }
    }

    val segments = segments()
    val joined = StringBuilder()
    val starts = segments.map { text ->
        if (joined.isNotEmpty()) joined.append(' ')
        joined.length.also { joined.append(text) }
    }
    val hits = LEAK.findAll(joined).map { match ->
        val spanned = segments.indices.filter { i ->
            starts[i] <= match.range.last && starts[i] + segments[i].length > match.range.first
        }
        Hit(match.value, spanned.map { segments[it] })
    }.toList()
    return LeakScan(tagged, hits)
}

/**
 * Text, editable text and content descriptions in tree order, every window included, so
 * "Energy" "190" "kcal" in sibling nodes reads "Energy 190 kcal" once joined, as it does on screen.
 */
internal fun ComposeContentTestRule.segments(): List<String> =
    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        .flatMap { root -> root.depthFirst() }
        .flatMap { node -> node.texts() }

private fun SemanticsNode.depthFirst(): List<SemanticsNode> = listOf(this) + children.flatMap { it.depthFirst() }

private fun SemanticsNode.texts(): List<String> = buildList {
    config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
    config.getOrNull(SemanticsProperties.EditableText)?.let { add(it.text) }
    config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
}.filter { it.isNotBlank() }
