package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * The strays [VerifyStrings] lets through. That rule only sees a bare literal handed straight to
 * `Text`; this one catches the other shapes copy is written in:
 *
 * - an interpolated literal with words in it passed to `Text`: `Text("$count foods")`;
 * - a literal behind an `if`, `when` or `?:` passed to `Text`: `Text(if (on) "On" else "Off")`;
 * - a literal, plain or interpolated, passed to a named argument that is nearly always read by a
 *   person ([USER_FACING_ARGUMENTS]: `contentDescription`, `label`, `title`, ...) on any call,
 *   because a design-system wrapper names its copy that way and `Text` never sees the literal;
 * - a literal assigned to a semantics property: `semantics { contentDescription = "Close" }`.
 *
 * Allowed, as in [VerifyStrings]: glyphs and numbers with no letters in the literal part
 * (`"$a · $b"`, `"${n}%"`), anything inside an `@Preview` function, and anything under a
 * `@Suppress("VerifyStrings")`, which preview helpers already carry. Animation labels
 * (`animateFloatAsState(label = "FillBar")`) are a debugging name, not copy, and are skipped.
 *
 * A separate rule id from [VerifyStrings] so it can be scoped on its own in `detekt.yml` while the
 * codebase catches up to it.
 */
class VerifyStringArguments(config: Config) : Rule(
    config,
    "User-facing copy must come from string resources, not interpolated literals or literal labels.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee in ANIMATION_CALLEES || callee.startsWith("animate")) return
        val isText = callee in TEXT_CALLEES
        for (argument in expression.valueArguments) {
            val value = argument.getArgumentExpression() ?: continue
            val name = argument.getArgumentName()?.asName?.asString()
            when {
                isText && (name == null || name == "text") -> checkTextArgument(value, callee)
                name in USER_FACING_ARGUMENTS -> literalsIn(value).forEach { report(it, "$name = in $callee()") }
            }
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (expression.operationToken != KtTokens.EQ) return
        val target = (expression.left as? KtNameReferenceExpression)?.getReferencedName() ?: return
        if (target !in SEMANTICS_PROPERTIES) return
        val value = expression.right ?: return
        literalsIn(value).forEach { report(it, "semantics $target") }
    }

    /** A bare literal straight to `Text` is [VerifyStrings]' finding; everything else is this rule's. */
    private fun checkTextArgument(value: KtExpression, callee: String) {
        val direct = value as? KtStringTemplateExpression
        if (direct != null) {
            if (direct.isInterpolated() && direct.hasWords()) report(direct, "$callee()")
            return
        }
        literalsIn(value).forEach { report(it, "$callee()") }
    }

    /** The literals [expression] can evaluate to, looking through `if`, `when`, `?:` and parentheses. */
    private fun literalsIn(expression: KtExpression?): List<KtStringTemplateExpression> = when (expression) {
        null -> emptyList()
        is KtStringTemplateExpression -> listOf(expression).filter { it.hasWords() }
        is KtParenthesizedExpression -> literalsIn(expression.expression)
        is KtIfExpression -> literalsIn(expression.then) + literalsIn(expression.`else`)
        is KtWhenExpression -> expression.entries.flatMap { literalsIn(it.expression) }
        is KtBinaryExpression ->
            if (expression.operationToken == KtTokens.ELVIS) literalsIn(expression.left) + literalsIn(expression.right) else emptyList()
        else -> emptyList()
    }

    private fun report(template: KtStringTemplateExpression, where: String) {
        if (template.isExempt()) return
        report(
            Finding(
                Entity.from(template),
                "Inline user-facing string ${template.text} ($where). Put it in :libraries:resources and read it with " +
                    "stringResource(...) or getString(...), with positional args (%1\$s) for anything interpolated.",
            ),
        )
    }

    private fun KtStringTemplateExpression.isInterpolated(): Boolean =
        entries.any { it is KtStringTemplateEntryWithExpression }

    /** Letters in the literal part only: `"$count foods"` has words, `"$a · $b"` does not. */
    private fun KtStringTemplateExpression.hasWords(): Boolean =
        entries.filterIsInstance<KtLiteralStringTemplateEntry>().any { entry -> entry.text.any { it.isLetter() } }

    /** Inside a preview, or under a `@Suppress` of either strings rule on any enclosing declaration or the file. */
    private fun KtStringTemplateExpression.isExempt(): Boolean = parents.any { parent ->
        (parent is KtNamedFunction && parent.isPreview()) || (parent is KtAnnotated && parent.suppressesStrings())
    }

    private fun KtNamedFunction.isPreview(): Boolean = annotationEntries.any { it.shortName?.asString() == "Preview" }

    private fun KtAnnotated.suppressesStrings(): Boolean = annotationEntries.any { entry ->
        entry.shortName?.asString() == "Suppress" &&
            entry.valueArguments.any { argument ->
                val text = argument.getArgumentExpression()?.text?.trim('"') ?: return@any false
                text == "VerifyStrings" || text == "VerifyStringArguments"
            }
    }

    private companion object {
        /** As [VerifyStrings]: the DS text composables whose first argument is shown. */
        val TEXT_CALLEES = setOf("Text")

        /** Named arguments that are copy wherever they appear in this codebase. */
        val USER_FACING_ARGUMENTS = setOf(
            "contentDescription",
            "label",
            "title",
            "text",
            "placeholder",
            "onClickLabel",
            "stateDescription",
        )

        /** Properties set inside `semantics { }` that a screen reader speaks. */
        val SEMANTICS_PROPERTIES = setOf("contentDescription", "stateDescription")

        /** Compose animation APIs whose `label` names the animation in tooling. Plus anything `animate*`. */
        val ANIMATION_CALLEES = setOf(
            "Crossfade",
            "AnimatedContent",
            "AnimatedVisibility",
            "updateTransition",
            "rememberInfiniteTransition",
            "rememberTransition",
            "createChildTransition",
            // :libraries:ui's own looping helper, labelled the same way.
            "rememberLoopingFloat",
        )
    }
}
