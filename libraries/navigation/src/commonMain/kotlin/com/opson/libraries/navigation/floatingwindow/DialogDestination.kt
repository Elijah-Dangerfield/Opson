package com.opson.libraries.navigation.floatingwindow

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import com.opson.libraries.ui.components.dialog.DialogState
import com.opson.libraries.ui.components.dialog.rememberDestinationDialogState

@Composable
fun DialogDestination(
    backStackEntry: NavBackStackEntry,
    content: @Composable (NavBackStackEntry, DialogState) -> Unit
) {
    val dialogState = rememberDestinationDialogState(backStackEntry.id)
    content(backStackEntry, dialogState)
}
