package com.opson.features.logger.impl

import com.opson.libraries.foods.BarcodeMatch
import com.opson.libraries.foods.EnergyCheck
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BarcodeMatches]: which of a code's matches fold into one row, and what each row says in each
 * mode. That the rows stay free of numbers on screen in a mode without them is the leak test's
 * (`logger-scan-matches`); the stage around them is [BarcodeScanStateBuilderTest]'s.
 */
class BarcodeMatchesTest {

    private val bare = FormatContext(bare = true, day = LoggerFixtures.today)

    private fun rows(matches: List<BarcodeMatch>, preset: PresetId = PresetId.Full) =
        BarcodeMatches.rows(BarcodeMatches.groups(matches), policy(preset), bare)

    @Test
    fun theSameLabelInTwoDatabasesIsOneRowNamingBoth() {
        val groups = BarcodeMatches.groups(LoggerFixtures.barMatches)
        assertEquals(
            listOf(listOf(LoggerFixtures.opdbBar.id, LoggerFixtures.offBar.id), listOf(LoggerFixtures.brandedBar.id)),
            groups.map { group -> group.map { it.food.id } },
        )
        val first = rows(LoggerFixtures.barMatches).first()
        assertEquals(listOf(SourceBadge.Opson, SourceBadge.OpenFoodFacts), first.sources)
        assertEquals(LoggerFixtures.opdbBar.id, first.foodId, "the row opens the server's answer")
    }

    @Test
    fun sameWithinRoundingAndNotBeyondIt() {
        val label = NutrientProfile(kcal = 364.0, protein = 36.4, carbs = 32.7, fat = 14.5)
        assertTrue(BarcodeMatches.sameNumbers(label, label.copy(kcal = 365.0, protein = 36.0)))
        assertTrue(BarcodeMatches.sameNumbers(label, label.copy(kcal = 370.0)), "2% of a label's energy is rounding")
        assertFalse(BarcodeMatches.sameNumbers(label, label.copy(kcal = 382.0)))
        assertFalse(BarcodeMatches.sameNumbers(label, label.copy(fat = 16.0)))
        assertFalse(BarcodeMatches.sameNumbers(label, label.copy(fat = null)), "a number one source lacks is a difference")
        assertTrue(BarcodeMatches.sameNumbers(NutrientProfile(kcal = 0.5), NutrientProfile(kcal = 1.4)), "a kcal either way near zero")
    }

    @Test
    fun oneMatchOrOnlyCopiesOfItIsOneGroup() {
        assertEquals(1, BarcodeMatches.groups(listOf(BarcodeMatch(LoggerFixtures.opdbBar))).size)
        assertEquals(1, BarcodeMatches.groups(listOf(BarcodeMatch(LoggerFixtures.opdbBar), BarcodeMatch(LoggerFixtures.offBar))).size)
        assertEquals(1, BarcodeMatches.groups(listOf(BarcodeMatch(LoggerFixtures.opdbBar), BarcodeMatch(LoggerFixtures.opdbBar))).size, "the same food twice")
    }

    @Test
    fun eachRowHasItsServingEnergyMacrosAndMarks() {
        val zero = BarcodeMatch(LoggerFixtures.zeroBar, energyCheck = setOf(EnergyCheck.ZeroWithMacros))
        val (suggested, branded, doubted) = rows(LoggerFixtures.barMatches + zero)

        assertEquals("1 bar (55 g)", suggested.amount)
        assertEquals(Display.Shown("200"), suggested.calories)
        assertTrue(suggested.macros.isNotEmpty())
        assertEquals("Trailhead", suggested.brand)
        assertTrue(suggested.suggested && suggested.verified)
        assertFalse(suggested.corrected || suggested.numbersLookOff)

        assertEquals(listOf(SourceBadge.UsdaBranded), branded.sources)
        assertTrue(branded.corrected)
        assertFalse(branded.suggested)
        assertTrue(branded.notChecked && !branded.verified, "a label nobody has checked against the pack says Unverified")

        assertTrue(doubted.numbersLookOff)
        assertFalse(doubted.verified)
        assertEquals(null, doubted.brand)
    }

    @Test
    fun withoutCaloriesNothingIsSaidAboutThem() {
        val zero = BarcodeMatch(LoggerFixtures.zeroBar, energyCheck = setOf(EnergyCheck.ZeroWithMacros))
        val rows = rows(LoggerFixtures.barMatches + zero, PresetId.NoNumbers)
        assertTrue(rows.all { it.calories == Display.Hidden && it.macros.isEmpty() })
        assertTrue(rows.none { it.corrected || it.numbersLookOff })
        assertTrue(rows.first().verified, "where a food comes from is not a number")
        assertEquals("1 bar (55 g)", rows.first().amount, "a serving is an amount of food")
    }
}
