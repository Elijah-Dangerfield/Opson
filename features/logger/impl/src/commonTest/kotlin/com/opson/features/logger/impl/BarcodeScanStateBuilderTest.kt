package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.CameraPermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every stage [BarcodeScanStateBuilder] can produce, and what decides it. */
class BarcodeScanStateBuilderTest {

    private val full = policy(PresetId.Full)

    private fun stage(model: ScanModel): ScanStage = BarcodeScanStateBuilder.build(MealSlot.Lunch, model, full).stage

    @Test
    fun untilThePermissionIsKnownOrAnsweredItIsStarting() {
        assertEquals(ScanStage.Starting, stage(ScanModel()))
        assertEquals(ScanStage.Starting, stage(ScanModel(permission = CameraPermission.NotAsked)))
    }

    @Test
    fun aRefusedCameraSaysWhetherItCanAskAgain() {
        assertEquals(ScanStage.NoCamera(canAskAgain = false), stage(LoggerFixtures.cameraOff))
        assertEquals(ScanStage.NoCamera(canAskAgain = true), stage(LoggerFixtures.cameraAskAgain))
    }

    @Test
    fun aLookupWinsOverTheCameraBecauseATypedCodeNeedsNone() {
        assertEquals(
            ScanStage.Miss("123", name = "", canSave = false),
            stage(LoggerFixtures.cameraOff.copy(lookup = Lookup.Missed("123"))),
        )
        assertEquals(
            ScanStage.LookingUp(slow = false, camera = false),
            stage(LoggerFixtures.cameraUnavailable.copy(lookup = Lookup.Running("123", typed = true))),
            "no live camera to show under the spinner",
        )
    }

    @Test
    fun aCameraThatCannotRunIsItsOwnCalmState() {
        assertEquals(ScanStage.CameraUnavailable, stage(LoggerFixtures.cameraUnavailable))
    }

    @Test
    fun typingCanLookUpFrom8To14DigitsAndSaysWhenTheyDoNotAddUp() {
        assertEquals(ScanStage.Typing("", canLookUp = false, misread = false), stage(LoggerFixtures.scanning.copy(typed = "")))
        assertEquals(ScanStage.Typing("1234567", canLookUp = false, misread = false), stage(LoggerFixtures.scanning.copy(typed = "1234567")))
        assertEquals(ScanStage.Typing("40063813", canLookUp = true, misread = false), stage(LoggerFixtures.typing))
        assertEquals(ScanStage.Typing("4006381333932", canLookUp = true, misread = true), stage(LoggerFixtures.typingMisread))
        assertEquals(
            ScanStage.Typing("40063813", canLookUp = true, misread = false),
            stage(LoggerFixtures.cameraOff.copy(typed = "40063813")),
            "typing needs no camera permission",
        )
    }

    @Test
    fun withTheCameraOnTheLookupDecides() {
        assertEquals(ScanStage.Scanning, stage(LoggerFixtures.scanning))
        assertEquals(ScanStage.LookingUp(slow = false), stage(LoggerFixtures.scanning.copy(lookup = Lookup.Running("123"))))
        assertEquals(ScanStage.LookingUp(slow = true), stage(LoggerFixtures.lookingUpSlow))
        assertEquals(ScanStage.Unavailable, stage(LoggerFixtures.unavailable))
    }

    @Test
    fun aMissShowsTheCodeAndCanSaveOnlyWithAName() {
        assertEquals(ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "", canSave = false), stage(LoggerFixtures.missed))
        assertEquals(
            ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "   ", canSave = false),
            stage(LoggerFixtures.missed.copy(name = "   ")),
        )
        assertEquals(ScanStage.Miss(LoggerFixtures.MISSED_CODE, name = "Rye crispbread", canSave = true), stage(LoggerFixtures.missedNamed))
    }

    @Test
    fun theLightIsOfferedWhereverTheLiveCameraShows() {
        fun torch(model: ScanModel) = BarcodeScanStateBuilder.build(MealSlot.Lunch, model, full).torch

        assertEquals(Torch.Off, torch(LoggerFixtures.scanning))
        assertEquals(Torch.On, torch(LoggerFixtures.scanningLit))
        assertEquals(Torch.On, torch(LoggerFixtures.scanningLit.copy(lookup = Lookup.Running("123"))), "under the lookup's scrim")
        assertEquals(Torch.None, torch(LoggerFixtures.scanning.copy(hasTorch = false)), "no torch, no button")
    }

    @Test
    fun theLightIsNotOfferedWithoutALiveCamera() {
        fun torch(model: ScanModel) = BarcodeScanStateBuilder.build(MealSlot.Lunch, model.copy(hasTorch = true, torchOn = true), full).torch

        assertEquals(Torch.None, torch(LoggerFixtures.missed))
        assertEquals(Torch.None, torch(LoggerFixtures.unavailable))
        assertEquals(Torch.None, torch(LoggerFixtures.cameraUnavailable))
        assertEquals(Torch.None, torch(LoggerFixtures.cameraOff))
        assertEquals(Torch.None, torch(LoggerFixtures.scanning.copy(typed = "123")))
        assertEquals(Torch.None, torch(LoggerFixtures.cameraUnavailable.copy(lookup = Lookup.Running("123", typed = true))))
    }

    @Test
    fun severalMatchesAreListedWithTheSuggestedFirstAndNoCameraOrLight() {
        val matches = assertIs<ScanStage.Matches>(stage(LoggerFixtures.matched.copy(torchOn = true)))
        assertEquals(LoggerFixtures.BAR_CODE, matches.code)
        assertEquals(listOf(LoggerFixtures.opdbBar.id, LoggerFixtures.brandedBar.id), matches.rows.map { it.foodId })
        assertEquals(listOf(true, false), matches.rows.map { it.suggested })
        assertTrue(matches.openFoodFacts, "Open Food Facts is folded into the first row, so its credit goes under the list")
        assertEquals(Torch.None, BarcodeScanStateBuilder.build(MealSlot.Lunch, LoggerFixtures.matched.copy(hasTorch = true, torchOn = true), full).torch)
    }

    @Test
    fun theSlotIsCarriedThrough() {
        assertEquals(MealSlot.Lunch, BarcodeScanStateBuilder.build(MealSlot.Lunch, ScanModel(), full).slot)
    }
}
