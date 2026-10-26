package com.opson.features.logger.impl

import com.opson.libraries.diary.Hunger
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [BasketStore]: toggling, replacing by food, and emptying on commit or a change of user. */
class BasketStoreTest : CoroutineTest() {
    private val banana = LoggerFixtures.pickedBasket().first()
    private val yogurt = LoggerFixtures.pickedBasket().last()

    @Test
    fun toggleAddsThenRemoves() {
        val store = BasketStore()

        store.toggle(banana)
        store.toggle(yogurt)
        assertEquals(listOf("f:banana", "f:yogurt"), store.basket.value.map { it.key })

        store.toggle(banana)
        assertEquals(listOf("f:yogurt"), store.basket.value.map { it.key })
    }

    @Test
    fun upsertReplacesTheSameFoodInPlace() {
        val store = BasketStore()
        store.upsert(banana)
        store.upsert(yogurt)

        store.upsert(banana.copy(hunger = Hunger.Peckish))

        assertEquals(listOf("f:banana", "f:yogurt"), store.basket.value.map { it.key })
        assertEquals(Hunger.Peckish, store.basket.value.first().hunger)
    }

    @Test
    fun clearEmptiesIt() {
        val store = BasketStore()
        store.upsert(banana)

        store.clear()

        assertTrue(store.basket.value.isEmpty())
    }

    @Test
    fun aChangeOfUserEmptiesIt() = runUnitTest {
        val store = BasketStore()
        store.upsert(banana)

        store.clear(previousUserId = "user-1")

        assertTrue(store.basket.value.isEmpty())
    }
}
