package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Flags a hand-rolled scroll container inside a `BottomSheet` content lambda.
 *
 * Material3 derives the sheet's Expanded anchor from the sheet's *measured* height, recomputes the
 * anchors on every measure pass, and snaps to the recomputed target whenever the new anchors differ
 * from the old. A drag that starts on scrolling content moves the sheet through nested scroll,
 * without the drag lock, so the snap wins every frame: dragging down to close does nothing, and
 * once released the sheet bounces indefinitely without ever closing. What changes the height
 * mid-drag is Material's top padding, the status bar minus the sheet's offset, for a sheet whose
 * top rests inside the status bar, which only tall content reaches.
 *
 * `BottomSheet` now takes that away for every shape: it applies the insets inside a pinned
 * height, and stops a sheet nothing pins at the status bar (see its KDoc and
 * `BottomSheetDragTest`). So this rule is no longer the only thing between a sheet and the bug,
 * which matters because it reads one file and cannot see a scroll inside a child composable. It
 * stays because what it asks for is still the right shape: a sheet that owns its scroll
 * (`scrollableContent = true`) or pins its height (`heightFraction`) has one height that does
 * not depend on its content, and a content-sized sheet that grows past the status bar is cut off
 * rather than scrolled.
 *
 * A lazy list (`LazyColumn`, `LazyVerticalGrid`, ...) is the right tool once the sheet passes
 * `heightFraction`, so the list owns the scroll between a pinned header and footer (the logger).
 * Without `heightFraction` it is reported, and `scrollableContent = true` is not the fix there: a
 * lazy list inside the sheet's own `verticalScroll` is measured with infinite height and throws.
 *
 * Matched by name, with no type resolution: the callee must be exactly `BottomSheet`, and the
 * scroll call must sit inside one of its lambda arguments. Material's own `ModalBottomSheet` is
 * left out on purpose: it is what the design-system wrapper is built on.
 */
class ScrollInsideBottomSheet(config: Config) : Rule(
    config,
    "A scroll container inside a bottom sheet's content lets the content decide the sheet's " +
        "height; pass `scrollableContent = true` and let the sheet own the scroll instead.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee !in SCROLL_MODIFIERS && callee !in LAZY_LISTS) return
        val sheet = expression.enclosingSheetCall() ?: return
        val name = sheet.calleeExpression?.text
        if (callee in LAZY_LISTS) {
            if (sheet.pinsHeight()) return
            report(
                Finding(
                    Entity.from(expression),
                    "`$callee` inside a `$name { }` without `heightFraction` measures to its items, so " +
                        "the sheet's height depends on its content. Pass `heightFraction` (the logger " +
                        "uses `BottomSheetDefaults.FullHeightFraction`) " +
                        "so the height comes from the constraints. Not `scrollableContent = true`: a " +
                        "lazy list inside that scroll is measured with infinite height and throws.",
                ),
            )
            return
        }
        report(
            Finding(
                Entity.from(expression),
                "`$callee(...)` inside a `$name { }` content lambda makes the sheet's height " +
                    "depend on its content. Pass `scrollableContent = true` to `$name` and drop " +
                    "the scrolling wrapper.",
            ),
        )
    }

    /**
     * The bottom sheet call whose lambda argument this call sits in, or null.
     *
     * Only lambdas that are *arguments* of the sheet call count. A `BottomSheet` mentioned further
     * up the file, or one nested inside this call's own arguments, is not an enclosing lambda.
     */
    private fun KtCallExpression.enclosingSheetCall(): KtCallExpression? = parents
        .filterIsInstance<KtLambdaExpression>()
        .mapNotNull { it.owningCall() }
        .firstOrNull { it.calleeExpression?.text in SHEET_COMPOSABLES }

    /** Whether the sheet passes a non-null `heightFraction`. By name, which is how every caller passes it. */
    private fun KtCallExpression.pinsHeight(): Boolean = valueArguments.any { argument ->
        argument.getArgumentName()?.asName?.asString() == "heightFraction" &&
            argument.getArgumentExpression()?.text != "null"
    }

    /**
     * The call this lambda is an argument of. Both shapes reach one: a trailing lambda, whose
     * `KtLambdaArgument` hangs off the call directly, and a named one (`content = { }`), which is
     * wrapped in the call's argument list first.
     */
    private fun KtLambdaExpression.owningCall(): KtCallExpression? {
        val argument = parent as? KtValueArgument ?: return null
        return argument.parent as? KtCallExpression
            ?: argument.parent?.parent as? KtCallExpression
    }

    private companion object {
        val SHEET_COMPOSABLES = setOf("BottomSheet")

        val SCROLL_MODIFIERS = setOf(
            "verticalScroll",
            "verticalScrollWithBar",
        )

        // Horizontal lazy lists scroll across, not down, and never decide the sheet's height.
        val LAZY_LISTS = setOf(
            "LazyColumn",
            "LazyVerticalGrid",
            "LazyVerticalStaggeredGrid",
        )
    }
}
