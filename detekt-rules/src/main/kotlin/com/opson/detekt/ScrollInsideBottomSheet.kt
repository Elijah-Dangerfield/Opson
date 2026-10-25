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
 * Flags a hand-rolled scroll container inside a `BottomSheet` / `BasicBottomSheet` content lambda.
 *
 * Material3 derives the sheet's Expanded anchor from the sheet's *measured* height, recomputes the
 * anchors on every measure pass, and snaps to the recomputed target whenever the new anchors differ
 * from the old. Content that decides its own height can be re-measured mid-drag and yanked back to
 * Expanded: dragging down to close jumps, and near the top the sheet bounces indefinitely without
 * ever closing. It only bites once the content is tall enough to be clamped against the available
 * height, so a hand-rolled scroll can sit in the codebase for a year and then break on the first
 * long sheet.
 *
 * `BottomSheet(scrollableContent = true)` owns the scroll *and* pins the sheet to the full height,
 * so every recompute produces identical anchors and there is nothing to snap to.
 *
 * Matched by name, with no type resolution: the callee must be exactly `BottomSheet`, and the
 * scroll call must sit inside one of its lambda arguments. Two deliberate omissions — Material's
 * own `ModalBottomSheet`, which is what the design-system wrapper is built on top of, and
 * `BasicBottomSheet`, whose sticky top and bottom regions mean its middle *is* meant to scroll on
 * its own and where `scrollableContent` would therefore be the wrong advice.
 */
class ScrollInsideBottomSheet(config: Config) : Rule(
    config,
    "A scroll container inside a bottom sheet's content lets the content decide the sheet's " +
        "measured height, which re-derives the sheet anchors mid-drag; pass " +
        "`scrollableContent = true` and let the sheet own the scroll instead.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee !in SCROLL_MODIFIERS) return
        val sheet = expression.enclosingSheetCall() ?: return
        report(
            Finding(
                Entity.from(expression),
                "`$callee(...)` inside a `$sheet { }` content lambda makes the sheet's height " +
                    "depend on its content, so Material3 re-derives the Expanded anchor on every " +
                    "measure pass and snaps the sheet back mid-drag. Pass " +
                    "`scrollableContent = true` to `$sheet` and drop the scrolling wrapper.",
            ),
        )
    }

    /**
     * The name of the bottom sheet composable whose lambda argument this call sits in, or null.
     *
     * Only lambdas that are *arguments* of the sheet call count. A `BottomSheet` mentioned further
     * up the file, or one nested inside this call's own arguments, is not an enclosing lambda.
     */
    private fun KtCallExpression.enclosingSheetCall(): String? = parents
        .filterIsInstance<KtLambdaExpression>()
        .mapNotNull { it.owningCall()?.calleeExpression?.text }
        .firstOrNull { it in SHEET_COMPOSABLES }

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
    }
}
