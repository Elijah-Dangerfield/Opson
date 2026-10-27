package com.opson.libraries.diary.ui

import com.opson.libraries.nutrition.ExerciseActivities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExerciseLabelsTest {

    @Test
    fun everyListedActivityAndOther_haveTheirOwnLabel() {
        val labels = (ExerciseActivities.all.map { it.key } + ExerciseActivities.OTHER).map { key ->
            assertNotNull(exerciseActivityLabel(key), "no label for $key")
        }
        assertEquals(labels.size, labels.toSet().size, "two activities share a label")
    }

    @Test
    fun aKeyFromANewerPhone_hasNoLabel() {
        assertNull(exerciseActivityLabel("underwater_hockey"))
    }
}
