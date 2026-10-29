package com.opson.features.builder.impl

import com.opson.features.builder.BuilderProgramRoute
import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.prompts.PromptEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-29T12:00:00Z")

private val newcomer = BuilderStanding(status = BuilderStatus.None, trust = BuilderTrust.Anyone, canJoin = true)
private val member = BuilderStanding(
    status = BuilderStatus.Active,
    trust = BuilderTrust.Builder,
    joinedAt = NOW,
    proUntil = NOW + 30.days,
    accepted30d = 2,
    canJoin = false,
)

/** Answers [next] to every call, and remembers the joins. */
private class FakeProgram(initial: BuilderStanding? = newcomer) : BuilderProgram {
    private val state = MutableStateFlow(initial)
    override val standing: StateFlow<BuilderStanding?> = state
    var next: BuilderResult? = null
    val joins = mutableListOf<Boolean>()
    var left = 0

    private fun answer(default: BuilderStanding?): BuilderResult =
        (next ?: default?.let { BuilderResult.Done(it) } ?: BuilderResult.Unavailable).also { if (it is BuilderResult.Done) state.value = it.standing }

    override suspend fun refresh(): BuilderResult = answer(state.value)

    override suspend fun join(photoRetention: Boolean): BuilderResult {
        joins += photoRetention
        return answer(member.copy(photoRetention = photoRetention))
    }

    override suspend fun leave(): BuilderResult {
        left++
        return answer(newcomer.copy(status = BuilderStatus.Left))
    }

    override suspend fun setPhotoRetention(keep: Boolean): BuilderResult = answer(state.value?.copy(photoRetention = keep))
}

private object Utc : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC
    override fun today(): LocalDate = LocalDate(2026, 10, 29)
}

private val fixed = object : Clock {
    override fun now(): Instant = NOW
}

private fun flag(on: Boolean) = BuildersEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("builders" to on))
    },
)

private class Submitted(statuses: Map<String, ReviewStatus> = emptyMap()) : SubmittedFoods {
    override val reviews: StateFlow<Map<String, ReviewStatus>> = MutableStateFlow(statuses)
    override suspend fun sent(customFoodId: String, submissionId: String, foodId: String) = Unit
}

class BuilderProgramViewModelTest : CoroutineTest() {

    private fun TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    private fun vm(program: FakeProgram) = BuilderProgramViewModel(BuilderProgramRoute(), program, Utc, fixed)

    @Test
    fun joiningWaitsForTheRulesAndCarriesThePhotoChoice() = runUnitTest {
        val program = FakeProgram()
        val vm = vm(program)
        settle()
        assertEquals(BuilderPage.Join(block = null, until = null, rejoin = false), vm.state.page)

        vm.takeAction(BuilderProgramAction.JoinTapped)
        settle()
        assertTrue(program.joins.isEmpty(), "not before the rules are ticked")

        vm.takeAction(BuilderProgramAction.AgreeChanged(true))
        vm.takeAction(BuilderProgramAction.RetentionChanged(true))
        vm.takeAction(BuilderProgramAction.JoinTapped)
        settle()

        assertEquals(listOf(true), program.joins)
        val page = assertIs<BuilderPage.Member>(vm.state.page)
        assertEquals("November 28", page.proUntil)
        assertTrue(page.proActive)
        assertTrue(page.retention)
    }

    @Test
    fun aBlockedJoinSaysWhyAndOfferNoButton() = runUnitTest {
        val program = FakeProgram(newcomer.copy(canJoin = false, joinBlock = JoinBlock.TooNew, availableAt = NOW + 5.days))
        val vm = vm(program)
        settle()

        assertEquals(BuilderPage.Join(block = JoinBlock.TooNew, until = "November 3", rejoin = false), vm.state.page)
    }

    @Test
    fun theServerSayingFullShowsOnThePage() = runUnitTest {
        val program = FakeProgram()
        val vm = vm(program)
        settle()
        program.next = BuilderResult.Blocked(JoinBlock.Full)
        vm.takeAction(BuilderProgramAction.AgreeChanged(true))
        vm.takeAction(BuilderProgramAction.JoinTapped)
        settle()

        assertEquals(JoinBlock.Full, assertIs<BuilderPage.Join>(vm.state.page).block)
    }

    @Test
    fun leavingAsksFirstThenOffersToJoinAgain() = runUnitTest {
        val program = FakeProgram(member)
        val vm = vm(program)
        settle()

        vm.takeAction(BuilderProgramAction.LeaveTapped)
        assertTrue(vm.state.confirmingLeave)
        vm.takeAction(BuilderProgramAction.LeaveConfirmed)
        settle()

        assertEquals(1, program.left)
        assertEquals(true, assertIs<BuilderPage.Join>(vm.state.page).rejoin)
    }

    @Test
    fun offlineKeepsThePageAndOffersARetry() = runUnitTest {
        val program = FakeProgram(member)
        program.next = BuilderResult.Failed(offline = true)
        val vm = vm(program)
        settle()

        assertEquals(BuilderProblem.Offline, vm.state.problem)
        assertIs<BuilderPage.Member>(vm.state.page)
    }

    @Test
    fun builderProRunOutReadsAsEnded() {
        val page = BuilderPages.of(member.copy(proUntil = NOW - 3.days), Utc, NOW)

        assertFalse(assertIs<BuilderPage.Member>(page).proActive)
    }
}

class JoinBuilderPromptTest {

    @Test
    fun itFollowsAFoodReportOrAnApprovedFoodAndNothingElse() = kotlinx.coroutines.test.runTest {
        val quiet = JoinBuilderPrompt(flag(true), FakeProgram(), Submitted())
        assertFalse(quiet.isEligible(moment = null))
        assertTrue(quiet.isEligible(PromptEvent.FoodReportSent))

        val approved = JoinBuilderPrompt(flag(true), FakeProgram(), Submitted(mapOf("food" to ReviewStatus.Approved)))
        assertTrue(approved.isEligible(moment = null))
    }

    @Test
    fun neverForABuilderSomeoneWhoCanNotJoinOrWithTheFlagOff() = kotlinx.coroutines.test.runTest {
        assertFalse(JoinBuilderPrompt(flag(true), FakeProgram(member), Submitted()).isEligible(PromptEvent.FoodReportSent))
        assertFalse(JoinBuilderPrompt(flag(true), FakeProgram(newcomer.copy(canJoin = false)), Submitted()).isEligible(PromptEvent.FoodReportSent))
        assertFalse(JoinBuilderPrompt(flag(false), FakeProgram(), Submitted()).isEligible(PromptEvent.FoodReportSent))
        assertFalse(JoinBuilderPrompt(flag(true), FakeProgram(initial = null), Submitted()).isEligible(PromptEvent.FoodReportSent))
    }

    @Test
    fun itShowsOnceEver() {
        val prompt = JoinBuilderPrompt(flag(true), FakeProgram(), Submitted())

        assertEquals(1, prompt.frequency.maxShows)
        assertEquals("join_builder", prompt.id)
    }
}
