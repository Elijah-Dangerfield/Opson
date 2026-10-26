package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.scanner.CameraPermission
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every stage [BarcodeScanStateBuilder] can produce, and what decides it. */
class BarcodeScanStateBuilderTest {

    private fun stage(model: ScanModel): ScanStage = BarcodeScanStateBuilder.build(MealSlot.Lunch, model).stage

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
    fun aRefusedCameraWinsOverAnyLookup() {
        assertEquals(ScanStage.NoCamera(canAskAgain = false), stage(LoggerFixtures.cameraOff.copy(lookup = Lookup.Missed("123"))))
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
    fun theSlotIsCarriedThrough() {
        assertEquals(MealSlot.Lunch, BarcodeScanStateBuilder.build(MealSlot.Lunch, ScanModel()).slot)
    }
}
