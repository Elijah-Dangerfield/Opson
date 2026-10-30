package com.opson.features.logger.impl

import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelLayout
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_unit_g
import opson.libraries.resources.generated.resources.submit_unit_kcal

class SubmitFoodStateBuilderTest {

    private val full = policy(PresetId.Full)
    private val noNumbers = policy(PresetId.NoNumbers)

    private fun form(model: SubmitModel = SubmitFoodFixtures.reviewModel(), policy: com.opson.libraries.nutrition.VisibilityPolicy = full): SubmitForm =
        assertIs<SubmitStage.Review>(SubmitFoodStateBuilder.build(model, policy).stage).form

    private fun SubmitForm.nutrient(key: LabelNutrient): FormField =
        (numbers!!.main + numbers.more).single { it.nutrient == key }.field

    @Test
    fun theReadingFillsTheFormAsPrinted() {
        val form = form()

        assertEquals("Oats & honey granola bar", form.name.text)
        assertEquals(SubmitFoodFixtures.BARCODE, form.barcode.text)
        assertTrue(form.barcodeLocked)
        assertEquals("60", form.servingSize.text)
        assertEquals("1 bar", form.servingLabel)
        assertEquals("280", form.nutrient(LabelNutrient.Energy).text)
        assertEquals("1.1", form.nutrient(LabelNutrient.Iron).text)
        assertEquals(listOf(LabelNutrient.Calcium, LabelNutrient.Iron), form.numbers!!.more.map { it.nutrient })
    }

    @Test
    fun aFieldTheModelWasUnsureOfAsksForALookUntilItIsTyped() {
        val model = SubmitFoodFixtures.reviewModel()
        assertTrue(form(model).nutrient(LabelNutrient.Protein).needsLook)
        assertFalse(form(model).name.needsLook)

        val typed = SubmitFoodForm.typed(model, LabelNutrient.Protein.wire, "11")
        assertFalse(form(typed).nutrient(LabelNutrient.Protein).needsLook)
    }

    @Test
    fun theEnergyCheckSaysByHowMuchAndOffersBothFigures() {
        val form = form()

        val row = form.checks.single()
        assertEquals(LabelCheckCode.EnergyMacrosMismatch, row.code)
        assertEquals(UnitAmount("46", Res.string.submit_unit_kcal), row.difference)
        assertEquals(UnitAmount("234", Res.string.submit_unit_kcal), row.fix)
        assertEquals(UnitAmount("280", Res.string.submit_unit_kcal), row.keep)
        assertTrue(form.nutrient(LabelNutrient.Energy).flagged)
        assertTrue(form.canSubmit, "a mismatch is the person's call, not a block")
    }

    @Test
    fun fixingWritesTheMacrosFigureAndTheCheckGoesAway() {
        val model = SubmitFoodFixtures.reviewModel()
        val fixed = SubmitFoodForm.fix(model, SubmitFoodForm.checks(model).single())

        assertEquals("234", fixed.texts[LabelNutrient.Energy.wire])
        assertEquals(emptyList(), form(fixed).checks)
    }

    @Test
    fun keepingTheLabelsFigureMarksTheCheckKept() {
        val model = SubmitFoodFixtures.reviewModel().copy(kept = setOf(LabelCheckCode.EnergyMacrosMismatch to LabelNutrient.Energy.wire))
        val form = form(model)

        assertTrue(form.checks.single().kept)
        assertFalse(form.nutrient(LabelNutrient.Energy).flagged)
    }

    @Test
    fun anImpossibleAmountBlocksAddingWhereItCanBeSeen() {
        val model = SubmitFoodForm.typed(SubmitFoodFixtures.reviewModel(), LabelNutrient.Sugars.wire, "50")

        val shown = form(model)
        assertFalse(shown.canSubmit)
        assertEquals(listOf("sugars_g"), shown.missing)
        assertEquals(LabelCheckCode.ImpossibleValue, shown.checks.first().code)
        assertNull(shown.checks.first().fix)

        assertTrue(form(model, noNumbers).canSubmit, "a mode that hides the numbers cannot ask for them to be fixed")
    }

