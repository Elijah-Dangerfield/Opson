"""Unit tests for the pure parts of import_usda_foods.py.

    python3 -I -m unittest discover -s scripts -p 'test_import_usda_foods.py'
"""

import json
import stat
import sys
import tempfile
import unittest
from pathlib import Path

from import_usda_foods import (
    KEEPALIVES,
    Psql,
    PsqlOutput,
    branded_category,
    default_portion_first,
    generic_category,
    gtin_check_digit,
    normalize_gtin,
    pg_env,
    serving_portion,
    stream,
    title_case,
    upc_e_to_upc_a,
)


class TitleCaseTest(unittest.TestCase):

    def test_all_caps_becomes_readable(self):
        self.assertEqual("Swanson Broth Beef", title_case("SWANSON BROTH BEEF"))
        self.assertEqual(
            "Campbell's Slow Kettle Soup Clam Chowder",
            title_case("CAMPBELL'S SLOW KETTLE SOUP CLAM CHOWDER"),
        )

    def test_text_a_person_cased_is_left_alone(self):
        self.assertEqual("WESSON Vegetable Oil 1 GAL", title_case("WESSON  Vegetable Oil 1 GAL"))

    def test_units_after_a_number_go_lowercase(self):
        self.assertEqual("Cola 12 fl oz", title_case("COLA 12 FL OZ"))
        self.assertEqual("Rice 2 lb Bag", title_case("RICE 2 LB BAG"))
        self.assertEqual("Water 500ml", title_case("WATER 500ML"))
        self.assertEqual("Oil 1 gal", title_case("OIL 1 GAL"))

    def test_abbreviations_stay_capitals(self):
        self.assertEqual("BBQ Sauce", title_case("BBQ SAUCE"))
        self.assertEqual("PB&J Bites", title_case("PB&J BITES"))
        self.assertEqual("CHKN BRST Strips", title_case("CHKN BRST STRIPS"))
        self.assertEqual("Omega-3 DHA Milk", title_case("OMEGA-3 DHA MILK"))

    def test_small_words_lowercase_except_first(self):
        self.assertEqual("Soup with Rice and Beans", title_case("SOUP WITH RICE AND BEANS"))
        self.assertEqual("The Best of Both", title_case("THE BEST OF BOTH"))
        self.assertEqual("Chicken w/ Rice", title_case("CHICKEN W/ RICE"))

    def test_names_and_hyphens(self):
        self.assertEqual("McCormick Grill Mates", title_case("MCCORMICK GRILL MATES"))
        self.assertEqual("O'Brien Potatoes", title_case("O'BRIEN POTATOES"))
        self.assertEqual("Ready-to-Eat Cereal", title_case("READY-TO-EAT CEREAL"))
        self.assertEqual("Dr Pepper", title_case("DR PEPPER"))

    def test_words_that_start_with_a_digit_are_lowercased(self):
        self.assertEqual("2pk Yogurt 1st Choice", title_case("2PK YOGURT 1ST CHOICE"))
        self.assertEqual("2% Reduced Fat Milk", title_case("2% REDUCED FAT MILK"))


class BarcodeTest(unittest.TestCase):

    def test_check_digit(self):
        self.assertEqual(1, gtin_check_digit("04900002891"))  # Coca-Cola 12 oz can, UPC-A 049000028911
        self.assertEqual(3, gtin_check_digit("301762042200"))  # EAN-13 3017620422003

    def test_every_form_of_one_code_pads_to_the_same_gtin14(self):
        expected = ("00049000028911", False)
        self.assertEqual(expected, normalize_gtin("049000028911"))  # UPC-A
        self.assertEqual(expected, normalize_gtin("0049000028911"))  # EAN-13
        self.assertEqual(expected, normalize_gtin("00049000028911"))  # GTIN-14
        self.assertEqual(("03017620422003", False), normalize_gtin(" 3017620422003 "))

    def test_eleven_digits_get_their_check_digit_back(self):
        self.assertEqual(("00049000028911", True), normalize_gtin("04900002891"))
        self.assertEqual(("00049000028911", True), normalize_gtin("4900002891"))

    def test_upc_e_expands_to_its_upc_a(self):
        self.assertEqual("042100005264", upc_e_to_upc_a("04252614"))
        self.assertEqual(("00042100005264", False), normalize_gtin("04252614"))
        self.assertIsNone(upc_e_to_upc_a("04252615"))  # wrong check digit
        self.assertIsNone(upc_e_to_upc_a("94252614"))  # number system 9 is not UPC-E

    def test_ean8_pads(self):
        self.assertEqual(("00000096385074", False), normalize_gtin("96385074"))

    def test_unusable_codes(self):
        for raw in ["", "abc", "049000028912", "12345", "123456789012345", "０４９０００", "813090000000",
                    "00000000000000", "00000000007924"]:
            self.assertIsNone(normalize_gtin(raw), raw)


