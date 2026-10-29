package com.opson.libraries.foods

import com.opson.libraries.foods.LabelNutrient.AddedSugars
import com.opson.libraries.foods.LabelNutrient.Calcium
import com.opson.libraries.foods.LabelNutrient.Carbs
import com.opson.libraries.foods.LabelNutrient.Cholesterol
import com.opson.libraries.foods.LabelNutrient.Choline
import com.opson.libraries.foods.LabelNutrient.Copper
import com.opson.libraries.foods.LabelNutrient.Energy
import com.opson.libraries.foods.LabelNutrient.Fat
import com.opson.libraries.foods.LabelNutrient.Fiber
import com.opson.libraries.foods.LabelNutrient.Folate
import com.opson.libraries.foods.LabelNutrient.Iron
import com.opson.libraries.foods.LabelNutrient.Magnesium
import com.opson.libraries.foods.LabelNutrient.Manganese
import com.opson.libraries.foods.LabelNutrient.Niacin
import com.opson.libraries.foods.LabelNutrient.Phosphorus
import com.opson.libraries.foods.LabelNutrient.Potassium
import com.opson.libraries.foods.LabelNutrient.Protein
import com.opson.libraries.foods.LabelNutrient.Riboflavin
import com.opson.libraries.foods.LabelNutrient.SaturatedFat
import com.opson.libraries.foods.LabelNutrient.Selenium
import com.opson.libraries.foods.LabelNutrient.Sodium
import com.opson.libraries.foods.LabelNutrient.SugarAlcohols
import com.opson.libraries.foods.LabelNutrient.Sugars
import com.opson.libraries.foods.LabelNutrient.Thiamin
import com.opson.libraries.foods.LabelNutrient.TransFat
import com.opson.libraries.foods.LabelNutrient.VitaminA
import com.opson.libraries.foods.LabelNutrient.VitaminB12
import com.opson.libraries.foods.LabelNutrient.VitaminB6
import com.opson.libraries.foods.LabelNutrient.VitaminC
import com.opson.libraries.foods.LabelNutrient.VitaminD
import com.opson.libraries.foods.LabelNutrient.VitaminE
import com.opson.libraries.foods.LabelNutrient.VitaminK
import com.opson.libraries.foods.LabelNutrient.Zinc
import com.opson.libraries.foods.LabelRow.Salt
import com.opson.libraries.foods.LabelRow.Skip

internal enum class LabelLanguage { English, French, German, Italian, Dutch, Spanish }

/** What a row label on a panel names. */
internal sealed interface LabelRow {
    data class Of(val nutrient: LabelNutrient) : LabelRow

    /** Salt is read in grams and becomes sodium, as the server does it. */
    data object Salt : LabelRow

    /**
     * A row the form has no field for. It still has to be matched, or its
     * numbers would run on into the row before it ("saturates 1.2g
     * mono-unsaturates 3g").
     */
    data object Skip : LabelRow
}

internal data class LabelPhrase(val text: String, val row: LabelRow, val language: LabelLanguage)

internal data class PhraseMatch(val start: Int, val end: Int, val phrase: LabelPhrase)

/**
 * The words a nutrition panel is printed in: row labels per language, the
 * anchors that say a panel is there, and which words mark a US or an EU
 * panel. Every phrase is stored [fold]ed, so OCR that drops an accent
 * ("Proteines") matches as well as OCR that keeps it.
 */
internal object LabelVocabulary {

    // First: every table below is folded as it is built.
    private val FOLDS: Map<Char, String> = buildMap {
        "àáâäãå".forEach { put(it, "a") }
        "èéêë".forEach { put(it, "e") }
        "ìíîï".forEach { put(it, "i") }
        "òóôöõ".forEach { put(it, "o") }
        "ùúûü".forEach { put(it, "u") }
        put('ç', "c"); put('ñ', "n"); put('ß', "ss"); put('œ', "oe")
        put('µ', "u"); put('μ', "u")
        put('’', "'"); put('≤', "<"); put('＜', "<")
    }

    private fun of(nutrient: LabelNutrient) = LabelRow.Of(nutrient)

