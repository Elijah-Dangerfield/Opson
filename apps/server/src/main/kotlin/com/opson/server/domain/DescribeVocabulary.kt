package com.opson.server.domain

/**
 * A restaurant chain the words can name. [key] is the name reduced to
 * letters and digits, which is how it is found in a catalog row: USDA writes
 * "CHICK-FIL-A, chicken sandwich" and "Big Mac (McDonalds)", Branded Foods
 * puts it in the brand. [search] is what goes to the catalog with the food.
 */
data class Brand(val name: String, val key: String, val search: String, internal val pattern: Regex)

/**
 * The chains people say most, with the spellings speech recognition writes
 * for them ("chick fil a", "chickfila", "chic filet", "mcdonalds",
 * "mickey d's"). A brand is read from "from X", "at X", "X's" or a sentence
 * that starts with it, and applies to the whole sentence.
 */
object Brands {
    private fun brand(name: String, key: String, search: String, pattern: String) =
        Brand(name, key, search, Regex("""\b(?:$pattern)\b"""))

    val ChickFilA = brand("Chick-fil-A", "chickfila", "chick-fil-a", """chick\W*fil\W*a|chickfila|chic\W*fil(?:et|ay|a)?(?:\W*a)?|chick\W*fil(?:et|ay)|chik\W*fil\W*a|chick\W*a\W*fil\W*a""")
    val McDonalds = brand("McDonald's", "mcdonald", "mcdonald's", """mc\s?donald'?s?|mac\s?donald'?s?|mickey\s?d'?s|mcd'?s""")
    val BurgerKing = brand("Burger King", "burgerking", "burger king", """burger\s?king|bk""")
    val Wendys = brand("Wendy's", "wendy", "wendy's", """wendy'?s""")
    val TacoBell = brand("Taco Bell", "tacobell", "taco bell", """taco\s?bell""")
    val Kfc = brand("KFC", "kfc", "kfc", """kfc|k\s?f\s?c|kentucky\s+fried\s+chicken""")
    val Starbucks = brand("Starbucks", "starbucks", "starbucks", """star\s?bucks?""")
    val Dunkin = brand("Dunkin'", "dunkin", "dunkin", """dunkin'?(?:\s+donuts)?|dunkin\s+doughnuts""")
    val Chipotle = brand("Chipotle", "chipotle", "chipotle", """chipotle'?s?|chipoltle|chipotlay""")
    val Subway = brand("Subway", "subway", "subway", """subway""")
    val Dominos = brand("Domino's", "domino", "domino's", """domino'?s""")
    val PizzaHut = brand("Pizza Hut", "pizzahut", "pizza hut", """pizza\s?hut""")
    val Popeyes = brand("Popeyes", "popeyes", "popeyes", """popeye'?s""")
    val Panera = brand("Panera", "panera", "panera", """panera(?:\s+bread)?""")
    val FiveGuys = brand("Five Guys", "fiveguys", "five guys", """five\s+guys|5\s+guys""")
    val InNOut = brand("In-N-Out", "innout", "in-n-out", """in\W*n\W*out|in\s+and\s+out""")
    val Arbys = brand("Arby's", "arby", "arby's", """arby'?s""")
    val Sonic = brand("Sonic", "sonic", "sonic", """sonic""")
    val DairyQueen = brand("Dairy Queen", "dairyqueen", "dairy queen", """dairy\s+queen|dq""")
    val PandaExpress = brand("Panda Express", "pandaexpress", "panda express", """panda\s+express""")
    val JackInTheBox = brand("Jack in the Box", "jackinthebox", "jack in the box", """jack\s+in\s+the\s+box""")
    val PapaJohns = brand("Papa John's", "papajohn", "papa john's", """papa\s+john'?s""")
    val LittleCaesars = brand("Little Caesars", "littlecaesar", "little caesars", """little\s+caesar'?s|little\s+cesar'?s""")
    val ShakeShack = brand("Shake Shack", "shakeshack", "shake shack", """shake\s+shack""")

