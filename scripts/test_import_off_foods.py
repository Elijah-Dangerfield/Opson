"""Unit tests for the pure parts of import_off_foods.py.

    python3 -I -m unittest discover -s scripts -p 'test_import_off_foods.py'
"""

import unittest

from import_off_foods import (
    Stats,
    batch_sql,
    clean_name,
    clean_text,
    country_tags,
    first_brand,
    macro_problem,
    micros_for,
    modified_at,
    normalize_barcode,
    parse_number,
    portions_for,
    product_from,
    scan_count,
)


def row(**values):
    """A `get` over an export row: the defaults pass the filter."""
    base = {
        "code": "3017620422003",
        "product_name": "Nutella",
        "brands": "Ferrero, Nutella",
        "energy-kcal_100g": "539",
        "proteins_100g": "6.3",
        "carbohydrates_100g": "57.5",
        "fat_100g": "30.9",
        "countries_tags": "en:france,en:germany",
        "last_modified_t": "1781851567",
    }
    base.update(values)
    return lambda column: base.get(column, "")


class NumberTest(unittest.TestCase):

    def test_plain_numbers(self):
        self.assertEqual(539.0, parse_number("539"))
        self.assertEqual(0.004, parse_number(" 0.004 "))
        self.assertEqual(1.5e-05, parse_number("1.5e-05"))
        self.assertEqual(-2.0, parse_number("-2"))

    def test_blank_and_junk_are_unknown(self):
        for raw in (None, "", "  ", "abc", "1,5", "12 g"):
            self.assertIsNone(parse_number(raw), raw)

    def test_nan_and_infinity_are_unknown(self):
        for raw in ("nan", "NaN", "inf", "-inf", "Infinity"):
            self.assertIsNone(parse_number(raw), raw)


class FilterTest(unittest.TestCase):

    def test_all_four_values_are_needed(self):
        self.assertEqual("missing", macro_problem(None, 1, 1, 1))
        self.assertEqual("missing", macro_problem(100, 1, None, 1))
        self.assertIsNone(macro_problem(0, 0, 0, 0))

    def test_ranges_are_inclusive(self):
        self.assertIsNone(macro_problem(900, 100, 0, 0))
        self.assertIsNone(macro_problem(884, 0, 0, 100))
        self.assertEqual("range", macro_problem(900.1, 1, 1, 1))
        self.assertEqual("range", macro_problem(-1, 1, 1, 1))
        self.assertEqual("range", macro_problem(100, 100.5, 0, 0))
        self.assertEqual("range", macro_problem(100, 1, -0.1, 1))

    def test_macros_sum_to_at_most_101_g(self):
        self.assertIsNone(macro_problem(400, 50, 30, 21))
        self.assertEqual("sum", macro_problem(400, 50, 30, 21.5))

    def test_a_good_row_passes(self):
        stats = Stats()
        product = product_from(row(), stats)
        self.assertIsNotNone(product)
        self.assertEqual(1, stats.usable)
        self.assertEqual("03017620422003", product.barcode)
        self.assertEqual("Nutella", product.name)
        self.assertEqual("Ferrero", product.brand)
        self.assertEqual(["en:france", "en:germany"], product.countries)
        self.assertEqual(1781851567, product.modified)

    def test_each_rejection_is_counted_once(self):
        cases = {
            "missing_macros": row(fat_100g=""),
            "out_of_range": row(**{"energy-kcal_100g": "1200"}),
            "macro_sum": row(proteins_100g="60", carbohydrates_100g="40", fat_100g="10"),
            "bad_barcode": row(code="1234567"),
            "no_name": row(product_name="   "),
        }
        for counter, get in cases.items():
            stats = Stats()
            self.assertIsNone(product_from(get, stats), counter)
            self.assertEqual(1, getattr(stats, counter), counter)
            self.assertEqual(0, stats.usable, counter)

    def test_the_filter_runs_in_the_measurements_order(self):
        # Missing macros and a bad code: counted as missing macros, as in the packet's numbers.
        stats = Stats()
        product_from(row(code="abc", proteins_100g=""), stats)
        self.assertEqual((1, 0), (stats.missing_macros, stats.bad_barcode))

    def test_codes_outside_8_to_14_digits_fail(self):
        for code in ("1234567", "123456789012345", "12345678901a", ""):
            stats = Stats()
            self.assertIsNone(product_from(row(code=code), stats), code)
            self.assertEqual(1, stats.bad_barcode, code)

    def test_fiber_out_of_range_is_unknown(self):
        self.assertIsNone(product_from(row(fiber_100g="140"), Stats()).fiber)
        self.assertEqual(7.0, product_from(row(fiber_100g="7"), Stats()).fiber)


