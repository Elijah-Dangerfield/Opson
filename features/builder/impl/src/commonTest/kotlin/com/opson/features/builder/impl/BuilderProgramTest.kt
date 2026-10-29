package com.opson.features.builder.impl

import com.opson.features.builder.BuilderProgramRoute
import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderApplication
import com.opson.libraries.foods.BuilderApplicationForm
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelRegion
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.ShoppingFrequency
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.prompts.PromptEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-29T12:00:00Z")

private val newcomer = BuilderStanding(status = BuilderStatus.None, trust = BuilderTrust.Anyone, canApply = true)
private val pending = newcomer.copy(
    canApply = false,
    joinBlock = JoinBlock.Pending,
    application = BuilderApplication(id = "a1", status = ApplicationStatus.Pending, submittedAt = NOW),
)
private val accepted = BuilderApplication(id = "a1", status = ApplicationStatus.Accepted, submittedAt = NOW - 3.days, decidedAt = NOW - 1.days)
private val member = BuilderStanding(
    status = BuilderStatus.Active,
    trust = BuilderTrust.Builder,
    joinedAt = NOW,
    proUntil = NOW + 30.days,
    accepted30d = 7,
    canApply = false,
    application = accepted,
    foodsAdded = 11,
    checks = 4,
    rejected = 1,
)
private val declined = newcomer.copy(
    canApply = false,
    joinBlock = JoinBlock.Declined,
    availableAt = NOW + 29.days,
    application = BuilderApplication(
        id = "a1",
        status = ApplicationStatus.Declined,
        submittedAt = NOW - 2.days,
        decidedAt = NOW - 1.days,
        note = "Not this month.",
        reapplyAt = NOW + 29.days,
    ),
)

/** Answers [next] to every call (or what each call would do), and remembers what was sent. */
private class FakeProgram(initial: BuilderStanding? = newcomer) : BuilderProgram {
    private val state = MutableStateFlow(initial)
    override val standing: StateFlow<BuilderStanding?> = state
    var next: BuilderResult? = null
    val applications = mutableListOf<BuilderApplicationForm>()
    var left = 0
    var withdrawn = 0
    val seen = mutableSetOf<String>()

    fun becomes(standing: BuilderStanding) {
        state.value = standing
    }

    private fun answer(default: BuilderStanding?): BuilderResult =
        (next ?: default?.let { BuilderResult.Done(it) } ?: BuilderResult.Unavailable).also { if (it is BuilderResult.Done) state.value = it.standing }

    override suspend fun refresh(): BuilderResult = answer(state.value)

    override suspend fun sendApplication(form: BuilderApplicationForm): BuilderResult {
        applications += form
        return answer(pending)
    }

    override suspend fun withdraw(): BuilderResult {
        withdrawn++
        return answer(newcomer.copy(application = pending.application?.copy(status = ApplicationStatus.Withdrawn)))
    }

    override suspend fun leave(): BuilderResult {
        left++
        return answer(newcomer.copy(status = BuilderStatus.Left))
    }

    override suspend fun setPhotoRetention(keep: Boolean): BuilderResult = answer(state.value?.copy(photoRetention = keep))

    override suspend fun decisionSeen(id: String): Boolean = id in seen

    override suspend fun markDecisionSeen(id: String) {
        seen += id
    }
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

    private fun vm(program: FakeProgram, via: String = BuilderProgramRoute.VIA_MORE) = BuilderProgramViewModel(BuilderProgramRoute(via), program, Utc, fixed)

