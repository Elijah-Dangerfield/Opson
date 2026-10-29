package com.opson.features.logger.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import com.opson.libraries.scanner.TextRecognition
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CheckFoodViewModelTest : CoroutineTest() {

    /** The granola as Opson has it, per 100 g: the panel's 230 kcal, 3, 37 and 8 g in 55 g. */
    private val granola = Food(
        id = "granola-1",
        source = FoodSource.UsdaBranded,
        name = "Maple granola",
        per100g = NutrientProfile(kcal = 418.0, protein = 5.5, carbs = 67.3, fat = 14.5),
    )
    private val catalog = FakeFoodCatalog(foods = listOf(granola))
    private val recognizer = FakeTextRecognizer(GRANOLA_PANEL)
    private val checks = FakeFoodChecks()
    private val reports = RecordingFoodReports()
    private val policy = FakePolicySource()
    private val photo = MealPhoto(byteArrayOf(9))

    private fun vm() = CheckFoodViewModel(
        route = CheckFoodRoute(granola.id, granola.name),
        catalog = catalog,
        phoneReader = PhoneLabelReader(recognizer),
        checks = checks,
        reports = reports,
        permissions = FakeCameraPermissions(),
        picker = FakeMealPhotoPicker(),
        policySource = policy,
    )

    private fun TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    private fun TestScope.photographed(vm: CheckFoodViewModel = vm()): CheckFoodViewModel {
        settle()
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(photo)))
        settle()
        return vm
    }

    @Test
    fun aLabelThatMatchesIsSentAsACheckWithItsTextAndNoPhoto() = runUnitTest {
        val vm = photographed()
        assertEquals(CheckStage.Matches, vm.state.stage)

        vm.takeAction(CheckFoodAction.SendCheckTapped)
        settle()

        assertEquals(CheckStage.Done(CheckDone.Confirmed), vm.state.stage)
        assertEquals(GRANOLA_PANEL.size, checks.sent.single().size)
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun aCheckTheServerCouldNotGroundSaysItDoesNotCount() = runUnitTest {
        checks.answer = FoodCheckResult.Unconfirmed(listOf("fat_g"))
        val vm = photographed()
        vm.takeAction(CheckFoodAction.SendCheckTapped)
        settle()

        assertEquals(CheckStage.Done(CheckDone.Unconfirmed), vm.state.stage)
    }

    @Test
    fun aLabelThatDiffersShowsTheRowsAndSendsACorrectionWithThePhoto() = runUnitTest {
        catalog.foods = listOf(granola.copy(per100g = granola.per100g.copy(kcal = 300.0)))
        val vm = photographed()
        val differs = assertIs<CheckStage.Differs>(vm.state.stage)
        assertTrue(differs.perServing)
        val energy = differs.rows.first()
        assertEquals("230 kcal", energy.pack)
        assertTrue(energy.differs)
        assertEquals(listOf(true, false, false, false), differs.rows.map { it.differs })

        vm.takeAction(CheckFoodAction.SendCorrectionTapped)
        settle()

        val report = reports.sent.single()
        assertEquals(FoodReportReason.WrongCalories, report.reason)
        val suggested = assertNotNull(report.suggested)
        assertEquals(100.0, suggested.basisGrams)
        assertEquals(418.0, suggested.kcal!!, 1.0)
        assertEquals(55.0, suggested.servingGrams)
        assertEquals(photo.jpeg.toList(), report.photo?.toList())
        assertEquals(CheckStage.Done(CheckDone.Corrected), vm.state.stage)
    }

    @Test
    fun inAModeThatHidesNumbersADifferenceIsSaidInWords() = runUnitTest {
        policy.policy.value = policy(PresetId.NoNumbers)
        catalog.foods = listOf(granola.copy(per100g = granola.per100g.copy(kcal = 300.0)))
        val vm = photographed()

        assertEquals(emptyList(), assertIs<CheckStage.Differs>(vm.state.stage).rows)
    }

    @Test
    fun aPhotoWithNoPanelAsksForAnotherAndAPhoneWithoutOcrSaysSo() = runUnitTest {
        recognizer.result = TextRecognition.Read(
            com.opson.libraries.scanner.RecognizedText(emptyList(), 100, 100, "vision"),
        )
        val vm = photographed()
        assertEquals(CheckStage.Unreadable, vm.state.stage)

        recognizer.result = TextRecognition.Unavailable
        vm.takeAction(CheckFoodAction.RetakeTapped)
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(photo)))
        settle()
        assertEquals(CheckProblem.Unavailable, vm.state.problem)
    }
}
