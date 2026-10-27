package com.opson.server.domain

/**
 * A portion picked by its label: [label] is matched against the start of a
 * catalog portion's label, case-insensitively ("1 medium or regular slice"),
 * and [count] of it is the amount.
 */
data class ServingSpec(val label: String, val count: Double = 1.0)

/**
 * What a bare, everyday word means when someone says it with nothing else:
 * "toast" is a slice of plain toasted white bread, "eggs" is one egg cooked
 * some ordinary way, "coffee" is a cup of brewed coffee. The catalog alone
 * can't know that: search for "toast" ranks French toast first, because no
 * USDA name is just "Toast".
 *
 * [name] is the catalog food, by its exact USDA name; [serving] the amount
 * "some" or no amount means, and [asAddition] the amount when it was said
 * after "with" ("coffee with milk" is a splash, not a glass). [alternatives]
 * are the variants worth one tap (scrambled, fried, boiled), offered before
 * whatever search would add.
 *
 * Only an exact match on [words] uses an entry, so anything the person said
 * about the food ("french toast", "scrambled eggs", "a Starbucks latte")
 * goes to search and is honoured. `DescribeFixturesTest` checks every name
 * and serving against the committed USDA seed, so a renamed food fails a
 * test rather than quietly falling back.
 */
data class CommonFood(
    val words: Set<String>,
    val name: String,
    val serving: ServingSpec,
    val asAddition: ServingSpec? = null,
    val alternatives: List<String> = emptyList(),
)

object CommonFoods {
    private fun food(
        vararg words: String,
        name: String,
        serving: ServingSpec,
        asAddition: ServingSpec? = null,
        alternatives: List<String> = emptyList(),
    ) = CommonFood(words.toSet(), name, serving, asAddition, alternatives)

    private val SLICE = ServingSpec("1 medium or regular slice")
    private val CUP = ServingSpec("1 cup")
    private val COFFEE_CUP = ServingSpec("1 cup (8 fl oz)")

