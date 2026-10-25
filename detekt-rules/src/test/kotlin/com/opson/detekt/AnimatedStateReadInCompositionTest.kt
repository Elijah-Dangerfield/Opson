package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two spellings of one bug, and the line between them and the fix.
 *
 * The `by` half shipped first and found nineteen violations downstream. The
 * `.value` half is here because three files had the same bug written without a
 * delegate and the rule could not see any of them — one of them by following
 * this rule's own advice to drop the `by`, and then reading the kept `State` in
 * composition anyway.
 *
 * Most of this file is the negative cases. A `.value` read is the *fix* far more
 * often than it is the bug, so a rule that cannot tell a `drawBehind` from an
 * early return would report the whole design system and get baselined away.
 */
class AnimatedStateReadInCompositionTest {

    private val rule get() = AnimatedStateReadInComposition(Config.empty)

    @Test
    fun `reports the by delegate form`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Fading(visible: Boolean) {
                val alpha by animateFloatAsState(if (visible) 1f else 0f)
                Box(modifier = Modifier.graphicsLayer { this.alpha = alpha })
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports the by delegate form on a transition member`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Pulsing() {
                val transition = rememberInfiniteTransition()
                val scale by transition.animateFloat(0f, 1f, infiniteRepeatable(tween()))
                Box(modifier = Modifier.scale(scale))
            }
            """
        )

        assertEquals(1, findings.size)
    }

    /**
     * The second spelling, reduced to the line the downstream instances shared.
     * The gate reads cheap and is the most expensive read in the file: it
     * subscribes the whole composable, the content lambda included, to a value
     * that moves every frame.
     */
    @Test
    fun `reports the early-return gate on a remembered Animatable`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Scrim(open: Boolean) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f) }

                if (progress.value <= 0f) return

                Box(modifier = Modifier.graphicsLayer { alpha = progress.value })
            }
            """
        )

        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("derivedStateOf"),
            "The message has to name the narrowing, or the fix is a guess: " +
                findings.single().message,
        )
    }

    /**
     * The blind spot that made widening worth more than fixing one call site:
     * this is what the `by` half of the rule told the author to write.
     */
    @Test
    fun `reports the gate on a State kept from animateFloatAsState`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Drawer(open: Boolean) {
                val slide = animateFloatAsState(if (open) 1f else 0f)

                if (slide.value <= 0f) return

                Box(modifier = Modifier.graphicsLayer { alpha = slide.value })
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports an animated value read into emitted content`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Counter() {
                val progress = remember { Animatable(0f) }
                Column {
                    Text(text = progress.value.toString())
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows the draw and layout lambdas the fix asks for`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Fading() {
                val progress = remember { Animatable(0f) }
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = progress.value }
                        .offset { IntOffset(0, progress.value.toInt()) }
                        .drawBehind { drawRect(Color.Red, alpha = progress.value) },
                )
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The draw lambda is the outer one. Matching only the innermost lambda would
     * report a read that is already in the draw phase, which is most of the
     * design system's drawing components.
     */
    @Test
    fun `allows a read nested inside a lambda within a draw lambda`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Holes(rects: List<Rect>) {
                val progress = remember { Animatable(0f) }
                Box(
                    modifier = Modifier.drawWithContent {
                        rects.forEach { rect -> drawRect(Color.Red, alpha = progress.value) }
                        drawContent()
                    },
                )
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * A read handed on for someone else to evaluate. The callee is the project's
     * own composable, so there is no name to allow-list; what says the read is
     * deferred is that the lambda does nothing but hand it on.
     */
    @Test
    fun `allows a read handed to another composable as a lambda`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Splash() {
                val fade = remember { Animatable(1f) }
                SplashContent(screenAlpha = { fade.value })
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The other side of that line. `Column` is inline, so its lambda has no
     * recompose scope of its own and the read subscribes the whole composable —
     * and a lambda that emits content is doing more than handing a value on.
     */
    @Test
    fun `still reports a read inside a content lambda that does more than return it`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Splash() {
                val fade = remember { Animatable(1f) }
                Column {
                    Text(text = fade.value.toString())
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows the derivedStateOf narrowing it recommends`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Scrim() {
                val progress = remember { Animatable(0f) }
                val visible by remember { derivedStateOf { progress.value > 0f } }

                if (!visible) return

                Box(modifier = Modifier.graphicsLayer { alpha = progress.value })
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    @Test
    fun `allows a read inside an effect, which runs in a coroutine`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Shaking(nonce: Int) {
                val shake = remember { Animatable(0f) }
                LaunchedEffect(nonce) {
                    if (shake.value > 0f) shake.snapTo(0f)
                    shake.animateTo(1f)
                }
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The rule has no type resolution, so it only knows about locals it watched
     * being built. Everything else called `.value` in a composable — a
     * `MutableState`, a `rememberUpdatedState`, a lazy, a field on a parameter —
     * has to stay silent, which is the whole reason the match is on the
     * initializer rather than on the word `value`.
     */
    @Test
    fun `stays quiet on ordinary state read in composition`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(external: State<Int>) {
                val count = remember { mutableStateOf(0) }
                val latest = rememberUpdatedState(external)
                Text(text = (count.value + latest.value.value).toString())
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    @Test
    fun `stays quiet outside a composable`() {
        val findings = rule.findingsOn(
            """
            fun render(progress: Animatable<Float, AnimationVector1D>) {
                val local = remember { Animatable(0f) }
                println(local.value)
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }

    /**
     * The `by` form has no initializer, so the second pass cannot see it — which
     * is what keeps one bug from being reported twice with two different fixes.
     */
    @Test
    fun `reports a delegated animation once, not once per pass`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Fading(visible: Boolean) {
                val alpha by animateFloatAsState(if (visible) 1f else 0f)
                if (alpha <= 0f) return
                Box(modifier = Modifier.graphicsLayer { this.alpha = alpha })
            }
            """
        )

        assertEquals(1, findings.size)
    }

    /**
     * The limit worth pinning: the animation was built in another function and
     * handed over as a parameter, so nothing in this file says the `.value` is
     * animated. If this ever starts failing, the rule grew type resolution.
     */
    @Test
    fun `does not see an animation passed in as a parameter`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Inner(progress: Animatable<Float, AnimationVector1D>) {
                if (progress.value <= 0f) return
                Box(modifier = Modifier.graphicsLayer { alpha = progress.value })
            }
            """
        )

        assertTrue(findings.isEmpty(), "$findings")
    }
}
