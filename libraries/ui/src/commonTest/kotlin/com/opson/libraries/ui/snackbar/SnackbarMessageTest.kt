package com.opson.libraries.ui.snackbar

import androidx.compose.material3.SnackbarResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SnackbarMessageTest {

    private var actions = 0
    private var dismissals = 0
    private val message = SnackbarMessage(message = "Saved", onAction = { actions++ }, onDismiss = { dismissals++ })

    @Test
    fun tappingTheActionRunsOnlyOnAction() {
        message.deliver(SnackbarResult.ActionPerformed)

        assertEquals(1, actions)
        assertEquals(0, dismissals)
    }

    @Test
    fun aDismissalRunsOnlyOnDismiss() {
        message.deliver(SnackbarResult.Dismissed)

        assertEquals(0, actions)
        assertEquals(1, dismissals)
    }
}
