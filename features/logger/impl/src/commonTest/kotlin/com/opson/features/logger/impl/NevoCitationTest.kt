package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals

/** RIVM allows NEVO's foods only with its citation, so a NEVO food names it in search and on the amount sheet. */
class NevoCitationTest {

    @Test
    fun aNevoRowCarriesTheNevoBadge() {
        assertEquals(SourceBadge.Nevo, SourceBadge.of(FoodSource.Nevo))
    }

    @Test
    fun theAmountSheetCitesNevo() {
        val state = AmountStateBuilder.build(
            AmountFood.Catalog(LoggerFixtures.arroz.copy(source = FoodSource.Nevo)),
            AmountChoice(slot = MealSlot.Dinner),
            LoggerFixtures.amountInputs(policy(PresetId.Full)),
            FoodRef.Catalog("arroz"),
            PickSource.Search,
        )

        assertEquals(FoodOrigin.Nevo, state.source?.source)
    }
}