class BarcodeTest(unittest.TestCase):

    def test_valid_codes_become_14_digit_gtins(self):
        self.assertEqual("03017620422003", normalize_barcode("3017620422003"))  # EAN-13
        self.assertEqual("00049000028911", normalize_barcode("049000028911"))  # UPC-A
        self.assertEqual("00049000028911", normalize_barcode("0049000028911"))  # its EAN-13 form
        self.assertEqual("00049000028911", normalize_barcode("00049000028911"))  # its GTIN-14 form
        self.assertEqual("00049000028911", normalize_barcode("49000028911"))  # leading zero lost

    def test_upc_e_expands_to_its_upc_a(self):
        self.assertEqual("00042100005264", normalize_barcode("04252614"))

    def test_ean_8_is_padded(self):
        self.assertEqual("00000040170725", normalize_barcode("40170725"))

    def test_a_failed_check_digit_keeps_the_code_as_published(self):
        self.assertEqual("3017620422004", normalize_barcode("3017620422004"))
        self.assertEqual("123456789", normalize_barcode("123456789"))

    def test_placeholders_and_non_digits_are_dropped(self):
        for code in ("00000000", "0000000000000", "00000000000017", "1234567", "12345678a", "١٢٣٤٥٦٧٨٩"):
            self.assertIsNone(normalize_barcode(code), code)

    def test_a_placeholder_code_passes_the_filter_but_is_counted_apart(self):
        stats = Stats()
        self.assertIsNone(product_from(row(code="0000000000000"), stats))
        self.assertEqual((1, 1), (stats.usable, stats.placeholder_barcode))


class NameTest(unittest.TestCase):

    def test_whitespace_entities_and_control_characters(self):
        self.assertEqual("Pain de mie complet", clean_name("  Pain  de\tmie\ncomplet "))
        self.assertEqual("Ben & Jerry's Cookie Dough", clean_name("Ben &amp; Jerry&#39;s Cookie Dough"))
        self.assertEqual("Skyr nature", clean_name("Skyr\x00 nature\x7f"))

    def test_all_caps_is_made_readable_and_mixed_case_left_alone(self):
        self.assertEqual("Greek Yogurt", clean_name("GREEK YOGURT"))
        self.assertEqual("KitKat Chunky", clean_name("KitKat Chunky"))
        self.assertEqual("Coca-Cola Zero", clean_name("COCA-COLA ZERO"))

    def test_other_scripts_pass_through(self):
        self.assertEqual("Йогурт питьевой", clean_name("Йогурт питьевой"))
        self.assertEqual("ポカリスエット", clean_name("ポカリスエット"))

    def test_blank_once_cleaned(self):
        self.assertEqual("", clean_name("&nbsp;  "))
        stats = Stats()
        self.assertIsNone(product_from(row(product_name="&nbsp;"), stats))
        self.assertEqual(1, stats.name_blank_after_cleaning)

    def test_names_are_capped(self):
        self.assertEqual(200, len(clean_name("a" * 500)))
        self.assertEqual("abc", clean_text("abc   def", 4))

    def test_first_brand(self):
        self.assertEqual("Ferrero", first_brand("Ferrero, Nutella"))
        self.assertEqual("Lagg's", first_brand(" , Lagg's"))
        self.assertIsNone(first_brand(""))


