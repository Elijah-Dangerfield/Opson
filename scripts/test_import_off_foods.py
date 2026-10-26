"""Unit tests for the pure parts of import_off_foods.py.

    python3 -I -m unittest discover -s scripts -p 'test_import_off_foods.py'
"""

import gzip
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest import mock

import import_off_foods
from import_off_foods import (
    SCAN_BUCKETS,
    Stats,
    batch_sql,
    clean_name,
    clean_text,
    country_tags,
    first_brand,
    macro_problem,
    micros_for,
    most_scanned_first,
    modified_at,
    normalize_barcode,
    parse_number,
    portions_for,
    finish,
    index_builds,
    product_from,
    reload_sql,
    run_watched,
    scan_bucket,
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


class OrderTest(unittest.TestCase):
    """The reload writes the most-scanned products first; search's caps rely on it."""

    def test_buckets_run_from_most_scanned(self):
        self.assertEqual(0, scan_bucket(5000))
        self.assertEqual(0, scan_bucket(1000))
        self.assertEqual(1, scan_bucket(999))
        self.assertEqual(len(SCAN_BUCKETS) - 1, scan_bucket(0))
        self.assertEqual(sorted(SCAN_BUCKETS, reverse=True), list(SCAN_BUCKETS))

    def test_products_come_back_most_scanned_first_and_intact(self):
        scans = [0, 7, 1200, 3, 0, 55, 7]
        products = [product_from(row(code=f"30176204220{i:02d}", unique_scans_n=str(n)), Stats())
                    for i, n in enumerate(scans)]
        with tempfile.TemporaryDirectory() as spill:
            ordered = list(most_scanned_first(iter(products), Path(spill)))
            self.assertEqual([], list(Path(spill).iterdir()), "spill files are removed once read")
        self.assertEqual([1200, 55, 7, 7, 3, 0, 0], [p.scans for p in ordered])
        self.assertEqual(sorted(products, key=lambda p: p.barcode), sorted(ordered, key=lambda p: p.barcode))
        sevens = [p.barcode for p in ordered if p.scans == 7]
        self.assertEqual([products[1].barcode, products[6].barcode], sevens, "export order within a bucket")


class ReloadTest(unittest.TestCase):
    """The whole reload as SQL chunks, from a small export, with no database."""

    COLUMNS = ["code", "product_name", "energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g",
               "countries_tags", "unique_scans_n", "last_modified_t"]

    def reload(self, rows, batch_size):
        with tempfile.TemporaryDirectory() as tmp:
            export = Path(tmp) / "products.csv.gz"
            with gzip.open(export, "wt", encoding="utf-8") as f:
                f.write("\t".join(self.COLUMNS) + "\n")
                for r in rows:
                    f.write("\t".join(r) + "\n")
            args = Namespace(maintenance_work_mem="64MB", min_rows=1, allow_shrink=False)
            stats = Stats()
            with mock.patch.object(import_off_foods, "BATCH_SIZE", batch_size):
                chunks = list(reload_sql(export, "2026-10-26", stats, args, Path(tmp)))
        return chunks, stats

    def test_staging_is_prepared_then_loaded_in_batches_most_scanned_first(self):
        rows = [
            ["3017620422003", "Nutella", "539", "6.3", "57.5", "30.9", "en:france", "5", "1781851567"],
            ["5449000000996", "Coca-Cola", "42", "0", "10.6", "0", "en:spain", "2000", "1781850000"],
            ["3017620422003", "Nutella old", "530", "6", "57", "31", "en:france", "5", "1781851000"],
            ["4006381333931", "Pencil fudge", "400", "4", "70", "10", "", "0", "1781800000"],
            ["8712345678906", "Rice cakes", "380", "8", "80", "3", "", "0", "1781800000"],
            ["123", "Too short a code", "100", "1", "1", "1", "", "0", ""],
        ]
        chunks, stats = self.reload(rows, batch_size=3)

        sql = [text for text, _ in chunks]
        batches = [(text, n) for text, n in chunks if n]
        self.assertIn("SET statement_timeout = 0", sql[2])
        self.assertIn("CREATE TABLE IF NOT EXISTS public.off_foods_next", sql[3])
        self.assertEqual([3, 1], [n for _, n in batches])
        self.assertEqual(batches[-1][0], sql[-1], "the load ends with its last batch; finish() swaps")
        self.assertNotIn("LOCK TABLE", "".join(sql))
        self.assertTrue(all("COMMIT;" in text for text, _ in batches), "every batch commits on its own")

        first = batches[0][0]
        self.assertLess(first.index("'Coca-Cola'"), first.index("'Nutella'"), "the most-scanned product loads first")
        self.assertNotIn("Nutella old", "".join(sql), "of a barcode twice in a batch, the newer is kept")
        self.assertIn("'Rice cakes'", batches[1][0])

        self.assertEqual(6, stats.rows)
        self.assertEqual(1, stats.bad_barcode)
        self.assertEqual(1, stats.same_batch_duplicates)
        self.assertEqual(4, stats.sent)


LIVE = [
    ["off_foods_countries_idx", "CREATE INDEX off_foods_countries_idx ON public.off_foods USING gin (countries_tags)"],
    ["off_foods_search_idx", "CREATE INDEX off_foods_search_idx ON public.off_foods USING gin (search)"],
]


class IndexBuildsTest(unittest.TestCase):

    def test_each_live_index_is_aimed_at_the_staging_table(self):
        self.assertEqual(
            [
                ("off_foods_next_countries_idx",
                 "CREATE INDEX off_foods_next_countries_idx ON public.off_foods_next USING gin (countries_tags)"),
                ("off_foods_next_search_idx",
                 "CREATE INDEX off_foods_next_search_idx ON public.off_foods_next USING gin (search)"),
            ],
            index_builds(LIVE, staged=set()),
        )

    def test_an_index_already_built_is_not_built_again(self):
        self.assertEqual(["off_foods_next_search_idx"],
                         [t for t, _ in index_builds(LIVE, staged={"off_foods_next_countries_idx"})])

    def test_a_name_the_swap_cannot_rename_stops_the_import(self):
        with self.assertRaises(SystemExit):
            index_builds([["search_idx", "CREATE INDEX search_idx ON public.off_foods USING gin (search)"]], set())


class FakeProc:
    """A psql that is still running for `runs` polls, then exits with `code`."""

    def __init__(self, runs, code=0):
        self.runs, self.code, self.killed, self.returncode = runs, code, False, None

    def poll(self):
        if self.returncode is None and (self.killed or self.runs <= 0):
            self.returncode = -9 if self.killed else self.code
        self.runs -= 1
        return self.returncode

    def kill(self):
        self.killed = True

    def wait(self):
        return self.poll()


class FakeDb:
    def __init__(self, proc, sightings=(), live=LIVE, staged=()):
        self.proc, self.sightings, self.live, self.staged = proc, list(sightings), live, list(staged)
        self.started, self.markers = [], []

    def start(self, sql):
        self.started.append(sql)
        return self.proc

    def statement_running(self, marker):
        self.markers.append(marker)
        return self.sightings.pop(0) if self.sightings else False

    def query(self, sql):
        return [[name] for name in self.staged] if "off_foods_next'" in sql else self.live


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class WatchdogTest(unittest.TestCase):
    """run_watched against a fake psql and a fake pg_stat_activity: no database."""

    def watch(self, db, **kwargs):
        clock = FakeClock()
        run_watched(db, "CREATE INDEX x;", "building x", sleep=clock.sleep, clock=clock, **kwargs)

    def test_a_step_that_finishes_is_left_alone(self):
        proc = FakeProc(runs=5)
        self.watch(FakeDb(proc, sightings=[True] * 5), marker="INDEX x ON")
        self.assertFalse(proc.killed)

    def test_a_session_seen_and_then_gone_is_stopped(self):
        proc = FakeProc(runs=1000)
        with self.assertRaises(SystemExit) as stopped:
            self.watch(FakeDb(proc, sightings=[True, True, False, False]), marker="INDEX x ON")
        self.assertTrue(proc.killed)
        self.assertIn("--swap-only", str(stopped.exception))

    def test_one_miss_is_not_enough(self):
        proc = FakeProc(runs=4)
        self.watch(FakeDb(proc, sightings=[True, False, True, True]), marker="INDEX x ON")
        self.assertFalse(proc.killed)

    def test_a_session_never_seen_is_stopped_after_the_grace_period(self):
        proc = FakeProc(runs=1000)
        db = FakeDb(proc)
        with self.assertRaises(SystemExit):
            self.watch(db, marker="INDEX x ON")
        self.assertTrue(proc.killed)
        self.assertGreater(len(db.markers) * import_off_foods.POLL_SECONDS, import_off_foods.START_GRACE_SECONDS)

    def test_a_quick_step_past_its_limit_is_stopped(self):
        proc = FakeProc(runs=1000)
        with self.assertRaises(SystemExit):
            self.watch(FakeDb(proc), marker=None, limit=90)
        self.assertTrue(proc.killed)

    def test_a_psql_error_stops_the_import(self):
        with self.assertRaises(SystemExit) as stopped:
            self.watch(FakeDb(FakeProc(runs=0, code=3)), marker="INDEX x ON")
        self.assertIn("exit 3", str(stopped.exception))


class FinishTest(unittest.TestCase):

    def test_checks_then_one_session_per_missing_index_then_analyze_then_swap(self):
        steps = []
        db = FakeDb(FakeProc(runs=0), staged=["off_foods_next_countries_idx"])
        args = Namespace(maintenance_work_mem=import_off_foods.DEFAULT_MAINTENANCE_WORK_MEM, min_rows=1, allow_shrink=False)

        finish(db, "2026-10-26", args, run=lambda _db, sql, what, marker, limit=None: steps.append((what, sql, marker)))

        self.assertEqual(["checking the load", "building off_foods_next_search_idx", "analyzing", "swapping"],
                         [what for what, _, _ in steps])
        check, build, analyze, swap = (sql for _, sql, _ in steps)
        self.assertIn("refusing to swap", check)
        self.assertIn("CREATE INDEX off_foods_next_search_idx ON public.off_foods_next", build)
        self.assertEqual(1, build.count("CREATE INDEX"), "one index per session")
        self.assertIn("SET maintenance_work_mem = '64MB'", build)
        self.assertEqual("INDEX off_foods_next_search_idx ON", steps[1][2])
        self.assertIn("ANALYZE public.off_foods_next", analyze)
        self.assertIn("LOCK TABLE public.off_foods IN ACCESS EXCLUSIVE MODE", swap)

if __name__ == "__main__":
    unittest.main()
