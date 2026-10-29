package com.opson.libraries.foods

/**
 * Real nutrition panels as Apple Vision and ML Kit return them: one entry per
 * line in reading order, with their misreads ("Og" for 0g, "l" for 1), units
 * glued on, a row split over two lines, and column headers on lines of their
 * own.
 */
internal object LabelTextFixtures {

    /** Lines stacked down the photo, each 1.8% of the image high, as a row-by-row OCR pass returns them. */
    fun lines(vararg text: String, confidence: Double = 1.0): List<LabelTextLine> = text.mapIndexed { i, line ->
        LabelTextLine(line, confidence, left = 0.1, top = 0.05 + i * 0.025, width = 0.8, height = 0.018)
    }

    /** A US cereal, single column. Protein's amount landed on the next line. */
    val US_SINGLE = lines(
        "Nutrition Facts",
        "8 servings per container",
        "Serving size 2/3 cup (55g)",
        "Amount per serving",
        "Calories 230",
        "% Daily Value*",
        "Total Fat 8g 10%",
        "Saturated Fat 1g 5%",
        "Trans Fat Og",
        "Cholesterol Omg 0%",
        "Sodium l60mg 7%",
        "Total Carbohydrate 37g 13%",
        "Dietary Fiber 4g 14%",
        "Total Sugars 12g",
        "Includes 10g Added Sugars 20%",
        "Protein",
        "3g",
        "Vitamin D 2mcg 10%",
        "Calcium 260mg 20%",
        "Iron 8mg 45%",
        "Potassium 240mg 6%",
        "* The % Daily Value (DV) tells you how much a nutrient in a serving of food contributes to a daily diet. " +
            "2,000 calories a day is used for general nutrition advice.",
    )

    /** The FDA's dual-column format: per serving, then per container. */
    val US_DUAL = lines(
        "Nutrition Facts",
        "2 servings per container",
        "Serving size 1 cup (255g)",
        "Per serving",
        "Per container",
        "Calories 220 440",
        "% DV* % DV*",
        "Total Fat 5g 6% 10g 13%",
        "Sat. Fat 2g 10% 4g 20%",
        "Trans Fat 0g 0g",
        "Cholesterol 15mg 5% 30mg 10%",
        "Sodium 240mg 10% 480mg 21%",
        "Total Carb. 35g 13% 70g 25%",
        "Dietary Fiber 6g 21% 12g 43%",
        "Total Sugars 7g 14g",
        "Incl. Added Sugars 4g 8% 8g 16%",
        "Protein 9g 18g",
        "Vitamin D 5mcg 25% 10mcg 50%",
        "Calcium 200mg 15% 400mg 30%",
        "Iron 1mg 6% 2mg 10%",
        "Potassium 470mg 10% 940mg 20%",
    )

    /** A UK cereal table, per 100 g and per 30 g serving. */
    val EU_TWO_COLUMNS = lines(
        "Nutrition information",
        "Typical values",
        "Per 100g",
        "Per 30g serving",
        "Energy 1650kJ / 392kcal  495kJ / 118kcal",
        "Fat 8.5g 2.6g",
        "of which saturates 1.2g 0.4g",
        "Carbohydrate 67g 20g",
        "of which sugars 22g 6.6g",
        "Fibre 7.0g 2.1g",
        "Protein 9.1g 2.7g",
        "Salt 0.48g 0.14g",
    )

    val GERMAN = lines(
        "Nährwerte",
        "pro 100 g",
        "Brennwert 1590 kJ / 380 kcal",
        "Fett 12 g",
        "davon gesättigte Fettsäuren 4,5 g",
        "Kohlenhydrate 58 g",
        "davon Zucker 21 g",
        "Ballaststoffe 3,2 g",
        "Eiweiß 7,4 g",
        "Salz 0,52 g",
    )

    /** French biscuits, with the thousands of the kJ figure grouped by a space. */
    val FRENCH = lines(
        "Valeurs nutritionnelles",
        "pour 100 g",
        "Énergie 1 966 kJ / 469 kcal",
        "Matières grasses 21 g",
        "dont acides gras saturés 9,8 g",
        "Glucides 62 g",
        "dont sucres 34 g",
        "Fibres alimentaires 2,9 g",
        "Protéines 6,2 g",
        "Sel 0,58 g",
    )

