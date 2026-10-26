package com.opson.libraries.diary

/**
 * What a food looks like on its tile: a rice bowl, a leaf, a piece of fruit.
 * The catalog has no category per food yet, so [of] reads the name. It only
 * picks a picture; nothing is counted or filtered by it.
 */
enum class FoodKind {
    Grain,
    Salad,
    Fruit,
    Dessert,
    Fish,
    Egg,
    Bread,
    Soup,
    Dairy,
    Drink,

    /** A cooked dish or anything the name does not place. */
    Plate,
    ;

    companion object {
        /**
         * The kind whose words come first in [name], checked in [ORDER] so a more
         * telling word wins: "Rice pudding" is a dessert, "Mango sticky rice" is
         * fruit, "Chicken kabsa" is a plate.
         */
        fun of(name: String): FoodKind {
            val lower = name.lowercase()
            if ("ice cream" in lower) return Dessert
            val words = lower.split(' ', ',', '(', ')', '-', '/').filter(String::isNotEmpty)
            return ORDER.firstOrNull { kind -> words.any { word -> KEYWORDS.getValue(kind).any { word.matches(it) } } } ?: Plate
        }

        /** The word itself or its plural; long words also by prefix ("chocolates", "omelettes", "broccolini"). */
        private fun String.matches(keyword: String): Boolean =
            this == keyword || this == keyword + "s" || this == keyword + "es" ||
                (keyword.length >= PREFIX_MIN && startsWith(keyword))

        private const val PREFIX_MIN = 6

        private val ORDER = listOf(Dessert, Soup, Salad, Drink, Egg, Fish, Fruit, Grain, Bread, Dairy)

        private val KEYWORDS: Map<FoodKind, List<String>> = mapOf(
            Dessert to listOf("pudding", "cake", "cookie", "brownie", "pie", "chocolate", "candy", "flan", "donut", "doughnut", "muffin"),
            Soup to listOf("soup", "stew", "broth", "chili", "ramen", "pho", "sinigang"),
            Salad to listOf("salad", "lettuce", "spinach", "kale", "greens", "broccoli", "vegetable", "veggie", "cabbage", "eggplant"),
            Drink to listOf("coffee", "tea", "juice", "soda", "latte", "smoothie", "water", "cola", "beer", "wine"),
            Egg to listOf("egg", "omelet", "omelette"),
            Fish to listOf("fish", "salmon", "tuna", "cod", "shrimp", "prawn", "tilapia", "sardine", "bangus"),
            Grain to listOf("rice", "sinangag", "oat", "oatmeal", "quinoa", "porridge", "congee", "noodle", "pasta", "spaghetti", "cereal", "granola"),
            Bread to listOf("bread", "toast", "bagel", "tortilla", "roti", "naan", "croissant", "pandesal", "bun"),
            Dairy to listOf("yogurt", "yoghurt", "milk", "cheese", "kefir", "cottage"),
            Fruit to listOf("banana", "apple", "mango", "orange", "berry", "berries", "watermelon", "grape", "pear", "peach", "melon", "pineapple", "kiwi", "fruit"),
        )
    }
}
