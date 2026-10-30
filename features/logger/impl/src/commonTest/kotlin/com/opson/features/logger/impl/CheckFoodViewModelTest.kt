package com.opson.features.logger.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import com.opson.libraries.scanner.TextRecognition
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_unit_kcal

class CheckFoodViewModelTest : CoroutineTest() {

    /** The granola as Opson has it, per 100 g: the panel's 230 kcal, 3, 37 and 8 g in 55 g. */
    private val granola = Food(
        id = "granola-1",
        source = FoodSource.UsdaBranded,
        name = "Maple granola",
        brand = "Nature's Path",
        per100g = NutrientProfile(kcal = 418.0, protein = 5.5, carbs = 67.3, fat = 14.5),
    )
    private val catalog = FakeFoodCatalog(foods = listOf(granola))
    private val recognizer = FakeTextRecognizer(GRANOLA_PANEL)
    private val checks = FakeFoodChecks()
    private val reports = RecordingFoodReports()
    private val policy = FakePolicySource()
    private val front = MealPhoto(byteArrayOf(1))
    private val label = MealPhoto(byteArrayOf(9))

    private fun vm() = CheckFoodViewModel(
        route = CheckFoodRoute(granola.id, granola.name),
        catalog = catalog,
        phoneReader = phoneReader(recognizer),
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

    /** The front, then the label, as the sheet asks for them. */
    private fun TestScope.photographed(vm: CheckFoodViewModel = vm()): CheckFoodViewModel {
        settle()
        assertEquals(CheckStage.Photos(PackSide.Front), vm.state.stage)
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(front)))
        settle()
        assertEquals(CheckStage.Photos(PackSide.Label), vm.state.stage)
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(label)))
        settle()
        return vm
    }

    private fun opsonHas(kcal: Double) {
        catalog.foods = listOf(granola.copy(per100g = granola.per100g.copy(kcal = kcal)))
    }

    @Test
    fun aLabelThatMatchesIsVerifiedWithItsTextAndBothPhotos() = runUnitTest {
        val vm = photographed()
        val review = assertIs<CheckStage.Review>(vm.state.stage)
        assertTrue(review.matches)
        assertNull(review.form, "nothing was wrong")

        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        assertEquals(CheckStage.Done(CheckDone.Confirmed), vm.state.stage)
        val sent = checks.sent.single()
        assertEquals(GRANOLA_PANEL.size, sent.lines.size)
        assertNull(sent.problem)
        assertContentEquals(front.jpeg, sent.front)
        assertContentEquals(label.jpeg, sent.label)
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun aMatchThatWaitsForASecondCheckSaysSoWithoutTheVerifiedMark() = runUnitTest {
        checks.answer = FoodCheckResult.AwaitingAgreement
        val vm = photographed()

        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        assertEquals(CheckStage.Done(CheckDone.AwaitingAgreement), vm.state.stage)
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun aRetryKeepsTheCheckIdButNewPhotosAreANewCheck() = runUnitTest {
        checks.answer = FoodCheckResult.Failed(offline = true)
        val vm = photographed()
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()
        assertEquals(1, checks.sent.map { it.id }.distinct().size, "a retry is the same check")

        vm.takeAction(CheckFoodAction.RetakeTapped)
        photographed(vm)
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        assertEquals(3, checks.sent.size)
        assertNotEquals(checks.sent.first().id, checks.sent.last().id, "the server would answer the new photos with the old check")
    }

    @Test
    fun aLabelThatDiffersOpensSomethingWasWrongWithCaloriesMarkedAndTheLabelsFigures() = runUnitTest {
        opsonHas(kcal = 300.0)
        val vm = photographed()

        val review = assertIs<CheckStage.Review>(vm.state.stage)
        assertFalse(review.matches)
        assertTrue(review.perServing)
        val energy = review.rows.first()
        assertEquals(UnitAmount("230", Res.string.submit_unit_kcal), energy.pack)
        assertEquals(listOf(true, false, false, false), review.rows.map { it.differs })
        val form = assertNotNull(review.form)
        assertEquals(FieldIssue.Wrong, form.fields.single { it.field == VerifyField.Calories }.issue)
        assertEquals(listOf(VerifyInputUi(VerifyForm.KCAL, "230")), form.values)
        assertEquals("2/3 cup", form.basis?.amount)
    }

    @Test
    fun somethingWrongGoesToAReviewerAgainstTheFoodThatIsThere() = runUnitTest {
        opsonHas(kcal = 300.0)
        checks.answer = FoodCheckResult.InReview
        val vm = photographed()
        vm.takeAction(CheckFoodAction.IssuePicked(VerifyField.Ingredients, FieldIssue.Missing))
        vm.takeAction(CheckFoodAction.IssuePicked(VerifyField.NameBrand, FieldIssue.Wrong))
        vm.takeAction(CheckFoodAction.ValueTyped(VerifyForm.BRAND, "Nature's Path Organic"))
        vm.takeAction(CheckFoodAction.NoteTyped("  New recipe  "))
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        assertEquals(CheckStage.Done(CheckDone.InReview), vm.state.stage)
        val problem = assertNotNull(checks.sent.single().problem)
        assertEquals(setOf(VerifyField.Calories, VerifyField.NameBrand), problem.wrong)
        assertEquals(setOf(VerifyField.Ingredients), problem.missing)
        val values = assertNotNull(problem.values)
        assertEquals(230.0, values.kcal)
        assertEquals(55.0, values.basisGrams)
        assertEquals("Nature's Path Organic", values.brand)
        assertEquals("Maple granola", values.name)
        assertNull(values.protein, "only the values the marked parts ask for")
        assertEquals("New recipe", problem.note)
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun aServerFromBeforeReviewsGetsTheProblemAsAReportWithBothPhotos() = runUnitTest {
        opsonHas(kcal = 300.0)
        checks.answer = FoodCheckResult.Unconfirmed(listOf("energy_kcal"))
        val vm = photographed()
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        val report = reports.sent.single()
        assertEquals(listOf(FoodReportReason.WrongNumbers), report.reasons)
        val suggested = assertNotNull(report.suggested)
        assertEquals(230.0, suggested.kcal)
        assertEquals(55.0, suggested.basisGrams)
        assertContentEquals(label.jpeg, report.photo)
        assertContentEquals(front.jpeg, report.frontPhoto)
        assertEquals(CheckStage.Done(CheckDone.Corrected), vm.state.stage)
    }

    @Test
    fun turningSomethingWasWrongOffSendsAPlainCheck() = runUnitTest {
        opsonHas(kcal = 300.0)
        checks.answer = FoodCheckResult.Unconfirmed(listOf("energy_kcal"))
        val vm = photographed()
        vm.takeAction(CheckFoodAction.SomethingWrongChanged(false))
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()

        assertNull(checks.sent.single().problem)
        assertEquals(CheckStage.Done(CheckDone.Unconfirmed), vm.state.stage)
        assertTrue(reports.sent.isEmpty())
    }

    @Test
    fun somethingWrongOnAMatchWaitsUntilItSaysWhat() = runUnitTest {
        val vm = photographed()
        vm.takeAction(CheckFoodAction.SomethingWrongChanged(true))
        assertFalse(assertIs<CheckStage.Review>(vm.state.stage).canSend)

        vm.takeAction(CheckFoodAction.IssuePicked(VerifyField.Barcode, FieldIssue.Missing))
        assertTrue(assertIs<CheckStage.Review>(vm.state.stage).canSend)
        // The same answer again takes it back.
        vm.takeAction(CheckFoodAction.IssuePicked(VerifyField.Barcode, FieldIssue.Missing))
        assertFalse(assertIs<CheckStage.Review>(vm.state.stage).canSend)
    }

    @Test
    fun inAModeThatHidesNumbersTheLabelsOwnFiguresGoUnseen() = runUnitTest {
        policy.policy.value = policy(PresetId.NoNumbers)
        opsonHas(kcal = 300.0)
        checks.answer = FoodCheckResult.InReview
        val vm = photographed()

        val review = assertIs<CheckStage.Review>(vm.state.stage)
        assertEquals(emptyList(), review.rows)
        assertEquals(emptyList(), assertNotNull(review.form).values, "no number to type")

        vm.takeAction(CheckFoodAction.SendTapped)
        settle()
        assertEquals(230.0, checks.sent.single().problem?.values?.kcal)
    }

    @Test
    fun theFrontCanBeSkipped() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(CheckFoodAction.SkipFrontTapped)
        assertEquals(CheckStage.Photos(PackSide.Label), vm.state.stage)
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(label)))
        settle()
        vm.takeAction(CheckFoodAction.SendTapped)
        settle()
        assertNull(checks.sent.single().front)
    }

    @Test
    fun aPhotoWithNoPanelAsksForAnotherAndAPhoneWithoutOcrSaysSo() = runUnitTest {
        recognizer.result = TextRecognition.Read(
            com.opson.libraries.scanner.RecognizedText(emptyList(), 100, 100, "vision"),
        )
        val vm = photographed()
        assertEquals(CheckStage.Unreadable, vm.state.stage)

        recognizer.result = TextRecognition.Unavailable
        vm.takeAction(CheckFoodAction.PhotoAnswered(PhotoPickResult.Picked(label)))
        settle()
        assertEquals(CheckProblem.Unavailable, vm.state.problem)
    }
}
