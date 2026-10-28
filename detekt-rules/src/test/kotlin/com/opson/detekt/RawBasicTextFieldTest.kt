package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class RawBasicTextFieldTest {

    private val rule get() = RawBasicTextField(Config.empty)

    @Test
    fun `reports Compose's own field`() {
        val findings = rule.findingsOn(
            """
            import androidx.compose.foundation.text.BasicTextField

            fun field() = Unit
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `leaves the design system's field and other foundation text imports alone`() {
        val findings = rule.findingsOn(
            """
            import com.opson.libraries.ui.components.text.BasicTextField
            import androidx.compose.foundation.text.KeyboardOptions

            fun field() = Unit
            """
        )

        assertEquals(0, findings.size)
    }
}
