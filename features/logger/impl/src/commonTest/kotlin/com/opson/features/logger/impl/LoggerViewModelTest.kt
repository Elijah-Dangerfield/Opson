package com.opson.features.logger.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

class LoggerViewModelTest : CoroutineTest() {

    @Test
    fun opensForTheSlotItWasGiven() = runUnitTest {
        val vm = LoggerViewModel(slot = "lunch")

        assertEquals("lunch", vm.state.slot)
    }

    @Test
    fun closeTappedAsksTheEntryPointToCloseTheSheet() = runUnitTest {
        val vm = LoggerViewModel(slot = null)

        vm.takeAction(LoggerAction.CloseTapped)

        assertEquals(LoggerEvent.Close, vm.eventFlow.first())
    }
}
