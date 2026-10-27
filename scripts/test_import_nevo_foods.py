"""Unit tests for import_nevo_foods.py. No database: the SQL is checked as text.

    python3 -I -m unittest discover -s scripts -p 'test_import_nevo_foods.py'
"""

import io
import tempfile
import unittest
import zipfile
from pathlib import Path

from import_nevo_foods import (
    CITED_VERSION,
    MICROS,
    SOURCE,
    Stats,
    csv_text,
    english_name,
    food_id,
    parse_number,
    read_foods,
    upsert_sql,
)

# The micros keys the app reads (MicroWireKeysTest in :libraries:nutrition)
# plus the label ones the amount sheet shows. A key spelled differently is a
# nutrient no food ever shows.
APP_KEYS = {
    "vitamin_a_rae_ug", "thiamin_mg", "riboflavin_mg", "niacin_mg", "vitamin_b6_mg", "folate_dfe_ug",
    "vitamin_b12_ug", "vitamin_c_mg", "vitamin_d_ug", "vitamin_e_mg", "vitamin_k_ug", "choline_mg",
    "calcium_mg", "copper_mg", "iron_mg", "magnesium_mg", "manganese_mg", "phosphorus_mg", "potassium_mg",
    "selenium_ug", "sodium_mg", "zinc_mg",
    "sugars_g", "added_sugars_g", "saturated_fat_g", "trans_fat_g", "cholesterol_mg",
}

HEADER = [
    "NEVO-versie/NEVO-version", "Voedingsmiddelgroep", "Food group", "NEVO-code",
    "Voedingsmiddelnaam/Dutch food name", "Engelse naam/Food name", "Synoniem", "Hoeveelheid/Quantity",
    "Opmerking", "ENERCJ (kJ)", "ENERCC (kcal)", "PROT (g)", "CHO (g)", "SUGAR (g)", "FAT (g)", "FASAT (g)",
    "FIBT (g)", "NA (mg)", "SE (µg)", "VITD (μg)", "TOCPHA (mg)", "VITK1 (µg)", "FOLFD (µg)", "FOLAC (µg)",
]


def line(cells):
    return "|".join(f'"{c}"' for c in cells)


def nevo_csv(*rows, header=HEADER):
    """A NEVO table in the 2025/9.0 layout: pipe separated, quoted, decimal commas, CRLF."""
    return "\r\n".join([line(header), *(line(r) for r in rows)]) + "\r\n"


def food(code, name, group="Fruit", dutch_group="Fruit", quantity="per 100g", version=CITED_VERSION, **nutrients):
    values = {
        "ENERCJ": "238", "ENERCC": "56", "PROT": "0,3", "CHO": "12", "SUGAR": "10,4", "FAT": "0,2",
        "FASAT": "0", "FIBT": "2", "NA": "1", "SE": "0,1", "VITD": "0", "TOCPHA": "0,15", "VITK1": "2,2",
        "FOLFD": "5", "FOLAC": "0",
    }
    values.update(nutrients)
    return [version, dutch_group, group, code, f"NL {name}", name, "", quantity, "",
            *(values[h.split(" ")[0]] for h in HEADER[9:])]


APPLE = food("875", "Apple w skin av")
BREAD = food("246", "Bread wholemeal av", group="Bread", dutch_group="Brood",
             ENERCC="235", PROT="11,1", CHO="39", FAT="2,3", FIBT="6,7", NA="383", FOLAC="35")
SYRUP = food("336", "Apple syrup", group="Sugar, sweets, sweet spreads and sweet sauces",
             dutch_group="Suiker, snoep, zoet beleg en zoete sauzen", FIBT="")
COLA = food("1001", "Cola", group="Non-alcoholic beverages", dutch_group="Niet-alcoholische dranken",
            ENERCC="42", PROT="0", CHO="10,6", FAT="0", FIBT="0")
FORMULA = food("2000", "Infant formula", group="Foods for special nutritional use",
               dutch_group="Voedingsmiddelen voor bijzondere voeding", quantity="per 100ml")
SOY_DRINK = food("2001", "Soy drink", group="Milk and milk products", dutch_group="Melk en melkproducten",
                 quantity="per 100ml")


def read(text):
    stats = Stats()
    return read_foods(io.StringIO(text, newline=""), stats), stats


