"""Unit tests for the pure parts of backfill_default_portions.py.

    python3 -I -m unittest discover -s scripts -p 'test_backfill_default_portions.py'
"""

import json
import unittest

from backfill_default_portions import changes, update_sql

BERRY = json.dumps([{"label": "1 berry", "grams": 0.5}, {"label": "1 cup", "grams": 148.0}])


class ChangesTest(unittest.TestCase):

    def test_a_row_whose_default_moves_is_reordered(self):
        (food_id, old, new), = changes([["a", "57", BERRY]])
        self.assertEqual(("a", BERRY), (food_id, old))
        self.assertEqual("1 cup", json.loads(new)[0]["label"])

    def test_a_row_already_in_order_is_left_alone(self):
        in_order = json.dumps([{"label": "1 cup", "grams": 148.0}, {"label": "1 berry", "grams": 0.5}])
        self.assertEqual([], changes([["a", "57", in_order]]))


class UpdateSqlTest(unittest.TestCase):

    def test_only_rows_still_holding_what_was_read_are_written(self):
        sql = update_sql([("00000000-0000-0000-0000-000000000001", BERRY, "[]")])
        self.assertIn("WHERE f.id = v.id AND f.portions = v.old", sql)
        self.assertTrue(sql.startswith("BEGIN;") and sql.rstrip().endswith("COMMIT;"))

    def test_text_is_quoted(self):
        quoted = json.dumps([{"label": "Gran's cup", "grams": 100.0}])
        self.assertIn("'[{\"label\": \"Gran''s cup\"", update_sql([("x", quoted, quoted)]))


if __name__ == "__main__":
    unittest.main()