    @Test
    fun nameAndServingAreAlwaysNeededAndNumbersOnlyWhereTheyShow() {
        val empty = SubmitFoodFixtures.reviewModel(read = null, notice = ReviewNotice.ByHand)

        val shown = form(empty)
        assertFalse(shown.canSubmit)
        assertEquals(listOf("name", "serving_size", "energy_kcal", "protein_g", "carbs_g", "fat_g"), shown.missing)
        assertTrue(shown.name.missing)

        val hidden = form(empty, noNumbers)
        assertEquals(listOf("name", "serving_size"), hidden.missing)
        val named = SubmitFoodForm.typed(SubmitFoodForm.typed(empty, SubmitFoodForm.NAME, "Rye crispbread"), SubmitFoodForm.SERVING_SIZE, "10")
        assertTrue(form(named, noNumbers).canSubmit)
        assertTrue(form(named, noNumbers).hidden!!.incomplete)
        assertFalse(form(named).canSubmit)
    }

    @Test
    fun noNumbersDrawsNoNumberAndSaysInWordsWhetherItAddsUp() {
        val hidden = form(SubmitFoodFixtures.reviewModel(), noNumbers)

        assertNull(hidden.numbers)
        assertEquals(emptyList(), hidden.checks)
        assertEquals(HiddenNumbers(addsUp = false, incomplete = false), hidden.hidden)
        assertFalse(hidden.nutrientFlagged(), "no field is flagged by a number it cannot show")

        val fixed = SubmitFoodForm.fix(SubmitFoodFixtures.reviewModel(), SubmitFoodForm.checks(SubmitFoodFixtures.reviewModel()).single())
        assertEquals(HiddenNumbers(addsUp = true, incomplete = false), form(fixed, noNumbers).hidden)
    }

    private fun SubmitForm.nutrientFlagged(): Boolean = listOf(name, brand, servingSize).any { it.flagged }

    @Test
    fun aPer100OnlyLabelTakesAHundredGramServingAndFollowsACorrectedSize() {
        val per100 = LabelFacts(
            name = "Corn flakes",
            per100 = mapOf(LabelNutrient.Energy to 380.0, LabelNutrient.Protein to 8.0, LabelNutrient.Carbs to 80.0, LabelNutrient.Fat to 3.0),
        )
        val model = SubmitFoodFixtures.reviewModel(read = LabelParse.Read("p", per100, emptyMap(), emptyList(), readable = true))

        assertEquals("100", model.texts[SubmitFoodForm.SERVING_SIZE])
        assertTrue(form(model).servingSize.needsLook)
        assertEquals("380", model.texts[LabelNutrient.Energy.wire])

        val thirty = SubmitFoodForm.typed(model, SubmitFoodForm.SERVING_SIZE, "30")
        assertEquals("114", thirty.texts[LabelNutrient.Energy.wire])
        assertEquals("2.4", thirty.texts[LabelNutrient.Protein.wire])
        assertEquals(emptyList(), form(thirty).checks)

        // A figure typed is the person's: it no longer follows the serving, and per 100 stops arguing with it.
        val typed = SubmitFoodForm.typed(thirty, LabelNutrient.Energy.wire, "120")
        assertEquals("120", SubmitFoodForm.typed(typed, SubmitFoodForm.SERVING_SIZE, "45").texts[LabelNutrient.Energy.wire])
    }

    @Test
    fun aServingSizeThatDisagreesWithPer100IsFixedToTheSizeTheNumbersImply() {
        val facts = LabelFacts(
            name = "Muesli",
            servingSize = 30.0,
            servingUnit = ServingUnit.Gram,
            perServing = mapOf(LabelNutrient.Energy to 180.0, LabelNutrient.Protein to 4.5, LabelNutrient.Carbs to 27.0, LabelNutrient.Fat to 5.4),
            per100 = mapOf(LabelNutrient.Energy to 400.0, LabelNutrient.Protein to 10.0, LabelNutrient.Carbs to 60.0, LabelNutrient.Fat to 12.0),
        )
        val model = SubmitFoodFixtures.reviewModel(read = LabelParse.Read("p", facts, emptyMap(), emptyList(), readable = true))
        val row = form(model).checks.first { it.code == LabelCheckCode.ServingMismatch }
        assertEquals(UnitAmount("45", Res.string.submit_unit_g), row.fix)

        val fixed = SubmitFoodForm.fix(model, SubmitFoodForm.checks(model).first { it.code == LabelCheckCode.ServingMismatch })
        assertEquals("45", fixed.texts[SubmitFoodForm.SERVING_SIZE])
        assertTrue(form(fixed).checks.none { it.code == LabelCheckCode.ServingMismatch })
    }

