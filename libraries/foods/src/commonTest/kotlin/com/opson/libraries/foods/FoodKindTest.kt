package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals

class FoodKindTest {

    @Test
    fun theDesignsFoodsGetTheirPictures() {
        assertEquals(FoodKind.Grain, FoodKind.of("Garlic fried rice (sinangag)"))
        assertEquals(FoodKind.Grain, FoodKind.of("Jasmine rice, cooked"))
        assertEquals(FoodKind.Dessert, FoodKind.of("Rice pudding"))
        assertEquals(FoodKind.Fruit, FoodKind.of("Mango sticky rice"))
        assertEquals(FoodKind.Salad, FoodKind.of("Side salad"))
        assertEquals(FoodKind.Fruit, FoodKind.of("Banana"))
        assertEquals(FoodKind.Dairy, FoodKind.of("Greek yogurt, plain"))
        assertEquals(FoodKind.Plate, FoodKind.of("Arroz con pollo"))
        assertEquals(FoodKind.Plate, FoodKind.of("Chicken kabsa"))
    }

    @Test
    fun aShortWordInsideAnotherIsNotAMatch() {
        assertEquals(FoodKind.Salad, FoodKind.of("Grilled eggplant"))
        assertEquals(FoodKind.Fruit, FoodKind.of("Watermelon"))
        assertEquals(FoodKind.Drink, FoodKind.of("Sparkling water"))
        assertEquals(FoodKind.Egg, FoodKind.of("Eggs, scrambled"))
        assertEquals(FoodKind.Dessert, FoodKind.of("Vanilla ice cream"))
        assertEquals(FoodKind.Plate, FoodKind.of("Pierogi"), "pie is too short to match by prefix")
    }
}