class ServingTest(unittest.TestCase):

    def test_gram_serving_keeps_household_text_and_grams(self):
        self.assertEqual({"label": "2 cookies (28 g)", "grams": 28.0}, serving_portion("28.0", "g", "2 COOKIES"))
        self.assertEqual({"label": "1 cup (30 g)", "grams": 30.0}, serving_portion("30", "GRM", "1 cup"))

    def test_ml_serving_has_a_label_and_no_grams(self):
        self.assertEqual({"label": "1 Can (340mL)", "grams": None}, serving_portion("340.0", "ml", "1 Can (340mL)"))
        self.assertEqual({"label": "1 cup (240 ml)", "grams": None}, serving_portion("240", "MLT", "1 CUP"))

    def test_unhelpful_household_text_falls_back_to_the_measure(self):
        self.assertEqual({"label": "1 serving (240 ml)", "grams": None}, serving_portion("240.0", "ml", "Amount per serving"))
        self.assertEqual({"label": "1 serving (20 ml)", "grams": None}, serving_portion("20.0", "ml", "20 MLT"))
        self.assertEqual({"label": "1 serving (85 g)", "grams": 85.0}, serving_portion("85", "g", ""))

    def test_no_portion_without_a_usable_size_or_unit(self):
        self.assertIsNone(serving_portion("", "g", "1 bar"))
        self.assertIsNone(serving_portion("0", "g", "1 bar"))
        self.assertIsNone(serving_portion("500", "MG", "1 capsule"))
        self.assertIsNone(serving_portion("1", "IU", "1 drop"))
        self.assertIsNone(serving_portion("30", "", "1 bar"))


def portions(*pairs):
    return [{"label": label, "grams": grams} for label, grams in pairs]


