package com.opson.libraries.diary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A food row's remembered state (how far it is swiped open) belongs to that food. Unkeyed, it
 * belonged to a position: Undo put a removed food back above a row swiped open, and the food put
 * back was drawn open beside Delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayMealCardTest {

    @get:Rule val compose = createComposeRule()

    private fun item(key: String) = MealCardItem(key = key, name = key, sub = "", calories = Display.Hidden)

    @Test
    fun aFoodPutBackAboveAnotherDoesNotTakeItsRowState() {
        var items by mutableStateOf(listOf(item("bread")))
        compose.setContent {
            AppTheme {
                TodayMealCard(
                    icon = AppIcons.LunchDining,
                    iconColor = AppTheme.colors.nutrients.fat.content,
                    title = "Lunch",
                    time = null,
                    calories = Display.Hidden,
                    macros = emptyList(),
                    macroStyle = MacroLineStyle.Compact,
                    items = items,
                    note = null,
                ) { row ->
                    // Stands in for the swipe row's remembered offset: state made the first time this row composed.
                    val firstShownAs = remember { row.key }
                    Text(text = firstShownAs, modifier = Modifier.testTag(row.key))
                }
            }
        }

        items = listOf(item("soup"), item("bread"))
        compose.waitForIdle()

        compose.onNodeWithTag("bread").assertTextEquals("bread")
        compose.onNodeWithTag("soup").assertTextEquals("soup")
    }
}