    @Test
    fun aBarcodeTypedKeepsOnlyDigitsAndNumbersTakeOneDecimalPoint() {
        val model = SubmitFoodFixtures.reviewModel(barcode = null)
        assertFalse(form(model).barcodeLocked)
        assertEquals("4006381333931", SubmitFoodForm.typed(model, SubmitFoodForm.BARCODE, "4006 3813-33931").texts[SubmitFoodForm.BARCODE])
        assertEquals("12.50", SubmitFoodForm.typed(model, LabelNutrient.Fat.wire, "12,5.0g").texts[LabelNutrient.Fat.wire])
    }

    @Test
    fun theFactsAreWhatTheFormSays() {
        val facts = SubmitFoodForm.facts(SubmitFoodForm.typed(SubmitFoodFixtures.reviewModel(), LabelNutrient.Energy.wire, ""))

        assertEquals("Oats & honey granola bar", facts.name)
        assertEquals(60.0, facts.servingSize)
        assertNull(facts.perServing[LabelNutrient.Energy])
        assertEquals(150.0, facts.perServing[LabelNutrient.Sodium])
        assertEquals("1 bar", facts.servingLabel)
    }

    @Test
    fun amountsReadAsALabelPrintsThem() {
        assertEquals("234", SubmitFoodForm.amount(233.6, com.opson.libraries.foods.LabelUnit.Kcal))
        assertEquals("1.08", SubmitFoodForm.amount(1.08))
        assertEquals("2.4", SubmitFoodForm.amount(2.4000000001))
        assertEquals("45", SubmitFoodForm.amount(45.0))
    }

    @Test
    fun thePhotosStageCanReadOnceBothPhotosAreThere() {
        assertEquals(SubmitStage.Photos(canRead = false), SubmitFoodFixtures.photosState(full, photos = 1).stage)
        assertEquals(SubmitStage.Photos(canRead = true), SubmitFoodFixtures.photosState(full, photos = 2).stage)
        assertNotNull(SubmitFoodFixtures.photosState(full, photos = 2).label)
    }

    @Test
    fun aNameGuessedFromTheFrontIsFilledInAndMarked() {
        val draft = LabelDraft(
            facts = LabelFacts(servingSize = 30.0, servingUnit = ServingUnit.Gram),
            layout = LabelLayout.NONE,
            fieldConfidence = mapOf(SubmitFoodForm.SERVING_SIZE to 0.9),
        )
        val model = SubmitFoodForm.fromDraft(SubmitFoodFixtures.model(SubmitStep.Review, photos = 2), draft, barcode = null, name = "Rye Crispbread")

        val form = form(model)
        assertEquals("Rye Crispbread", form.name.text)
        assertTrue(form.name.needsLook)
        assertFalse(form.servingSize.needsLook)

        val typed = SubmitFoodForm.typed(model, SubmitFoodForm.NAME, "Rye crispbread")
        assertFalse(form(typed).name.needsLook, "typed over, it is the person's word")
    }

    @Test
    fun howMuchOfThePanelThePhoneReadDecidesTheNotice() {
        fun draft(facts: LabelFacts, panel: Boolean) = LabelDraft(facts, LabelLayout.NONE.copy(panelFound = panel), emptyMap())
        val all = SubmitFoodFixtures.facts.copy(name = null, brand = null)
        val some = LabelFacts(perServing = mapOf(LabelNutrient.Energy to 280.0))

        assertTrue(SubmitFoodForm.usable(draft(all, panel = true)))
        assertFalse(SubmitFoodForm.usable(draft(all, panel = false)), "numbers with no panel around them are not trusted to fill the form")
        assertTrue(SubmitFoodForm.partly(draft(some, panel = true)))
        assertFalse(SubmitFoodForm.partly(draft(LabelFacts(), panel = false)))
    }
}
