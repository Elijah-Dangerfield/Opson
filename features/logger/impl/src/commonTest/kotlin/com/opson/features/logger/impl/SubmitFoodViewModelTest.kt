package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class SubmitFoodViewModelTest : CoroutineTest() {
    private val labels = FakeFoodLabels()
    private val customFoods = FakeCustomFoodRepository()
    private val diary = FakeDiaryRepository()
    private val permissions = FakeCameraPermissions()
    private val telemetry = RecordingSubmitFoodTelemetry()
    private val policySource = FakePolicySource()
    private val front = MealPhoto(byteArrayOf(1))
    private val label = MealPhoto(byteArrayOf(2))

    private fun vm(barcode: String? = SubmitFoodFixtures.BARCODE, fromSearch: Boolean = false) = SubmitFoodViewModel(
        route = SubmitFoodRoute(LoggerFixtures.today.toString(), MealSlot.Breakfast, barcode = barcode, fromSearch = fromSearch),
        labels = labels,
        customFoods = customFoods,
        logger = FoodLogger(diary, FakeFoodCatalog(), FakeRecentLogEvents(), RecordingTelemetry(), Clock.fixed(NOON)),
        permissions = permissions,
        telemetry = telemetry,
        picker = FakeMealPhotoPicker(),
        policySource = policySource,
    )

    private fun TestScope.events(vm: SubmitFoodViewModel): List<SubmitFoodEvent> {
        val events = mutableListOf<SubmitFoodEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    private fun TestScope.withPhotos(vm: SubmitFoodViewModel = vm()): SubmitFoodViewModel {
        settle()
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Front, PhotoPickResult.Picked(front), fromCamera = true))
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, PhotoPickResult.Picked(label), fromCamera = false))
        settle()
        return vm
    }

    private fun TestScope.reviewing(vm: SubmitFoodViewModel = vm()): SubmitFoodViewModel = withPhotos(vm).also {
        it.takeAction(SubmitFoodAction.ReadTapped)
        settle()
    }

    private val SubmitFoodViewModel.form: SubmitForm get() = assertIs<SubmitStage.Review>(state.stage).form

    @Test
    fun itStartsOnThePhotosAndSaysWhereThePersonCameFrom() = runUnitTest {
        val scanned = vm()
        settle()
        assertEquals(SubmitStage.Photos(canRead = false), scanned.state.stage)
        vm(barcode = null, fromSearch = true)
        settle()
        assertEquals(listOf("started:barcode", "started:search"), telemetry.events)
    }

    @Test
    fun takingAPhotoFollowsTheCameraPermissionForThatSide() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(SubmitFoodAction.TakePhotoTapped(PackSide.Front))
        permissions.permission = CameraPermission.NotAsked
        vm.takeAction(SubmitFoodAction.TakePhotoTapped(PackSide.Label))
        settle()
        vm.takeAction(SubmitFoodAction.CameraPermissionAnswered(CameraPermission.Granted))
        vm.takeAction(SubmitFoodAction.ChooseFromLibraryTapped(PackSide.Front))
        settle()

        assertEquals(
            listOf(
                SubmitFoodEvent.LaunchCamera(PackSide.Front),
                SubmitFoodEvent.RequestCameraPermission,
                SubmitFoodEvent.LaunchCamera(PackSide.Label),
                SubmitFoodEvent.LaunchLibrary(PackSide.Front),
            ),
            events,
        )
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        vm.takeAction(SubmitFoodAction.TakePhotoTapped(PackSide.Front))
        settle()
        assertEquals(SubmitStage.CameraOff(canAskAgain = false), vm.state.stage)
    }

    @Test
    fun bothPhotosAreReadTogetherIntoTheForm() = runUnitTest {
        val vm = withPhotos()
        assertEquals(SubmitStage.Photos(canRead = true), vm.state.stage)
        assertEquals(listOf("photo:front:camera", "photo:label:library"), telemetry.events.drop(1))

        vm.takeAction(SubmitFoodAction.ReadTapped)
        settle()

        val (sentFront, sentLabel) = labels.parsed.single()
        assertContentEquals(front.jpeg, sentFront)
        assertContentEquals(label.jpeg, sentLabel)
        assertEquals("Oats & honey granola bar", vm.form.name.text)
        assertEquals("read:read:1:1", telemetry.events.last())
    }

    @Test
    fun retakingAPhotoKeepsTheOther() = runUnitTest {
        val vm = withPhotos()
        val again = MealPhoto(byteArrayOf(9))
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, PhotoPickResult.Picked(again), fromCamera = true))
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Front, PhotoPickResult.Cancelled, fromCamera = true))
        settle()

        assertEquals(front, vm.state.front)
        assertEquals(again, vm.state.label)
    }

    @Test
    fun aPhotoWithNoLabelAsksForTheLabelAgainOrToTypeItIn() = runUnitTest {
        labels.parse = SubmitFoodFixtures.read.copy(readable = false)
        val vm = reviewing()
        assertEquals(SubmitStage.NoLabel, vm.state.stage)

        vm.takeAction(SubmitFoodAction.TypeItInTapped)
        settle()
        assertEquals(ReviewNotice.ByHand, vm.form.notice)
        assertEquals("Oats & honey granola bar", vm.form.name.text, "what was read is kept")

        val retake = reviewing(vm())
        retake.takeAction(SubmitFoodAction.RetakeLabelTapped)
        settle()
        assertEquals(SubmitStage.Photos(canRead = false), retake.state.stage)
        assertEquals(front, retake.state.front)
    }

    @Test
    fun withNoReaderOnTheServerTheFormIsFilledByHand() = runUnitTest {
        labels.parse = LabelParse.Unavailable
        val vm = reviewing()

        assertEquals(ReviewNotice.Unavailable, vm.form.notice)
        assertEquals("", vm.form.name.text)
        assertEquals(SubmitFoodFixtures.BARCODE, vm.form.barcode.text, "the scanned code is kept")
        assertEquals("read:unavailable:0:0", telemetry.events.last())
    }

    @Test
    fun aFailedReadCanBeTriedAgain() = runUnitTest {
        labels.parse = LabelParse.Failed(offline = true)
        val vm = reviewing()
        assertEquals(SubmitStage.ReadFailed(offline = true), vm.state.stage)

        labels.parse = SubmitFoodFixtures.read
        vm.takeAction(SubmitFoodAction.TryAgainTapped)
        settle()
        assertEquals(2, labels.parsed.size)
        assertIs<SubmitStage.Review>(vm.state.stage)
    }

    @Test
    fun aScannedBarcodeCannotBeRetypedButOneFromSearchCan() = runUnitTest {
        val scanned = reviewing()
        scanned.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.BARCODE, "4006381333931"))
        settle()
        assertEquals(SubmitFoodFixtures.BARCODE, scanned.form.barcode.text)

        labels.parse = SubmitFoodFixtures.read.copy(facts = SubmitFoodFixtures.facts.copy(barcode = null))
        val searched = reviewing(vm(barcode = null, fromSearch = true))
        searched.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.BARCODE, "4006381333931"))
        settle()
        assertEquals("4006381333931", searched.form.barcode.text)
    }

    @Test
    fun theOneTapFixAndKeepWorkLive() = runUnitTest {
        val vm = reviewing()
        assertEquals("234 kcal", vm.form.checks.single().fix)

        vm.takeAction(SubmitFoodAction.KeepTapped(LabelCheckCode.EnergyMacrosMismatch, LabelNutrient.Energy.wire))
        settle()
        assertTrue(vm.form.checks.single().kept)

        vm.takeAction(SubmitFoodAction.FixTapped(LabelCheckCode.EnergyMacrosMismatch, LabelNutrient.Energy.wire))
        settle()
        assertEquals("234", vm.form.numbers!!.main.first().field.text)
        assertEquals(emptyList(), vm.form.checks)
    }

    @Test
    fun addingLogsTheFoodAsTheirOwnThenSendsItForReview() = runUnitTest {
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.KeepTapped(LabelCheckCode.EnergyMacrosMismatch, LabelNutrient.Energy.wire))
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        val custom = customFoods.foods.value.single()
        assertEquals("Oats & honey granola bar", custom.name)
        assertEquals(60.0, custom.servingAmount)
        assertEquals("g", custom.servingUnit)
        assertEquals(280.0, custom.kcal)
        val logged = diary.added.single()
        assertEquals(MealSlot.Breakfast, logged.slot)
        assertEquals(custom.id, logged.items.single().customFoodId)

        val sent = labels.submitted.single()
        assertEquals("parse-1", sent.parseId)
        assertEquals(SubmissionOrigin.Barcode, sent.origin)
        assertEquals(SubmitFoodFixtures.BARCODE, sent.barcode)
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), sent.acknowledged)
        assertEquals(280.0, sent.facts.perServing[LabelNutrient.Energy])
        assertContentEquals(label.jpeg, sent.label)

        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished("Oats & honey granola bar", MealSlot.Breakfast, sent = true)), events)
        assertEquals("submitted:barcode:edited=false:kept=1:shown=true:sent=true", telemetry.events.last())
    }

    @Test
    fun whenTheReviewCopyFailsTheFoodIsStillLoggedOnceAndCanBeSentAgain() = runUnitTest {
        labels.submit = SubmitResult.Failed(offline = true)
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertEquals(SubmitStage.SendFailed(offline = true, tooMany = false), vm.state.stage)
        assertEquals(1, diary.added.size)

        labels.submit = SubmitResult.Submitted("submission-1", "food-1")
        vm.takeAction(SubmitFoodAction.TryAgainTapped)
        settle()

        assertEquals(1, diary.added.size, "logged once")
        assertEquals(1, customFoods.foods.value.size)
        assertEquals(labels.submitted[0].id, labels.submitted[1].id, "the same submission on the server")
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished("Oats & honey granola bar", MealSlot.Breakfast, sent = true)), events)
    }

    @Test
    fun doneAfterAFailedSendClosesWithTheFoodKept() = runUnitTest {
        labels.submit = SubmitResult.TooMany
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertEquals(SubmitStage.SendFailed(offline = false, tooMany = true), vm.state.stage)

        vm.takeAction(SubmitFoodAction.DoneTapped)
        settle()
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished("Oats & honey granola bar", MealSlot.Breakfast, sent = false)), events)
        assertTrue(telemetry.events.last().endsWith("sent=false"))
    }

    @Test
    fun nothingIsAddedUntilTheRequiredFieldsAreThere() = runUnitTest {
        labels.parse = LabelParse.Unavailable
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertTrue(diary.added.isEmpty())
        assertTrue(labels.submitted.isEmpty())
    }

    @Test
    fun inNoNumbersTheFoodIsAddedWithoutAskingForTheNumbers() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        labels.parse = SubmitFoodFixtures.read.copy(facts = SubmitFoodFixtures.facts.copy(perServing = emptyMap()))
        val vm = reviewing()
        assertTrue(vm.form.canSubmit)

        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertEquals(1, labels.submitted.size)
        assertTrue(telemetry.events.last().contains("shown=false"))
    }

    @Test
    fun backStepsToThePhotosThenCloses() = runUnitTest {
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.BackTapped)
        settle()
        assertEquals(SubmitStage.Photos(canRead = true), vm.state.stage)
        vm.takeAction(SubmitFoodAction.BackTapped)
        settle()
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Close), events)
    }

    @Test
    fun aClosedDayKeepsTheFoodSendsItForReviewAndOpensTheClosedDaySheet() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        assertEquals(1, customFoods.foods.value.size, "in their foods, to log once the day reopens")
        assertTrue(diary.added.isEmpty(), "the closed day took nothing")
        assertEquals(1, labels.submitted.size)
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.DayClosed(LoggerFixtures.today, sent = true)), events)
        assertTrue(telemetry.events.last().endsWith("sent=true"))
    }
}
