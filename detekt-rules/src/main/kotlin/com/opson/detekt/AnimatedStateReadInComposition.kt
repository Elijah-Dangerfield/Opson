package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * > **Needs detekt `2.0.0-alpha.6` or later.** On `2.0.0-alpha.5` a rule of this
 * > shape can be registered in [OpsonRuleSetProvider], configured active,
 * > and compiled into the ruleset jar, and detekt will simply never dispatch to
 * > it. The build passes and detekt reports success the whole time, which is
 * > exactly what a working rule that finds nothing looks like. Downstream, rule
 * > order, config-cache staleness, jar freshness, YAML shape and the baseline
 * > were each ruled out first; the version alone was the cause, and `alpha.6`
 * > made it dispatch with no change to the logic below.
 * >
 * > It then found **19 violations on its first real run, seven of them in files
 * > that had just been swept by hand for this exact pattern.**
 * >
 * > The lesson generalises: if a custom detekt rule appears to do nothing,
 * > suspect the detekt version before the rule, and verify by making it report
 * > unconditionally rather than by trusting a clean run.
 *
 * Flags animated state read during composition, in the two spellings that reach
 * it: `val x by animateFloatAsState(...)`, and a plain `x.value` read of an
 * animation held as a `State` or an `Animatable`.
 *
 * The `by` delegate unwraps the `State<T>` **during composition**, which
 * subscribes the enclosing composable to a value that changes every animation
 * frame. Everything in that scope then recomposes at 60fps for as long as the
 * animation runs, whether or not anything it draws actually changed.
 *
 * This is not theoretical. Downstream it cost four production ANRs: a row
 * component read a pulsing alpha this way, so the text it contained recomposed
 * 471 times in a 25-second trace. Rebuilding that text every frame thrashed
 * Skia's glyph cache and wedged the RenderThread hard enough that anything else
 * needing it — opening a dialog, closing one, drawing an ordinary frame — hung
 * past the ANR threshold. Fixing it dropped that component from 471
 * recompositions to 6.
 *
 * It also hid a second instance of itself: a neighbouring component had the same
 * bug in three places, invisible until the first was fixed. That is what this
 * rule is for — the instance nobody has found yet.
 *
 * **The fix** is to keep the `State` and read it where it is cheapest:
 *
 * ```kotlin
 * // Recomposes every frame:
 * val alpha by animateFloatAsState(target)
 * Modifier.graphicsLayer { this.alpha = alpha }
 *
 * // Recomposes never; the draw phase alone invalidates:
 * val alpha = animateFloatAsState(target)
 * Modifier.graphicsLayer { this.alpha = alpha.value }
 * ```
 *
 * **When composition genuinely needs the value** — usually because it decides
 * which composable to emit — derive the narrower thing it actually needs, so
 * you recompose on that instead of on every frame:
 *
 * ```kotlin
 * val showingFace by remember { derivedStateOf { rotation.value <= 90f } }
 * ```
 *
 * If neither applies, `@Suppress("AnimatedStateReadInComposition")` on the
 * property — not the function, so a later animation in the same composable is
 * still caught — with a comment saying why.
 *
 * Before reaching for the suppression, check whether the modifier you are
 * feeding has a `graphicsLayer` equivalent. `Modifier.shadow` looks like the
 * classic unavoidable case, because it takes elevation as a plain argument and
 * has no lambda form — but it is itself only a graphicsLayer setting
 * `shadowElevation`, `shape` and `clip`, so writing that lambda directly defers
 * the read to the draw phase and needs no suppression. `Modifier.scale`,
 * `Modifier.alpha` and `Modifier.rotate` are the same. The genuinely
 * unavoidable case is a value composition needs in order to decide *what to
 * emit*, and `derivedStateOf` usually narrows that.
 *
 * A per-frame recomposition that is genuinely required is fine; one nobody
 * noticed is what causes ANRs.
 *
 * ### The second spelling, and why matching `by` alone was not enough
 *
 * `by` is the readable version of the bug and not the common one. The shape
 * that hid from this rule in three separate files downstream was the early
 * return:
 *
 * ```kotlin
 * val progress = remember { Animatable(0f) }
 * if (progress.value <= 0f) return
 * ```
 *
 * No delegate, so nothing to match, and it *looks* like the fix — the `State`
 * is kept, and the draw lambdas below it read `.value` exactly as they should.
 * The read on the gate line is the whole composable's subscription to a value
 * that moves every frame, and one of those instances got there by following
 * this rule's own advice to drop the `by` and then reading the result in
 * composition anyway.
 *
 * So the second pass tracks locals in a `@Composable` that hold an animation —
 * a kept `State` from [ANIMATION_PRODUCERS], or a `remember { Animatable(…) }` —
 * and reports `.value` reads of them that are not inside a lambda belonging to
 * [PHASE_DEFERRED], which is the draw, layout, effect and derivation callees
 * where reading is the point. Names only, with no type resolution: a local that
 * is not visibly built from an animation in the same function is invisible
 * here, which is the price of not reporting every `.value` in the codebase.
 */
