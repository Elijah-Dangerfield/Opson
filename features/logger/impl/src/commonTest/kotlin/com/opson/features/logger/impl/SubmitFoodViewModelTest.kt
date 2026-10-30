package com.opson.features.logger.impl

import com.opson.features.logger.SubmitEntry
import com.opson.features.logger.SubmitFoodRoute
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelModelMerge
import com.opson.libraries.foods.LabelModelResult
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.scanner.PhotoPickResult
import com.opson.libraries.scanner.RecognizedLine
import com.opson.libraries.scanner.TextRecognition
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_unit_kcal

class SubmitFoodViewModelTest : CoroutineTest() {
    private val labels = FakeFoodLabels()
    private val customFoods = FakeCustomFoodRepository()
    private val diary = FakeDiaryRepository()
    private val permissions = FakeCameraPermissions()
    private val telemetry = RecordingSubmitFoodTelemetry()
    private val policySource = FakePolicySource()
    private val submittedFoods = FakeSubmittedFoods()
    private val front = MealPhoto(byteArrayOf(1))
    private val label = MealPhoto(byteArrayOf(2))

    /** The label reads as [BAR_PANEL]; the front as [BAR_FRONT]. */
    private val recognizer = FakeTextRecognizer(BAR_PANEL, frontJpeg = front.jpeg, front = BAR_FRONT)
    private val labelModel = FakeLabelTextModel()
    private var modelOn = false

    private fun vm(
        barcode: String? = SubmitFoodFixtures.BARCODE,
        entry: SubmitEntry = if (barcode != null) SubmitEntry.BarcodeMiss else SubmitEntry.EmptySearch,
    ) = vmFor(SubmitFoodRoute(LoggerFixtures.today.toString(), MealSlot.Breakfast, barcode = barcode, entry = entry))

    /** From the Foods tab's Add a food: no day, no meal, saved only. */
    private fun saveOnly() = vmFor(SubmitFoodRoute(entry = SubmitEntry.Foods))

