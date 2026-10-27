package com.opson.features.logger.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScannedCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** [BarcodeScanViewModel], one test per action. The stage mapping itself is [BarcodeScanStateBuilderTest]'s. */
class BarcodeScanViewModelTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog()
    private val misses = FakeMissReporter()
    private val permissions = FakeCameraPermissions()
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val diary = FakeDiaryRepository()
    private val telemetry = RecordingBarcodeTelemetry()

    private fun vm(overSearch: Boolean = false) = BarcodeScanViewModel(
        route = BarcodeScanRoute(dayIso = LoggerFixtures.today.toString(), slot = MealSlot.Dinner, overSearch = overSearch),
        foodCatalog = catalog,
        missReporter = misses,
        permissions = permissions,
        customFoods = customFoods,
        logger = FoodLogger(diary, catalog, FakeRecentLogEvents(), RecordingTelemetry(), Clock.fixed(NOON)),
        telemetry = telemetry,
    )

    private fun TestScope.events(vm: BarcodeScanViewModel): List<BarcodeScanEvent> {
        val events = mutableListOf<BarcodeScanEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    /** Lets every queued action run without crossing the 400 ms "slow" mark. */
    private fun TestScope.settle() {
        advanceTimeBy(100)
        runCurrent()
    }

    private fun ean13(code: String = LoggerFixtures.MISSED_CODE) = BarcodeScanAction.CodeRead(ScannedCode(code, BarcodeFormat.Ean13))

    private fun TestScope.missedVm(): BarcodeScanViewModel = vm().also {
        settle()
        // Takes the read's haptic, so a test of what the miss offers sees only its own events.
        backgroundScope.launch { assertEquals(BarcodeScanEvent.Scanned, it.eventFlow.first()) }
        it.takeAction(ean13())
        settle()
    }

    @Test
    fun permissionCheckedGrantedStartsScanning() = runUnitTest {
        val vm = vm()
        settle()

        assertEquals(ScanStage.Scanning, vm.state.stage)
        assertEquals(listOf("scan_started asked=false"), telemetry.events)
    }

    @Test
    fun permissionCheckedNotAskedShowsTheSystemPrompt() = runUnitTest {
        permissions.permission = CameraPermission.NotAsked
        val vm = vm()
        val events = events(vm)
        settle()

        assertEquals(ScanStage.Starting, vm.state.stage)
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.RequestPermission), events)
    }

    @Test
    fun permissionCheckedDeniedShowsNoCamera() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        settle()

        assertEquals(ScanStage.NoCamera(canAskAgain = false), vm.state.stage)
        assertEquals(listOf("permission_denied can_ask_again=false"), telemetry.events)
    }

    @Test
    fun permissionAnsweredYesStartsAndNoExplains() = runUnitTest {
        permissions.permission = CameraPermission.NotAsked
        val yes = vm()
        settle()
        yes.takeAction(BarcodeScanAction.PermissionAnswered(CameraPermission.Granted))
        settle()
        assertEquals(ScanStage.Scanning, yes.state.stage)

        val no = vm()
        settle()
        no.takeAction(BarcodeScanAction.PermissionAnswered(CameraPermission.Denied(canAskAgain = true)))
        settle()
        assertEquals(ScanStage.NoCamera(canAskAgain = true), no.state.stage)

        assertEquals(listOf("scan_started asked=true", "permission_denied can_ask_again=true"), telemetry.events)
    }

    @Test
    fun resumedAfterTurningTheCameraOnInSettingsStartsScanning() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.Resumed)
        settle()
        assertEquals(ScanStage.NoCamera(canAskAgain = false), vm.state.stage, "nothing changed in Settings")

        permissions.permission = CameraPermission.Granted
        vm.takeAction(BarcodeScanAction.Resumed)
        settle()
        assertEquals(ScanStage.Scanning, vm.state.stage)
        assertTrue(events.isEmpty(), "resuming never asks on its own")
    }

    @Test
    fun resumedWhileThePromptIsUpDoesNotAskAgain() = runUnitTest {
        permissions.permission = CameraPermission.NotAsked
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.Resumed)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.RequestPermission), events)
    }

    @Test
    fun codeReadOfAFoodOpensTheAmountSheetInThisMeal() = runUnitTest {
        catalog.barcodeResult = Catching.success(LoggerFixtures.scannedYogurt)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.CodeRead(ScannedCode("0036632027627", BarcodeFormat.Ean13)))
        settle()

        assertEquals(listOf("0036632027627"), catalog.barcodes, "looked up as read")
        assertEquals(
            listOf(
                BarcodeScanEvent.Scanned,
                BarcodeScanEvent.OpenFoodDetail(LoggerFixtures.scannedYogurt.id, LoggerFixtures.today, MealSlot.Dinner),
            ),
            events,
        )
        assertEquals(listOf("scan_started asked=false", "hit usda_branded slow=false"), telemetry.events)
        assertTrue(misses.reported.isEmpty())
    }

    @Test
    fun codeReadIgnoresMisreadsAndRepeats() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(ean13("4006381333932"))
        settle()
        assertTrue(catalog.barcodes.isEmpty(), "a failed check digit is a misread")
        assertEquals(ScanStage.Scanning, vm.state.stage)

        vm.takeAction(ean13())
        vm.takeAction(ean13())
        vm.takeAction(ean13("0036632027627"))
        settle()
        assertEquals(listOf(LoggerFixtures.MISSED_CODE), catalog.barcodes, "one lookup per scan")
    }

    @Test
    fun codeReadOfAValidCodeIsLookedUpAtOnceWithNoSecondReadNeeded() = runUnitTest {
        catalog.barcodeDelay = 2.seconds
        val vm = vm()
        settle()

        vm.takeAction(ean13())
        runCurrent()

        assertEquals(listOf(LoggerFixtures.MISSED_CODE), catalog.barcodes, "no debounce, no confirming read")
        assertEquals(ScanStage.LookingUp(slow = false), vm.state.stage)
    }

    @Test
    fun codeReadBuzzesOncePerScanAndNeverForAMisread() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(ean13("4006381333932"))
        settle()
        assertTrue(events.isEmpty(), "a failed check digit is not a catch")

        vm.takeAction(ean13())
        vm.takeAction(ean13())
        vm.takeAction(ean13())
        settle()
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Scanned), events, "the camera's repeats of the same pack")

        vm.takeAction(BarcodeScanAction.ScanAgainTapped)
        vm.takeAction(ean13())
        settle()
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Scanned, BarcodeScanEvent.Scanned), events, "a new scan buzzes again")
    }

    @Test
    fun cameraStartedWithATorchOffersTheLightOff() = runUnitTest {
        val vm = vm()
        settle()
        assertEquals(Torch.None, vm.state.torch, "not until the camera says")

        vm.takeAction(BarcodeScanAction.CameraStarted(hasTorch = true))
        settle()

        assertEquals(Torch.Off, vm.state.torch)
    }

    @Test
    fun torchTappedTurnsTheLightOnAndItStaysOnThroughALookupAndScanAgain() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.CameraStarted(hasTorch = true))

        vm.takeAction(BarcodeScanAction.TorchTapped)
        settle()
        assertEquals(Torch.On, vm.state.torch)

        vm.takeAction(ean13())
        settle()
        assertIs<ScanStage.Miss>(vm.state.stage)
        assertEquals(Torch.None, vm.state.torch, "no camera on a miss")

        vm.takeAction(BarcodeScanAction.ScanAgainTapped)
        settle()
        assertEquals(Torch.On, vm.state.torch, "the kitchen is still dim")

        vm.takeAction(BarcodeScanAction.TorchTapped)
        settle()
        assertEquals(Torch.Off, vm.state.torch)
    }

    @Test
    fun torchTappedWithoutATorchDoesNothing() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.CameraStarted(hasTorch = false))

        vm.takeAction(BarcodeScanAction.TorchTapped)
        vm.takeAction(BarcodeScanAction.CameraStarted(hasTorch = true))
        settle()

        assertEquals(Torch.Off, vm.state.torch, "the tap before the torch was known is not remembered")
    }

    @Test
    fun codeReadOfASixDigitUpcEIsCompletedFirst() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(BarcodeScanAction.CodeRead(ScannedCode("425261", BarcodeFormat.UpcE)))
        settle()

        assertEquals(listOf("04252614"), catalog.barcodes)
    }

    @Test
    fun codeReadIsIgnoredWithoutTheCamera() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        settle()

        vm.takeAction(ean13())
        settle()

        assertTrue(catalog.barcodes.isEmpty())
    }

    @Test
    fun lookupSlowAfter400MillisecondsShowsTheFirstLookupCopyAndIsCountedOnTheHit() = runUnitTest {
        catalog.barcodeResult = Catching.success(LoggerFixtures.scannedYogurt)
        catalog.barcodeDelay = 2.seconds
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.CodeRead(ScannedCode("0036632027627", BarcodeFormat.Ean13)))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(ScanStage.LookingUp(slow = false), vm.state.stage, "a catalog hit lands inside 400 ms: a quiet spinner")

        advanceTimeBy(200)
        runCurrent()
        assertEquals(ScanStage.LookingUp(slow = true), vm.state.stage)

        advanceTimeBy(2_000)
        runCurrent()
        assertIs<BarcodeScanEvent.OpenFoodDetail>(events.last())
        assertEquals("hit usda_branded slow=true", telemetry.events.last())
    }

    @Test
    fun answersAndSlowMarksForAnotherCodeAreDropped() = runUnitTest {
        catalog.barcodeDelay = 2.seconds
        val vm = vm()
        settle()
        vm.takeAction(ean13())

        vm.takeAction(BarcodeScanAction.LookupSlow("0036632027627"))
        vm.takeAction(BarcodeScanAction.FoodNotFound("0036632027627"))
        vm.takeAction(BarcodeScanAction.LookupFailed("0036632027627"))
        runCurrent()

        assertEquals(ScanStage.LookingUp(slow = false), vm.state.stage)
        assertTrue(misses.reported.isEmpty())
    }

    @Test
    fun foodNotFoundShowsTheMissAndReportsItOnce() = runUnitTest {
        val vm = missedVm()

        assertEquals(ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "", canSave = false), vm.state.stage)
        assertEquals(listOf(LoggerFixtures.MISSED_CODE), misses.reported)
        assertEquals(listOf("scan_started asked=false", "miss"), telemetry.events)
    }

    @Test
    fun lookupFailedIsUnavailableAndNotAMiss() = runUnitTest {
        catalog.barcodeResult = Catching.failure(IllegalStateException("offline"))
        val vm = vm()
        settle()

        vm.takeAction(ean13())
        settle()

        assertEquals(ScanStage.Unavailable, vm.state.stage)
        assertTrue(misses.reported.isEmpty(), "offline is not a gap in the catalog")
        assertEquals(listOf("scan_started asked=false"), telemetry.events)
    }

    @Test
    fun foodFoundForAnOldCodeIsDropped() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.FoodFound(LoggerFixtures.MISSED_CODE, LoggerFixtures.scannedYogurt))
        settle()

        assertTrue(events.isEmpty())
        assertIs<ScanStage.Miss>(vm.state.stage)
    }

    @Test
    fun missNameTypedEnablesSave() = runUnitTest {
        val vm = missedVm()

        vm.takeAction(BarcodeScanAction.MissNameTyped("Rye crispbread"))
        settle()

        assertEquals(ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "Rye crispbread", canSave = true), vm.state.stage)
    }

    @Test
    fun saveNameTappedLogsTheNameOnlyAndCloses() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.MissNameTyped("  Rye crispbread "))
        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        val logged = diary.added.single()
        assertEquals(MealSlot.Dinner, logged.slot)
        val picked = logged.items.single()
        assertEquals("Rye crispbread", picked.name)
        assertEquals(Precision.Named, picked.precision)
        assertEquals(null, picked.kcal, "no numbers")
        assertEquals("Rye crispbread", customFoods.foods.value.last().name)
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
    }

    @Test
    fun saveNameTappedReusesAFoodByThatName() = runUnitTest {
        val vm = missedVm()

        vm.takeAction(BarcodeScanAction.MissNameTyped("lola's GRANOLA"))
        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        assertEquals(1, customFoods.foods.value.size)
        assertEquals("granola", diary.added.single().items.single().customFoodId)
    }

    /** The screen takes a moment to close after the write; a second tap in that time is queued behind the first. */
    @Test
    fun aSecondSaveWhileTheScannerClosesLogsNothingMore() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.MissNameTyped("Rye crispbread"))
        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        assertEquals(1, diary.added.size)
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
    }

    @Test
    fun saveNameTappedWithoutANameDoesNothing() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        assertTrue(diary.added.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun searchInsteadTappedOpensSearchInItsPlace() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.SearchInsteadTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.OpenSearch(LoggerFixtures.today, MealSlot.Dinner)), events)
        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun scanAgainTappedClearsTheMissAndAcceptsANewRead() = runUnitTest {
        val vm = missedVm()
        vm.takeAction(BarcodeScanAction.MissNameTyped("Rye"))

        vm.takeAction(BarcodeScanAction.ScanAgainTapped)
        settle()
        assertEquals(ScanStage.Scanning, vm.state.stage)

        vm.takeAction(ean13("0036632027627"))
        settle()
        assertEquals(listOf(LoggerFixtures.MISSED_CODE, "0036632027627"), catalog.barcodes)
    }

    @Test
    fun allowCameraTappedAsksAgain() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = true)
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.AllowCameraTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.RequestPermission), events)
    }

    @Test
    fun openSettingsTappedOpensTheAppsSettings() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        settle()

        vm.takeAction(BarcodeScanAction.OpenSettingsTapped)
        settle()

        assertEquals(1, permissions.settingsOpened)
    }

    @Test
    fun searchInsteadFromTheDeniedStateGoesBackToSearch() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm(overSearch = true)
        val events = events(vm)
        settle()
        assertEquals(ScanStage.NoCamera(canAskAgain = false), vm.state.stage)

        vm.takeAction(BarcodeScanAction.SearchInsteadTapped)
        settle()

        // Opened over search, Close pops this screen back to it, with nothing logged or opened.
        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
        assertTrue(diary.added.isEmpty())
        assertTrue(catalog.barcodes.isEmpty())
    }

    @Test
    fun searchInsteadWhileScanningOverSearchGoesBackToIt() = runUnitTest {
        val vm = vm(overSearch = true)
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.SearchInsteadTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
    }

    @Test
    fun cameraUnavailableShowsACalmStateOnce() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(BarcodeScanAction.CameraUnavailable)
        vm.takeAction(BarcodeScanAction.CameraUnavailable)
        settle()

        assertEquals(ScanStage.CameraUnavailable, vm.state.stage)
        assertEquals(listOf("scan_started asked=false", "camera_unavailable"), telemetry.events)
    }

    @Test
    fun cameraUnavailableIsIgnoredWithoutPermission() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        settle()

        vm.takeAction(BarcodeScanAction.CameraUnavailable)
        settle()

        assertEquals(ScanStage.NoCamera(canAskAgain = false), vm.state.stage)
    }

    @Test
    fun codeReadIsIgnoredWhileTheCameraIsUnavailableOrTheCodeIsBeingTyped() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.CameraUnavailable)
        vm.takeAction(ean13())
        vm.takeAction(BarcodeScanAction.RetryCameraTapped)
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(ean13())
        settle()

        assertTrue(catalog.barcodes.isEmpty())
    }

    @Test
    fun retryCameraTappedScansAgain() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.CameraUnavailable)

        vm.takeAction(BarcodeScanAction.RetryCameraTapped)
        settle()

        assertEquals(ScanStage.Scanning, vm.state.stage)
    }

    @Test
    fun typeCodeTappedOpensAnEmptyField() = runUnitTest {
        val vm = vm()
        settle()

        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        settle()

        assertEquals(ScanStage.Typing("", canLookUp = false, misread = false), vm.state.stage)
    }

    @Test
    fun codeTypedKeepsDigitsOnlyUpTo14() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)

        vm.takeAction(BarcodeScanAction.CodeTyped("0 12345-67890 5 999 1"))
        settle()

        assertEquals(ScanStage.Typing("01234567890599", canLookUp = true, misread = false), vm.state.stage)
    }

    @Test
    fun lookUpTypedTappedRunsTheSameLookupAsAScan() = runUnitTest {
        catalog.barcodeResult = Catching.success(LoggerFixtures.scannedYogurt)
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.CodeTyped("036632027627"))

        vm.takeAction(BarcodeScanAction.LookUpTypedTapped)
        settle()

        assertEquals(listOf("036632027627"), catalog.barcodes)
        assertEquals(
            listOf<BarcodeScanEvent>(BarcodeScanEvent.OpenFoodDetail(LoggerFixtures.scannedYogurt.id, LoggerFixtures.today, MealSlot.Dinner)),
            events,
            "a typed code was not caught by the camera, so no buzz",
        )
        assertEquals("hit usda_branded slow=false typed", telemetry.events.last())
    }

    @Test
    fun lookUpTypedTappedMissesLikeAScanAndIsReported() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.CodeTyped(LoggerFixtures.MISSED_CODE))

        vm.takeAction(BarcodeScanAction.LookUpTypedTapped)
        settle()

        assertEquals(ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "", canSave = false), vm.state.stage)
        assertEquals(listOf(LoggerFixtures.MISSED_CODE), misses.reported)
        assertEquals("miss typed", telemetry.events.last())
    }

    @Test
    fun lookUpTypedTappedWithABadCheckDigitAsksToCheckTheNumbers() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.CodeTyped("4006381333932"))

        vm.takeAction(BarcodeScanAction.LookUpTypedTapped)
        settle()
        assertEquals(ScanStage.Typing("4006381333932", canLookUp = true, misread = true), vm.state.stage)
        assertTrue(catalog.barcodes.isEmpty())

        vm.takeAction(BarcodeScanAction.CodeTyped("400638133393"))
        settle()
        assertEquals(ScanStage.Typing("400638133393", canLookUp = true, misread = false), vm.state.stage, "editing clears it")
    }

    @Test
    fun lookUpTypedTappedWithTooFewDigitsDoesNothing() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.CodeTyped("1234567"))

        vm.takeAction(BarcodeScanAction.LookUpTypedTapped)
        settle()

        assertTrue(catalog.barcodes.isEmpty())
        assertIs<ScanStage.Typing>(vm.state.stage)
    }

    @Test
    fun typingCancelledGoesBackToWhatTheCameraCanDo() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.TypingCancelled)
        settle()
        assertEquals(ScanStage.Scanning, vm.state.stage)

        vm.takeAction(BarcodeScanAction.CameraUnavailable)
        vm.takeAction(BarcodeScanAction.TypeCodeTapped)
        vm.takeAction(BarcodeScanAction.TypingCancelled)
        settle()
        assertEquals(ScanStage.CameraUnavailable, vm.state.stage)
    }

    @Test
    fun closeTappedCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.CloseTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
    }
}