    val all: List<CommonFood> = listOf(
        // Breakfast
        food(
            "toast", "toasts", "piece of toast", "slice of toast",
            name = "Bread, white, toasted", serving = SLICE,
            alternatives = listOf("Bread, whole wheat, toasted", "Bread, multigrain, toasted", "Bread, sour dough, toasted"),
        ),
        food(
            "bread", name = "Bread, white", serving = SLICE,
            alternatives = listOf("Bread, whole wheat", "Bread, multigrain", "Bread, white, toasted"),
        ),
        food(
            "egg", "eggs", name = "Egg, whole, cooked, NS as to cooking method", serving = ServingSpec("1 egg"),
            alternatives = listOf("Egg omelet or scrambled egg, NS as to fat", "Egg, whole, fried, NS as to fat", "Egg, whole, boiled or poached"),
        ),
        food(
            "bacon", name = "Bacon, NS as to type of meat, cooked", serving = ServingSpec("1 medium slice", 2.0),
            alternatives = listOf("Bacon, NS as to type of meat, reduced sodium, cooked"),
        ),
        food(
            "sausage", "sausages", name = "Sausage, NFS", serving = ServingSpec("1 breakfast size link", 2.0),
            alternatives = listOf("Pork sausage"),
        ),
        food("oatmeal", "oats", "porridge", name = "Oatmeal, NFS", serving = ServingSpec("1 cup, cooked")),
        food(
            "cereal", name = "Cereal, ready-to-eat, NFS", serving = CUP,
            alternatives = listOf("Cereal, O's, NFS", "Cereal, cooked, NFS"),
        ),
        food("pancake", "pancakes", name = "Pancakes, plain", serving = ServingSpec("1 medium pancake", 2.0)),
        food("bagel", "bagels", name = "Bagel", serving = ServingSpec("1 regular")),
        food("croissant", "croissants", name = "Croissant", serving = ServingSpec("1 medium croissant")),
        food("muffin", "muffins", name = "Muffin, NFS", serving = ServingSpec("1 medium")),
        food("donut", "donuts", "doughnut", "doughnuts", name = "Doughnut, NFS", serving = ServingSpec("1 doughnut")),
        food(
            "yogurt", name = "Yogurt, NFS", serving = ServingSpec("1 6 oz container"),
            alternatives = listOf("Yogurt, Greek, NS as to type of milk or flavor"),
        ),
        food("greek yogurt", name = "Yogurt, Greek, NS as to type of milk or flavor", serving = ServingSpec("1 container, NFS")),

        // Spreads, toppings and things added to something else
        food(
            "butter", name = "Butter, NFS", serving = ServingSpec("1 pat"), asAddition = ServingSpec("1 pat"),
        ),
        food(
            "peanut butter", name = "Peanut butter", serving = ServingSpec("1 tablespoon", 2.0),
            asAddition = ServingSpec("1 tablespoon"),
        ),
        food("jam", "jelly", name = "Jam or jelly, NFS", serving = ServingSpec("1 tablespoon")),
        food("honey", name = "Honey", serving = ServingSpec("1 tablespoon"), asAddition = ServingSpec("1 tablespoon")),
        food(
            "sugar", name = "Sugar, white, granulated or lump", serving = ServingSpec("1 teaspoon"),
            asAddition = ServingSpec("1 teaspoon"),
        ),
        food(
            "cream", "creamer", name = "Cream, light", serving = ServingSpec("1 fl oz"),
            asAddition = ServingSpec("1 fl oz"), alternatives = listOf("Cream, half and half"),
        ),
        food("half and half", name = "Cream, half and half", serving = ServingSpec("1 fl oz"), asAddition = ServingSpec("1 fl oz")),
        food(
            "milk", name = "Milk, NFS", serving = CUP, asAddition = ServingSpec("1 fl oz"),
            alternatives = listOf("Milk, whole", "Milk, reduced fat (2%)", "Milk, fat free (skim)"),
        ),
        food("cheese", name = "Cheese, NFS", serving = ServingSpec("1 slice"), asAddition = ServingSpec("1 slice")),
        food(
            "berries", name = "Berries, NFS", serving = CUP, asAddition = ServingSpec("1 cup", 0.5),
            alternatives = listOf("Blueberries, raw", "Strawberries, raw"),
        ),
        food("ketchup", name = "Ketchup", serving = ServingSpec("1 tablespoon"), asAddition = ServingSpec("1 tablespoon")),
        food("mayo", "mayonnaise", name = "Mayonnaise, regular", serving = ServingSpec("1 tablespoon"), asAddition = ServingSpec("1 tablespoon")),
        food("ranch", name = "Ranch dressing", serving = ServingSpec("1 tablespoon", 2.0), asAddition = ServingSpec("1 tablespoon", 2.0)),
        food("dressing", "salad dressing", name = "Salad dressing, NFS, for salads", serving = ServingSpec("1 tablespoon", 2.0), asAddition = ServingSpec("1 tablespoon", 2.0)),
        food("olive oil", "oil", name = "Olive oil", serving = ServingSpec("1 tablespoon"), asAddition = ServingSpec("1 tablespoon")),

        // Fruit and snacks
        food("banana", "bananas", name = "Banana, raw", serving = ServingSpec("1 banana")),
        food("apple", "apples", name = "Apple, raw", serving = ServingSpec("1 medium")),
        food("orange", "oranges", name = "Orange, raw", serving = ServingSpec("1 fruit")),
        food("grapes", name = "Grapes, raw", serving = CUP),
        food("avocado", "avocados", name = "Avocado, raw", serving = ServingSpec("1 fruit", 0.5), asAddition = ServingSpec("1 slice", 2.0)),
        food("almonds", name = "Almonds, NFS", serving = ServingSpec("1 oz")),
        food("chips", "potato chips", "crisps", name = "Potato chips, NFS", serving = ServingSpec("1 small single serving bag")),
        food("cookie", "cookies", name = "Cookie, NFS", serving = ServingSpec("1 medium")),
        food("ice cream", name = "Ice cream, NFS", serving = ServingSpec("1 scoop")),

        // Mains and sides
        food("rice", name = "Rice, cooked, NFS", serving = ServingSpec("1 cup, cooked")),
        food(
            "spaghetti", "pasta with sauce", "spaghetti with sauce", "spaghetti with tomato sauce",
            name = "Pasta with tomato-based sauce, home recipe", serving = ServingSpec("1 cup", 1.5),
            alternatives = listOf("Pasta with tomato-based sauce and meat, home recipe", "Pasta, cooked"),
        ),
        food("macaroni and cheese", "mac and cheese", name = "Macaroni or noodles with cheese", serving = CUP),
        food("chicken salad", name = "Chicken salad spread", serving = ServingSpec("1 cup", 0.5)),
        food(
            "fries", "french fries", name = "Potato, french fries, NFS", serving = ServingSpec("1 cup", 2.0),
            alternatives = listOf("Sweet potato fries, NFS"),
        ),
        food("broccoli", name = "Broccoli, NS as to form, cooked", serving = CUP),
        food("pasta", "noodles", name = "Pasta, cooked", serving = ServingSpec("1 cup, cooked")),
        food(
            "chicken", "chicken breast", name = "Chicken breast, NS as to cooking method, skin not eaten",
            serving = ServingSpec("1 breast"),
            alternatives = listOf(
                "Chicken breast, grilled without sauce, skin not eaten",
                "Chicken breast, baked, broiled, or roasted, skin not eaten, from raw",
            ),
        ),
        food(
            "nuggets", "nugget", "chicken nuggets", name = "Chicken nuggets, NFS", serving = ServingSpec("1 nugget", 6.0),
            alternatives = listOf("Chicken nuggets, from fast food", "Chicken breast, grilled without sauce, skin not eaten"),
        ),
        food("pizza", name = "Pizza, cheese, from restaurant or fast food, NS as to type of crust", serving = ServingSpec("1 piece, NFS")),
        food("burger", "hamburger", name = "Hamburger, NFS", serving = ServingSpec("1 hamburger")),
        food("cheeseburger", name = "Cheeseburger, NFS", serving = ServingSpec("1 cheeseburger")),
        food("hot dog", name = "Hot dog sandwich, NFS, on white bun", serving = ServingSpec("1 sandwich")),
        food("burrito", name = "Burrito, NFS", serving = ServingSpec("1 small/regular")),
        food("burrito bowl", name = "Burrito bowl, NFS", serving = ServingSpec("1 item, any size")),
        food(
            "salad", "side salad", name = "Lettuce, salad with assorted vegetables including tomatoes and/or carrots, no dressing",
            serving = ServingSpec("1 side salad"),
        ),
        food("potato", name = "Potato, baked, NFS", serving = ServingSpec("1 medium")),
        food("steak", name = "Steak, NS as to type of meat, NS as to fat eaten", serving = ServingSpec("1 regular")),
        food("sushi", name = "Sushi, NFS", serving = ServingSpec("1 piece", 6.0)),

        // Drinks
        food(
            "coffee", "cup of coffee", "black coffee", name = "Coffee, brewed", serving = COFFEE_CUP,
            alternatives = listOf("Coffee, Latte", "Coffee, brewed, decaffeinated", "Coffee, Cappuccino"),
        ),
        food(
            "latte", "lattes", name = "Coffee, Latte", serving = ServingSpec("1 medium"),
            alternatives = listOf("Coffee, Latte, nonfat", "Coffee, Latte, with non-dairy milk", "Coffee, Cappuccino"),
        ),
        food(
            "oat milk latte", "oatmilk latte", "almond milk latte", "soy latte", "soy milk latte",
            name = "Coffee, Latte, with non-dairy milk", serving = ServingSpec("1 medium"),
            alternatives = listOf("Coffee, Latte", "Coffee, Cappuccino, with non-dairy milk"),
        ),
        food("cappuccino", name = "Coffee, Cappuccino", serving = ServingSpec("1 small")),
        food("espresso", name = "Coffee, espresso", serving = ServingSpec("1 espresso cup")),
        food("tea", name = "Tea, hot, leaf, black", serving = CUP, alternatives = listOf("Tea, hot, leaf, green", "Tea, hot, herbal")),
        food(
            "orange juice", name = "Orange juice, chilled, includes from concentrate", serving = CUP,
            alternatives = listOf("Orange juice, 100%, freshly squeezed"),
        ),
        food("apple juice", name = "Apple juice, canned or bottled, unsweetened, without added ascorbic acid", serving = CUP),
        food("water", name = "Water, tap", serving = CUP, alternatives = listOf("Water, bottled, plain")),
        food(
            "cola", "soda", "pop", name = "Soft drink, cola", serving = ServingSpec("1 can (12 fl oz)"),
            alternatives = listOf("Soft drink, cola, diet"),
        ),
        food("diet cola", "diet soda", name = "Soft drink, cola, diet", serving = ServingSpec("1 can (12 fl oz)")),
        food("beer", "beers", name = "Beer", serving = ServingSpec("1 can or bottle (12 fl oz)")),
        food("wine", "red wine", "glass of wine", name = "Wine, red", serving = ServingSpec("1 glass"), alternatives = listOf("Wine, white")),
        food("white wine", name = "Wine, white", serving = ServingSpec("1 glass")),
    )

    private val byWord: Map<String, CommonFood> = all.flatMap { food -> food.words.map { it to food } }.toMap()

    /** The entry for exactly these words, or null. */
    fun lookup(foodText: String): CommonFood? = byWord[foodText.trim().lowercase()]

    /** Every word an entry answers to: what a run-on sentence ("eggs bacon toast") is split by. */
    val words: Set<String> get() = byWord.keys
}
