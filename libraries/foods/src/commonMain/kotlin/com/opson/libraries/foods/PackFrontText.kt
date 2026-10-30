package com.opson.libraries.foods

import kotlin.math.abs

/**
 * A first guess at a product's name from the OCR of the front of its pack: the biggest printed
 * line, joined with a second line of nearly the same size just below it ("OATS & HONEY" over
 * "GRANOLA BAR"). Only ever a guess, so the form marks it for a look. Brands are usually a logo
 * that OCR reads badly, so this does not try.
 *
 * Lines that are clearly not a name are skipped: mostly digits, a weight or a volume ("NET WT
 * 12 OZ (340g)"), claims a pack prints large ("NEW!", "GLUTEN FREE"), the words of a nutrition
 * panel (a label photographed as the front), and anything shorter than three letters.
 */
object PackFrontText {

    fun guessName(lines: List<LabelTextLine>): String? {
        val candidates = lines.filter { it.height > 0.0 && isNameLike(it.text) }
        val biggest = candidates.maxByOrNull { it.height } ?: return null
        val below = candidates
            .filter { it !== biggest && it.top > biggest.top && it.top - (biggest.top + biggest.height) < biggest.height }
            .filter { abs(it.height - biggest.height) <= biggest.height * SAME_SIZE }
            .minByOrNull { it.top }
        val raw = listOfNotNull(biggest.text, below?.text).joinToString(" ") { it.trim() }
        return tidy(raw).takeIf { it.length >= MIN_LETTERS }?.take(MAX_LENGTH)
    }

    private fun isNameLike(text: String): Boolean {
        val trimmed = text.trim()
        val letters = trimmed.count { it.isLetter() }
        if (letters < MIN_LETTERS) return false
        if (trimmed.count { it.isDigit() } > letters) return false
        val lower = trimmed.lowercase()
        if (AMOUNT.containsMatchIn(lower)) return false
        if (PANEL_WORDS.any { it in lower }) return false
        return CLAIMS.none { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
    }

    /** All caps, as packs print names, becomes Title Case; anything already mixed case stays as printed. */
    private fun tidy(raw: String): String {
        val text = raw.replace(SPACES, " ").trim().trim('*', '•', '-', '|').trim()
        val hasLower = text.any { it.isLowerCase() }
        if (hasLower) return text
        return text.lowercase().split(' ').joinToString(" ") { word ->
            if (word in SMALL_WORDS) word else word.replaceFirstChar { it.uppercase() }
        }.replaceFirstChar { it.uppercase() }
    }

    private const val MIN_LETTERS = 3
    private const val MAX_LENGTH = 120

    /** How close in height a second line must be to count as the same name, as a fraction. */
    private const val SAME_SIZE = 0.2

    private val SPACES = Regex("\\s+")
    private val AMOUNT = Regex("\\b(net\\s*wt|net\\s*weight|\\d+\\s*(g|kg|mg|mcg|µg|oz|lb|lbs|ml|l|fl\\s*oz|kj|kcal|cal)\\b|e\\s*\\d+\\s*(g|ml))")
    private val CLAIMS = listOf(
        "new", "new!", "gluten free", "non-gmo", "non gmo", "organic", "usda organic", "vegan",
        "high protein", "no added sugar", "family size", "value pack", "limited edition",
    )
    private val PANEL_WORDS = listOf(
        "nutrition", "ingredients", "serving", "calories", "per 100", "daily value", "nährwert", "valeur nutritive",
    )
    private val SMALL_WORDS = setOf("and", "or", "of", "with", "in", "the", "a", "&", "de", "et", "und")
}
