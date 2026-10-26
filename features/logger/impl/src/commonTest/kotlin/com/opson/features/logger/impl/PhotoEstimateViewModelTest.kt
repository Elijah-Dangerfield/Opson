package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.PhotoEstimate
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** [PhotoEstimateViewModel], one test per action. The drawing is [PhotoEstimateStateBuilderTest]'s. */
class PhotoEstimateViewModelTest : CoroutineTest() {
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day()))
    private val catalog = FakeFoodCatalog(foods = PhotoFixtures.foods.values.toList())
    private val estimator = FakeMealPhotoEstimator()
    private val permissions = FakeCameraPermissions()
    private val basket = BasketStore()
    private val loggerTelemetry = RecordingTelemetry()
    private val telemetry = RecordingPhotoTelemetry()
    private val policySource = FakePolicySource()
    private val recentLogEvents = FakeRecentLogEvents()
    private val appState = FakeAppState()
    private val photo = MealPhoto(byteArrayOf(7, 7, 7))

    private fun vm(canTakePhoto: Boolean = true) = PhotoEstimateViewModel(
        route = PhotoEstimateRoute(LoggerFixtures.today.toString(), MealSlot.Dinner),
        estimator = estimator,
        foodCatalog = catalog,
        permissions = permissions,
        basketStore = basket,
        committer = BasketCommitter(diary, catalog, basket, recentLogEvents, loggerTelemetry, Clock.fixed(NOON)),
        settingsRepository = FakeSettingsRepository(Settings(askTags = false)),
        appState = appState,
        telemetry = telemetry,
        diary = diary,
        picker = FakeMealPhotoPicker(canTakePhoto),
        policySource = policySource,
        calendar = FakeCalendar(),
    )

    private fun TestScope.events(vm: PhotoEstimateViewModel): List<PhotoEstimateEvent> {
        val events = mutableListOf<PhotoEstimateEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    /** Opened, a photo picked from the library, the estimate in. */
    private fun TestScope.reviewing(note: String = ""): PhotoEstimateViewModel {
        val vm = vm()
        settle()
        if (note.isNotEmpty()) vm.takeAction(PhotoEstimateAction.NoteTyped(note))
        vm.takeAction(PhotoEstimateAction.PhotoAnswered(PhotoPickResult.Picked(photo), fromCamera = false))
        settle()
        return vm
    }

    private val PhotoEstimateViewModel.review: PhotoStage.Review get() = assertIs(state.stage)

    @Test
    fun itOpensOnTheStartWithTheCameraWhereThereIsOne() = runUnitTest {
        val vm = vm()
        settle()
        assertEquals(PhotoStage.Start, vm.state.stage)
        assertTrue(vm.state.canTakePhoto)
        assertEquals(false, vm(canTakePhoto = false).state.canTakePhoto)
    }

    @Test
    fun noteTypedIsKeptUpToTheLimit() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(PhotoEstimateAction.NoteTyped("x".repeat(400)))
        settle()
        assertEquals(PhotoEstimateViewModel.NOTE_LIMIT, vm.state.note.length)
    }

    @Test
    fun takePhotoTappedFollowsTheCameraPermission() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(PhotoEstimateAction.TakePhotoTapped)
        settle()
        permissions.permission = CameraPermission.NotAsked
        vm.takeAction(PhotoEstimateAction.TakePhotoTapped)
        settle()
        assertEquals(listOf(PhotoEstimateEvent.LaunchCamera, PhotoEstimateEvent.RequestCameraPermission), events)

        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        vm.takeAction(PhotoEstimateAction.TakePhotoTapped)
        settle()
        assertEquals(PhotoStage.CameraOff(canAskAgain = false), vm.state.stage)
    }

    @Test
    fun cameraPermissionAnsweredLaunchesTheCameraOrSaysItIsOff() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(PhotoEstimateAction.CameraPermissionAnswered(CameraPermission.Denied(canAskAgain = true)))
        settle()
        assertEquals(PhotoStage.CameraOff(canAskAgain = true), vm.state.stage)

        vm.takeAction(PhotoEstimateAction.CameraPermissionAnswered(CameraPermission.Granted))
        settle()
        assertEquals(PhotoStage.Start, vm.state.stage)
        assertEquals(listOf<PhotoEstimateEvent>(PhotoEstimateEvent.LaunchCamera), events)
    }

    @Test
    fun allowCameraAsksAgainAndOpenSettingsOpensThem() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(PhotoEstimateAction.AllowCameraTapped)
        vm.takeAction(PhotoEstimateAction.OpenSettingsTapped)
        settle()
        assertEquals(listOf<PhotoEstimateEvent>(PhotoEstimateEvent.RequestCameraPermission), events)
        assertEquals(1, permissions.settingsOpened)
    }

    @Test
    fun chooseFromLibraryLaunchesThePicker() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(PhotoEstimateAction.ChooseFromLibraryTapped)
        settle()
        assertEquals(listOf<PhotoEstimateEvent>(PhotoEstimateEvent.LaunchLibrary), events)
    }

    @Test
    fun aPickedPhotoIsEstimatedWithTheNoteAndReviewed() = runUnitTest {
        val vm = reviewing(note = "  no butter  ")

        assertEquals(listOf(photo.jpeg.toList() to "no butter"), estimator.sent.map { (bytes, note) -> bytes.toList() to note })
        assertEquals(3, vm.review.rows.size)
        assertTrue(vm.review.usedNote)
        assertEquals(photo, vm.state.photo)
        assertEquals(listOf("started library", "estimated items=3 matched=3"), telemetry.events)
    }

    @Test
    fun aCameraPhotoSaysSo() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(PhotoEstimateAction.PhotoAnswered(PhotoPickResult.Picked(photo), fromCamera = true))
        settle()
        assertEquals("started camera", telemetry.events.first())
    }

    @Test
    fun backingOutOfThePickerChangesNothing() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(PhotoEstimateAction.PhotoAnswered(PhotoPickResult.Cancelled, fromCamera = true))
        settle()
        assertEquals(PhotoStage.Start, vm.state.stage)
        assertTrue(estimator.sent.isEmpty())
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun aPhotoThatCannotBeReadFails() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(PhotoEstimateAction.PhotoAnswered(PhotoPickResult.Failed, fromCamera = false))
        settle()
        assertEquals(PhotoStage.Failed(offline = false), vm.state.stage)
        assertEquals(listOf("failed error"), telemetry.events)
    }

    @Test
    fun theCalmEndsFollowTheAnswer() = runUnitTest {
        val cases = listOf(
            PhotoEstimate.Items(emptyList()) to (PhotoStage.NothingFound to "failed nothing_found"),
            PhotoEstimate.Unavailable to (PhotoStage.Unavailable to "failed unavailable"),
            PhotoEstimate.TooMany to (PhotoStage.TooMany to "failed too_many"),
            PhotoEstimate.Failed(offline = true) to (PhotoStage.Failed(offline = true) to "failed offline"),
            PhotoEstimate.Failed(offline = false) to (PhotoStage.Failed(offline = false) to "failed error"),
        )
        cases.forEach { (answer, expected) ->
            estimator.answer = answer
            telemetry.events.clear()
            val vm = reviewing()
            assertEquals(expected.first, vm.state.stage)
            assertEquals(expected.second, telemetry.events.last())
        }
    }

    @Test
    fun offlineAppStateMakesAFailureOffline() = runUnitTest {
        estimator.answer = PhotoEstimate.Failed(offline = false)
        appState.isOffline.value = true
        assertEquals(PhotoStage.Failed(offline = true), reviewing().state.stage)
    }

    @Test
    fun rowAmountTappedOpensAndClosesTheStepper() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.RowAmountTapped("photo:1"))
        settle()
        assertEquals("photo:1", vm.review.editor?.key)
        vm.takeAction(PhotoEstimateAction.RowAmountTapped("photo:1"))
        settle()
        assertNull(vm.review.editor)
    }

    @Test
    fun moreLessAndUnitChangeTheLine() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.MoreTapped("photo:1"))
        settle()
        assertEquals("1.5 tbsp", vm.review.rows[1].amount)
        vm.takeAction(PhotoEstimateAction.LessTapped("photo:1"))
        vm.takeAction(PhotoEstimateAction.LessTapped("photo:1"))
        settle()
        assertEquals("0.5 tbsp", vm.review.rows[1].amount)
        vm.takeAction(PhotoEstimateAction.UnitPicked("photo:2", index = 1))
        settle()
        assertEquals("312 g", vm.review.rows[2].amount)
    }

    @Test
    fun rowRemoveTappedDropsTheLine() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.RowRemoveTapped("photo:0"))
        settle()
        assertEquals(listOf("Olive oil", "Broccoli, steamed"), vm.review.rows.map { it.name })
        assertEquals(2, vm.review.footer?.count)
    }

    @Test
    fun rowNameTappedSearchesFromTheLinesNameAndAPickSwapsIt() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.RowNameTapped("photo:0"))
        settle()
        val swap = assertIs<PhotoStage.Swap>(vm.state.stage)
        assertEquals("Chicken breast", swap.query)
        assertEquals(listOf("Chicken breast"), catalog.searches)

        vm.takeAction(PhotoEstimateAction.SwapTyped("thigh"))
        settle()
        vm.takeAction(PhotoEstimateAction.SwapPicked(PhotoFixtures.thigh.id))
        settle()

        assertEquals("Chicken thigh, grilled", vm.review.rows.first().name)
        assertEquals("120 g", vm.review.rows.first().amount)
    }

    @Test
    fun addMissedAddsALine() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.AddMissedTapped)
        vm.takeAction(PhotoEstimateAction.SwapTyped("thigh"))
        settle()
        vm.takeAction(PhotoEstimateAction.SwapPicked(PhotoFixtures.thigh.id))
        settle()
        assertEquals(4, vm.review.rows.size)
        assertEquals("1 thigh", vm.review.rows.last().amount)
    }

    @Test
    fun aStaleSearchAnswerIsDropped() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.AddMissedTapped)
        vm.takeAction(PhotoEstimateAction.SwapTyped("broccoli"))
        settle()
        vm.takeAction(PhotoEstimateAction.SwapResultsArrived("chicken", listOf(PhotoFixtures.chicken)))
        settle()
        assertEquals(listOf("Broccoli, steamed"), assertIs<PhotoStage.Swap>(vm.state.stage).results?.map { it.name })
    }

    @Test
    fun backFromTheSwapReturnsToTheListAndBackFromTheListCloses() = runUnitTest {
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(PhotoEstimateAction.AddMissedTapped)
        settle()
        vm.takeAction(PhotoEstimateAction.BackTapped)
        settle()
        assertEquals(3, vm.review.rows.size)
        assertTrue(events.isEmpty())

        vm.takeAction(PhotoEstimateAction.BackTapped)
        settle()
        assertEquals(listOf<PhotoEstimateEvent>(PhotoEstimateEvent.Dismiss), events)
    }

    @Test
    fun tryAgainSendsTheSamePhotoAgain() = runUnitTest {
        estimator.answer = PhotoEstimate.Failed(offline = false)
        val vm = reviewing()
        estimator.answer = PhotoEstimate.Items(PhotoFixtures.items)
        vm.takeAction(PhotoEstimateAction.TryAgainTapped)
        settle()
        assertEquals(2, estimator.sent.size)
        assertEquals(3, vm.review.rows.size)
    }

    @Test
    fun anotherPhotoStartsOver() = runUnitTest {
        val vm = reviewing()
        vm.takeAction(PhotoEstimateAction.AnotherPhotoTapped)
        settle()
        assertEquals(PhotoStage.Start, vm.state.stage)
        assertNull(vm.state.photo)
    }

    @Test
    fun describeInsteadReopensTheLoggerOnDescribe() = runUnitTest {
        val vm = reviewing()
        val events = events(vm)
        vm.takeAction(PhotoEstimateAction.DescribeInsteadTapped)
        settle()
        assertEquals(listOf<PhotoEstimateEvent>(PhotoEstimateEvent.OpenDescribe(LoggerFixtures.today, MealSlot.Dinner)), events)
    }

    @Test
    fun addToMealLogsTheLinesAsEstimatesWithTheRestOfTheBasket() = runUnitTest {
        val vm = reviewing()
        val events = events(vm)
        basket.upsert(LoggerFixtures.pickedBasket().first())
        settle()

        vm.takeAction(PhotoEstimateAction.AddToMealTapped)
        settle()

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(4, added.items.size)
        val photoLines = added.items.drop(1)
        assertTrue(photoLines.all { it.method == LogMethod.Describe && it.precision == Precision.Estimate })
        assertTrue(basket.basket.value.isEmpty())
        assertTrue(LogVia.Photo to 3 in loggerTelemetry.events)
        assertEquals("added items=3", telemetry.events.last())
        val logged = assertIs<PhotoEstimateEvent.Logged>(events.single())
        assertEquals(LoggerFixtures.today, logged.day)
        assertEquals(1, recentLogEvents.logged.size)
    }

    @Test
    fun hidingCaloriesMidReviewMovesWeightsToMeasures() = runUnitTest {
        val vm = reviewing()
        assertEquals("120 g", vm.review.rows.first().amount)
        policySource.policy.value = policy(PresetId.Blind)
        settle()
        assertEquals("1 piece", vm.review.rows.first().amount)
    }
}