class FieldsTest(unittest.TestCase):

    def test_country_tags_are_kept_clean(self):
        self.assertEqual(["en:france", "en:united-states"], country_tags("en:france, en:united-states,en:france"))
        self.assertEqual(["en:españa"], country_tags("en:españa,'; drop table x;--,france"))
        self.assertEqual([], country_tags(""))

    def test_serving_in_grams(self):
        self.assertEqual([{"label": "1 serving (30 g)", "grams": 30.0}], portions_for("30g", ""))
        self.assertEqual([{"label": "1 biscuit (12,5 g)", "grams": 12.5}], portions_for("1 biscuit (12,5 g)", ""))
        self.assertEqual([{"label": "1.61 ONZ (45 g)", "grams": 45.0}], portions_for("1.61 ONZ (45 g)", ""))

    def test_serving_in_ml_has_no_grams(self):
        self.assertEqual([{"label": "1 serving (250 ml)", "grams": None}], portions_for("250 ml", ""))
        self.assertEqual([{"label": "1 glass (200ml)", "grams": None}], portions_for("1 glass (200ml)", ""))

    def test_a_serving_with_no_measure_is_left_out(self):
        self.assertEqual([], portions_for("1 biscuit", ""))
        self.assertEqual([], portions_for("", ""))

    def test_a_small_pack_by_weight_is_a_portion(self):
        self.assertEqual(
            [{"label": "1 serving (15 g)", "grams": 15.0}, {"label": "1 pack (45 g)", "grams": 45.0}],
            portions_for("15 g", "45 g"),
        )
        self.assertEqual([{"label": "1 pack (45 g)", "grams": 45.0}], portions_for("", "45g"))
        self.assertEqual([], portions_for("", "1 kg"))
        self.assertEqual([], portions_for("", "1500 g"))
        self.assertEqual([{"label": "1 serving (45 g)", "grams": 45.0}], portions_for("45 g", "45 g"))

    def test_micros_convert_from_grams(self):
        get = row(**{"sodium_100g": "0.4", "vitamin-c_100g": "0.03", "vitamin-d_100g": "0.0000025",
                     "sugars_100g": "56.3", "iron_100g": "-1", "calcium_100g": "150"})
        self.assertEqual({"sodium_mg": 400.0, "vitamin_c_mg": 30.0, "vitamin_d_ug": 2.5, "sugars_g": 56.3},
                         micros_for(get))

    def test_scan_count(self):
        self.assertEqual(12, scan_count("12"))
        self.assertEqual(3, scan_count("3.0"))
        for raw in ("", "-4", "nan", "1e12", "lots"):
            self.assertEqual(0, scan_count(raw), raw)
        self.assertEqual(57, product_from(row(unique_scans_n="57"), Stats()).scans)

    def test_modified_at(self):
        self.assertEqual(1781851567, modified_at("1781851567"))
        self.assertIsNone(modified_at(""))
        self.assertIsNone(modified_at("12"))
        self.assertIsNone(modified_at("99999999999"))


class SqlTest(unittest.TestCase):

    def test_batch_commits_on_its_own_and_quotes_text(self):
        product = product_from(row(product_name="L'Original"), Stats())
        sql = batch_sql([product], "2026-10-26")
        self.assertTrue(sql.startswith("BEGIN;\nINSERT INTO public.off_foods_next "))
        self.assertTrue(sql.endswith("COMMIT;\n"))
        self.assertIn("'L''Original'", sql)
        self.assertIn("ARRAY['en:france','en:germany']::text[]", sql)
        self.assertIn("ON CONFLICT (barcode) DO UPDATE", sql)
        self.assertIn("to_timestamp(1781851567),0)", sql)

    def test_ids_are_stable_per_barcode(self):
        a = batch_sql([product_from(row(), Stats())], "2026-10-26")
        b = batch_sql([product_from(row(product_name="Other"), Stats())], "2026-11-26")
        self.assertEqual(a.split("'::uuid")[0][-36:], b.split("'::uuid")[0][-36:])


if __name__ == "__main__":
    unittest.main()
