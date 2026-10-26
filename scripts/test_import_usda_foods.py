"""Unit tests for the pure parts of import_usda_foods.py.

    python3 -I -m unittest discover -s scripts -p 'test_import_usda_foods.py'
"""

import unittest

from import_usda_foods import (
    gtin_check_digit,
    normalize_gtin,
    pg_env,
    serving_portion,
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


if __name__ == "__main__":
    unittest.main()