class DefaultPortionTest(unittest.TestCase):
    """Real FDC portion lists; the first portion is what the app logs by default."""

    def first(self, items, kcal_100g):
        result = default_portion_first(items, kcal_100g)
        self.assertEqual(sorted(map(str, items)), sorted(map(str, result)), "every portion is kept")
        self.assertEqual(result, default_portion_first(result, kcal_100g), "a second pass changes nothing")
        return result[0]["label"]

    def test_berries_default_to_a_cup_not_one_berry(self):
        blueberries = portions(("1 berry", 2.0), ("1 cup", 150.0), ("50 berries", 68.0))
        self.assertEqual("1 cup", self.first(blueberries, 57.0))
        strawberries = portions(("1 fruit", 18.0), ("1 cup", 150.0), ("1 medium (1-1/4\" dia)", 12.0),
                                ("1 NLEA serving", 147.0))
        self.assertEqual("1 cup", self.first(strawberries, 32.0))
        grapes = portions(("1 grape", 7.0), ("1 cup", 150.0))
        self.assertEqual("1 cup", self.first(grapes, 69.0))

    def test_nuts_default_to_an_ounce(self):
        almonds = portions(("1 nut", 1.2), ("1 cup", 141.0), ("1 package", 50.0), ("1 100 calorie package", 18.0),
                           ("1 oz", 28.4))
        self.assertEqual("1 oz", self.first(almonds, 579.0))
        # A cup of almonds is 828 kcal: dense food by the cup is replaced too.
        nuts = portions(("1 cup, whole", 143.0), ("1 cup, sliced", 92.0), ("1 oz (23 whole kernels)", 28.4),
                        ("1 almond", 1.2))
        self.assertEqual("1 oz (23 whole kernels)", self.first(nuts, 579.0))

    def test_grains_keep_their_cup(self):
        rice = portions(("1 cup, cooked", 195.0), ("1 oz, cooked", 28.4))
        self.assertEqual("1 cup, cooked", self.first(rice, 123.0))
        oats = portions(("1 cup", 80.0))
        self.assertEqual("1 cup", self.first(oats, 379.0))

    def test_candy_defaults_to_a_regular_size_not_one_piece(self):
        candy = portions(("1 piece", 8.0), ("1 miniature/bite size", 10.0), ("1 fun/snack size", 15.0),
                         ("1 pouch/regular size", 45.0), ("1 large", 90.0))
        self.assertEqual("1 pouch/regular size", self.first(candy, 490.0))
        # Ten small jelly beans are 41 kcal: small, but a real serving. Left alone.
        jelly_beans = portions(("10 small", 11.0), ("10 large (1 oz)", 28.0))
        self.assertEqual("10 small", self.first(jelly_beans, 375.0))

    def test_drinks_default_to_a_cup_or_can_not_a_fluid_ounce(self):
        coffee = portions(("1 fl oz", 30.0), ("1 cup (8 fl oz)", 240.0), ("1 small", 360.0), ("1 medium", 480.0))
        self.assertEqual("1 cup (8 fl oz)", self.first(coffee, 1.0))
        cola = portions(("1 fl oz (no ice)", 31.0), ("1 fl oz (with ice)", 23.0), ("1 can (12 fl oz)", 372.0),
                        ("1 bottle (20 fl oz)", 620.0))
        self.assertEqual("1 can (12 fl oz)", self.first(cola, 42.0))
        milk = portions(("1 cup", 244.0), ("1 fl oz", 30.5),
                        ("Guideline amount per fl oz of beverage", 2.5))
        self.assertEqual("1 cup", self.first(milk, 61.0))

    def test_small_on_purpose_stays(self):
        sugar = portions(("1 teaspoon", 4.2), ("1 cup", 200.0))
        self.assertEqual("1 teaspoon", self.first(sugar, 387.0))
        bacon = portions(("1 medium slice (yield after cooking)", 8.0), ("1 oz, raw (yield after cooking)", 8.0))
        self.assertEqual("1 medium slice (yield after cooking)", self.first(bacon, 468.0))

    def test_a_cup_of_butter_gives_way_to_a_spoon(self):
        butter = portions(("1 cup", 224.0), ("1 tablespoon", 14.0), ("1 pat", 7.0),
                          ("Guideline amount per slice of bread/roll", 7.0))
        self.assertEqual("1 tablespoon", self.first(butter, 717.0))

    def test_meals_keep_a_big_default(self):
        meal = portions(("1 meal", 340.0), ("1 cup", 240.0))
        self.assertEqual("1 meal", self.first(meal, 180.0))

    def test_nothing_better_leaves_the_list_alone(self):
        spice = portions(("1 tsp", 1.4), ("1 tbsp", 4.4))
        self.assertEqual("1 tsp", self.first(spice, 300.0))
        self.assertEqual([], default_portion_first([], 100.0))
        one = portions(("1 berry", 2.0))
        self.assertEqual(one, default_portion_first(one, 57.0))

    def test_unknown_energy_still_moves_a_sip(self):
        coffee = portions(("1 fl oz", 30.0), ("1 cup (8 fl oz)", 240.0))
        self.assertEqual("1 cup (8 fl oz)", self.first(coffee, None))


