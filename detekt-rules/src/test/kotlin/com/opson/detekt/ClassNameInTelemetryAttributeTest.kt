package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassNameInTelemetryAttributeTest {

    private val rule get() = ClassNameInTelemetryAttribute(Config.empty)

    @Test
    fun `reports every spelling of a class name in a logEvent attribute`() {
        val findings = rule.findingsOn(
            """
            fun report(result: PurchaseOutcome) {
                logEvent("iap.purchase_result", "outcome" to result::class.simpleName)
                logEvent("iap.purchase_result", "outcome" to result::class.qualifiedName)
                logEvent("iap.purchase_result", "outcome" to result::class.java.simpleName)
                logEvent("iap.purchase_result", "outcome" to result::class.java.name)
                logEvent("iap.purchase_result", "outcome" to result::class.java.canonicalName)
            }
            """
        )

        assertEquals(5, findings.size)
    }

    @Test
    fun `leaves explicit names and log messages alone`() {
        val findings = rule.findingsOn(
            """
            fun report(result: PurchaseOutcome) {
                logEvent("iap.purchase_result", "outcome" to result.name)
                logEvent("iap.purchase_result", "outcome" to result.kind.name)
                KLog.w("purchase ended as ${'$'}{result::class.simpleName}")
            }
            """
        )

        assertEquals(0, findings.size)
    }
}
