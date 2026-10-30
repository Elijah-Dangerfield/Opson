package com.opson.libraries.ui.components.text

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Typing a date into [DateField] faster than the screen redraws. The bug, on the iOS simulator:
 * "05111998" typed quickly left Month empty, Day "19" and the cursor in an empty Year.
 *
 * The host holds the date the way a screen's view model does, by default: an edit comes back as
 * state on a coroutine, a moment later. "Quickly" holds the clock, so no frame is drawn between
 * keystrokes and the cursor cannot move on, which is what a fast typist, the simulator typing for
 * a test, a paste or autofill looks like to the field. Against the field as it was, the quick
 * test sent the screen a date with Month and Day wiped, and the slashes test filled Month with 51.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateFieldTypingTest {

    @get:Rule val compose = createComposeRule()

    /** What the screen holds: the last date the field sent, or a prefill. */
    private var held by mutableStateOf(DateParts())
    private var done = 0

    @OptIn(ExperimentalResourceApi::class)
    private fun render(
        order: DateOrder = DateOrder.MonthDayYear,
        echoLater: Boolean = true,
        focusOnOpen: Boolean = false,
    ) {
        compose.setContent {
            AppTheme(darkTheme = false) {
                // Compose resources take the Android context only from this effect, and only
                // under inspection mode. Kept to the effect: the field itself runs as on a phone.
                CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
                val scope = rememberCoroutineScope()
                DateField(
                    parts = held,
                    onChange = { typed -> if (echoLater) scope.launch { held = typed } else held = typed },
                    order = order,
                    onDone = { done++ },
                    focusOnOpen = focusOnOpen,
                )
            }
        }
    }

    private fun boxes() = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)

    private fun focused() = compose.onNode(isFocused(), useUnmergedTree = true)

    private fun shown(): List<String> = boxes().fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    }

    /** Each character into whichever box has focus, with no frame drawn between them. */
    private fun typeQuickly(text: String) {
        compose.mainClock.autoAdvance = false
        text.forEach { char -> focused().performTextInput(char.toString()) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /** Each character into whichever box has focus, the screen keeping up between them. */
    private fun typeSteadily(text: String) {
        text.forEach { char ->
            focused().performTextInput(char.toString())
            compose.waitForIdle()
        }
    }

    private fun focusFirstBox() {
        boxes()[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun aDateTypedQuickly_landsInItsBoxes() {
        render()
        focusFirstBox()

        typeQuickly("05111998")

        assertEquals(listOf("05", "11", "1998"), shown())
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
    }

    @Test
    fun aDateTypedQuickly_withAScreenThatHoldsItAtOnce_landsInItsBoxes() {
        render(echoLater = false)
        focusFirstBox()

        typeQuickly("05111998")

        assertEquals(listOf("05", "11", "1998"), shown())
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
    }

    @Test
    fun aMonthThatStandsAlone_movesOn_andTheRestFollows() {
        render()
        focusFirstBox()

        typeQuickly("5111998")

        assertEquals(listOf("5", "11", "1998"), shown())
    }

    @Test
    fun aDateTypedQuickly_dayFirst() {
        render(order = DateOrder.DayMonthYear)
        focusFirstBox()

        typeQuickly("11051998")

        assertEquals(listOf("11", "05", "1998"), shown())
        assertEquals(DateParts(day = "11", month = "05", year = "1998"), held)
    }

    @Test
    fun aDateTypedSteadily_movesTheCursorOn_andEndsInTheYear() {
        render()
        focusFirstBox()

        typeSteadily("05111998")

        assertEquals(listOf("05", "11", "1998"), shown())
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
        boxes()[2].assertIsFocused()
    }

    @Test
    fun aDateWithSlashes_intoTheFirstBox_fillsAllThree() = pastedIntoTheFirstBox("05/11/1998")

    @Test
    fun eightDigits_intoTheFirstBox_fillAllThree() = pastedIntoTheFirstBox("05111998")

    @Test
    fun aDateWithoutLeadingZeros_intoTheFirstBox_fillsAllThree() = pastedIntoTheFirstBox("5/11/1998")

    private fun pastedIntoTheFirstBox(pasted: String) {
        render()
        boxes()[0].performTextInput(pasted)
        compose.waitForIdle()

        assertEquals(listOf("05", "11", "1998"), shown())
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
        boxes()[2].assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deleteInAnEmptyBox_goesBack_andTakesTheDigitBefore() {
        render()
        focusFirstBox()
        typeSteadily("0511")
        boxes()[2].assertIsFocused()

        focused().performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()

        assertEquals(listOf("05", "1", ""), shown())
        assertEquals(DateParts(month = "05", day = "1"), held)
        boxes()[1].assertIsFocused()
    }

    // An on-screen keyboard's delete edits the box's text itself, where the invisible lead sits in
    // front of the digits; replacing the empty box's text with nothing removes it the same way.
    @Test
    fun theKeyboardsDelete_inAnEmptyBox_goesBack_andTakesTheDigitBefore() {
        render()
        focusFirstBox()
        typeSteadily("0511")

        boxes()[2].performTextReplacement("")
        compose.waitForIdle()

        assertEquals(listOf("05", "1", ""), shown())
        assertEquals(DateParts(month = "05", day = "1"), held)
        boxes()[1].assertIsFocused()
    }

    @Test
    fun theCursorStarts_inTheFirstEmptyBox() {
        held = DateParts(month = "05")
        render(focusOnOpen = true)

        compose.mainClock.advanceTimeBy(SETTLED_MILLIS)

        boxes()[1].assertIsFocused()
    }

    @Test
    fun aDateTheScreenSets_replacesWhatIsShown() {
        render()
        focusFirstBox()
        typeSteadily("12")

        held = DateParts.of(LocalDate(1998, 5, 11))
        compose.waitForIdle()

        assertEquals(listOf("05", "11", "1998"), shown())
    }

    @Test
    fun theLastBoxsDoneKey_runsOnDone() {
        render()
        focusFirstBox()
        typeSteadily("05111998")

        boxes()[2].performImeAction()

        assertEquals(1, done)
    }

    private companion object {
        const val SETTLED_MILLIS = 500L
    }
}
