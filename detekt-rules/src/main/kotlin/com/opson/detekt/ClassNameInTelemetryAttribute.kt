package com.opson.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags a telemetry attribute whose value is a class name — `::class.simpleName`
 * and friends — because R8 renames those in release builds and the dashboard
 * keeps filtering on the original.
 *
 * ## What this looks like when it bites
 *
 * A panel reads zero on Android while the store console shows the events it is
 * meant to be counting. Nothing errors, nothing is logged, and the panel is not
 * empty — the other values in its filter still match, so it looks like a real
 * decline. iOS is unaffected, which makes it read as a platform bug rather than
 * a build one.
 *
 * Downstream, `logEvent("iap.purchase_result", "outcome" to result::class.simpleName)`
 * shipped a whole release that way. `PurchaseOutcome.Success` is a plain sealed
 * object: not `@Serializable`, not a `Throwable`, so no keep rule covered it.
 * A real minified build's `mapping.txt` had it renamed to `ta.l`, and the
 * attribute arrived as the letter `l` while the dashboard filtered on
 * `outcome="Success"`. A quarter of that release's purchase data was
 * unattributable.
 *
 * ## Why this repo is exposed
 *
 * Minification is on, the only name-keeping rule in `proguard-rules.pro` is
 * `-keepnames class * extends java.lang.Throwable`, and the boards in
 * `ops/grafana` filter app events by attribute value. A `::class.simpleName`
 * inside a log *message* is read by a human, so an obfuscated name there is
 * ugly, not wrong, and this rule leaves those alone. Fed to `logEvent` it
 * becomes a machine-queried value, and the value is whatever R8 chose.
 *
 * ## The fix
 *
 * Give the type an explicit `val name` literal and emit that. A `-keepnames`
 * rule also works and is worse: the dashboards are keyed on these strings, and
 * nobody renaming a class will think to open a ProGuard file.
 *
 * ## Two things checked on the way, so they are not re-derived
 *
 * - `Throwable` names ARE kept by the rule above, so `simpleName` on an
 *   exception is safe.
 * - Enum `.name` survives minification even though R8 renames the constant
 *   fields, because the string is baked into the class initializer —
 *   disassembling `<clinit>` shows the `const-string` intact.
 *
 * ## Limit, stated honestly
 *
 * This matches on source text at the call site. A class name laundered through
 * a helper — `logEvent("e", "k" to describe(result))` where `describe` reads
 * `::class.simpleName` — is invisible to it. It catches the direct form, which
 * is the form people write.
 */
class ClassNameInTelemetryAttribute(config: Config) : Rule(
    config,
    "A telemetry attribute spelled with a class name is renamed by R8, so the " +
        "release build reports something the dashboard does not filter on.",
) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in TELEMETRY_SINKS) return
        for (argument in expression.valueArguments) {
            val text = argument.text ?: continue
            val culprit = CLASS_NAME_READS.firstOrNull { text.contains(it) } ?: continue
            report(
                Finding(
                    Entity.from(argument),
                    "`$culprit` in a telemetry attribute. R8 renames this class in release " +
                        "builds, so the attribute ships as whatever R8 chose while dashboards " +
                        "keep filtering on the source name — a panel that reads zero and looks " +
                        "like a real zero. Give the type an explicit `val name` literal and emit " +
                        "that. Log messages are fine; this is about machine-queried values.",
                ),
            )
        }
    }

    private companion object {
        /**
         * Calls whose arguments become machine-queried attributes. A log message
         * is not one of them — an obfuscated name there costs readability, not
         * correctness — so `KLog.i`/`w`/`e` are deliberately absent.
         */
        val TELEMETRY_SINKS = setOf("logEvent")

        /**
         * Every spelling that resolves to a class's name at runtime. The
         * `.java` forms matter on Android even in common code, because that is
         * what the Kotlin/JVM backend emits.
         */
        val CLASS_NAME_READS = listOf(
            "::class.simpleName",
            "::class.qualifiedName",
            "::class.java.simpleName",
            "::class.java.name",
            "::class.java.canonicalName",
        )
    }
}
