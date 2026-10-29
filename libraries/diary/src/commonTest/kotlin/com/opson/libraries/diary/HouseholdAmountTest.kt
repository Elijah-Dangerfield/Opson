package com.opson.libraries.diary

import com.opson.libraries.nutrition.AmountCopy
import com.opson.libraries.nutrition.AmountWords
import kotlin.test.Test
import kotlin.test.assertEquals

/** Amounts read the way people say them: fractions, and a plural unit past one. */
class HouseholdAmountTest {

    @Test
    fun commonFractionsBecomeGlyphs() {
        assertEquals("¼", HouseholdAmount.number(0.25))
        assertEquals("½", HouseholdAmount.number(0.5))
        assertEquals("¾", HouseholdAmount.number(0.75))
        assertEquals("⅓", HouseholdAmount.number(1.0 / 3))
        assertEquals("⅔", HouseholdAmount.number(0.67))
        assertEquals("⅛", HouseholdAmount.number(0.125))
        assertEquals("1½", HouseholdAmount.number(1.5))
        assertEquals("2¼", HouseholdAmount.number(2.25))
    }

    @Test
    fun wholeNumbersStayWholeAndOddValuesKeepTwoDecimals() {
        assertEquals("2", HouseholdAmount.number(2.0))
        assertEquals("1", HouseholdAmount.number(0.999))
        assertEquals("0.4", HouseholdAmount.number(0.4))
        assertEquals("1.1", HouseholdAmount.number(1.1))
    }

    @Test
    fun aHouseholdUnitPluralizesPastOne() {
        assertEquals("¼ cup", HouseholdAmount.of(0.25, "cup"))
        assertEquals("1 cup", HouseholdAmount.of(1.0, "cup"))
        assertEquals("1½ cups", HouseholdAmount.of(1.5, "cup"))
        assertEquals("2 slices", HouseholdAmount.of(2.0, "slice"))
        assertEquals("2 bars", HouseholdAmount.of(2.0, "bar"))
        assertEquals("3 glasses", HouseholdAmount.of(3.0, "glass"))
        assertEquals("2 patties", HouseholdAmount.of(2.0, "patty"))
        assertEquals("2 pinches", HouseholdAmount.of(2.0, "pinch"))
        assertEquals("2 loaves", HouseholdAmount.of(2.0, "loaf"))
    }

    @Test
    fun onlyTheNounOfAPhrasePluralizes() {
        assertEquals("2 cups, sliced", HouseholdAmount.of(2.0, "cup, sliced"))
        assertEquals("2 large eggs", HouseholdAmount.of(2.0, "large egg"))
        assertEquals("2 NLEA servings", HouseholdAmount.of(2.0, "NLEA serving"))
        assertEquals("2 containers (6 oz)", HouseholdAmount.of(2.0, "container (6 oz)"))
    }

    @Test
    fun sizesAbbreviationsAndPluralsStayAsTheyAre() {
        assertEquals("2 medium", HouseholdAmount.of(2.0, "medium"))
        assertEquals("1½ medium", HouseholdAmount.of(1.5, "medium"))
        assertEquals("2 medium (7\" long)", HouseholdAmount.of(2.0, "medium (7\" long)"))
        assertEquals("2 tbsp", HouseholdAmount.of(2.0, "tbsp"))
        assertEquals("2 fries", HouseholdAmount.of(2.0, "fries"))
    }

    @Test
    fun metricAmountsStayDecimalAndSingular() {
        assertEquals("150 g", HouseholdAmount.of(150.0, "g"))
        assertEquals("12.5 g", HouseholdAmount.of(12.5, "g"))
        assertEquals("1.5 oz", HouseholdAmount.of(1.5, "oz"))
        assertEquals("250 ml", HouseholdAmount.of(250.0, "ml"))
        assertEquals("1.5 fl oz", HouseholdAmount.of(1.5, "fl oz"))
    }