    val all: List<Brand> = listOf(
        ChickFilA, McDonalds, BurgerKing, Wendys, TacoBell, Kfc, Starbucks, Dunkin, Chipotle, Subway, Dominos,
        PizzaHut, Popeyes, Panera, FiveGuys, InNOut, Arbys, Sonic, DairyQueen, PandaExpress, JackInTheBox,
        PapaJohns, LittleCaesars, ShakeShack,
    )

    /**
     * The brand a sentence names and the sentence without it. "Chipotle" and
     * "Sonic" are also a pepper and a word, so those two count only after
     * "from" or "at", or before a word that only a restaurant meal has.
     */
    fun extract(sentence: String): Pair<Brand?, String> {
        for (brand in all) {
            val match = brand.pattern.find(sentence) ?: continue
            val before = sentence.substring(0, match.range.first)
            val after = sentence.substring(match.range.last + 1)
            val preposition = Regex("""\b(?:from|at|in|of|via)\s+(?:the\s+)?$""").find(before)
            val leading = before.isBlank() || before.trimEnd().endsWith(",")
            val possessive = after.startsWith("'s") || after.startsWith("s ")
            val ambiguous = brand == Chipotle || brand == Sonic
            if (ambiguous && preposition == null && !(leading && after.trim().split(' ').any { it in RESTAURANT_WORDS })) continue
            if (preposition == null && !leading && !possessive && brand.key.length < MIN_UNANCHORED) continue
            val head = preposition?.let { before.substring(0, it.range.first) } ?: before
            val tail = after.removePrefix("'s").removePrefix("s ").let { if (possessive) " $it" else it }
            return brand to "$head $tail".replace(Regex("\\s+"), " ").trim()
        }
        return null to sentence
    }

    /** "bk" and "dq" anywhere but a preposition or the start would match too much. */
    private const val MIN_UNANCHORED = 4

    private val RESTAURANT_WORDS = setOf("bowl", "burrito", "burritos", "tacos", "quesadilla", "slush", "burger", "tots")
}

/**
 * Word lists the parser leans on, kept here so the rules read in
 * [DescribeParser] and the data reads as data.
 */
object DescribeVocabulary {

    /**
     * Misrecognitions and spellings people use for foods, fixed before
     * anything else reads the text. Whole words only; the catalog's own typo
     * search covers the long tail.
     */
    private val MISHEARD = listOf(
        "nuggest" to "nuggets", "nuggetts" to "nuggets", "nugget's" to "nuggets", "nuggies" to "nuggets",
        "big mack" to "big mac", "bigmac" to "big mac", "big macs" to "big mac",
        "quarter pounder" to "quarter pounder", "whopper's" to "whopper",
        "frys" to "fries", "fry's" to "fries", "french frys" to "french fries", "frenchfries" to "french fries",
        "expresso" to "espresso", "cappucino" to "cappuccino", "capuccino" to "cappuccino", "latte's" to "lattes",
        "lattay" to "latte", "lotte" to "latte", "frappucino" to "frappuccino",
        "sandwhich" to "sandwich", "sandwhiches" to "sandwiches", "samwich" to "sandwich",
        "yoghurt" to "yogurt", "yogourt" to "yogurt", "brocoli" to "broccoli", "brocolli" to "broccoli",
        "tomatoe" to "tomato", "potatoe" to "potato", "spagetti" to "spaghetti", "spaghetti's" to "spaghetti",
        "cheeseburger's" to "cheeseburgers", "hamburger's" to "hamburgers", "donut's" to "donuts",
        "oj" to "orange juice", "o j" to "orange juice", "pb" to "peanut butter",
        "pb and j" to "peanut butter and jelly sandwich", "pbj" to "peanut butter and jelly sandwich",
        "pb and js" to "peanut butter and jelly sandwich",
        "mac n cheese" to "macaroni and cheese", "mac and cheese" to "macaroni and cheese",
        "mac 'n cheese" to "macaroni and cheese", "mac n' cheese" to "macaroni and cheese",
        "bbq" to "barbecue", "avo" to "avocado", "guac" to "guacamole", "parm" to "parmesan",
        "oatmeal's" to "oatmeal", "oat meal" to "oatmeal", "pea nut" to "peanut", "ice-cream" to "ice cream",
        "hash brown" to "hash browns", "hashbrowns" to "hash browns", "coca cola" to "cola", "coke" to "cola",
        "pepsi" to "cola", "soda pop" to "soda",
    )

