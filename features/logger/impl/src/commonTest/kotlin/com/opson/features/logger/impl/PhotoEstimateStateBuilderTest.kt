package com.opson.features.logger.impl

import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PhotoEstimateStateBuilder]: every stage, and what each visibility level leaves on a line and in the footer. */
class PhotoEstimateStateBuilderTest {
    private val full = policy(PresetId.Full)
    private val blind = policy(PresetId.NoNumbers)

    private fun review(policy: VisibilityPolicy, editing: Boolean = false): PhotoStage.Review =
        assertIs(PhotoFixtures.reviewState(policy, editing).stage)

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    @Test
    fun eachStepIsItsStage() {
        assertEquals(PhotoStage.Start, PhotoFixtures.startState(full).stage)
        assertEquals(PhotoStage.Estimating, PhotoFixtures.estimatingState(full).stage)
        assertEquals(PhotoStage.NothingFound, PhotoFixtures.calmState(PhotoStep.NothingFound, full).stage)
        assertEquals(PhotoStage.Unavailable, PhotoFixtures.calmState(PhotoStep.Unavailable, full).stage)
        assertEquals(PhotoStage.TooMany, PhotoFixtures.calmState(PhotoStep.TooMany, full).stage)
        assertEquals(PhotoStage.Failed(offline = true), PhotoFixtures.calmState(PhotoStep.Failed(offline = true), full).stage)
        assertEquals(PhotoStage.CameraOff(canAskAgain = false), PhotoFixtures.calmState(PhotoStep.CameraOff(false), full).stage)
    }

    @Test
    fun inFullEachLineShowsItsAmountAndBareEnergy() {
        val rows = review(full).rows
        assertEquals(listOf("Chicken breast, grilled", "Olive oil", "Broccoli, steamed"), rows.map { it.name })
        assertEquals(listOf("120 g", "1 tbsp", "2 cups"), rows.map { it.amount })
        assertEquals(listOf(true, false, false), rows.map { it.isWeight })
        assertEquals(listOf("198", "119", "62"), rows.map { it.calories.text() })
        assertTrue(rows.all { it.matched })
    }

    @Test
    fun theFooterCountsTheLinesAndTotalsTheirEnergy() {
        val footer = review(full).footer
        assertEquals(3, footer?.count)
        assertEquals("380 kcal", footer?.calories?.text())
    }

    @Test
    fun blindDropsEveryEnergyValueAndCountsInHouseholdMeasures() {
        val review = review(blind)
        assertTrue(review.rows.all { it.calories == Display.Hidden })
        assertEquals(Display.Hidden, review.footer?.calories)
        // The chicken was a weight; with calories hidden it is counted in its household measure.
        assertEquals("1 piece", review.rows.first().amount)
        assertFalse(review.rows.first().isWeight)
    }

    @Test
    fun delayedHoldsBackTodayBeforeTheReveal() {
        val review = review(policy(PresetId.Later))
        assertTrue(review.rows.all { it.calories == Display.Hidden })
        assertEquals(Display.Hidden, review.footer?.calories)
    }

    @Test
    fun theStepperOffersGramsOnlyWhenCaloriesShow() {
        val fullEditor = review(full, editing = true).editor
        assertEquals("photo:2", fullEditor?.key)
        assertEquals(listOf("cup", "grams"), fullEditor?.units)
        assertEquals(0, fullEditor?.unitIndex)
        assertEquals("2 cups", fullEditor?.amount)

        assertEquals(listOf("cup"), review(blind, editing = true).editor?.units)
    }

    @Test
    fun aLineTheCatalogDidNotKnowKeepsTheServersAmountAndHasNoEnergy() {
        val unknown = ParsedItem(
            text = "a spoon of chutney", name = "chutney", quantity = 1.0, amountText = "1 tbsp", confidence = Confidence.Low, unit = "tbsp",
        )
        val model = PhotoFixtures.model(PhotoStep.Review).copy(rows = listOf(PhotoRows.fromEstimate("photo:9", unknown, null, false)))
        val row = assertIs<PhotoStage.Review>(PhotoFixtures.state(model, full).stage).rows.single()
        assertFalse(row.matched)
        assertEquals("1 tbsp", row.amount)
        assertEquals(Display.Hidden, row.calories)
    }

    @Test
    fun theFooterCountsExactlyTheLines() {
        val review = assertIs<PhotoStage.Review>(PhotoFixtures.state(PhotoFixtures.reviewModel(full), full).stage)
        assertEquals(PhotoFixtures.reviewModel(full).rows.size, review.footer?.count)
    }

    @Test
    fun anEmptyListHasNoFooter() {
        val model = PhotoFixtures.reviewModel(full).copy(rows = emptyList())
        assertNull(assertIs<PhotoStage.Review>(PhotoFixtures.state(model, full).stage).footer)
    }

    @Test
    fun theSwapSearchWaitsForItsAnswerThenShowsIt() {
        val swap = assertIs<PhotoStage.Swap>(PhotoFixtures.swapState(full).stage)
        assertTrue(swap.replacing)
        assertEquals(listOf("Chicken breast, grilled", "Chicken thigh, grilled"), swap.results?.map { it.name })
        assertEquals("1 piece", swap.results?.first()?.amount)
        assertEquals("198", swap.results?.first()?.calories?.text())

        val pending = PhotoFixtures.reviewModel(full).copy(swap = PhotoSwap(replacing = null, query = "rice"))
        val waiting = assertIs<PhotoStage.Swap>(PhotoFixtures.state(pending, full).stage)
        assertFalse(waiting.replacing)
        assertNull(waiting.results)

        val blank = PhotoFixtures.reviewModel(full).copy(swap = PhotoSwap(replacing = null))
        assertEquals(emptyList(), assertIs<PhotoStage.Swap>(PhotoFixtures.state(blank, full).stage).results)
    }

    @Test
    fun blindHidesTheSwapResultsEnergy() {
        val swap = assertIs<PhotoStage.Swap>(PhotoFixtures.swapState(blind).stage)
        assertTrue(swap.results.orEmpty().all { it.calories == Display.Hidden })
    }

    @Test
    fun theUsedNoteBannerFollowsTheModel() {
        assertTrue(review(full).usedNote)
        val model = PhotoFixtures.reviewModel(full).copy(usedNote = false)
        assertFalse(assertIs<PhotoStage.Review>(PhotoFixtures.state(model, full).stage).usedNote)
    }
}
