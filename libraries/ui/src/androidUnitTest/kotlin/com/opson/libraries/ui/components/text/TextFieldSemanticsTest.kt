package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FIELD = "field"

/**
 * What the text fields put in the semantics tree, which is what `BlindModeLeakTest` reads. The
 * leak test joins `Text`, `EditableText` and content descriptions in tree order and greps the
 * result for calorie amounts, so a field's value has to be in `EditableText` (or an Energy field
 * holding 190 would sail through Blind), and a masked value must not be.
 *
 * The scan here is a cut-down copy of the leak test's, on purpose: the claim is about what that
 * scan would see.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextFieldSemanticsTest {

    @get:Rule val compose = createComposeRule()

    private fun render(content: @Composable () -> Unit) {
        compose.setContent { AppTheme(darkTheme = false) { content() } }
    }

    private fun fieldNode(): SemanticsNode = compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()

    private fun editableText(): String? = fieldNode().config.getOrNull(SemanticsProperties.EditableText)?.text

    private fun scannedText(): String {
        val parts = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { parts += it.text }
            node.config.getOrNull(SemanticsProperties.EditableText)?.let { parts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { parts += it }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    @Test
    fun numberField_valueAndUnitScanAsOneAmount() {
        render {
            NumberField(value = "190", onValueChange = {}, label = "Energy", unit = "kcal", modifier = Modifier.testTag(FIELD))
        }
        assertEquals("190", editableText())
        assertTrue("190 kcal" in scannedText(), scannedText())
    }

    @Test
    fun numberField_sanitizesWhatIsTyped() {
        var value by mutableStateOf("")
        render { NumberField(value = value, onValueChange = { value = it }, modifier = Modifier.testTag(FIELD)) }

        compose.onNodeWithTag(FIELD).performTextInput("1,5a.2")

        assertEquals("1.52", value)
    }

    @Test
    fun textArea_exposesItsValue() {
        render { TextArea(value = "Ate outside with Sam.", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        assertEquals("Ate outside with Sam.", editableText())
    }

    @Test
    fun maskedTextField_masked_keepsTheValueOutOfTheTree() {
        render { MaskedTextField(value = "72.6", onValueChange = {}, masked = true, modifier = Modifier.testTag(FIELD)) }

        val node = fieldNode()
        assertFalse("72" in editableText().orEmpty(), "editable text: ${editableText()}")
        assertFalse("72" in scannedText(), scannedText())
        assertTrue(SemanticsProperties.Password in node.config, "a masked field is marked as a password for screen readers")
    }

    @Test
    fun maskedTextField_unmasked_exposesItsValue() {
        render { MaskedTextField(value = "72.6", onValueChange = {}, masked = false, modifier = Modifier.testTag(FIELD)) }
        assertEquals("72.6", editableText())
    }

    @Test
    fun numberField_aKeystrokeTheFilterDropsNeverShows() {
        var value by mutableStateOf("1.5")
        render { NumberField(value = value, onValueChange = { value = it }, modifier = Modifier.testTag(FIELD)) }

        // A second point: the filter turns "1.5." back into "1.5", so nothing changed.
        compose.onNodeWithTag(FIELD).performTextReplacement("1.5.")

        assertEquals("1.5", editableText())
        assertEquals("1.5", value)
    }

    @Test
    fun aViewModelThatRefusesAnEdit_putsTheFieldBackOnItsValue() {
        // A length cap in the ViewModel: past the limit the state does not change, so no echo comes.
        var value by mutableStateOf("abc")
        render { TextArea(value = value, onValueChange = { value = it.take(3) }, modifier = Modifier.testTag(FIELD)) }

        compose.onNodeWithTag(FIELD).performTextReplacement("abcd")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("abc", editableText())
    }
}