    private val ENGLISH: Map<String, LabelRow> = mapOf(
        "energy" to of(Energy), "energy value" to of(Energy), "calories" to of(Energy), "calorie" to of(Energy),
        "calories from fat" to Skip, "cal from fat" to Skip, "fat cal" to Skip,
        "protein" to of(Protein), "proteins" to of(Protein),
        "total carbohydrate" to of(Carbs), "total carbohydrates" to of(Carbs), "total carb" to of(Carbs),
        "total carbs" to of(Carbs), "carbohydrate" to of(Carbs), "carbohydrates" to of(Carbs), "carbs" to of(Carbs),
        "total fat" to of(Fat), "fat" to of(Fat), "fats" to of(Fat),
        "saturated fat" to of(SaturatedFat), "saturated" to of(SaturatedFat), "saturates" to of(SaturatedFat),
        "of which saturates" to of(SaturatedFat), "sat fat" to of(SaturatedFat), "sat. fat" to of(SaturatedFat),
        "trans fat" to of(TransFat), "trans" to of(TransFat),
        "monounsaturated fat" to Skip, "polyunsaturated fat" to Skip, "monounsaturates" to Skip,
        "mono-unsaturates" to Skip, "of which mono-unsaturates" to Skip, "polyunsaturates" to Skip,
        "of which polyunsaturates" to Skip, "omega-3" to Skip, "omega 3" to Skip,
        "cholesterol" to of(Cholesterol),
        "sodium" to of(Sodium),
        "salt" to Salt,
        "dietary fiber" to of(Fiber), "dietary fibre" to of(Fiber), "total fiber" to of(Fiber),
        "fiber" to of(Fiber), "fibre" to of(Fiber),
        "soluble fiber" to Skip, "insoluble fiber" to Skip,
        "total sugars" to of(Sugars), "total sugar" to of(Sugars), "sugars" to of(Sugars), "sugar" to of(Sugars),
        "of which sugars" to of(Sugars),
        "added sugars" to of(AddedSugars), "added sugar" to of(AddedSugars),
        "sugar alcohols" to of(SugarAlcohols), "sugar alcohol" to of(SugarAlcohols), "polyols" to of(SugarAlcohols),
        "of which polyols" to of(SugarAlcohols),
        "starch" to Skip, "of which starch" to Skip, "alcohol" to Skip,
        "potassium" to of(Potassium), "calcium" to of(Calcium), "iron" to of(Iron), "magnesium" to of(Magnesium),
        "phosphorus" to of(Phosphorus), "zinc" to of(Zinc), "copper" to of(Copper), "manganese" to of(Manganese),
        "selenium" to of(Selenium),
        "vitamin a" to of(VitaminA), "vitamin c" to of(VitaminC), "vitamin d" to of(VitaminD),
        "vitamin e" to of(VitaminE), "vitamin k" to of(VitaminK),
        "thiamin" to of(Thiamin), "thiamine" to of(Thiamin), "vitamin b1" to of(Thiamin),
        "riboflavin" to of(Riboflavin), "vitamin b2" to of(Riboflavin),
        "niacin" to of(Niacin), "vitamin b3" to of(Niacin), "vitamin b6" to of(VitaminB6),
        "folate" to of(Folate), "folic acid" to of(Folate), "vitamin b12" to of(VitaminB12), "choline" to of(Choline),
        "% daily value" to Skip, "daily value" to Skip, "%dv" to Skip, "% dv" to Skip,
        "%ri" to Skip, "% ri" to Skip, "reference intake" to Skip,
    )

    private val FRENCH: Map<String, LabelRow> = mapOf(
        "énergie" to of(Energy), "valeur énergétique" to of(Energy),
        "matières grasses" to of(Fat), "lipides" to of(Fat), "graisses" to of(Fat),
        "dont acides gras saturés" to of(SaturatedFat), "acides gras saturés" to of(SaturatedFat),
        "dont saturés" to of(SaturatedFat), "saturés" to of(SaturatedFat),
        "acides gras trans" to of(TransFat),
        "dont acides gras mono-insaturés" to Skip, "acides gras mono-insaturés" to Skip,
        "dont acides gras polyinsaturés" to Skip, "acides gras polyinsaturés" to Skip,
        "glucides" to of(Carbs),
        "dont sucres" to of(Sugars), "sucres" to of(Sugars),
        "dont polyols" to of(SugarAlcohols),
        "dont amidon" to Skip, "amidon" to Skip,
        "fibres alimentaires" to of(Fiber), "fibres" to of(Fiber),
        "protéines" to of(Protein),
        "sel" to Salt,
        "cholestérol" to of(Cholesterol), "fer" to of(Iron),
        "% valeur quotidienne" to Skip, "valeur quotidienne" to Skip, "% vq" to Skip,
    )

