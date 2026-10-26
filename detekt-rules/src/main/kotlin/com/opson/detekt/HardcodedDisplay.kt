package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * Flags a resolved nutrition value built by hand: `Display.Shown(...)`, `Display.Soft(...)`,
 * `RingValue(...)` or `MacroPart(...)` outside `:libraries:nutrition`.
 *
 * Those four are what `VisibilityPolicy` returns once it has decided whether a number may be
 * seen. Constructing one anywhere else skips that decision. In a screen it is a number shown to
 * someone who chose Blind. In a leak-test fixture it is worse, because it fails the test for the
 * wrong reason or, with a hand-built `Display.Hidden`, passes it for the wrong one: either way
 * `BlindModeLeakTest` stops testing the real builder (`docs/plans/screens/blind-leak-test.md`).
 * `Display.Hidden` is an object, never "constructed", and is not reported.
 *
 * Scope is set in `detekt.yml`, not here: `:libraries:nutrition` itself is excluded (it is the
 * one place these are meant to be built), and so are test source sets, which may build expected
 * values to compare against.
 *
 * Matched by name, without type resolution: the qualified forms (`Display.Shown(`, with or
 * without the package), and the bare forms when the file imports them (`Shown(` after
 * `import com.opson.libraries.nutrition.Display.Shown` or `Display.*`). `RingValue` and
 * `MacroPart` are unique names in this codebase, so their bare calls are always reported. A
 * `.copy(text = ...)` on a resolved value is the same mistake and is not caught; that needs types.
 */
class HardcodedDisplay(config: Config) : Rule(
    config,
    "A Display, RingValue or MacroPart built by hand skips VisibilityPolicy, so it can show a " +
        "number the user chose not to see. Get it from the policy instead.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeExpression?.text ?: return
        val reported = when (name) {
            in ALWAYS -> "`$name(...)`"
            in DISPLAY_MEMBERS -> when {
                expression.isQualifiedBy("Display") -> "`Display.$name(...)`"
                expression.parent !is KtDotQualifiedExpression &&
                    expression.containingKtFile.importsDisplayMember(name) -> "`$name(...)`"
                else -> null
            }
            else -> null
        } ?: return
        report(
            Finding(
                Entity.from(expression),
                "$reported builds a resolved nutrition value by hand, outside `:libraries:nutrition`. " +
                    "Get it from `VisibilityPolicy` (`resolve`, `ringValue`, `macroLine`, `resolveRange`, " +
                    "`share`), so the user's mode decides whether it is seen. In a fixture, build the " +
                    "screen's inputs and run them through its real state builder.",
            ),
        )
    }

    /** `Display.Shown(...)` or `com.opson.libraries.nutrition.Display.Shown(...)`. */
    private fun KtCallExpression.isQualifiedBy(receiver: String): Boolean {
        val qualified = parent as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression != this) return false
        val text = qualified.receiverExpression.text
        return text == receiver || text.endsWith(".$receiver")
    }

    private fun KtFile.importsDisplayMember(name: String): Boolean = importDirectives.any { directive ->
        val path = directive.importPath?.pathStr
        path == "$DISPLAY.*" || path == "$DISPLAY.$name"
    }

    private companion object {
        const val DISPLAY = "com.opson.libraries.nutrition.Display"
        val DISPLAY_MEMBERS = setOf("Shown", "Soft")
        val ALWAYS = setOf("RingValue", "MacroPart")
    }
}
