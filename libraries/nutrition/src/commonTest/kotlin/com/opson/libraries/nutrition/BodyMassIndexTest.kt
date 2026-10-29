package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BodyMassIndexTest {

    @Test
    fun weightOverHeightSquared_toOneDecimal() {
        val bmi = BodyMassIndex.of(weightKg = 72.0, heightCm = 175.0)!!
        assertEquals(23.51, bmi, 0.01)
        assertEquals("23.5", BodyMassIndex.text(bmi))
    }

    @Test
    fun noAnswer_withoutBoth_orForATypo() {
        assertNull(BodyMassIndex.of(weightKg = null, heightCm = 175.0))
        assertNull(BodyMassIndex.of(weightKg = 72.0, heightCm = null))
        assertNull(BodyMassIndex.of(weightKg = 72.0, heightCm = 17.5), "17.5 cm is a slipped decimal")
        assertNull(BodyMassIndex.of(weightKg = 7200.0, heightCm = 175.0))
    }

    @Test
    fun thePolicyShowsIt_onlyWhereWeightIsShownInFull() {
        assertEquals(Display.Shown("23.5"), policy(PresetId.Full).bmi(72.0, 175.0, Surface.Settings))
        assertEquals(Display.Hidden, policy(PresetId.NoNumbers).bmi(72.0, 175.0, Surface.Settings))
        val softened = policy(PresetId.Full, overrides = mapOf(Metric.Weight to Visibility.Soften))
        assertEquals(Display.Hidden, softened.bmi(72.0, 175.0, Surface.Settings), "no honest soft word for it")
        assertEquals(Display.Hidden, policy(PresetId.Full).bmi(null, 175.0, Surface.Settings))
    }

    @Test
    fun bodyFat_takesASensibleShare_andWritesItBackPlainly() {
        assertEquals(18.5, BodyFat.inRange(18.5))
        assertNull(BodyFat.inRange(1.0))
        assertNull(BodyFat.inRange(185.0))
        assertEquals("18", BodyFat.text(18.0))
        assertEquals("18.5", BodyFat.text(18.5))
    }
}
