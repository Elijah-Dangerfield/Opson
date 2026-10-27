package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomSheetStateSaverTest {

    private val scope = SaverScope { true }

    @Test
    fun aHalfOpenSheetComesBackHalfOpen() {
        @Suppress("UNCHECKED_CAST")
        val saver = BottomSheetState.Saver(
            confirmValueChange = { true },
            density = Density(1f),
            skipPartiallyExpanded = false,
        ) as Saver<BottomSheetState, BottomSheetValue>
        val sheet = BottomSheetState(BottomSheetValue.PartiallyExpanded, Density(1f), skipPartiallyExpanded = false)

        val saved = with(saver) { scope.save(sheet) }!!
        val restored = saver.restore(saved)!!

        assertEquals(BottomSheetValue.PartiallyExpanded, restored.currentValue)
    }
}