class CategoryTest(unittest.TestCase):

    def test_sr_and_foundation_categories(self):
        self.assertEqual("restaurant", generic_category("usda_sr", "21", "McDONALD'S, Big Mac"))
        self.assertEqual("restaurant", generic_category("usda_foundation", "25", "Restaurant, Chinese, fried rice"))
        self.assertEqual("beverage", generic_category("usda_sr", "14", "Beverages, coffee, brewed"))
        self.assertEqual("baby_food", generic_category("usda_sr", "3", "Babyfood, cereal, rice"))
        self.assertIsNone(generic_category("usda_sr", "20", "Rice, white, cooked"))

    def test_sr_juices_and_fluid_milk_are_drinks_but_not_their_powders(self):
        self.assertEqual("beverage", generic_category("usda_sr", "9", "Orange juice, raw"))
        self.assertIsNone(generic_category("usda_sr", "9", "Oranges, raw"))
        self.assertEqual("beverage", generic_category("usda_sr", "1", "Milk, whole, 3.25% milkfat, with added vitamin D"))
        self.assertIsNone(generic_category("usda_sr", "1", "Milk, dry, nonfat, instant"))
        self.assertIsNone(generic_category("usda_sr", "1", "Milk, canned, condensed, sweetened"))
        self.assertIsNone(generic_category("usda_sr", "1", "Cheese, cheddar"))

    def test_fndds_wweia_categories(self):
        self.assertEqual("beverage", generic_category("usda_fndds", "7302", "Coffee, brewed"))
        self.assertEqual("beverage", generic_category("usda_fndds", "7506", "Margarita"))
        self.assertEqual("beverage", generic_category("usda_fndds", "1004", "Milk, NFS"))
        self.assertEqual("beverage", generic_category("usda_fndds", "1902", "Almond milk, unsweetened"))
        self.assertEqual("baby_food", generic_category("usda_fndds", "9402", "Infant formula, ready-to-feed"))
        self.assertEqual("supplement", generic_category("usda_fndds", "9802", "Protein powder, whey based"))
        self.assertIsNone(generic_category("usda_fndds", "4002", "Rice, white, cooked"))
        self.assertIsNone(generic_category("usda_fndds", "", "Rice"))

    def test_fndds_names_that_say_fast_food_are_restaurant_food(self):
        self.assertEqual(
            "restaurant",
            generic_category("usda_fndds", "2202", "Chicken breast, fried, coated, from fast food / restaurant"),
        )
        self.assertEqual("restaurant", generic_category("usda_fndds", "1402", "Milk shake, fast food, chocolate"))

    def test_branded_categories_in_every_spelling(self):
        self.assertEqual("beverage", branded_category("Soda"))
        self.assertEqual("beverage", branded_category("Fruit & Vegetable Juice, Nectars & Fruit Drinks"))
        for spelling in (
            "Non Alcoholic Beverages  Ready to Drink",
            "Non Alcoholic Beverages - Ready to Drink",
            "Non Alcoholic Beverages \u2013 Ready to Drink",
        ):
            self.assertEqual("beverage", branded_category(spelling), spelling)
        self.assertEqual("baby_food", branded_category("Baby/Infant  Foods/Beverages"))
        self.assertEqual("baby_food", branded_category("Baby/Infant \u2013 Foods/Beverages"))
        self.assertEqual("supplement", branded_category("Meal Replacement Supplements"))

    def test_branded_foods_that_only_mention_a_drink_are_not_drinks(self):
        self.assertIsNone(branded_category("Milk Additives"))
        self.assertIsNone(branded_category("Vinegars/Cooking Wines"))
        self.assertIsNone(branded_category("Frozen Fruit & Fruit Juice Concentrates"))
        self.assertIsNone(branded_category("Includes any products that can be described/observed as ... water, milk, oil"))
        self.assertIsNone(branded_category(""))
        self.assertIsNone(branded_category(None))


class ConnectionTest(unittest.TestCase):

    def test_url_becomes_libpq_environment(self):
        env = pg_env("postgresql://postgres.ref:p%24ss@aws-0-us-east-1.pooler.supabase.com:5432/postgres")
        self.assertEqual(
            {
                "PGHOST": "aws-0-us-east-1.pooler.supabase.com", "PGPORT": "5432", "PGUSER": "postgres.ref",
                "PGPASSWORD": "p$ss", "PGDATABASE": "postgres", "PGSSLMODE": "require",
            },
            env,
        )

    def test_local_host_keeps_libpq_default_ssl(self):
        env = pg_env("postgresql://test:test@localhost:55432/opson?application_name=import")
        self.assertNotIn("PGSSLMODE", env)
        self.assertEqual("import", env["PGAPPNAME"])

    def test_key_value_form(self):
        self.assertEqual("db.example.com", pg_env("host=db.example.com dbname=x sslmode=verify-full")["PGHOST"])

    def test_unknown_parameters_are_refused(self):
        with self.assertRaises(SystemExit):
            pg_env("postgresql://u:p@h/db?sslpassword=x")


