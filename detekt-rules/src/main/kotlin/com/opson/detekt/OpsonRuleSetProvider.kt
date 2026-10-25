package com.opson.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Registers the project's custom rule set. Discovered by detekt via the
 * `META-INF/services/dev.detekt.api.RuleSetProvider` entry. The rule set id
 * (`opson`) namespaces the rules in `detekt.yml`.
 */
class OpsonRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("opson")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::VerifyStrings,
            ::AnimatedStateReadInComposition,
            ::ClassNameInTelemetryAttribute,
            ::ScrollInsideBottomSheet,
        ),
    )
}
