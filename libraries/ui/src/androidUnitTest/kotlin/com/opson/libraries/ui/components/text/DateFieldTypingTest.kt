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
 * Typing into [DateField], the real composable, the ways a phone sends text.
 *
 * The bug, on the iOS simulator: the three boxes were three text fields handing focus on, and iOS
 * swaps its text input view with every hand-off, so keys that arrived in between went nowhere
 * ("05111998" typed by the simulator left Year empty). The field is one text field now.
 *
 * [iosKey] and [iosDelete] edit the way Compose on iOS does: UIKit's `insertText` and
 * `deleteBackward` become a new text that replaces the field's whole text, which is the
 * semantics' set-text action. [androidKey] is a commit at the cursor, Android's way. "Quickly"
 * holds the clock, so no frame is drawn between keys, as with a fast typist, a paste, autofill or
 * a simulator typing. The host holds the date the way a screen's view model does: an edit comes
 * back as state on a coroutine, a moment later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DateFieldTypingTest {

    @get:Rule val compose = createComposeRule()

    /** What the screen holds: the last date the field sent, or a prefill. */
    private var held by mutableStateOf(DateParts())
    private var done = 0

    @OptIn(ExperimentalResourceApi::class)
    private fun render(order: DateOrder = DateOrder.MonthDayYear, focusOnOpen: Boolean = false) {
        compose.setContent {
            AppTheme(darkTheme = false) {
                // Compose resources take the Android context only from this effect, and only
                // under inspection mode. Kept to the effect: the field itself runs as on a phone.
                CompositionLocalProvider(LocalInspectionMode provides true) { PreviewContextConfigurationEffect() }
                val scope = rememberCoroutineScope()
                DateField(
                    parts = held,
                    onChange = { typed -> scope.launch { held = typed } },
                    order = order,
                    onDone = { done++ },
                    focusOnOpen = focusOnOpen,
                )
            }
        }
    }

    private fun field() = compose.onNode(hasSetTextAction(), useUnmergedTree = true)

    /** The digits the field holds, as the text input sees them. */
    private fun typed(): String = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.InputText)?.text.orEmpty()

    /** What a screen reader says for the field. */
    private fun spoken(): String = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun iosKey(key: Char) = field().performTextReplacement(typed() + key)

    private fun iosDelete() = field().performTextReplacement(typed().dropLast(1))

    private fun androidKey(key: Char) = field().performTextInput(key.toString())

    private fun quickly(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        block()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun steadily(text: String, key: (Char) -> Unit) = text.forEach { char ->
        key(char)
        compose.waitForIdle()
    }

    private fun tapIn() {
        field().performClick()
        compose.waitForIdle()
    }

    // The lead's taps on the simulator: 0, 5, 1, 1, one by one. Year stays empty.
    @Test
    fun iosKeys_oneByOne_fillMonthThenDay_andLeaveTheYear() {
        render()
        tapIn()

        steadily("0511", ::iosKey)

        assertEquals(DateParts(month = "05", day = "11"), held)
        assertEquals("Month 05, Day 11", spoken())
        field().assertIsFocused()
    }

    @Test
    fun iosKeys_typedQuickly_keepEveryDigit() {
        render()
        tapIn()

        quickly { "05111998".forEach(::iosKey) }

        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
        assertEquals("Month 05, Day 11, Year 1998", spoken())
    }

    @Test
    fun androidKeys_typedQuickly_keepEveryDigit() {
        render(order = DateOrder.DayMonthYear)
        tapIn()

        quickly { "11051998".forEach(::androidKey) }

        assertEquals(DateParts(day = "11", month = "05", year = "1998"), held)
        assertEquals("Day 11, Month 05, Year 1998", spoken())
    }

    // Autofill, a paste and the simulator's typed text all reach the field as one string.
    @Test
    fun aWholeDate_insertedAtOnce_fillsAllThree() {
        render()
        listOf("05111998", "5/11/1998", "1998-05-11").forEach { whole ->
            field().performTextReplacement("")
            compose.waitForIdle()
            field().performTextInput(whole)
            compose.waitForIdle()

            assertEquals(DateParts(month = "05", day = "11", year = "1998"), held, whole)
            assertEquals("05111998", typed(), whole)
        }
    }

    @Test
    fun aWholeDate_replacingTheText_fillsAllThree() {
        render()
        tapIn()
        steadily("12", ::iosKey)

        field().performTextReplacement("12" + "05/11/1998")
        compose.waitForIdle()

        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
    }

    @Test
    fun iosDelete_takesTheLastDigit_backAcrossTheBoxes() {
        render()
        tapIn()
        steadily("05111", ::iosKey)

        quickly {
            iosDelete()
            iosDelete()
        }

        assertEquals(DateParts(month = "05", day = "1"), held)
        assertEquals("051", typed())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aHardwareBackspace_takesTheLastDigit() {
        render()
        tapIn()
        steadily("0511", ::androidKey)

        field().performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()

        assertEquals(DateParts(month = "05", day = "1"), held)
    }

    @Test
    fun digitsPastTheYear_areDropped() {
        render()
        tapIn()

        quickly { "051119987".forEach(::iosKey) }

        assertEquals("05111998", typed())
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)
    }

    @Test
    fun theKeyboardIsUp_whenTheStepOpens() {
        render(focusOnOpen = true)

        compose.mainClock.advanceTimeBy(SETTLED_MILLIS)

        field().assertIsFocused()
    }

    @Test
    fun aDateTheScreenSets_replacesWhatIsTyped() {
        render()
        tapIn()
        steadily("12", ::iosKey)

        held = DateParts.of(LocalDate(1998, 5, 11))
        compose.waitForIdle()

        assertEquals("05111998", typed())
        assertEquals("Month 05, Day 11, Year 1998", spoken())
    }

    // Settings that knew only a birth year: the year shows until digits reach its box.
    @Test
    fun aYearAlone_fromTheScreen_staysUntilTypedOver() {
        held = DateParts(year = "1998")
        render()
        tapIn()

        steadily("0511", ::iosKey)
        assertEquals(DateParts(month = "05", day = "11", year = "1998"), held)

        iosKey('2')
        compose.waitForIdle()
        assertEquals(DateParts(month = "05", day = "11", year = "2"), held)
    }

    @Test
    fun theDoneKey_runsOnDone() {
        render()
        tapIn()
        steadily("05111998", ::iosKey)

        field().performImeAction()

        assertEquals(1, done)
    }

    private companion object {
        const val SETTLED_MILLIS = 500L
    }
}