    private fun TestScope.events(vm: BuilderProgramViewModel): List<BuilderProgramEvent> {
        val events = mutableListOf<BuilderProgramEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun beforeApplyingThePageOffersApplyAndTheRules() = runUnitTest {
        val vm = vm(FakeProgram(), via = BuilderProgramRoute.VIA_PROMPT)
        val events = events(vm)
        settle()
        assertEquals(BuilderPage.Intro(block = null, until = null, returning = false), vm.state.page)

        vm.takeAction(BuilderProgramAction.ApplyTapped)
        vm.takeAction(BuilderProgramAction.RulesTapped)
        settle()

        assertEquals(listOf(BuilderProgramEvent.OpenApply(BuilderProgramRoute.VIA_PROMPT), BuilderProgramEvent.OpenRules), events)
    }

    @Test
    fun anApplicationSentFromTheSheetShowsAsReceived() = runUnitTest {
        val program = FakeProgram()
        val vm = vm(program)
        settle()

        program.becomes(pending)
        settle()

        assertEquals(BuilderPage.Applied(sentOn = "October 29"), vm.state.page)
    }

    @Test
    fun withdrawingAsksFirstThenOffersApplyAgain() = runUnitTest {
        val program = FakeProgram(pending)
        val vm = vm(program)
        settle()

        vm.takeAction(BuilderProgramAction.WithdrawTapped)
        assertEquals(BuilderConfirm.Withdraw, vm.state.confirming)
        vm.takeAction(BuilderProgramAction.ConfirmCancelled)
        assertEquals(0, program.withdrawn)
        vm.takeAction(BuilderProgramAction.WithdrawTapped)
        vm.takeAction(BuilderProgramAction.Confirmed)
        settle()

        assertEquals(1, program.withdrawn)
        assertEquals(BuilderPage.Intro(block = null, until = null, returning = false), vm.state.page)
    }

    @Test
    fun aBlockedApplicantSeesWhyAndApplyDoesNothing() = runUnitTest {
        val vm = vm(FakeProgram(newcomer.copy(canApply = false, joinBlock = JoinBlock.TooNew, availableAt = NOW + 5.days)))
        val events = events(vm)
        settle()

        assertEquals(BuilderPage.Intro(block = JoinBlock.TooNew, until = "November 3", returning = false), vm.state.page)
        vm.takeAction(BuilderProgramAction.ApplyTapped)
        settle()
        assertTrue(events.isEmpty())
    }

    @Test
    fun aDeclineShowsTheNoteAndWhenToTryAgainAndIsSeenOnce() = runUnitTest {
        val program = FakeProgram(declined)
        val vm = vm(program)
        settle()

        assertEquals(BuilderPage.Declined(note = "Not this month.", againOn = "November 27"), vm.state.page)
        assertEquals(setOf("a1"), program.seen)
    }

    @Test
    fun anAcceptanceWelcomesOnceAndTheWelcomeStaysWhileThePageIsOpen() = runUnitTest {
        val program = FakeProgram(member)
        val vm = vm(program)
        settle()

        val home = assertIs<BuilderPage.Home>(vm.state.page)
        assertTrue(home.welcome)
        assertEquals(setOf("a1"), program.seen)
        vm.takeAction(BuilderProgramAction.Load)
        settle()
        assertTrue(assertIs<BuilderPage.Home>(vm.state.page).welcome, "a refresh keeps it up")

        val again = vm(program)
        settle()
        assertFalse(assertIs<BuilderPage.Home>(again.state.page).welcome, "the next visit is just home")
    }

    @Test
    fun theHomeCountsTheirWorkAndTheirPro() {
        val page = assertIs<BuilderPage.Home>(BuilderPages.of(member, Utc, NOW, welcome = false))

        assertEquals(7, page.accepted)
        assertEquals("November 28", page.proUntil)
        assertTrue(page.proActive)
        assertEquals(11, page.foodsAdded)
        assertEquals(4, page.checks)
        assertEquals(92, page.acceptedPercent)
        assertNull(BuilderPages.acceptedPercent(0, 0), "nothing decided yet")
    }

    @Test
    fun builderProRunOutReadsAsEnded() {
        val page = BuilderPages.of(member.copy(proUntil = NOW - 3.days), Utc, NOW, welcome = false)

        assertFalse(assertIs<BuilderPage.Home>(page).proActive)
    }

    @Test
    fun theHomesTwoThingsToDoOpenWhereTheyShould() = runUnitTest {
        val vm = vm(FakeProgram(member))
        val events = events(vm)
        settle()

        vm.takeAction(BuilderProgramAction.AddFoodTapped)
        vm.takeAction(BuilderProgramAction.CheckFoodTapped)
        settle()

        assertEquals(listOf(BuilderProgramEvent.AddFood, BuilderProgramEvent.CheckFood("2026-10-29")), events)
    }

    @Test
    fun leavingAsksFirstThenOffersToApplyAgain() = runUnitTest {
        val program = FakeProgram(member)
        val vm = vm(program)
        settle()

        vm.takeAction(BuilderProgramAction.LeaveTapped)
        assertEquals(BuilderConfirm.Leave, vm.state.confirming)
        vm.takeAction(BuilderProgramAction.Confirmed)
        settle()

        assertEquals(1, program.left)
        assertEquals(true, assertIs<BuilderPage.Intro>(vm.state.page).returning)
    }

    @Test
    fun offlineKeepsThePageAndOffersARetry() = runUnitTest {
        val program = FakeProgram(member)
        program.next = BuilderResult.Failed(offline = true)
        val vm = vm(program)
        settle()

        assertEquals(BuilderProblem.Offline, vm.state.problem)
        assertIs<BuilderPage.Home>(vm.state.page)
    }

    @Test
    fun removedReadsAsRemovedWhateverTheApplicationSays() {
        assertEquals(BuilderPage.Removed, BuilderPages.of(newcomer.copy(status = BuilderStatus.Removed, application = accepted), Utc, NOW, welcome = false))
        assertEquals(BuilderPage.Removed, BuilderPages.of(newcomer.copy(canApply = false, joinBlock = JoinBlock.Removed), Utc, NOW, welcome = false))
    }
}

class BuilderApplyViewModelTest : CoroutineTest() {

