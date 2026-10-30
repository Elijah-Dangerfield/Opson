package com.opson.libraries.foods

/**
 * Matching what someone typed against a food on the phone, the way the server's search reads a
 * query: every word typed starts a word of the name or brand, singular or plural, so `chicken
 * breast` finds "Chicken, breast, raw" and `strawberry` finds "Strawberries". Offline search and
 * the person's own foods in results both go through it, so they agree with each other.
 */
object FoodQuery {

    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")

    /** Lowercase, trimmed, single spaces: one key for "Rice " and "rice". */
    fun normalize(query: String): String = query.trim().lowercase().replace(SPACES, " ")

    fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

    /**
     * Whether [query] finds a food named [name] by [brand]. Three letters or more also match
     * inside a word, as the person's own foods always did ("brownie" in "ProteinBrownie"), which
     * also covers symbols and scripts without spaces between words.
     */
    fun matches(query: String, name: String, brand: String? = null): Boolean {
        val typed = normalize(query)
        if (typed.isEmpty()) return false
        val inside = typed.length >= MIN_INSIDE &&
            (name.contains(typed, ignoreCase = true) || brand?.contains(typed, ignoreCase = true) == true)
        if (inside) return true
        val queryWords = words(typed)
        if (queryWords.isEmpty()) return false
        val foodWords = words(name) + brand?.let(::words).orEmpty()
        return queryWords.all { word -> variants(word).any { variant -> foodWords.any { it.startsWith(variant) } } }
    }

    /**
     * The server's `SearchTerms.variantsOf`: a word plus its English singular or plural. Change
     * the two together.
     */
    @Suppress("CyclomaticComplexMethod")
    fun variants(word: String): List<String> {
        val variants = linkedSetOf(word)
        val letters = word.all { it.isLetter() }
        when {
            word.length > 4 && word.endsWith("ies") -> variants += word.dropLast(3) + "y"
            word.length > 4 && word.endsWith("oes") -> variants += word.dropLast(2)
            word.length > 4 && SIBILANT_PLURAL.any { word.endsWith(it) } -> {
                variants += word.dropLast(2)
                variants += word.dropLast(1)
            }
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> variants += word.dropLast(1)
            !letters || word.length <= 2 -> Unit
            word.endsWith("ss") || SIBILANT.any { word.endsWith(it) } -> variants += word + "es"
            word.length > 3 && word.endsWith("y") && word[word.length - 2] !in VOWELS -> variants += word.dropLast(1) + "ies"
            word.length > 3 && word.endsWith("o") && word[word.length - 2] !in VOWELS -> {
                variants += word + "es"
                variants += word + "s"
            }
            !word.endsWith("s") -> variants += word + "s"
        }
        return variants.toList()
    }

    private const val VOWELS = "aeiou"

    /** Shorter than this, what was typed has to start a word: "a" is in nearly every name. */
    private const val MIN_INSIDE = 3
    private val SIBILANT = listOf("ch", "sh", "x", "z")
    private val SIBILANT_PLURAL = listOf("ches", "shes", "xes", "zes", "sses")
}
