@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.coroutines.delay
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.tip_no_more
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.seconds

/**
 * Which tip, if any, a screen visit shows. The app root provides the real one ([LocalCoachMarks]);
 * the default never shows a tip, so previews, screenshots and tests are never covered by one.
 * Hidden from the iOS header: Swift never uses it.
 */
@HiddenFromObjC
interface CoachMarks {
    /**
     * Of [candidates], the tip ids on screen now, the one to show this visit, or null for none.
     * The one picked counts as seen from here on: a tip shows once.
     */
    fun pick(candidates: Collection<String>): String?

    /** Whether the tip [pick] last chose offers "No more tips": the first few tips do. */
    fun offersOptOut(): Boolean = false

    /** "No more tips": tips stop on every screen, until they are turned back on in More. */
    fun optOut() = Unit

    companion object {
        val None: CoachMarks = object : CoachMarks {
            override fun pick(candidates: Collection<String>): String? = null
        }
    }
}

val LocalCoachMarks = staticCompositionLocalOf { CoachMarks.None }

/**
 * One visit to a screen: the tips its anchors offered, and the one showing. A screen's root makes
 * one with [CoachMarkScreen], so leaving and coming back is a new visit.
 */
@Stable
class CoachMarkVisit internal constructor() {
    /** Each tip id offered, and the first anchor that offered it: a tip on every food row shows on one. */
    internal val owners = mutableStateMapOf<String, Any>()
    internal var showing by mutableStateOf<String?>(null)

    /** The tip showing offers "No more tips". */
    internal var offersOptOut by mutableStateOf(false)

    /** Picked already, or put away: no other tip takes its place until the next visit. */
    internal var decided: Boolean = false

    internal fun finish() {
        showing = null
        decided = true
    }
}

private val LocalCoachMarkVisit = compositionLocalOf<CoachMarkVisit?> { null }

/**
 * A screen that may show one tip a visit. A moment after it settles it asks [LocalCoachMarks] to
 * pick among the anchors on it, so the tip that shows is the most useful one there rather than
 * whichever composed first. Nothing is asked in previews.
 */
@Composable
fun CoachMarkScreen(content: @Composable () -> Unit) {
    val visit = remember { CoachMarkVisit() }
    val marks = LocalCoachMarks.current
    val inspecting = LocalInspectionMode.current
    if (!inspecting) {
        LaunchedEffect(visit, marks) {
            delay(SETTLE)
            if (!visit.decided) {
                visit.decided = true
                visit.showing = marks.pick(visit.owners.keys.toList())
                visit.offersOptOut = visit.showing != null && marks.offersOptOut()
            }
        }
    }
    CompositionLocalProvider(LocalCoachMarkVisit provides visit, content = content)
}

/**
 * [content] with a tip, [id], that can point at it: offered to the screen's visit while it is on
 * screen, and drawn as a small bubble under it (or over it, near the bottom) if the visit picks
 * it. "Got it" puts it away; so does leaving, and it goes on its own after a few seconds. The
 * first few tips also offer "No more tips" ([CoachMarks.offersOptOut]). Outside a
 * [CoachMarkScreen] it is just [content].
 */
@Composable
fun CoachMarkAnchor(id: String, text: String, modifier: Modifier = Modifier, gotIt: String, content: @Composable () -> Unit) {
    val visit = LocalCoachMarkVisit.current
    if (visit == null) {
        Box(modifier) { content() }
        return
    }
    val marks = LocalCoachMarks.current
    val token = remember { Any() }
    DisposableEffect(visit, id) {
        if (id !in visit.owners) visit.owners[id] = token
        onDispose { if (visit.owners[id] === token) visit.owners.remove(id) }
    }
    Box(modifier) {
        content()
        if (visit.showing == id && visit.owners[id] === token) {
            LaunchedEffect(id) {
                delay(SHOWN_FOR)
                if (visit.showing == id) visit.finish()
            }
            Popup(
                popupPositionProvider = BelowOrAbove,
                onDismissRequest = { visit.finish() },
                properties = PopupProperties(focusable = false),
            ) {
                CoachMarkBubble(
                    text = text,
                    gotIt = gotIt,
                    onDismiss = { visit.finish() },
                    onOptOut = if (visit.offersOptOut) {
                        {
                            visit.finish()
                            marks.optOut()
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The tip itself: a short line and "Got it", on the ink colour so it reads as the app speaking.
 * With [onOptOut], "No more tips" sits beside "Got it".
 */
@Composable
fun CoachMarkBubble(text: String, gotIt: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, onOptOut: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .widthIn(max = 300.dp)
            .background(AppTheme.colors.ink, AppTheme.shapes.inputLarge)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.bg, modifier = Modifier.padding(end = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FlatButton(text = gotIt, onClick = onDismiss, tone = FlatButtonTone.AccentText, modifier = Modifier.padding(start = 0.dp))
            if (onOptOut != null) {
                FlatButton(text = stringResource(Res.string.tip_no_more), onClick = onOptOut, tone = FlatButtonTone.AccentText)
            }
        }
    }
}

/** Under the anchor, centred on it and kept on screen; over it when there is no room below. */
private object BelowOrAbove : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** Long enough for the screen's first layout and any entrance animation to finish. */
private val SETTLE = 1.5.seconds

/** A tip nobody answers goes on its own, so it never sits over the screen for long. */
private val SHOWN_FOR = 8.seconds

@Suppress("VerifyStrings")
@Preview(widthDp = 360)
@Composable
private fun CoachMarkBubblePreview() {
    PreviewLightDark {
        CoachMarkBubble(text = "Tap a number with the arrows to see it another way.", gotIt = "Got it", onDismiss = {})
    }
}

/** One of the first few tips, with the way to turn them off. */
@Suppress("VerifyStrings")
@Preview(widthDp = 360)
@Composable
private fun CoachMarkBubbleOptOutPreview() {
    PreviewLightDark {
        CoachMarkBubble(text = "Tap a number with the arrows to see it another way.", gotIt = "Got it", onDismiss = {}, onOptOut = {})
    }
}