    private fun TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    @Test
    fun sendWaitsForBothAnswersAndTheRulesThenSendsTheForm() = runUnitTest {
        val program = FakeProgram()
        val vm = BuilderApplyViewModel(BuilderApplyRoute(via = BuilderProgramRoute.VIA_REPORT), program)
        val events = mutableListOf<BuilderApplyEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(BuilderApplyAction.ShoppingPicked(ShoppingFrequency.Monthly))
        vm.takeAction(BuilderApplyAction.RegionPicked(LabelRegion.Canada))
        vm.takeAction(BuilderApplyAction.SendTapped)
        settle()
        assertTrue(program.applications.isEmpty(), "not before the rules are accepted")
        assertFalse(vm.state.canSend)

        vm.takeAction(BuilderApplyAction.MotivationTyped("   "))
        vm.takeAction(BuilderApplyAction.AgreeChanged(true))
        vm.takeAction(BuilderApplyAction.RetentionChanged(true))
        vm.takeAction(BuilderApplyAction.SendTapped)
        settle()

        val form = program.applications.single()
        assertEquals(ShoppingFrequency.Monthly, form.shopping)
        assertEquals(LabelRegion.Canada, form.region)
        assertNull(form.motivation, "a blank line is no line")
        assertTrue(form.photoRetention)
        assertEquals(BuilderProgramRoute.VIA_REPORT, form.via)
        assertEquals(listOf<BuilderApplyEvent>(BuilderApplyEvent.Sent), events)
    }

    @Test
    fun aRefusalStaysOnTheSheetWithItsReason() = runUnitTest {
        val program = FakeProgram()
        program.next = BuilderResult.Blocked(JoinBlock.Declined)
        val vm = BuilderApplyViewModel(BuilderApplyRoute(via = BuilderProgramRoute.VIA_MORE), program)

        vm.takeAction(BuilderApplyAction.ShoppingPicked(ShoppingFrequency.Weekly))
        vm.takeAction(BuilderApplyAction.RegionPicked(LabelRegion.Uk))
        vm.takeAction(BuilderApplyAction.AgreeChanged(true))
        vm.takeAction(BuilderApplyAction.SendTapped)
        settle()

        assertEquals(ApplyProblem.Blocked(JoinBlock.Declined), vm.state.problem)
        assertFalse(vm.state.sending)
    }

    @Test
    fun theLineAboutWhyStopsAtWhatTheServerKeeps() = runUnitTest {
        val vm = BuilderApplyViewModel(BuilderApplyRoute(via = BuilderProgramRoute.VIA_MORE), FakeProgram())

        vm.takeAction(BuilderApplyAction.MotivationTyped("x".repeat(600)))
        settle()

        assertEquals(BuilderApplyViewModel.MAX_MOTIVATION, vm.state.motivation.length)
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
    fun neverForABuilderSomeoneWhoCanNotApplyOrWithTheFlagOff() = kotlinx.coroutines.test.runTest {
        assertFalse(JoinBuilderPrompt(flag(true), FakeProgram(member), Submitted()).isEligible(PromptEvent.FoodReportSent))
        assertFalse(JoinBuilderPrompt(flag(true), FakeProgram(pending), Submitted()).isEligible(PromptEvent.FoodReportSent))
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

class BuilderDecisionPromptTest {

    @Test
    fun anAcceptanceIsToldOnceWhileItIsNewsAndStartMarksItSeen() = kotlinx.coroutines.test.runTest {
        val program = FakeProgram(member)
        val prompt = BuilderAcceptedPrompt(flag(true), program, fixed)

        assertTrue(prompt.isEligible())
        prompt.accept()
        assertEquals(setOf("a1"), program.seen)
        assertFalse(prompt.isEligible(), "seen now")
        assertEquals(1, prompt.frequency.maxShows)
    }

    @Test
    fun anOldDecisionOneAlreadyShownOrTheFlagOffIsNotNews() = kotlinx.coroutines.test.runTest {
        val old = member.copy(application = accepted.copy(decidedAt = NOW - 20.days))
        assertFalse(BuilderAcceptedPrompt(flag(true), FakeProgram(old), fixed).isEligible())
        assertFalse(BuilderAcceptedPrompt(flag(false), FakeProgram(member), fixed).isEligible())
        assertFalse(BuilderAcceptedPrompt(flag(true), FakeProgram(member).apply { seen += "a1" }, fixed).isEligible())
        // Accepted and then removed before Today opened: nothing to welcome them to.
        assertFalse(BuilderAcceptedPrompt(flag(true), FakeProgram(member.copy(status = BuilderStatus.Removed)), fixed).isEligible())
    }

    @Test
    fun aDeclineHasItsOwnGentlePromptAndAnAcceptanceIsNotIt() = kotlinx.coroutines.test.runTest {
        assertTrue(BuilderDeclinedPrompt(flag(true), FakeProgram(declined), fixed).isEligible())
        assertFalse(BuilderDeclinedPrompt(flag(true), FakeProgram(member), fixed).isEligible())
        assertFalse(BuilderAcceptedPrompt(flag(true), FakeProgram(declined), fixed).isEligible())
        assertFalse(BuilderDeclinedPrompt(flag(true), FakeProgram(pending), fixed).isEligible())
    }
}
