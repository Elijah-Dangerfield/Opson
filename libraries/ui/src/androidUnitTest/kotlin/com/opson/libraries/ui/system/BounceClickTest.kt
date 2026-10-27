package com.opson.libraries.ui.system

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.opson.libraries.ui.bounceClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BounceClickTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aBounceClickIsAButtonToScreenReaders() {
        // Rows, cards and menu items are clickable through bounceClick; without a role a screen
        // reader announces them as plain text.
        compose.setContent { Box(Modifier.testTag("target").bounceClick {}) }

        val role = compose.onNodeWithTag("target").fetchSemanticsNode().config[SemanticsProperties.Role]
        assertEquals(Role.Button, role)
    }
}
