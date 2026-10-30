package com.opson.libraries.diary

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who the gate lets in, what an older teen's goal flow leaves out, and the day the gate opens. */
class AgeGateTest {

    private val today = LocalDate(2026, 10, 30)

    @Test
    fun ageCountsWholeYears_andTheBirthdayItself() {
        assertEquals(15, AgeGate.ageOn(LocalDate(2010, 10, 31), today))
        assertEquals(16, AgeGate.ageOn(LocalDate(2010, 10, 30), today))
        assertNull(AgeGate.ageOn(LocalDate(2027, 1, 1), today))
    }

    @Test
    fun bands_splitAtSixteenAndEighteen() {
        assertEquals(AgeBand.TooYoung, AgeGate.bandOf(LocalDate(2012, 5, 1), today))
        assertEquals(AgeBand.TooYoung, AgeGate.bandOf(LocalDate(2010, 10, 31), today))
        assertEquals(AgeBand.OlderTeen, AgeGate.bandOf(LocalDate(2010, 10, 30), today))
        assertEquals(AgeBand.OlderTeen, AgeGate.bandOf(LocalDate(2008, 10, 31), today))
        assertEquals(AgeBand.Adult, AgeGate.bandOf(LocalDate(2008, 10, 30), today))
        assertEquals(AgeBand.Unknown, AgeGate.bandOf(null, today))
    }

    @Test
    fun theGateOpensOnTheSixteenthBirthday() {
        assertEquals(LocalDate(2028, 4, 18), AgeGate.opensOn(LocalDate(2012, 4, 18)))
    }

    @Test
    fun aLeapDayBirthday_countsFromTheFirstOfMarchInAnOrdinaryYear() {
        assertEquals(LocalDate(2028, 2, 29), AgeGate.opensOn(LocalDate(2012, 2, 29)))
        assertEquals(LocalDate(2100, 3, 1), AgeGate.opensOn(LocalDate(2084, 2, 29)))
        assertEquals(15, AgeGate.ageOn(LocalDate(2008, 2, 29), LocalDate(2024, 2, 28)))
        assertEquals(16, AgeGate.ageOn(LocalDate(2008, 2, 29), LocalDate(2024, 2, 29)))
        assertEquals(17, AgeGate.ageOn(LocalDate(2008, 2, 29), LocalDate(2025, 3, 1)))
        assertEquals(16, AgeGate.ageOn(LocalDate(2008, 2, 29), LocalDate(2025, 2, 28)))
    }

    @Test
    fun weightPlans_areOffForTeens_andAfterNumbersHaveBeenHard() {
        val teen = Settings().withBirthDate(LocalDate(2009, 6, 1))
        val adult = Settings().withBirthDate(LocalDate(1990, 6, 1))
        assertTrue(teen.weightPlansOff(today))
        assertFalse(adult.weightPlansOff(today))
        assertFalse(Settings().weightPlansOff(today))
        assertTrue(adult.copy(onboarding = OnboardingAnswers(hard = true)).weightPlansOff(today))
    }
}
