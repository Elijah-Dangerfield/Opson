package com.opson.features.logger.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.foods.SuggestedFoodValues
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodReportViewModelTest : CoroutineTest() {

    private class FakeFoodReports(var answer: FoodReportResult = FoodReportResult.Sent(applied = false)) : FoodReports {
        val sent = mutableListOf<FoodReport>()
        override suspend fun send(report: FoodReport): FoodReportResult {
            sent += report
            return answer
        }
    }

    private val reports = FakeFoodReports()
    private val permissions = FakeCameraPermissions()
    private val moments = mutableListOf<PromptEvent>()
    private val route = FoodReportRoute(foodId = "f1", name = "Monster Energy Zero Ultra", amountText = "8.0 fl oz (240 ml)", amountGrams = 240)

    private fun vm(preset: PresetId = PresetId.Full, route: FoodReportRoute = this.route) = FoodReportViewModel(
        route = route,
        reports = reports,
        permissions = permissions,
        promptEvents = PromptEvents { moments += it },
        picker = FakeMealPhotoPicker(),
        policySource = FakePolicySource(preset),
    )

    @Test
    fun sendWaitsForAReason() = runUnitTest {
        val vm = vm()
        assertFalse(vm.stateFlow.value.canSend)
        vm.takeAction(FoodReportAction.SendTapped)
        assertTrue(reports.sent.isEmpty())

        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.Duplicate))
        assertTrue(vm.stateFlow.value.canSend)
        assertNull(vm.stateFlow.value.values, "a duplicate asks for no numbers")
    }

    @Test
    fun wrongCaloriesSendsTheNumbersForTheAmountOnScreen_photoAndNote() = runUnitTest {
        val vm = vm()
        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.WrongCalories))
        assertEquals("8.0 fl oz (240 ml)", vm.stateFlow.value.values?.basis)
        vm.takeAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.KCAL, "5"))
        vm.takeAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.CARBS, "2,0x"))
        vm.takeAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.SERVING, "1 can, 473 ml"))
        vm.takeAction(FoodReportAction.NoteTyped("  The can says 10  "))
        vm.takeAction(FoodReportAction.PhotoAnswered(PhotoPickResult.Picked(MealPhoto(byteArrayOf(7)))))
        assertEquals("2,0", vm.stateFlow.value.values?.carbs, "only digits and one point")

        vm.takeAction(FoodReportAction.SendTapped)

        val report = reports.sent.single()
        assertEquals("f1", report.foodId)
        assertEquals(FoodReportReason.WrongCalories, report.reason)
        assertEquals(
            SuggestedFoodValues(basisLabel = "8.0 fl oz (240 ml)", basisGrams = 240.0, kcal = 5.0, carbs = 2.0, servingLabel = "1 can, 473 ml"),
            report.suggested,
        )
        assertEquals("The can says 10", report.note)
        assertContentEquals(byteArrayOf(7), report.photo)
        assertEquals(listOf(PromptEvent.ReportSent, PromptEvent.FoodReportSent), moments, "a sent report is the moment the Builder prompt follows")
    }

    @Test
    fun aModeThatHidesNumbersNeverAsksForThemOrSendsThem() = runUnitTest {
        val vm = vm(PresetId.NoNumbers)
        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.WrongCalories))
        val values = vm.stateFlow.value.values
        assertFalse(values!!.numbers)
        vm.takeAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.KCAL, "5"))
        assertEquals("", vm.stateFlow.value.values?.kcal)
        vm.takeAction(FoodReportAction.FieldTyped(FoodReportStateBuilder.SERVING, "1 can"))
        vm.takeAction(FoodReportAction.SendTapped)
        assertEquals(SuggestedFoodValues(basisLabel = "8.0 fl oz (240 ml)", basisGrams = 240.0, servingLabel = "1 can"), reports.sent.single().suggested)
    }

    @Test
    fun offlineKeepsEverythingAndTryingAgainSendsTheSameReport() = runUnitTest {
        reports.answer = FoodReportResult.Failed(offline = true)
        val vm = vm()
        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.WrongName))
        vm.takeAction(FoodReportAction.NoteTyped("It's the mango one"))
        vm.takeAction(FoodReportAction.SendTapped)

        val state = vm.stateFlow.value
        assertEquals(ReportProblem.Offline, state.problem)
        assertEquals("It's the mango one", state.note)
        assertFalse(state.sending)
        assertTrue(state.canSend)
        assertTrue(moments.isEmpty())

        reports.answer = FoodReportResult.Sent(applied = false)
        vm.takeAction(FoodReportAction.SendTapped)
        assertEquals(1, reports.sent.map { it.id }.distinct().size, "the same id, so the server never files it twice")
        assertEquals(listOf(PromptEvent.ReportSent, PromptEvent.FoodReportSent), moments)
    }

    @Test
    fun aFoodThatIsGoneCannotBeSentAgain() = runUnitTest {
        reports.answer = FoodReportResult.Gone
        val vm = vm()
        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.Other))
        vm.takeAction(FoodReportAction.SendTapped)
        assertEquals(ReportProblem.Gone, vm.stateFlow.value.problem)
        assertFalse(vm.stateFlow.value.canSend)
        vm.takeAction(FoodReportAction.ReasonPicked(FoodReportReason.NotFood))
        assertEquals(ReportProblem.Gone, vm.stateFlow.value.problem)
    }

    @Test
    fun theCameraAsksFirst_andARefusalLeavesTheLibrary() = runUnitTest {
        permissions.permission = CameraPermission.NotAsked
        val vm = vm()
        vm.takeAction(FoodReportAction.TakePhotoTapped)
        vm.takeAction(FoodReportAction.CameraPermissionAnswered(CameraPermission.Denied(canAskAgain = false)))
        assertTrue(vm.stateFlow.value.cameraOff)
        vm.takeAction(FoodReportAction.PhotoAnswered(PhotoPickResult.Failed))
        assertTrue(vm.stateFlow.value.photoFailed)
        vm.takeAction(FoodReportAction.PhotoAnswered(PhotoPickResult.Picked(MealPhoto(byteArrayOf(1)))))
        assertFalse(vm.stateFlow.value.photoFailed)
        vm.takeAction(FoodReportAction.RemovePhotoTapped)
        assertNull(vm.stateFlow.value.photo)
    }

    @Test
    fun withoutAKnownWeightTheNumbersAreFor100Grams() {
        val model = FoodReportModel(name = "Oats", amountText = null, amountGrams = null, canTakePhoto = true, reason = FoodReportReason.WrongNutrients)
        assertNull(FoodReportStateBuilder.basis(model))
        assertEquals("1 cup (81 g)", FoodReportStateBuilder.basis(model.copy(amountText = "1 cup", amountGrams = 81.0)))
        assertEquals("150 g", FoodReportStateBuilder.basis(model.copy(amountText = "150 g", amountGrams = 150.0)))
    }

    @Test
    fun theDetailHandsOverTheAmountAsWholeGrams() {
        assertEquals(FoodReportRoute("f1", "Oats", "1 cup", 81), ReportTarget("f1", "Oats", "1 cup", 80.6).route())
        assertEquals(0, ReportTarget("f1", "Oats", null, null).route().amountGrams)
    }
}
