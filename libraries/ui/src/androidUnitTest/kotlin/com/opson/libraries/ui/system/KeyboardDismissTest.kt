package com.opson.libraries.ui.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.SearchField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextArea
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FIELD = "field"
private const val NEXT_FIELD = "next"
private const val EMPTY = "empty"
private const val BUTTON = "button"
private const val LIST = "list"

/**
 * The ways out of a keyboard: a tap on nothing, a return key, a drag of the results under a
 * search. Each one ends in a focus clear, since dropping focus is what closes the keyboard on iOS.
 *
 * The clears are counted rather than read off the field. Robolectric runs this library's test app
 * at target SDK 24 and out of touch mode, where Android hands focus straight back to the first
 * focusable after any clear; a phone is in touch mode and does not. Moving focus is real either
 * way, so Next is checked on the fields themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardDismissTest {

    @get:Rule val compose = createComposeRule()

    private var clears = 0

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme(darkTheme = false) {
                val real = LocalFocusManager.current
                val counting = object : FocusManager by real {
                    override fun clearFocus(force: Boolean) {
                        clears++
                        real.clearFocus(force)
                    }
                }
                CompositionLocalProvider(LocalFocusManager provides counting) {
                    Box(Modifier.fillMaxSize().clearFocusOnTapOutside()) { content() }
                }
            }
        }
    }

    private fun focusField(tag: String = FIELD) {
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithTag(tag).assertIsFocused()
        clears = 0
    }

    @Test
    fun aTapOnEmptySpaceDropsFocus() {
        render {
            Column {
                NumberField(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                Box(Modifier.fillMaxWidth().height(200.dp).testTag(EMPTY))
            }
        }
        focusField()

        compose.onNodeWithTag(EMPTY).performClick()

        assertEquals(1, clears)
    }

    @Test
    fun aTapOnTheFocusedFieldKeepsIt() {
        render { NumberField(value = "12", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        focusField()

        compose.onNodeWithTag(FIELD).performClick()

        assertEquals(0, clears)
        compose.onNodeWithTag(FIELD).assertIsFocused()
    }

    @Test
    fun aTapOnAMultiLineFieldKeepsIt() {
        render { TextArea(value = "Ate outside with Sam.", onValueChange = {}, modifier = Modifier.testTag(FIELD)) }
        focusField()

        compose.onNodeWithTag(FIELD).performClick()

        assertEquals(0, clears)
    }

    @Test
    fun aTapOnAnotherFieldMovesFocusThere() {
        render {
            Column {
                TextInput(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                TextInput(value = "", onValueChange = {}, modifier = Modifier.testTag(NEXT_FIELD))
            }
        }
        focusField()

        compose.onNodeWithTag(NEXT_FIELD).performClick()

        assertEquals(0, clears)
        compose.onNodeWithTag(NEXT_FIELD).assertIsFocused()
    }

    @Test
    fun aButtonStillGetsItsClick() {
        var clicks = 0
        render {
            Column {
                NumberField(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                Box(Modifier.fillMaxWidth().height(48.dp).testTag(BUTTON).clickable { clicks++ })
            }
        }
        focusField()

        compose.onNodeWithTag(BUTTON).performClick()

        assertEquals(1, clicks)
        // A button is not nothing: what it does decides the keyboard, not the tap.
        assertEquals(0, clears)
    }

    @Test
    fun doneRunsTheCallersHandlerThenDropsFocus() {
        var done = 0
        render {
            NumberField(
                value = "",
                onValueChange = {},
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { done++ }),
                modifier = Modifier.testTag(FIELD),
            )
        }
        focusField()

        compose.onNodeWithTag(FIELD).performImeAction()

        assertEquals(1, done)
        assertEquals(1, clears)
    }

    @Test
    fun nextMovesToTheNextField() {
        render {
            Column {
                TextInput(value = "", onValueChange = {}, imeAction = ImeAction.Next, modifier = Modifier.testTag(FIELD))
                NumberField(value = "", onValueChange = {}, imeAction = ImeAction.Done, modifier = Modifier.testTag(NEXT_FIELD))
            }
        }
        focusField()

        compose.onNodeWithTag(FIELD).performImeAction()

        compose.onNodeWithTag(NEXT_FIELD).assertIsFocused()
        assertEquals(0, clears)
    }

    @Test
    fun searchDropsFocus() {
        render { SearchField(value = "banana", onValueChange = {}, placeholder = "Search", modifier = Modifier.testTag(FIELD)) }
        focusField()

        compose.onNodeWithTag(FIELD).performImeAction()

        assertEquals(1, clears)
    }

    @Test
    fun draggingTheResultsDropsFocus() {
        render {
            Column {
                SearchField(value = "", onValueChange = {}, placeholder = "Search", modifier = Modifier.testTag(FIELD))
                LazyColumn(Modifier.fillMaxSize().testTag(LIST).clearFocusOnScroll()) {
                    items(50) { Text("Result $it", modifier = Modifier.height(56.dp)) }
                }
            }
        }
        focusField()

        compose.onNodeWithTag(LIST).performTouchInput { swipeUp() }

        assertTrue(clears > 0)
    }

    @Test
    fun scrollingAFormKeepsItsKeyboard() {
        render {
            Column {
                NumberField(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                LazyColumn(Modifier.fillMaxSize().testTag(LIST)) {
                    items(50) { Text("Row $it", modifier = Modifier.height(56.dp)) }
                }
            }
        }
        focusField()

        compose.onNodeWithTag(LIST).performTouchInput { swipeUp() }

        // A drag is not a tap on nothing, so only a list that opts in closes the keyboard.
        assertEquals(0, clears)
    }
}
