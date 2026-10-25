package com.opson.features.home.impl.harness

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test

/**
 * Demonstrates the scenario-harness pattern over the real [HomeViewModel]:
 * build → act → assertState, in user vocabulary. Covers only the harness
 * shape; the exhaustive action/state coverage lives in `HomeViewModelTest`.
 */
class HomeScenarioTest : CoroutineTest() {

    @Test
    fun renamedUserAppearsAfterRefresh() = runUnitTest {
        val scenario = homeScenario { user("Ada") }

        scenario.assertState { userNameIs("Ada") }

        scenario.renameUser("Grace")
        scenario.refresh()

        scenario.assertState { userNameIs("Grace") }
    }

    @Test
    fun signedOutUserLosesNameOnRefresh() = runUnitTest {
        val scenario = homeScenario { user("Ada") }

        scenario.signOut()
        scenario.refresh()

        scenario.assertState { userNameIsAbsent() }
    }
}
