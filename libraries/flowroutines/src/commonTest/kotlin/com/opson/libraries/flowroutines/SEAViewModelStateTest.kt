package com.opson.libraries.flowroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SEAViewModelStateTest {

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateReadRightAfterAnUpdate_seesTheUpdate_unconfined() = readAfterUpdate(UnconfinedTestDispatcher())

    @Test
    fun stateReadRightAfterAnUpdate_seesTheUpdate_standard() = readAfterUpdate(StandardTestDispatcher())

    private fun readAfterUpdate(dispatcher: TestDispatcher) = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val viewModel = CounterViewModel()
        runCurrent()

        viewModel.takeAction(Bump)
        runCurrent()

        assertEquals(listOf(1), viewModel.seenAfterUpdate)
        assertEquals(1, viewModel.state)
    }
}

private data object Bump

private class CounterViewModel : SEAViewModel<Int, Unit, Bump>(initialStateArg = 0) {
    val seenAfterUpdate = mutableListOf<Int>()

    override suspend fun handleAction(action: Bump) {
        action.updateState { it + 1 }
        seenAfterUpdate += state
    }
}
