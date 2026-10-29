package com.opson.features.qa.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.prompts.PromptBlock
import com.opson.libraries.prompts.PromptDebug
import com.opson.libraries.prompts.PromptInspection
import com.opson.libraries.prompts.PromptPresentation
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_checkin_title
import kotlin.test.Test
import kotlin.test.assertEquals

class QaPromptsTest : CoroutineTest() {

    private class FakePromptDebug : PromptDebug {
        var shows = 1
        val calls = mutableListOf<String>()

        override suspend fun inspect(): List<PromptInspection> = listOf(
            PromptInspection(
                id = "try_check_in",
                title = Res.string.prompt_checkin_title,
                presentation = PromptPresentation.Card,
                priority = 100,
                blockedBy = if (shows > 0) PromptBlock.ShownRecently else null,
                eligible = true,
                shows = shows,
                dismissals = 0,
                accepted = false,
            ),
        )

        override suspend fun reset(id: String?) {
            calls += "reset:${id ?: "all"}"
            shows = 0
        }

        override fun showNext(id: String) {
            calls += "next:$id"
        }
    }

    @Test
    fun `lists the prompts as they stand and a reset reloads them`() = runUnitTest {
        val debug = FakePromptDebug()
        val vm = QaPromptsViewModel(debug)

        assertEquals(PromptBlock.ShownRecently, vm.state.prompts.single().blockedBy)

        vm.takeAction(QaPromptsAction.ResetTapped("try_check_in"))
        assertEquals(listOf("reset:try_check_in"), debug.calls)
        assertEquals(null, vm.state.prompts.single().blockedBy)
        assertEquals(0, vm.state.prompts.single().shows)
    }

    @Test
    fun `Reset all and Show next reach the processor`() = runUnitTest {
        val debug = FakePromptDebug()
        val vm = QaPromptsViewModel(debug)

        vm.takeAction(QaPromptsAction.ResetAllTapped)
        vm.takeAction(QaPromptsAction.ShowNextTapped("try_check_in"))

        assertEquals(listOf("reset:all", "next:try_check_in"), debug.calls)
    }
}