class ReadTest(unittest.TestCase):
    def test_keeps_foods_per_100_g_and_leaves_out_formula(self):
        foods, stats = read(nevo_csv(APPLE, FORMULA, BREAD, SOY_DRINK, SYRUP, COLA))

        self.assertEqual([f.code for f in foods], ["875", "246", "336", "1001"])
        self.assertEqual(stats.rows, 6)
        self.assertEqual(stats.special, 1)
        self.assertEqual(stats.not_per_100g, 1)
        self.assertEqual(stats.imported, 4)

    def test_formula_is_left_out_by_its_group_even_if_given_per_100_g(self):
        foods, stats = read(nevo_csv(food("2002", "Follow-on formula", group="Foods for special nutritional use",
                                          dutch_group="Voedingsmiddelen voor bijzondere voeding"), APPLE))

        self.assertEqual([f.code for f in foods], ["875"])
        self.assertEqual(stats.special, 1)

    def test_amounts_are_copied_exactly(self):
        bread = read(nevo_csv(BREAD))[0][0]

        self.assertEqual(
            (bread.kcal, bread.protein, bread.carbs, bread.fat, bread.fiber), (235.0, 11.1, 39.0, 2.3, 6.7),
        )
        self.assertEqual(bread.micros["sodium_mg"], 383.0)
        self.assertEqual(bread.micros["sugars_g"], 10.4)
        self.assertEqual(bread.micros["vitamin_e_mg"], 0.15)  # not rounded to 0.2

    def test_a_blank_cell_is_unknown_not_zero(self):
        syrup = read(nevo_csv(SYRUP))[0][0]

        self.assertIsNone(syrup.fiber)
        self.assertEqual(read(nevo_csv(APPLE))[0][0].micros["saturated_fat_g"], 0.0)

    def test_micro_keys_are_the_ones_the_app_reads(self):
        self.assertLessEqual(set(MICROS) | {"folate_dfe_ug"}, APP_KEYS)
        apple = read(nevo_csv(APPLE))[0][0]
        self.assertEqual(apple.micros["selenium_ug"], 0.1)
        self.assertEqual(apple.micros["vitamin_d_ug"], 0.0)  # header spelled with a Greek mu
        self.assertEqual(apple.micros["vitamin_k_ug"], 2.2)

    def test_folate_only_when_there_is_no_folic_acid(self):
        foods, stats = read(nevo_csv(APPLE, BREAD))

        self.assertEqual(foods[0].micros["folate_dfe_ug"], 5.0)
        self.assertNotIn("folate_dfe_ug", foods[1].micros)
        self.assertEqual(stats.folate_left_out, 1)

    def test_a_unit_other_than_ours_stops_the_run(self):
        header = [h if h != "SE (µg)" else "SE (mg)" for h in HEADER]
        with self.assertRaises(SystemExit) as raised:
            read(nevo_csv(APPLE, header=header))
        self.assertIn("SE", str(raised.exception))

    def test_a_missing_required_column_stops_the_run(self):
        header = [h for h in HEADER if h != "Engelse naam/Food name"]
        with self.assertRaises(SystemExit):
            read("|".join(header) + "\r\n")

    def test_another_release_is_refused_until_the_citation_moves(self):
        with self.assertRaises(SystemExit) as raised:
            read(nevo_csv(food("875", "Apple", version="2027/10.0")))
        self.assertIn(CITED_VERSION, str(raised.exception))

    def test_beverage_groups_get_the_beverage_category(self):
        foods, _ = read(nevo_csv(APPLE, COLA))

        self.assertEqual([f.category for f in foods], [None, "beverage"])

    def test_a_repeated_code_is_kept_once(self):
        foods, stats = read(nevo_csv(APPLE, APPLE))

        self.assertEqual(len(foods), 1)
        self.assertEqual(stats.duplicate_code, 1)


class NameTest(unittest.TestCase):
    def test_writes_out_nevos_abbreviations_as_whole_words(self):
        self.assertEqual(english_name("Apple w skin av"), "Apple with skin average")
        self.assertEqual(english_name("Potatoes wo skin  boiled"), "Potatoes without skin boiled")
        self.assertEqual(english_name("Wafers avocado"), "Wafers avocado")

    def test_numbers(self):
        self.assertEqual(parse_number('"12,3"'), 12.3)
        self.assertEqual(parse_number("0,05"), 0.05)
        self.assertIsNone(parse_number(""))
        self.assertIsNone(parse_number("tr"))


class SqlTest(unittest.TestCase):
    def sql(self, *rows):
        foods, _ = read(nevo_csv(*rows))
        return "".join(chunk for chunk, _ in upsert_sql(foods))

    def test_is_an_upsert_that_skips_unchanged_rows(self):
        sql = self.sql(APPLE, BREAD)

        self.assertIn("ON CONFLICT (source, source_id) DO UPDATE SET", sql)
        self.assertIn(") IS DISTINCT FROM (", sql)
        self.assertNotIn("popularity", sql)
        self.assertNotIn("DELETE", sql)

    def test_the_same_file_gives_the_same_sql(self):
        self.assertEqual(self.sql(APPLE, BREAD), self.sql(APPLE, BREAD))

    def test_every_row_carries_source_code_and_release(self):
        sql = self.sql(APPLE, COLA)

        self.assertIn(f"'{food_id('875')}'::uuid,'{SOURCE}','875','Apple with skin average'", sql)
        self.assertEqual(sql.count(f"'{CITED_VERSION}')"), 2)
        self.assertIn("'beverage','2025/9.0')", sql)

    def test_ids_are_stable_and_differ_from_usda(self):
        self.assertEqual(food_id("875"), food_id("875"))
        self.assertNotEqual(food_id("875"), food_id("876"))

    def test_unknown_is_null(self):
        self.assertIn("NULL", self.sql(SYRUP))

    def test_batches(self):
        many = [food(str(1000 + i), f"Food {i}") for i in range(1200)]
        foods, _ = read(nevo_csv(*many))
        chunks = list(upsert_sql(foods))

        self.assertEqual([rows for _, rows in chunks], [0, 500, 500, 200])


class FileTest(unittest.TestCase):
    def test_reads_the_table_out_of_rivms_zip(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "nevo.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("NEVO2025_v9.0/Referenties.csv", '"Bron"|"Referentie"\r\n"1"|"RIVM"\r\n')
                zf.writestr("NEVO2025_v9.0/NEVO2025_v9.0.csv", nevo_csv(APPLE).encode("utf-8-sig"))

            foods, _ = read(csv_text(archive))

        self.assertEqual([f.name for f in foods], ["Apple with skin average"])

    def test_refuses_a_path_outside_the_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "nevo.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("../evil.csv", nevo_csv(APPLE))
            with self.assertRaises(SystemExit):
                csv_text(archive)

    def test_refuses_a_file_that_isnt_nevo(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "other.csv"
            path.write_text("a|b\r\n1|2\r\n", encoding="utf-8")
            with self.assertRaises(SystemExit):
                csv_text(path)


if __name__ == "__main__":
    unittest.main()