    @Test
    fun aPortionLabelCarriesItsOwnAmount() {
        assertEquals("1 cup", HouseholdAmount.ofPortion("1 cup"))
        assertEquals("¼ cup", HouseholdAmount.ofPortion("0.25 cup"))
        assertEquals("2 cups", HouseholdAmount.ofPortion("1 cup", 2.0))
        assertEquals("1½ cups", HouseholdAmount.ofPortion("1/2 cup", 3.0))
        assertEquals("¾ cup", HouseholdAmount.ofPortion("1 1/2 cup", 0.5))
        assertEquals("1 cup", HouseholdAmount.ofPortion("½ cup", 2.0))
        assertEquals("3 slices", HouseholdAmount.ofPortion("slice", 3.0))
        assertEquals("½ medium", HouseholdAmount.ofPortion("1 medium", 0.5))
    }

    @Test
    fun aPortionLabelSplitsIntoAmountAndUnit() {
        assertEquals("cup, sliced", HouseholdAmount.unitOf("1/2 cup, sliced"))
        assertEquals(0.5, HouseholdAmount.amountOf("1/2 cup, sliced"))
        assertEquals(1.5, HouseholdAmount.amountOf("1½ cups"))
        assertEquals(1.0, HouseholdAmount.amountOf("slice"))
        assertEquals("100g bag", HouseholdAmount.unitOf("100g bag"))
    }

    @Test
    fun tidyRewritesOnlyTextThatStartsWithAnAmount() {
        assertEquals("1½ cups", HouseholdAmount.tidy("1 1/2 cups"))
        assertEquals("¼ cup", HouseholdAmount.tidy("0.25 cup"))
        assertEquals("2 large", HouseholdAmount.tidy("2 large"))
        assertEquals("a handful", HouseholdAmount.tidy("a handful"))
    }

    @Test
    fun tidyRewritesWhatOlderBuildsSaved() {
        assertEquals("1½ cups", HouseholdAmount.tidy("1.5 × 1 cup"))
        assertEquals("2 medium", HouseholdAmount.tidy("2 × 1 medium"))
        assertEquals("2 bars", HouseholdAmount.tidy("2 bar"))
        assertEquals("150 g", HouseholdAmount.tidy("150 g"))
        assertEquals("", HouseholdAmount.tidy(""))
    }

    @Test
    fun aLoggedEntryReadsItsAmountTidied() {
        val old = LogItem(
            id = "i",
            day = kotlinx.datetime.LocalDate(2026, 10, 25),
            slot = MealSlot.Breakfast,
            loggedAt = kotlin.time.Instant.fromEpochSeconds(0),
            name = "Oats",
            precision = Precision.Estimate,
            method = LogMethod.Search,
            amountText = "0.25 cup",
        )
        assertEquals("¼ cup", old.amountLabel)
        assertEquals("1½ cups", old.toDraft().copy(amountText = "1.5 × 1 cup").amountLabel)
        assertEquals("", old.copy(amountText = null).amountLabel)
    }

    @Test
    fun aCustomFoodsServingReadsInItsOwnUnit() {
        val bar = CustomFood(id = "c", name = "Oat bar", servingAmount = 1.0, servingUnit = "bar")
        val granola = CustomFood(id = "g", name = "Granola", servingAmount = 30.0, servingUnit = "g")

        assertEquals("2 bars", bar.toDraft(servings = 2.0).amountText)
        assertEquals("½ bar", bar.toDraft(servings = 0.5).amountText)
        assertEquals("45 g", granola.toDraft(servings = 1.5).amountText)
    }

    @Test
    fun displaySpellsTheAppsOwnUnitsAndLeavesCatalogPortionsAlone() {
        assertEquals("150 g", HouseholdAmount.display("150 g"))
        assertEquals("1½ cups", HouseholdAmount.display("1.5 × 1 cup"))

        val stored = HouseholdAmount.of(150.0, "g")
        try {
            AmountCopy.install(AmountWords(grams = "%1${'$'}s г", fluidOunces = "%1${'$'}s жидк. унц."))
            assertEquals("150 г", HouseholdAmount.display(stored))
            assertEquals("8 жидк. унц.", HouseholdAmount.display("8 fl oz"))
            assertEquals("1 cup (240 ml)", HouseholdAmount.display("1 cup (240 ml)"))
            // What is stored does not change with the language.
            assertEquals("150 g", HouseholdAmount.of(150.0, "g"))
        } finally {
            AmountCopy.install(AmountWords())
        }
    }
}