    private val GERMAN: Map<String, LabelRow> = mapOf(
        "brennwert" to of(Energy), "energie" to of(Energy),
        "fett" to of(Fat),
        "davon gesättigte fettsäuren" to of(SaturatedFat), "gesättigte fettsäuren" to of(SaturatedFat),
        "davon gesättigte" to of(SaturatedFat),
        "davon einfach ungesättigte fettsäuren" to Skip, "einfach ungesättigte fettsäuren" to Skip,
        "davon mehrfach ungesättigte fettsäuren" to Skip, "mehrfach ungesättigte fettsäuren" to Skip,
        "kohlenhydrate" to of(Carbs),
        "davon zucker" to of(Sugars), "zucker" to of(Sugars),
        "davon mehrwertige alkohole" to of(SugarAlcohols), "mehrwertige alkohole" to of(SugarAlcohols),
        "davon stärke" to Skip, "stärke" to Skip,
        "ballaststoffe" to of(Fiber),
        "eiweiß" to of(Protein),
        "salz" to Salt,
        "natrium" to of(Sodium), "kalium" to of(Potassium), "eisen" to of(Iron),
    )

    private val ITALIAN: Map<String, LabelRow> = mapOf(
        "energia" to of(Energy), "valore energetico" to of(Energy),
        "grassi" to of(Fat),
        "di cui acidi grassi saturi" to of(SaturatedFat), "acidi grassi saturi" to of(SaturatedFat),
        "di cui saturi" to of(SaturatedFat),
        "carboidrati" to of(Carbs),
        "di cui zuccheri" to of(Sugars), "zuccheri" to of(Sugars),
        "fibra" to of(Fiber), "fibre alimentari" to of(Fiber),
        "proteine" to of(Protein),
        "sale" to Salt,
        "sodio" to of(Sodium),
    )

    private val DUTCH: Map<String, LabelRow> = mapOf(
        "vetten" to of(Fat), "vet" to of(Fat),
        "waarvan verzadigde vetzuren" to of(SaturatedFat), "verzadigde vetzuren" to of(SaturatedFat),
        "waarvan verzadigd" to of(SaturatedFat),
        "koolhydraten" to of(Carbs),
        "waarvan suikers" to of(Sugars), "suikers" to of(Sugars),
        "voedingsvezel" to of(Fiber), "vezels" to of(Fiber),
        "eiwitten" to of(Protein), "eiwit" to of(Protein),
        "zout" to Salt,
    )

    private val SPANISH: Map<String, LabelRow> = mapOf(
        "valor energético" to of(Energy),
        "grasas" to of(Fat),
        "de las cuales saturadas" to of(SaturatedFat), "saturadas" to of(SaturatedFat),
        "hidratos de carbono" to of(Carbs),
        "de los cuales azúcares" to of(Sugars), "azúcares" to of(Sugars),
        "fibra alimentaria" to of(Fiber),
        "proteínas" to of(Protein),
        "sal" to Salt,
    )

    /** Longest first, so "total fat" wins over "fat" and "saturated fat" over both. */
    private val PHRASES: List<LabelPhrase> = listOf(
        LabelLanguage.English to ENGLISH,
        LabelLanguage.French to FRENCH,
        LabelLanguage.German to GERMAN,
        LabelLanguage.Italian to ITALIAN,
        LabelLanguage.Dutch to DUTCH,
        LabelLanguage.Spanish to SPANISH,
    ).flatMap { (language, table) -> table.map { (text, row) -> LabelPhrase(fold(text), row, language) } }
        .distinctBy { it.text }
        .sortedByDescending { it.text.length }