class AnimatedStateReadInComposition(config: Config) : Rule(
    config,
    "Animated state unwrapped with `by` during composition recomposes its scope every frame; " +
        "keep the State and read it in a draw/layout lambda instead.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee !in ANIMATION_PRODUCERS) return
        // Only the `by` form is a problem. `val x = animateFloatAsState(...)`
        // keeps the State and is exactly the fix this rule asks for.
        val property = expression.delegatedProperty() ?: return
        report(
            Finding(
                Entity.from(expression),
                "`${property.name} by $callee(...)` unwraps animated state during composition, so " +
                    "everything in this scope recomposes on every animation frame. Drop the `by`, " +
                    "keep the State, and read `.value` inside the graphicsLayer/drawBehind lambda " +
                    "that uses it. If composition truly needs it (it picks which composable to " +
                    "emit), wrap the narrower condition in `derivedStateOf`.",
            ),
        )
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.annotationEntries.none { it.shortName?.asString() == COMPOSABLE }) return
        val animated = function.animatedLocals()
        if (animated.isEmpty()) return

        function.bodyExpression?.forEachDescendantOfType<KtDotQualifiedExpression> { read ->
            if (read.selectorExpression?.text != VALUE) return@forEachDescendantOfType
            val name = (read.receiverExpression as? KtNameReferenceExpression)
                ?.getReferencedName()
                ?.takeIf { it in animated }
                ?: return@forEachDescendantOfType
            if (read.isPhaseDeferredWithin(function)) return@forEachDescendantOfType
            report(
                Finding(
                    Entity.from(read),
                    "`$name.$VALUE` is read during composition, so everything in `${function.name}` " +
                        "recomposes on every animation frame — including the whole subtree when the " +
                        "read is a gate that decides what to emit. Move the read into the " +
                        "graphicsLayer/drawBehind/offset lambda that uses it, or wrap the narrower " +
                        "condition composition actually needs in " +
                        "`remember { derivedStateOf { $name.$VALUE … } }`.",
                ),
            )
        }
    }

    /**
     * Names of locals in this function that hold something animated: a `State`
     * kept from an `animate*AsState` call, or an `Animatable` built inside a
     * `remember`.
     *
     * Delegated properties are absent by construction — they have no initializer
     * — which is what keeps the `by` form from being reported twice.
     */
    private fun KtNamedFunction.animatedLocals(): Set<String> =
        collectDescendantsOfType<KtProperty>()
            .filter { property ->
                property.initializer?.anyDescendantOfType<KtCallExpression> { call ->
                    call.calleeExpression?.text in ANIMATION_HOLDERS
                } == true
            }
            .mapNotNull { it.name }
            .toSet()

    /**
     * Whether this read sits inside a lambda that runs somewhere other than
     * composition.
     *
     * Every lambda between the read and the function is checked rather than only
     * the innermost, so a `drawBehind { lit.forEach { … progress.value … } }`
     * is still recognised as a draw-phase read.
     */
    private fun KtDotQualifiedExpression.isPhaseDeferredWithin(function: KtNamedFunction): Boolean =
        parents
            .takeWhile { it != function }
            .filterIsInstance<KtLambdaExpression>()
            .any { it.owningCallee() in PHASE_DEFERRED || it.isBareReadOf(this) }

    /**
     * Whether this lambda exists only to hand the read on unevaluated —
     * `SplashContent(alpha = { fade.value })`, against an `alpha: () -> Float`.
     *
     * The callee is somebody's own composable, so there is no name to allow-list
     * and no type resolution to say whether the parameter is `() -> Float` or
     * `@Composable () -> Unit`. The shape decides instead: a lambda whose whole
     * body is the read cannot be emitting content, because a bare `State` value
     * is not a composable, and passing a read on as a lambda is the idiom for
     * deferring it. One statement, and it is this one.
     */
    private fun KtLambdaExpression.isBareReadOf(read: KtDotQualifiedExpression): Boolean =
        bodyExpression?.statements?.singleOrNull() == read

    /** The name of the function this lambda was passed to, trailing or not. */
    private fun KtLambdaExpression.owningCallee(): String? {
        val call = when (val parent = parent) {
            is KtLambdaArgument -> parent.parent as? KtCallExpression
            is KtValueArgument -> parent.parent?.parent as? KtCallExpression
            else -> null
        } ?: return null
        return call.calleeExpression?.text
    }

    /**
     * The property this call is the `by` delegate of, or null when it isn't one.
     *
     * Detected from the call rather than by overriding `visitProperty`, which
     * detekt does not dispatch to rules here — [VerifyStrings] uses the same
     * call-expression entry point. Two shapes reach a delegate: the bare call
     * (`by animateFloatAsState(...)`), whose parent is the delegate directly,
     * and the receiver call (`by transition.animateFloat(...)`), which is
     * wrapped in a dot-qualified expression first.
     */
    private fun KtCallExpression.delegatedProperty(): KtProperty? {
        val delegate = when (val parent = parent) {
            is KtPropertyDelegate -> parent
            is KtDotQualifiedExpression -> parent.parent as? KtPropertyDelegate
            else -> null
        } ?: return null
        return delegate.parent as? KtProperty
    }

    private companion object {
        /**
         * Everything in `androidx.compose.animation.core` that returns a
         * `State<T>`. Matched by name because this rule runs without type
         * resolution; a same-named function that isn't an animation is a false
         * positive worth the coverage, and is suppressible.
         */
        val ANIMATION_PRODUCERS = setOf(
            // animate*AsState
            "animateFloatAsState",
            "animateIntAsState",
            "animateDpAsState",
            "animateColorAsState",
            "animateSizeAsState",
            "animateOffsetAsState",
            "animateRectAsState",
            "animateIntOffsetAsState",
            "animateIntSizeAsState",
            "animateValueAsState",
            // Transition / InfiniteTransition members
            "animateFloat",
            "animateInt",
            "animateDp",
            "animateColor",
            "animateSize",
            "animateOffset",
            "animateRect",
            "animateIntOffset",
            "animateIntSize",
            "animateValue",
            // This project's own wrappers that also return State<T>. A wrapper
            // is the easiest place for this bug to hide, because it doesn't
            // look like an androidx animation call at the site that uses it —
            // add yours here as you write them.
            "animateColorResourceAsState",
            "rememberLoopingFloat",
        )

        /**
         * What a local has to be built from before its `.value` is worth
         * reporting. The producers above, plus `Animatable`, which is the
         * hand-driven form and the one the early-return gates were written
         * against.
         *
         * `mutableStateOf` is deliberately absent. Reading ordinary state in
         * composition is how Compose works; it is only a bug when the thing
         * behind it moves sixty times a second.
         */
        val ANIMATION_HOLDERS = ANIMATION_PRODUCERS + "Animatable"

        /**
         * Lambdas whose body is not composition, so a read inside one is the
         * fix rather than the bug.
         *
         * The draw and layout ones are where the value belongs. The effects and
         * `pointerInput` run in a coroutine, `derivedStateOf` is the narrowing
         * this rule recommends, and `remember` is the block that built the
         * thing in the first place.
         *
         * Matched by callee name with no receiver check, so a project function
         * that happens to be called `layout` or `offset` also exempts its
         * lambda. That is the direction to err in: a rule that reports the
         * correct code gets a blanket suppression and then catches nothing.
         */
        val PHASE_DEFERRED = setOf(
            "graphicsLayer",
            "drawBehind",
            "Canvas",
            "drawWithContent",
            "drawWithCache",
            "onDrawBehind",
            "onDrawWithContent",
            "offset",
            "absoluteOffset",
            "layout",
            "pointerInput",
            "derivedStateOf",
            "remember",
            "snapshotFlow",
            "produceState",
            "LaunchedEffect",
            "DisposableEffect",
            "SideEffect",
        )

        const val COMPOSABLE = "Composable"

        const val VALUE = "value"
    }
}