    /** A can of soft drink: the table runs on as prose, several rows to a line. */
    val DRINK = lines(
        "Nutrition per 100ml: Energy 180kJ/42kcal,",
        "Fat Og, of which saturates Og,",
        "Carbohydrate 10.6g, of which sugars 10.6g,",
        "Protein Og, Salt 0.01g",
        "Serving size 330ml",
    )

    /** Canadian orange juice, every row in English and French. */
    val CANADIAN = lines(
        "Nutrition Facts / Valeur nutritive",
        "Per 1 cup (250 mL) / pour 1 tasse (250 mL)",
        "Calories 110 % Daily Value* / % valeur quotidienne*",
        "Fat / Lipides 0 g 0 %",
        "Saturated / saturés 0 g 0 %",
        "+ Trans / trans 0 g",
        "Carbohydrate / Glucides 26 g",
        "Fibre / Fibres 0 g 0 %",
        "Sugars / Sucres 22 g 22 %",
        "Protein / Protéines 2 g",
        "Cholesterol / Cholestérol 0 mg",
        "Sodium 0 mg 0 %",
        "Potassium 450 mg 10 %",
        "Calcium 20 mg 2 %",
        "Iron / Fer 0.2 mg 1 %",
    )

    /** Hot chocolate powder: per 100 g of powder, and a serving made up with milk. */
    val AS_PREPARED = lines(
        "Nutrition Information",
        "Typical values",
        "Per 100g as sold",
        "Per serving as prepared*",
        "Energy 1550kJ/367kcal 501kJ/119kcal",
        "Fat 4.1g 3.9g",
        "of which saturates 2.5g 2.5g",
        "Carbohydrate 73g 14g",
        "of which sugars 70g 14g",
        "Fibre 6.2g 0.4g",
        "Protein 5.5g 7.3g",
        "Salt 0.38g 0.24g",
        "*Prepared with 200ml semi-skimmed milk",
    )

    /** An Australian table that prints kilojoules only. */
    val KJ_ONLY = lines(
        "Nutrition Information",
        "Per 100g",
        "Energy 1500kJ",
        "Fat 10g",
        "of which saturates 2g",
        "Carbohydrate 50g",
        "of which sugars 5g",
        "Protein 10g",
        "Salt 1.0g",
    )

    /** Soy sauce: amounts too small to print. */
    val LESS_THAN = lines(
        "Nutrition Facts",
        "About 66 servings per container",
        "Serving size 1 Tbsp (15mL)",
        "Amount per serving",
        "Calories 10",
        "Total Fat 0g 0%",
        "Sodium 920mg 40%",
        "Total Carbohydrate 1g 0%",
        "Dietary Fiber <1g 2%",
        "Total Sugars less than 1g",
        "Protein 2g",
    )

    val PACK_FRONT = lines(
        "KELLOGG'S",
        "Crunchy Nut",
        "Golden honey with peanuts",
        "High in Vitamin D",
        "Source of fibre",
        "500g e",
    )

    val RECEIPT = lines(
        "TESCO EXPRESS",
        "SALT & VINEGAR CRISPS 1.25",
        "PROTEIN BAR 2.99",
        "SUBTOTAL 4.24",
        "VISA ************1234",
    )

    /** Glare over the middle of the table: the carbohydrate figure and the protein label did not survive. */
    val MESSY = lines(
        "Nutrition Information",
        "Typical values Per 100g",
        "Energy 1650kJ / 392kcal",
        "Fat 8.5g",
        "of which saturates l.2g",
        "Carbohydrate 6?g",
        "of which sugars 22g",
        "Pr0tein 9.1g",
        "Salt 0.48g",
    )

    /** OCR that read the label column and then the value column, so every row's numbers come after all the labels. */
    val COLUMN_ORDER_LABELS = listOf("Nutrition Information", "Per 100g", "Fat", "Carbohydrate", "Protein")
    val COLUMN_ORDER_VALUES = listOf("8.5g", "67g", "9.1g")
}
