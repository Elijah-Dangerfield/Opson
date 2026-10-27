package com.opson.libraries.ui.components.radio

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** A row that offers only some columns: the rest are a dash, with nothing to tap or hear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RadioMatrixTest {

    @get:Rule val compose = createComposeRule()

    private val columns = listOf("Show", "Soft", "Later", "Hide")

    @Test
    fun disabledColumns_areNotRadioButtons_andTheOthersStillSelect() {
        val taps = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            AppTheme(darkTheme = false) {
                RadioMatrix(
                    header = "",
                    columns = columns,
                    rows = listOf(
                        RadioMatrixRow("Calories", 0, columns.map { "Calories: $it" }),
                        RadioMatrixRow("Progress photos", 3, columns.map { "Progress photos: $it" }, disabledColumns = setOf(1, 2)),
                    ),
                    onSelect = { row, column -> taps += row to column },
                )
            }
        }

        val radios = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
        assertEquals(columns.size + 2, radios.size, "four cells for Calories, two for Progress photos")
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Progress photos: Soft")))
            .assertCountEquals(0)

        compose.onNodeWithContentDescription("Progress photos: Show").performClick()
        assertEquals(listOf(1 to 0), taps)
    }
}
