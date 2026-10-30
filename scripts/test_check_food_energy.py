"""Unit tests for check_food_energy.py. The rule itself is SQL (V33) and is
tested against Postgres by FoodEnergyConsistencyTest in :apps:server.

    python3 -I -m unittest discover -s scripts -p 'test_check_food_energy.py'
"""

import unittest

from check_food_energy import batch_sql, judge, pending_sql

A = "00000000-0000-0000-0000-00000000000a"
B = "00000000-0000-0000-0000-00000000000b"


class FakeProc:
    def __init__(self, code=0):
        self.code = code

    def wait(self):
        return self.code


class FakeDb:
    """Answers each batch from a script of (last id, looked at, written)."""

    def __init__(self, batches, vacuum_exit=0):
        self.batches = list(batches)
        self.queries, self.sessions = [], []
        self.vacuum_exit = vacuum_exit

    def query(self, sql):
        self.queries.append(sql)
        last, scanned, updated = self.batches.pop(0) if self.batches else ("", 0, 0)
        return [["SET"], [last, str(scanned), str(updated)]]

    def start(self, sql):
        self.sessions.append(sql)
        return FakeProc(self.vacuum_exit)


class BatchSqlTest(unittest.TestCase):

    def test_the_first_batch_starts_from_the_beginning(self):
        sql = batch_sql("foods", None, 5000, None)
        self.assertIn("food_energy_backfill('public.foods', NULL, 5000, NULL)", sql)
        self.assertIn("SET lock_timeout = '5s'", sql)

    def test_a_later_batch_starts_after_the_last_id_and_can_recheck(self):
        sql = batch_sql("off_foods", A, 100, "2026-10-30T12:00:00Z")
        self.assertIn(f"('public.off_foods', '{A}'::uuid, 100, '2026-10-30T12:00:00Z'::timestamptz)", sql)

    def test_only_the_food_tables(self):
        for build in (lambda: batch_sql("profiles", None, 1, None), lambda: pending_sql("profiles", None)):
            with self.assertRaises(ValueError):
                build()

    def test_pending_counts_rows_never_judged_and_with_a_recheck_those_judged_before(self):
        self.assertTrue(pending_sql("foods", None).endswith("WHERE energy_checked_at IS NULL"))
        self.assertIn("OR energy_checked_at < '2026-10-30'::timestamptz", pending_sql("foods", "2026-10-30"))


class JudgeTest(unittest.TestCase):

    def test_batches_run_until_one_looks_at_nothing_each_after_the_last(self):
        db = FakeDb([(A, 2, 2), (B, 2, 1)])
        self.assertEqual((4, 3), judge(db, "foods", 2, vacuum_every=0))
        self.assertEqual(3, len(db.queries))
        self.assertIn("NULL, 2, NULL", db.queries[0])
        self.assertIn(f"'{A}'::uuid", db.queries[1])
        self.assertIn(f"'{B}'::uuid", db.queries[2])

    def test_a_run_with_nothing_to_write_does_not_vacuum(self):
        db = FakeDb([(A, 2, 0)])
        self.assertEqual((2, 0), judge(db, "foods", 2))
        self.assertEqual([], db.sessions)

    def test_it_vacuums_every_so_many_rows_written_and_at_the_end(self):
        db = FakeDb([(A, 3, 3), (B, 3, 3), (B, 1, 1)])
        judge(db, "off_foods", 3, vacuum_every=5)
        self.assertEqual(2, len(db.sessions))
        self.assertIn("SET statement_timeout = 0;\nVACUUM public.off_foods;", db.sessions[0])

    def test_a_failed_vacuum_stops_the_run(self):
        with self.assertRaises(SystemExit):
            judge(FakeDb([(A, 1, 1)], vacuum_exit=1), "foods", 1, vacuum_every=1)

    def test_resuming_starts_after_the_given_id(self):
        db = FakeDb([])
        judge(db, "foods", 10, after=A)
        self.assertIn(f"'{A}'::uuid", db.queries[0])


if __name__ == "__main__":
    unittest.main()
