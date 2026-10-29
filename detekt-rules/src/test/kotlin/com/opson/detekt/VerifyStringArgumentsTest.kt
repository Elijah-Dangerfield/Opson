package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyStringArgumentsTest {

    private val rule get() = VerifyStringArguments(Config.empty)

    @Test
    fun `reports an interpolated literal with words passed to Text`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(count: Int, name: String) {
                Text("${'$'}count foods")
                Text(text = "Hi ${'$'}name", style = AppTheme.type.body)
            }
            """
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `reports literals behind if, when and elvis passed to Text`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(on: Boolean, name: String?, kind: Int) {
                Text(if (on) "On" else "Off")
                Text(name ?: "Someone")
                Text(when (kind) { 0 -> "Zero"; else -> stringResource(Res.string.many) })
            }
            """
        )

        assertEquals(4, findings.size)
    }

    @Test
    fun `leaves a bare literal to Text for VerifyStrings`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen() {
                Text("Log food")
                Text(text = "Skip for now")
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports literals passed to user-facing named arguments on any call`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(count: Int) {
                Icon(AppIcons.Close, contentDescription = "Close")
                PrimaryButton(label = "Save", onClick = {})
                SheetHeader(title = "${'$'}count foods", onClose = {})
                TextField(value = "", placeholder = "Search foods")
                Modifier.clickable(onClickLabel = "Open", onClick = {})
                Modifier.toggleable(stateDescription = "On")
                Chip(text = "Breakfast")
            }
            """
        )

        assertEquals(7, findings.size)
    }

    @Test
    fun `reports literals assigned to semantics properties`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Ring(done: Boolean) {
                Box(Modifier.semantics { contentDescription = "Calories ring" })
                Box(Modifier.semantics { stateDescription = if (done) "Done" else "Not yet" })
            }
            """
        )

        assertEquals(3, findings.size)
    }

    @Test
    fun `leaves resources, glyphs, numbers, other arguments and animation labels alone`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen(a: String, b: String, n: Int, label: String) {
                Text("${'$'}a · ${'$'}b")
                Text("${'$'}{n}%")
                Text(label)
                Icon(AppIcons.Close, contentDescription = null)
                PrimaryButton(label = stringResource(Res.string.save), onClick = {})
                Modifier.testTag("metric:${'$'}n")
                Box(Modifier.semantics { contentDescription = label })
                val fill by animateFloatAsState(0.5f, label = "FillBar")
                Crossfade(targetState = n, label = "flip") {}
                val transition = rememberInfiniteTransition(label = "steam")
                val name = "Opson"
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `allows previews and anything under a strings suppression`() {
        val findings = rule.findingsOn(
            """
            @Preview
            @Composable
            private fun ScreenPreview(count: Int) {
                Text("${'$'}count foods")
                Icon(AppIcons.Close, contentDescription = "Close")
            }

            @Suppress("VerifyStrings")
            @Composable
            private fun PreviewBody() {
                SheetHeader(title = "Trends settings", onClose = {})
            }

            @Suppress("VerifyStringArguments")
            val sample = Sample(label = "Breakfast")
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `a file-level suppression covers the file`() {
        val findings = rule.findingsOn(
            """
            @file:Suppress("VerifyStrings")

            package com.opson.sample

            val sample = Sample(label = "Breakfast", title = "Lunch")
            """
        )

        assertEquals(0, findings.size)
    }
}