    /** The panel headings. "Nutrition Facts" and Canada's "Valeur nutritive" are the US-style panels. */
    private val ANCHORS: List<Pair<String, LabelLanguage>> = listOf(
        LabelLanguage.English to listOf(
            "nutrition facts", "nutrition information", "nutritional information", "nutrition info",
            "nutrition per", "nutritional values", "nutritional value", "nutrition declaration",
            "typical values", "typical value",
        ),
        LabelLanguage.French to listOf(
            "valeur nutritive", "valeurs nutritionnelles", "informations nutritionnelles",
            "déclaration nutritionnelle", "valeurs moyennes",
        ),
        LabelLanguage.German to listOf(
            "nährwerte", "nährwertangaben", "nährwertinformation", "nährwertinformationen", "nährwertdeklaration",
        ),
        LabelLanguage.Italian to listOf(
            "valori nutrizionali", "dichiarazione nutrizionale", "informazioni nutrizionali", "valori medi",
        ),
        LabelLanguage.Dutch to listOf("voedingswaarde", "voedingswaarden", "gemiddelde voedingswaarde"),
        LabelLanguage.Spanish to listOf(
            "información nutricional", "valores nutricionales", "valor nutricional", "declaración nutricional",
        ),
    ).flatMap { (language, anchors) -> anchors.map { fold(it) to language } }

    val US_ANCHORS: Set<String> = setOf("nutrition facts", "valeur nutritive")

    /** Words only a US-style panel uses, where total carbohydrate includes fiber. */
    val US_PHRASES: Set<String> = setOf(
        "calories", "calorie", "total carbohydrate", "total carbohydrates", "total carb", "total carbs",
        "dietary fiber", "total fat", "total sugars", "added sugars", "% daily value", "daily value",
        "% valeur quotidienne", "valeur quotidienne",
    )

    private val EU_PREFIXES = listOf("of which", "davon", "dont", "di cui", "waarvan", "de las cuales", "de los cuales")

    /** Words an EU-style table uses, where carbohydrate excludes fiber. Salt rows count too (see [LabelRow.Salt]). */
    fun isEuPhrase(phrase: LabelPhrase): Boolean =
        phrase.row == Salt || phrase.text == "saturates" ||
            (phrase.row == of(Energy) && phrase.text !in US_PHRASES) ||
            EU_PREFIXES.any { phrase.text.startsWith("$it ") }

    fun anchors(line: String): List<Pair<String, LabelLanguage>> = ANCHORS.filter { (anchor, _) -> line.hasPhrase(anchor) }

    /** Every row label in [line], left to right, none overlapping. */
    fun matches(line: String): List<PhraseMatch> = buildList {
        var i = 0
        while (i < line.length) {
            val phrase = if (line[i] == ' ' || (i > 0 && line[i - 1].isLetter())) null else PHRASES.firstOrNull { line.phraseAt(it.text, i) }
            if (phrase == null) {
                i++
            } else {
                add(PhraseMatch(i, i + phrase.text.length, phrase))
                i += phrase.text.length
            }
        }
    }

    /**
     * Lowercase, accents off, every run of whitespace one space. "µg" becomes
     * "ug" and "≤" "<", so the number reader sees one spelling of each.
     */
    fun fold(text: String): String = buildString(text.length) {
        var space = false
        for (c in text.lowercase()) {
            if (c.isWhitespace()) {
                if (!space && isNotEmpty()) append(' ')
                space = true
                continue
            }
            space = false
            append(FOLDS[c] ?: c)
        }
    }.trimEnd()
}

/** Whether [phrase] starts at [at] and ends on a word boundary. The caller checks the start boundary. */
internal fun String.phraseAt(phrase: String, at: Int): Boolean {
    if (!startsWith(phrase, at)) return false
    val after = at + phrase.length
    return after == length || !this[after].isLetter()
}

/** Whether [phrase] appears in this string as whole words. */
internal fun String.hasPhrase(phrase: String): Boolean {
    var from = indexOf(phrase)
    while (from >= 0) {
        if ((from == 0 || !this[from - 1].isLetter()) && phraseAt(phrase, from)) return true
        from = indexOf(phrase, from + 1)
    }
    return false
}