# A stand-in for psql: records its arguments, environment and stdin, answers a
# query with one row, and exits with FAKE_PSQL_EXIT. No database is involved.
FAKE_PSQL = """#!{python}
import json, os, sys
record = os.environ["FAKE_PSQL_RECORD"]
stdin = sys.stdin.read() if "-f" in sys.argv else ""
with open(record, "w") as f:
    json.dump({{"argv": sys.argv[1:], "password": os.environ.get("PGPASSWORD"), "stdin": stdin}}, f)
if "-c" in sys.argv:
    print(os.environ.get("FAKE_PSQL_OUTPUT", "t"))
sys.exit(int(os.environ.get("FAKE_PSQL_EXIT", "0")))
"""


class PsqlTest(unittest.TestCase):
    """The psql seam both importers stream through, against a fake psql binary."""

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.binary = Path(self.dir.name) / "psql"
        self.binary.write_text(FAKE_PSQL.format(python=sys.executable))
        self.binary.chmod(self.binary.stat().st_mode | stat.S_IXUSR)
        self.record = Path(self.dir.name) / "record.json"
        env = pg_env("postgresql://importer:s3cret@db.example.com:5432/postgres")
        self.db = Psql(str(self.binary), {**env, "FAKE_PSQL_RECORD": str(self.record)})

    def recorded(self):
        return json.loads(self.record.read_text())

    def test_a_query_carries_keepalives_and_keeps_the_password_off_the_command_line(self):
        self.assertEqual([["t"]], self.db.query("SELECT true"))
        call = self.recorded()
        self.assertEqual(KEEPALIVES, call["argv"][call["argv"].index("-d") + 1])
        self.assertEqual("s3cret", call["password"])
        self.assertNotIn("s3cret", " ".join(call["argv"]))

    def test_a_stream_carries_keepalives_stops_on_error_and_sends_every_chunk(self):
        stream([("BEGIN;\n", 0), ("INSERT 1;\n", 1), ("COMMIT;\n", 0)], PsqlOutput(self.db))
        call = self.recorded()
        self.assertEqual(KEEPALIVES, call["argv"][call["argv"].index("-d") + 1])
        self.assertIn("ON_ERROR_STOP=1", call["argv"])
        self.assertEqual("BEGIN;\nINSERT 1;\nCOMMIT;\n", call["stdin"])

    def test_a_psql_failure_stops_the_import_and_says_to_run_it_again(self):
        self.db.env["FAKE_PSQL_EXIT"] = "3"
        with self.assertRaises(SystemExit) as stopped:
            stream([("INSERT 1;\n", 1)], PsqlOutput(self.db))
        self.assertIn("re-run to finish", str(stopped.exception))

    def test_a_started_session_gets_its_sql_on_stdin_with_keepalives(self):
        proc = self.db.start("CREATE INDEX x ON t (c);\n")
        self.assertEqual(0, proc.wait())
        call = self.recorded()
        self.assertEqual(KEEPALIVES, call["argv"][call["argv"].index("-d") + 1])
        self.assertIn("ON_ERROR_STOP=1", call["argv"])
        self.assertEqual("CREATE INDEX x ON t (c);\n", call["stdin"])

    def test_the_watchdog_asks_pg_stat_activity_for_other_sessions_running_the_statement(self):
        self.db.env["FAKE_PSQL_OUTPUT"] = "1"
        self.assertTrue(self.db.statement_running("INDEX off_foods_next_search_idx ON"))
        query = self.recorded()["argv"][-1]
        self.assertIn("pg_stat_activity", query)
        self.assertIn("pid <> pg_backend_pid()", query)
        self.assertIn("'INDEX off_foods_next_search_idx ON'", query)
        self.db.env["FAKE_PSQL_OUTPUT"] = "0"
        self.assertFalse(self.db.statement_running("INDEX x ON"))

    def test_the_watchdog_counts_a_failed_look_as_not_seen(self):
        self.db.env["FAKE_PSQL_EXIT"] = "2"
        self.assertFalse(self.db.statement_running("INDEX x ON"))

    def test_a_failed_query_stops_the_import(self):
        self.db.env["FAKE_PSQL_EXIT"] = "2"
        with self.assertRaises(SystemExit):
            self.db.query("SELECT true")


if __name__ == "__main__":
    unittest.main()
