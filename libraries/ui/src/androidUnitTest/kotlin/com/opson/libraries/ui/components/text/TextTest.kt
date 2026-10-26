package com.opson.libraries.ui.components.text

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun text_drawsWhatPeopleTypeIncludingAngleBrackets() {
        // A note or a food name is the user's own text. "</b>" used to crash the markup parser and
        // "<2 slices>" used to vanish as an unknown tag.
        val note = "Pizza <2 slices> and a stray </b>"
        compose.setContent { AppTheme(darkTheme = false) { Text(note, modifier = Modifier.testTag("text")) } }

        compose.onNodeWithTag("text").assertTextEquals(note)
    }
}
