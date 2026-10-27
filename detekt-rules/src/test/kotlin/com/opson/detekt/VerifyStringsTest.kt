package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyStringsTest {

    private val rule get() = VerifyStrings(Config.empty)

    @Test
    fun `reports a literal passed to Text, positional or named`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen() {
                Text("Log food")
                Text(text = "Skip for now", style = AppTheme.type.body)
            }
            """
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `leaves resources, variables, templates and glyphs alone`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(name: String) {
                Text(stringResource(Res.string.log_food))
                Text(name)
                Text("Hi ${'$'}name")
                Text("·")
                Text("12:30")
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `allows sample copy inside a preview`() {
        val findings = rule.findingsOn(
            """
            @Preview
            @Composable
            private fun ScreenPreview() {
                Text("Log food")
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not look inside other calls`() {
        val findings = rule.findingsOn(
            """
            fun log() {
                println("Log food")
                Button(label = "Log food")
            }
            """
        )

        assertEquals(0, findings.size)
    }
}
