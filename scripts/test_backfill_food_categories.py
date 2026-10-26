"""Unit tests for the pure parts of backfill_food_categories.py.

    python3 -I -m unittest discover -s scripts -p 'test_backfill_food_categories.py'
"""

import unittest

from backfill_food_categories import changes, update_sql


class ChangesTest(unittest.TestCase):

    def test_only_rows_whose_category_moves(self):
        rows = [["a", ""], ["b", "beverage"], ["c", "beverage"], ["d", ""], ["e", "restaurant"]]
        wanted = {"a": "beverage", "b": "beverage", "c": None, "d": None}
        self.assertEqual([("a", None, "beverage"), ("c", "beverage", None)], changes(rows, wanted))

    def test_rows_the_release_does_not_know_are_left_alone(self):
        self.assertEqual([], changes([["custom", "restaurant"]], {}))


class UpdateSqlTest(unittest.TestCase):

    def test_guards_on_the_category_read_and_types_nulls(self):
        sql = update_sql([("00012345678905", None, "beverage")], "barcode", "text", "f.source = 'usda_branded'")
        self.assertIn("('00012345678905'::text, NULL::text, 'beverage'::text)", sql)
        self.assertIn("f.barcode = v.key AND f.category IS NOT DISTINCT FROM v.old", sql)
        self.assertTrue(sql.startswith("BEGIN;") and sql.rstrip().endswith("COMMIT;"))


if __name__ == "__main__":
    unittest.main()