    private val MISHEARD_PATTERN: List<Pair<Regex, String>> =
        MISHEARD.sortedByDescending { it.first.length }.map { (from, to) -> Regex("""(?<![\p{L}'])${Regex.escape(from)}(?![\p{L}'])""") to to }

    fun fixMisheard(text: String): String = MISHEARD_PATTERN.fold(text) { s, (pattern, to) -> pattern.replace(s, to) }

    /**
     * Foods whose name has "and" or "with" in it. Joined with underscores
     * before the split so "mac and cheese" stays one food; the parser puts
     * the spaces back.
     */
    val COMPOUNDS = listOf(
        "macaroni and cheese", "peanut butter and jelly", "fish and chips", "half and half", "sweet and sour",
        "salt and vinegar", "cookies and cream", "biscuits and gravy", "rice and beans", "beans and rice",
        "chips and salsa", "pork and beans", "spaghetti and meatballs", "shrimp and grits", "cheese and crackers",
        "sour cream and onion", "ham and cheese", "bacon egg and cheese", "sausage egg and cheese", "egg and cheese",
    )

    private val COMPOUND_PATTERNS = COMPOUNDS.map { Regex("""\b${Regex.escape(it)}\b""") to it.replace(' ', '_') }

    fun protectCompounds(text: String): String = COMPOUND_PATTERNS.fold(text) { s, (pattern, joined) -> pattern.replace(s, joined) }

    /** "No wait", "actually", "I mean": what was said before it is taken back. */
    val CORRECTION = Regex("""\b(?:no\s+wait|wait\s+no|no\s+sorry|sorry|actually|i\s+mean|scratch\s+that|make\s+that|or\s+rather|i\s+meant|correction|not\s+that)\b""")

    /**
     * Words around the food that are about the person or the occasion, not
     * the food. Longest first, whole words, anywhere in the phrase.
     */
    private val FILLER = listOf(
        "i also had", "i just had", "i've just had", "i have had", "i've had", "ive had", "i had", "i ate", "i've eaten",
        "i drank", "i got", "i grabbed", "i made", "i finished", "i snacked on", "we had", "we ate", "we got",
        "i'm having", "im having", "i am having", "i'll have", "having", "had", "ate", "eaten", "drank", "grabbed",
        "for breakfast", "for lunch", "for dinner", "for a snack", "for snack", "for supper", "for brunch",
        "as a snack", "this morning", "this afternoon", "this evening", "last night", "tonight", "today",
        "earlier", "just now", "right now", "um", "umm", "uh", "uhh", "er", "hmm", "like", "just", "basically",
        "leftover", "leftovers", "left over", "homemade", "home made", "maybe", "probably", "about", "around",
        "roughly", "approximately", "approx", "ish", "kind of", "sort of", "kinda", "sorta", "too", "as well",
        "please", "okay", "ok", "so", "well", "yeah", "also", "then", "and", "with", "some kind of", "a side of",
        "side of", "on the side", "real quick", "quickly", "really", "pretty", "good", "delicious", "yummy",
    )

    // Underscores hold a compound together ("macaroni_and_cheese"), so the "and" inside one is not filler.
    private val FILLER_PATTERN = Regex("""(?<![\p{L}'_])(?:${FILLER.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})(?![\p{L}'_])""")

    fun stripFiller(text: String): String = FILLER_PATTERN.replace(text, " ").replace(Regex("\\s+"), " ").trim()
}