    private fun vmFor(route: SubmitFoodRoute, builder: Boolean = false) = SubmitFoodViewModel(
        route = route,
        labels = labels,
        customFoods = customFoods,
        logger = FoodLogger(diary, FakeFoodCatalog(), FakeRecentLogEvents(), RecordingTelemetry(), Clock.fixed(NOON)),
        permissions = permissions,
        telemetry = telemetry,
        submittedFoods = submittedFoods,
        picker = FakeMealPhotoPicker(),
        policySource = policySource,
        builders = FakeBuilderProgram(active = builder),
        buildersEnabled = buildersFlag(builder),
        phoneReader = phoneReader(recognizer, labelModel, modelOn),
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

    private fun SubmitForm.nutrient(key: LabelNutrient): FormField = numbers!!.main.single { it.nutrient == key }.field

    @Test
    fun itStartsOnThePhotosAndSaysWhereThePersonCameFrom() = runUnitTest {
        val scanned = vm()
        settle()
        assertEquals(SubmitStage.Photos(canRead = false), scanned.state.stage)
        vm(barcode = null)
        vm(barcode = null, entry = SubmitEntry.SearchResults)
        vm(barcode = null, entry = SubmitEntry.AddSheet)
        saveOnly()
        settle()
        assertEquals(
            listOf(
                "started:barcode:barcode_miss",
                "started:search:empty_search",
                "started:search:search_results",
                "started:search:add_sheet",
                "started:search:foods",
            ),
            telemetry.events,
        )
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
    fun thePhoneReadsBothPhotosIntoTheFormAndTheServerIsNeverAsked() = runUnitTest {
        val vm = withPhotos()
        assertEquals(SubmitStage.Photos(canRead = true), vm.state.stage)
        assertEquals(listOf("photo:front:camera", "photo:label:library"), telemetry.events.drop(1))

        vm.takeAction(SubmitFoodAction.ReadTapped)
        settle()

        assertTrue(labels.parsed.isEmpty(), "no server read")
        assertEquals(2, recognizer.calls, "the label, then the front")
        val form = vm.form
        assertEquals(ReviewNotice.ReadOnPhone, form.notice)
        assertEquals("60", form.servingSize.text)
        assertEquals("280", form.nutrient(LabelNutrient.Energy).text)
        assertEquals("Oats & Honey Granola Bar", form.name.text)
        assertTrue(form.name.needsLook, "a name read off the front is a guess")
        assertEquals("read:on_phone:2:0", telemetry.events.last(), "the energy check, and no name on a label")
    }

    @Test
    fun aHazyDigitIsMarkedForALook() = runUnitTest {
        recognizer.hazy = setOf("Protein 10g")
        val form = reviewing().form

        assertTrue(form.nutrient(LabelNutrient.Protein).needsLook)
        assertTrue(!form.nutrient(LabelNutrient.Fat).needsLook)
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
    fun aPhotoWithNoPanelAsksForTheLabelAgainOrToTypeItIn() = runUnitTest {
        recognizer.lines = listOf("Hillside", "Baked in small batches")
        val vm = reviewing()
        assertEquals(SubmitStage.NoLabel, vm.state.stage)
        assertEquals("read:no_label:1:0", telemetry.events.last())

        vm.takeAction(SubmitFoodAction.TypeItInTapped)
        settle()
        assertEquals(ReviewNotice.ByHand, vm.form.notice)
        assertEquals("Oats & Honey Granola Bar", vm.form.name.text, "the name from the front is kept")
        assertEquals("", vm.form.servingSize.text)

        val retake = reviewing(vm())
        retake.takeAction(SubmitFoodAction.RetakeLabelTapped)
        settle()
        assertEquals(SubmitStage.Photos(canRead = false), retake.state.stage)
        assertEquals(front, retake.state.front)
    }

    @Test
    fun aPanelReadInPartFillsWhatItFoundAndSaysSo() = runUnitTest {
        recognizer.lines = listOf("Nutrition Facts", "Serving size 1 bar (60g)", "Calories 280")
        val vm = reviewing()

        assertEquals(ReviewNotice.ReadPartly, vm.form.notice)
        assertEquals("60", vm.form.servingSize.text)
        assertEquals("280", vm.form.nutrient(LabelNutrient.Energy).text)
        assertEquals("", vm.form.nutrient(LabelNutrient.Fat).text)
        assertTrue(telemetry.events.last().startsWith("read:partly:"))
    }

    @Test
    fun aPhoneThatCannotReadTextOpensTheFormEmpty() = runUnitTest {
        recognizer.result = TextRecognition.Unavailable
        recognizer.frontJpeg = null
        val vm = reviewing()

        assertEquals(ReviewNotice.Unavailable, vm.form.notice)
        assertEquals("", vm.form.name.text)
        assertEquals(SubmitFoodFixtures.BARCODE, vm.form.barcode.text, "the scanned code is kept")
        assertEquals("read:unavailable:0:0", telemetry.events.last())
        assertTrue(labels.parsed.isEmpty())
    }

    @Test
    fun aScannedBarcodeCannotBeRetypedButOneFromSearchCan() = runUnitTest {
        val scanned = reviewing()
        scanned.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.BARCODE, "4006381333931"))
        settle()
        assertEquals(SubmitFoodFixtures.BARCODE, scanned.form.barcode.text)

        val searched = reviewing(vm(barcode = null))
        searched.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.BARCODE, "4006381333931"))
        settle()
        assertEquals("4006381333931", searched.form.barcode.text)
    }

    @Test
    fun theOneTapFixAndKeepWorkLive() = runUnitTest {
        val vm = reviewing()
        assertEquals(UnitAmount("234", Res.string.submit_unit_kcal), vm.form.checks.single().fix)

        vm.takeAction(SubmitFoodAction.KeepTapped(LabelCheckCode.EnergyMacrosMismatch, LabelNutrient.Energy.wire))
        settle()
        assertTrue(vm.form.checks.single().kept)

        vm.takeAction(SubmitFoodAction.FixTapped(LabelCheckCode.EnergyMacrosMismatch, LabelNutrient.Energy.wire))
        settle()
        assertEquals("234", vm.form.nutrient(LabelNutrient.Energy).text)
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
        assertEquals(BAR_NAME, custom.name)
        assertEquals(60.0, custom.servingAmount)
        assertEquals("g", custom.servingUnit)
        assertEquals(280.0, custom.kcal)
        val logged = diary.added.single()
        assertEquals(MealSlot.Breakfast, logged.slot)
        assertEquals(custom.id, logged.items.single().customFoodId)

        val sent = labels.submitted.single()
        assertNull(sent.parseId, "the server never read it")
        assertEquals(SubmissionOrigin.Barcode, sent.origin)
        assertEquals(SubmitFoodFixtures.BARCODE, sent.barcode)
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), sent.acknowledged)
        assertEquals(280.0, sent.facts.perServing[LabelNutrient.Energy])
        assertContentEquals(label.jpeg, sent.label)

        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished(BAR_NAME, MealSlot.Breakfast, sent = true)), events)
        assertEquals("submitted:barcode:barcode_miss:edited=false:kept=1:shown=true:sent=true", telemetry.events.last())
    }

    @Test
    fun everyonesSubmissionCarriesWhatThePhoneReadForTheReviewer() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        val sent = labels.submitted.single()
        val capture = assertNotNull(sent.capture, "not only Builders: the reviewer checks every number against the text")
        assertEquals("vision", capture.engine)
        assertEquals(false, capture.fromCamera, "the label came from the library")
        assertEquals(BAR_PANEL.size, capture.lines.size)
        assertEquals(5, capture.signals?.grounded)
        assertNull(sent.ingredients)
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
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished(BAR_NAME, MealSlot.Breakfast, sent = true)), events)
    }

    @Test
    fun aRestrictedAccountKeepsItsFoodAndIsToldWhy() = runUnitTest {
        labels.submit = SubmitResult.Restricted
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertEquals(SubmitStage.SendFailed(offline = false, tooMany = false, restricted = true), vm.state.stage)
        assertEquals(1, diary.added.size, "logged all the same")
        assertEquals(1, customFoods.foods.value.size)
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
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished(BAR_NAME, MealSlot.Breakfast, sent = false)), events)
        assertTrue(telemetry.events.last().endsWith("sent=false"))
    }

    @Test
    fun nothingIsAddedUntilTheRequiredFieldsAreThere() = runUnitTest {
        recognizer.result = TextRecognition.Unavailable
        recognizer.frontJpeg = null
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()
        assertTrue(diary.added.isEmpty())
        assertTrue(labels.submitted.isEmpty())
    }

    @Test
    fun inNoNumbersTheFoodIsAddedWithoutAskingForTheNumbers() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        recognizer.lines = listOf("Nutrition Facts", "6 servings per container", "Serving size 1 bar (60g)")
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

    @Test
    fun sendingItMarksTheirOwnCopyUnverifiedAndNamesItOnTheSubmission() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        val custom = customFoods.foods.value.single()
        assertEquals(custom.id, labels.submitted.single().customFoodId)
        assertEquals(listOf(Triple(custom.id, "submission-1", "food-1")), submittedFoods.sent)
        assertEquals(mapOf(custom.id to ReviewStatus.Pending), submittedFoods.reviews.value)
    }

    @Test
    fun aCopyThatFailedToSendIsNotMarked() = runUnitTest {
        labels.submit = SubmitResult.Failed(offline = false)
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        assertTrue(submittedFoods.sent.isEmpty(), "only the person has it, so nothing is waiting for review")
    }

    @Test
    fun theirCopyKeepsTheRestOfTheLabelPerServing() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        val micros = customFoods.foods.value.single().micros
        assertEquals(150.0, micros[LabelNutrient.Sodium.wire])
        assertTrue(LabelNutrient.Energy.wire !in micros && LabelNutrient.Protein.wire !in micros, "the main numbers have their own fields")
    }

    @Test
    fun fromTheFoodsTabItIsSavedAndSentButLoggedNowhere() = runUnitTest {
        val vm = saveOnly()
        val events = events(vm)
        reviewing(vm)
        assertEquals(null, vm.state.slot, "no meal: the screen says Save food")

        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        assertEquals(1, customFoods.foods.value.size)
        assertTrue(diary.added.isEmpty(), "there is no meal to log to")
        assertEquals(SubmissionOrigin.Search, labels.submitted.single().origin)
        assertEquals(listOf<SubmitFoodEvent>(SubmitFoodEvent.Finished(BAR_NAME, slot = null, sent = true)), events)
        assertEquals("submitted:search:foods:edited=false:kept=0:shown=true:sent=true", telemetry.events.last())
    }

    @Test
    fun fromTheFoodsTabAClosedDayDoesNotMatter() = runUnitTest {
        diary.close(LoggerFixtures.today)
        val vm = saveOnly()
        val events = events(vm)
        reviewing(vm)
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        assertIs<SubmitFoodEvent.Finished>(events.single())
    }

    @Test
    fun aPhotoThatCouldNotBeOpenedSaysSoUntilAnotherArrives() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, PhotoPickResult.Failed, fromCamera = false))
        settle()
        assertEquals(PackSide.Label, vm.state.photoFailed)
        assertEquals(null, vm.state.label)

        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Label, PhotoPickResult.Picked(label), fromCamera = false))
        settle()
        assertEquals(null, vm.state.photoFailed)
        assertEquals(label, vm.state.label)
    }

    // The on-device model's second opinion (labels.onDeviceModel)

    @Test
    fun withTheModelOnAGapThePanelLeftIsFilledAndMarked() = runUnitTest {
        modelOn = true
        // The fat row is missing from the text the parser places; the 6 is still printed ("6 servings").
        recognizer.lines = BAR_PANEL.filterNot { it.startsWith("Total Fat") }
        labelModel.answer = LabelModelResult.Read(LabelFacts(perServing = mapOf(LabelNutrient.Fat to 6.0)), "apple-foundation-models")
        val form = reviewing().form

        assertEquals(1, labelModel.calls)
        assertEquals("6", form.nutrient(LabelNutrient.Fat).text)
        assertTrue(form.nutrient(LabelNutrient.Fat).needsLook)
        assertTrue(LabelModelMerge.FILLED < SubmitFoodStateBuilder.LOW_CONFIDENCE)
    }

    @Test
    fun withTheModelOffItIsNeverAsked() = runUnitTest {
        labelModel.answer = LabelModelResult.Read(LabelFacts(perServing = mapOf(LabelNutrient.Fat to 6.0)), "apple-foundation-models")
        reviewing()

        assertEquals(0, labelModel.calls)
    }

    // Builder mode (release.builders, an active Builder)

    private fun builderVm() = vmFor(SubmitFoodRoute(entry = SubmitEntry.Builders), builder = true)

    @Test
    fun aBuilderAddsAnIngredientsPhotoToo() = runUnitTest {
        val vm = withPhotos(builderVm())
        assertTrue(vm.state.builderMode)
        vm.takeAction(SubmitFoodAction.PhotoAnswered(PackSide.Ingredients, PhotoPickResult.Picked(MealPhoto(byteArrayOf(3))), fromCamera = true))
        vm.takeAction(SubmitFoodAction.ReadTapped)
        settle()
        vm.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.NAME, "Maple granola"))
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        val sent = labels.submitted.single()
        val capture = assertNotNull(sent.capture)
        assertTrue((capture.score ?: -1.0) in 0.0..1.0)
        assertContentEquals(byteArrayOf(3), sent.ingredients)
        assertEquals(null, sent.parseId)
    }

    @Test
    fun someoneWhoIsNotABuilderSeesNoBuilderMode() = runUnitTest {
        val vm = reviewing()

        assertEquals(false, vm.state.builderMode)
        assertEquals(2, recognizer.calls, "the phone reads for everyone")
    }

    @Test
    fun aFoodTheServerPublishedSaysSoWhenItCloses() = runUnitTest {
        labels.submit = SubmitResult.Submitted("submission-1", "food-1", lane = "owner")
        val vm = withPhotos(builderVm())
        val events = events(vm)
        vm.takeAction(SubmitFoodAction.ReadTapped)
        settle()
        vm.takeAction(SubmitFoodAction.FieldTyped(SubmitFoodForm.NAME, "Maple granola"))
        vm.takeAction(SubmitFoodAction.SubmitTapped)
        settle()

        assertEquals(true, assertIs<SubmitFoodEvent.Finished>(events.last()).published)
    }

    private companion object {
        const val BAR_NAME = "Oats & Honey Granola Bar"

        /** A US panel for a 60 g bar: 280 kcal printed, where 10 g protein, 35 g carbs and 6 g fat make 234. */
        val BAR_PANEL = listOf(
            "Nutrition Facts",
            "6 servings per container",
            "Serving size 1 bar (60g)",
            "Amount per serving",
            "Calories 280",
            "Total Fat 6g 8%",
            "Saturated Fat 1g 5%",
            "Sodium 150mg 7%",
            "Total Carbohydrate 35g 13%",
            "Dietary Fiber 3g",
            "Total Sugars 12g",
            "Protein 10g",
        )

        /** The front of the pack: the name in big type over two lines, the brand smaller above. */
        val BAR_FRONT = listOf(
            RecognizedLine("Hillside", 0.9, 0.3, 0.05, 0.4, 0.04),
            RecognizedLine("OATS & HONEY", 0.9, 0.1, 0.2, 0.8, 0.08),
            RecognizedLine("GRANOLA BAR", 0.9, 0.15, 0.29, 0.7, 0.075),
        )
    }
}
