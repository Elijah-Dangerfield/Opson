package com.opson.features.logger.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.ScannedCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** [BarcodeScanViewModel], one test per action. The stage mapping itself is [BarcodeScanStateBuilderTest]'s. */
class BarcodeScanViewModelTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog()
    private val misses = FakeMissReporter()
    private val permissions = FakeCameraPermissions()
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val basket = BasketStore()
    private val telemetry = RecordingBarcodeTelemetry()

    private fun vm() = BarcodeScanViewModel(
        route = BarcodeScanRoute(dayIso = LoggerFixtures.today.toString(), slot = MealSlot.Dinner),
        foodCatalog = catalog,
        missReporter = misses,
        permissions = permissions,
        customFoods = customFoods,
        basketStore = basket,
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
            listOf<BarcodeScanEvent>(BarcodeScanEvent.OpenAmount(LoggerFixtures.scannedYogurt.id, LoggerFixtures.today, MealSlot.Dinner)),
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
        assertIs<BarcodeScanEvent.OpenAmount>(events.single())
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
    fun saveNameTappedPicksTheNameOnlyAndGoesBackToTheLogger() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.MissNameTyped("  Rye crispbread "))
        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        val picked = basket.basket.value.single()
        assertEquals(PickSource.Name, picked.source)
        assertEquals("Rye crispbread", picked.draft.name)
        assertEquals(Precision.Named, picked.draft.precision)
        assertEquals(null, picked.draft.kcal, "no numbers")
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
        assertEquals("granola", basket.basket.value.single().ref?.id)
    }

    @Test
    fun saveNameTappedWithoutANameDoesNothing() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.SaveNameTapped)
        settle()

        assertTrue(basket.basket.value.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun searchInsteadTappedGoesBackToTheLogger() = runUnitTest {
        val vm = missedVm()
        val events = events(vm)

        vm.takeAction(BarcodeScanAction.SearchInsteadTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
        assertTrue(basket.basket.value.isEmpty())
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
    fun closeTappedCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(BarcodeScanAction.CloseTapped)
        settle()

        assertEquals(listOf<BarcodeScanEvent>(BarcodeScanEvent.Close), events)
    }
}
