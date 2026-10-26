#!/usr/bin/env python3
"""Load the Open Food Facts export into its own table, `off_foods`.

Open Food Facts is ODbL (share-alike), so its rows live in `off_foods` and
never in `foods` (see docs/food-data-sources.md and V12__off_foods.sql). Each
run is a full reload: the daily deltas don't record deletions.

    python3 -I scripts/import_off_foods.py --psql                     # download, load CATALOG_DATABASE_URL
    python3 -I scripts/import_off_foods.py --csv products.csv.gz --psql
    python3 -I scripts/import_off_foods.py --csv products.csv.gz --output /tmp/off.sql.gz
    python3 -I scripts/import_off_foods.py --csv products.csv.gz --dry-run   # counts only

The export (`products.csv.gz`, about 1.5 GB, tab separated) is streamed with
the stdlib csv module and never extracted. A product is kept when:

  - energy-kcal, proteins, carbohydrates and fat per 100 g are all numbers,
  - energy is 0 to 900 kcal, each macro 0 to 100 g, the three sum to at most 101 g,
  - the code is 8 to 14 digits and the name isn't blank.

That is the filter behind the 3,303,905 usable products in
docs/food-data-sources.md. A code with a valid GS1 check digit is stored as
its 14-digit GTIN (an 8-digit UPC-E expanded first), like Branded Foods, so a
scan in any form finds it; a code that is mostly zeros is a placeholder many
products share, and is dropped; any other code is kept as published. When two
products end up with one barcode, the one Open Food Facts changed last wins.

The reload never writes to `off_foods`. It fills `off_foods_next`, a copy of
the table's definition, one committed batch at a time (no run-wide
transaction: a dropped connection costs one batch, and re-running resumes,
since every batch is an idempotent upsert on barcode). Then it builds the
search indexes, copied from `off_foods`, and swaps the tables in one short
transaction. V12's header has the details. If the swap step fails (a lock
timeout behind a long query, say), `--swap-only` retries just that step.

`--psql` reads the connection string from CATALOG_DATABASE_URL when given no
value and hands it to psql through libpq environment variables, so it never
appears in a process list. The swap refuses to replace the table with fewer
than `--min-rows` rows, or with under 80% of the live table's rows unless
`--allow-shrink` is given.

The export is untrusted data. It is downloaded into a fresh temporary
directory, read through a size cap, and nothing in it is executed. Run this
with `python3 -I` so nothing in the current directory can shadow a stdlib
module.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import html
import io
import json
import os
import re
import shutil
import sys
import tempfile
import time
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

# `-I` leaves the script's own directory off sys.path. This is the repo's
# scripts/ directory, never the download directory, so adding it back is safe.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import (  # noqa: E402
    MIN_SIGNIFICANT_DIGITS,
    FileOutput,
    Psql,
    PsqlOutput,
    _CappedReader,
    find_psql,
    gtin_check_digit,
    log,
    pg_env,
    rounded,
    sql_number,
    sql_text,
    stream,
    title_case,
    upc_e_to_upc_a,
)

EXPORT_URL = "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz"
USER_AGENT = "Opson/0.1 (food logger; Open Food Facts catalog import)"

# The 2026-10 export is 1.5 GB compressed. The caps refuse something absurd.
MAX_DOWNLOAD_BYTES = 6 * 1024 ** 3
MAX_CSV_BYTES = 80 * 1024 ** 3

TABLE = "off_foods"
STAGING = "off_foods_next"
OLD = "off_foods_old"
BATCH_SIZE = 2000
DEFAULT_MIN_ROWS = 1_000_000

# Same namespace as the USDA importer; the "off:" prefix keeps the ids apart.
ID_NAMESPACE = uuid.UUID("6f1c1a52-5b8e-4f0e-9b7a-0a5d3c8e2f41")

MAX_KCAL = 900.0
MAX_MACRO = 100.0
MAX_MACRO_SUM = 101.0

# micros key (unit in the key, as in `foods.micros`) -> (export column, factor).
# The export gives every nutrient but energy in grams per 100 g.
MICROS = {
    "sugars_g": ("sugars_100g", 1),
    "added_sugars_g": ("added-sugars_100g", 1),
    "saturated_fat_g": ("saturated-fat_100g", 1),
    "trans_fat_g": ("trans-fat_100g", 1),
    "cholesterol_mg": ("cholesterol_100g", 1e3),
    "sodium_mg": ("sodium_100g", 1e3),
    "potassium_mg": ("potassium_100g", 1e3),
    "calcium_mg": ("calcium_100g", 1e3),
    "iron_mg": ("iron_100g", 1e3),
    "magnesium_mg": ("magnesium_100g", 1e3),
    "phosphorus_mg": ("phosphorus_100g", 1e3),
    "zinc_mg": ("zinc_100g", 1e3),
    "copper_mg": ("copper_100g", 1e3),
    "manganese_mg": ("manganese_100g", 1e3),
    "selenium_ug": ("selenium_100g", 1e6),
    "vitamin_a_rae_ug": ("vitamin-a_100g", 1e6),
    "vitamin_c_mg": ("vitamin-c_100g", 1e3),
    "vitamin_d_ug": ("vitamin-d_100g", 1e6),
    "vitamin_e_mg": ("vitamin-e_100g", 1e3),
    "vitamin_k_ug": ("vitamin-k_100g", 1e6),
    "thiamin_mg": ("vitamin-b1_100g", 1e3),
    "riboflavin_mg": ("vitamin-b2_100g", 1e3),
    "niacin_mg": ("vitamin-pp_100g", 1e3),
    "vitamin_b6_mg": ("vitamin-b6_100g", 1e3),
    "vitamin_b12_ug": ("vitamin-b12_100g", 1e6),
    "choline_mg": ("choline_100g", 1e3),
}
# Folate is left out: the export's vitamin-b9 is not in dietary folate
# equivalents, which is what `folate_dfe_ug` means everywhere else.

COLUMNS = [
    "id", "barcode", "name", "brand", "quantity", "serving",
    "kcal_100g", "protein_100g", "carbs_100g", "fat_100g", "fiber_100g",
    "micros", "portions", "countries_tags", "source_version", "last_modified_at", "scans",
]
UPDATE_COLUMNS = [c for c in COLUMNS if c not in ("id", "barcode")]

CONTROL = re.compile(r"[\x00-\x1f\x7f-\x9f]")
COUNTRY_TAG = re.compile(r"^[a-z]{2}:[\w-]{1,80}$")
MAX_COUNTRY_TAGS = 40
# A measure in a serving or quantity text: "30 g", "1 biscuit (12,5 g)", "250ml".
MEASURE = re.compile(r"(\d+(?:[.,]\d+)?)\s*(g|gr|grams?|grammes?|ml|cl|dl|l|fl\.?\s?oz)(?![\w])", re.IGNORECASE)
GRAM_UNITS = {"g", "gr", "gram", "grams", "gramme", "grammes"}
MEASURE_ONLY = re.compile(r"^\d+(?:[.,]\d+)?\s*(g|gr|grams?|grammes?|ml|cl|dl|l)\.?$", re.IGNORECASE)
MAX_PORTION_GRAMS = 2000.0
MAX_PACKAGE_GRAMS = 1000.0
# 2000-01-01 and 2100-01-01: anything outside is a bad timestamp.
MIN_EPOCH, MAX_EPOCH = 946684800, 4102444800


# ─── Parsing a row ──────────────────────────────────────────────────────────

def parse_number(raw: str | None) -> float | None:
    """A finite float, or None for blank, unparseable, NaN or infinite text."""
    if raw is None:
        return None
    text = raw.strip()
    if not text:
        return None
    try:
        value = float(text)
    except ValueError:
        return None
    if value != value or value in (float("inf"), float("-inf")):
        return None
    return value


def clean_text(raw: str | None, limit: int) -> str:
    """Entities decoded, control characters and runs of whitespace collapsed, capped at `limit`."""
    text = html.unescape(raw or "")
    text = CONTROL.sub(" ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit].rstrip()


def clean_name(raw: str | None) -> str:
    """A product name fit to show: cleaned, and ALL CAPS made readable. Empty when there is none."""
    return title_case(clean_text(raw, 200))


def first_brand(raw: str | None) -> str | None:
    """The first of the export's comma-separated brands ("Ferrero, Nutella" -> "Ferrero")."""
    for part in (raw or "").split(","):
        brand = title_case(clean_text(part, 200))
        if brand:
            return brand
    return None


def normalize_barcode(raw: str | None) -> str | None:
    """The code as it will be stored, or None when it fails the filter or is a placeholder.

    A code of 8 to 14 digits with a valid GS1 check digit becomes its 14-digit
    GTIN: leading zeros never change a GTIN, so the UPC-A, EAN-13 and GTIN-14
    forms of one product meet on the same 14 digits, and the server matches a
    scan in any of them. An 8-digit code is read as a UPC-E first, as the
    Branded Foods import does. A code whose check digit fails is kept as
    published, so a scan of exactly that code still finds it.
    """
    code = (raw or "").strip()
    if not (code.isascii() and code.isdecimal() and 8 <= len(code) <= 14):
        return None
    gtin = None
    if len(code) == 8:
        upc_a = upc_e_to_upc_a(code)
        if upc_a is not None:
            gtin = upc_a.zfill(14)
    if gtin is None and gtin_check_digit(code[:-1]) == int(code[-1]):
        gtin = code.zfill(14)
    stored = gtin or code
    if len(stored.lstrip("0")) < MIN_SIGNIFICANT_DIGITS:
        return None
    return stored


def macro_problem(kcal: float | None, protein: float | None, carbs: float | None, fat: float | None) -> str | None:
    """Why the per-100 g numbers fail the filter ("missing", "range", "sum"), or None when they pass."""
    if None in (kcal, protein, carbs, fat):
        return "missing"
    if not (0 <= kcal <= MAX_KCAL and all(0 <= x <= MAX_MACRO for x in (protein, carbs, fat))):
        return "range"
    if protein + carbs + fat > MAX_MACRO_SUM:
        return "sum"
    return None


def country_tags(raw: str | None) -> list[str]:
    tags: list[str] = []
    for part in (raw or "").split(","):
        tag = part.strip().lower()
        if COUNTRY_TAG.match(tag) and tag not in tags:
            tags.append(tag)
            if len(tags) >= MAX_COUNTRY_TAGS:
                break
    return tags


def _measure(text: str) -> tuple[float | None, bool]:
    """(grams, is_liquid) from the first measure in `text`. Grams win over a volume."""
    liquid = False
    for match in MEASURE.finditer(text):
        amount = parse_number(match.group(1).replace(",", "."))
        unit = match.group(2).lower()
        if unit in GRAM_UNITS:
            return amount, False
        liquid = True
    return None, liquid


def _amount_text(value: float) -> str:
    return f"{rounded(value, 1):g}"


def portions_for(serving_raw: str | None, quantity_raw: str | None) -> list[dict]:
    """The label's serving and, for a small pack sold by weight, the whole pack.

    Grams only ever come from a measure stated in grams. A serving stated in
    ml keeps its label with no grams, as Branded Foods does, and the client
    falls back to weighing. A serving with no measure at all ("1 biscuit") is
    left out: without grams it can't scale anything.
    """
    portions: list[dict] = []
    serving = clean_text(serving_raw, 80)
    grams, liquid = _measure(serving)
    if grams is not None and 0 < grams <= MAX_PORTION_GRAMS:
        label = f"1 serving ({_amount_text(grams)} g)" if MEASURE_ONLY.match(serving) else serving
        portions.append({"label": label, "grams": rounded(grams, 1)})
    elif liquid and grams is None:
        label = serving
        if MEASURE_ONLY.match(serving):
            label = f"1 serving ({serving})"
        portions.append({"label": label[:80], "grams": None})
    quantity = clean_text(quantity_raw, 80)
    if MEASURE_ONLY.match(quantity):
        pack, _ = _measure(quantity)
        if pack is not None and 0 < pack <= MAX_PACKAGE_GRAMS and all(p["grams"] != rounded(pack, 1) for p in portions):
            portions.append({"label": f"1 pack ({_amount_text(pack)} g)", "grams": rounded(pack, 1)})
    return portions


def micros_for(get) -> dict[str, float]:
    micros = {}
    for key, (column, factor) in MICROS.items():
        grams = parse_number(get(column))
        # No nutrient can be negative or more than the 100 g it is part of.
        if grams is None or not 0 <= grams <= 100:
            continue
        micros[key] = rounded(grams * factor, 3)
    return micros


def scan_count(raw: str | None) -> int:
    """unique_scans_n as a non-negative int; 0 when blank or nonsense."""
    value = parse_number(raw)
    if value is None or not 0 <= value < 2 ** 31:
        return 0
    return int(value)


def modified_at(raw: str | None) -> int | None:
    value = parse_number(raw)
    if value is None or not MIN_EPOCH <= value < MAX_EPOCH:
        return None
    return int(value)


@dataclass
class Product:
    barcode: str
    name: str
    brand: str | None
    quantity: str | None
    serving: str | None
    kcal: float
    protein: float
    carbs: float
    fat: float
    fiber: float | None
    micros: dict
    portions: list
    countries: list
    modified: int | None
    scans: int = 0


@dataclass
class Stats:
    rows: int = 0
    missing_macros: int = 0
    out_of_range: int = 0
    macro_sum: int = 0
    bad_barcode: int = 0
    no_name: int = 0
    usable: int = 0  # passed the packet's filter
    placeholder_barcode: int = 0
    name_blank_after_cleaning: int = 0
    gtin: int = 0
    as_published: int = 0
    same_batch_duplicates: int = 0
    sent: int = 0
    with_portion: int = 0
    with_fiber: int = 0
    with_micros: int = 0
    with_country: int = 0


def product_from(get, stats: Stats) -> Product | None:
    """One export row as a Product, or None (counted in `stats`) when it doesn't pass the filter."""
    kcal, protein, carbs, fat = (
        parse_number(get(c)) for c in ("energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g")
    )
    problem = macro_problem(kcal, protein, carbs, fat)
    if problem == "missing":
        stats.missing_macros += 1
        return None
    if problem == "range":
        stats.out_of_range += 1
        return None
    if problem == "sum":
        stats.macro_sum += 1
        return None
    code = (get("code") or "").strip()
    if not (code.isdigit() and 8 <= len(code) <= 14):
        stats.bad_barcode += 1
        return None
    if not (get("product_name") or "").strip():
        stats.no_name += 1
        return None
    stats.usable += 1

    barcode = normalize_barcode(code)
    if barcode is None:
        stats.placeholder_barcode += 1
        return None
    name = clean_name(get("product_name"))
    if not name:
        stats.name_blank_after_cleaning += 1
        return None
    if len(barcode) == 14 and _valid_gtin(barcode):
        stats.gtin += 1
    else:
        stats.as_published += 1

    fiber = parse_number(get("fiber_100g"))
    if fiber is not None and not 0 <= fiber <= MAX_MACRO:
        fiber = None
    return Product(
        barcode=barcode,
        name=name,
        brand=first_brand(get("brands")),
        quantity=clean_text(get("quantity"), 80) or None,
        serving=clean_text(get("serving_size"), 80) or None,
        kcal=kcal,
        protein=protein,
        carbs=carbs,
        fat=fat,
        fiber=fiber,
        micros=micros_for(get),
        portions=portions_for(get("serving_size"), get("quantity")),
        countries=country_tags(get("countries_tags")),
        modified=modified_at(get("last_modified_t")),
        scans=scan_count(get("unique_scans_n")),
    )


def _valid_gtin(code: str) -> bool:
    return gtin_check_digit(code[:-1]) == int(code[-1])


# ─── Reading the export ─────────────────────────────────────────────────────

def export_rows(path: Path):
    """(get, row) per product: `get(column)` is that column's text, '' when the row is short.

    Read with the same dialect as the measurement in docs/food-data-sources.md:
    tab separated, default quoting, undecodable bytes replaced. Blank lines
    are skipped, as csv.DictReader skips them.
    """
    csv.field_size_limit(min(sys.maxsize, 2 ** 31 - 1))
    raw = gzip.open(path, "rb")
    capped = io.BufferedReader(_CappedReader(raw, MAX_CSV_BYTES, path.name), 1024 * 1024)
    text = io.TextIOWrapper(capped, encoding="utf-8", errors="replace", newline="")
    reader = csv.reader(text, delimiter="\t")
    header = {name: i for i, name in enumerate(next(reader))}
    missing = {"code", "product_name", "energy-kcal_100g", "proteins_100g", "carbohydrates_100g", "fat_100g",
               "countries_tags"} - header.keys()
    if missing:
        raise SystemExit(f"the export has no {sorted(missing)} column; has the format changed?")
    try:
        for row in reader:
            if not row:
                continue

            def get(column: str, row=row) -> str:
                i = header.get(column)
                return row[i] if i is not None and i < len(row) else ""

            yield get
    finally:
        text.close()


# ─── SQL ────────────────────────────────────────────────────────────────────

def sql_row(product: Product, version: str) -> str:
    food_id = uuid.uuid5(ID_NAMESPACE, f"off:{product.barcode}")
    countries = (
        "ARRAY[" + ",".join(sql_text(t) for t in product.countries) + "]::text[]"
        if product.countries else "'{}'::text[]"
    )
    return "(" + ",".join([
        f"'{food_id}'::uuid",
        sql_text(product.barcode),
        sql_text(product.name),
        sql_text(product.brand) if product.brand else "NULL",
        sql_text(product.quantity) if product.quantity else "NULL",
        sql_text(product.serving) if product.serving else "NULL",
        sql_number(rounded(product.kcal, 1)),
        sql_number(rounded(product.protein)),
        sql_number(rounded(product.carbs)),
        sql_number(rounded(product.fat)),
        sql_number(rounded(product.fiber)),
        sql_text(json.dumps(product.micros, separators=(",", ":"), sort_keys=True)) + "::jsonb",
        sql_text(json.dumps(product.portions, separators=(",", ":"), ensure_ascii=False)) + "::jsonb",
        countries,
        sql_text(version),
        f"to_timestamp({product.modified})" if product.modified is not None else "NULL",
        str(product.scans),
    ]) + ")"


def batch_sql(products: list[Product], version: str) -> str:
    """One committed upsert. A barcode already loaded is only rewritten by a product changed later."""
    update = ", ".join(f"{c} = EXCLUDED.{c}" for c in UPDATE_COLUMNS) + ", updated_at = now()"
    return (
        "BEGIN;\n"
        f"INSERT INTO public.{STAGING} ({', '.join(COLUMNS)}) VALUES\n"
        + ",\n".join(sql_row(p, version) for p in products)
        + f"\nON CONFLICT (barcode) DO UPDATE SET {update}\n"
        f"WHERE coalesce(EXCLUDED.last_modified_at, '-infinity') > coalesce({STAGING}.last_modified_at, '-infinity');\n"
        "COMMIT;\n"
    )


def session_sql(maintenance_work_mem: str) -> str:
    if not re.fullmatch(r"\d+(kB|MB|GB)", maintenance_work_mem):
        raise SystemExit("--maintenance-work-mem takes a size like 128MB")
    # Supabase gives the postgres role a 2 minute statement timeout; the index
    # builds take longer than that.
    return (
        "SET client_encoding = 'UTF8';\n"
        "SET statement_timeout = 0;\n"
        f"SET maintenance_work_mem = '{maintenance_work_mem}';\n"
    )


def prepare_sql(version: str) -> str:
    """Create (or keep, to resume) the staging table, shaped and secured like off_foods.

    A staging table left by a run of another export, or shaped differently
    from off_foods (a migration changed it since), is dropped and recreated.
    """
    return f"""
BEGIN;
DO $off$
DECLARE
    live regclass := to_regclass('public.{TABLE}');
    staged regclass := to_regclass('public.{STAGING}');
    other_export boolean;
BEGIN
    IF live IS NULL THEN
        RAISE EXCEPTION '{TABLE} is missing: run the server migrations (V12) against this database first';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_policy WHERE polrelid = live) THEN
        RAISE EXCEPTION '{TABLE} has RLS policies, and the swap would drop them: teach scripts/import_off_foods.py to copy them first';
    END IF;
    IF staged IS NULL THEN
        RETURN;
    END IF;
    EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.{STAGING} WHERE source_version <> %L)', {sql_text(version)})
        INTO other_export;
    IF other_export OR (
        (SELECT array_agg(attname || ':' || format_type(atttypid, atttypmod) ORDER BY attnum)
           FROM pg_attribute WHERE attrelid = live AND attnum > 0 AND NOT attisdropped)
        IS DISTINCT FROM
        (SELECT array_agg(attname || ':' || format_type(atttypid, atttypmod) ORDER BY attnum)
           FROM pg_attribute WHERE attrelid = staged AND attnum > 0 AND NOT attisdropped)
    ) THEN
        RAISE NOTICE 'dropping a stale {STAGING}';
        DROP TABLE public.{STAGING};
    END IF;
END
$off$;

-- Columns, defaults, the generated tsvector and the CHECKs. Indexes come
-- after the load; the two unique ones are needed now, for the upsert.
CREATE TABLE IF NOT EXISTS public.{STAGING} (LIKE public.{TABLE} INCLUDING ALL EXCLUDING INDEXES);

DO $off$
DECLARE
    c record;
    g record;
    live_owner name := (SELECT relowner::regrole::name FROM pg_class WHERE oid = 'public.{TABLE}'::regclass);
BEGIN
    -- Primary key and unique barcode, from off_foods' own constraints.
    FOR c IN
        SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint
        WHERE conrelid = 'public.{TABLE}'::regclass AND contype IN ('p', 'u')
    LOOP
        IF left(c.conname, {len(TABLE) + 1}) <> '{TABLE}_' THEN
            RAISE EXCEPTION 'constraint % on {TABLE} does not start with {TABLE}_, so the swap cannot rename it', c.conname;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.{STAGING}'::regclass AND conname = '{STAGING}' || substr(c.conname, {len(TABLE) + 1})
        ) THEN
            EXECUTE format('ALTER TABLE public.{STAGING} ADD CONSTRAINT %I %s',
                           '{STAGING}' || substr(c.conname, {len(TABLE) + 1}), c.def);
        END IF;
    END LOOP;

    -- LIKE copies neither RLS nor grants nor the owner, so mirror them.
    ALTER TABLE public.{STAGING} ENABLE ROW LEVEL SECURITY;
    IF (SELECT relforcerowsecurity FROM pg_class WHERE oid = 'public.{TABLE}'::regclass) THEN
        ALTER TABLE public.{STAGING} FORCE ROW LEVEL SECURITY;
    END IF;
    IF live_owner <> (SELECT relowner::regrole::name FROM pg_class WHERE oid = 'public.{STAGING}'::regclass) THEN
        EXECUTE format('ALTER TABLE public.{STAGING} OWNER TO %I', live_owner);
    END IF;
    FOR g IN
        SELECT DISTINCT a.grantee FROM pg_class t, aclexplode(t.relacl) a
        WHERE t.oid = 'public.{STAGING}'::regclass AND a.grantee <> t.relowner
    LOOP
        EXECUTE format('REVOKE ALL ON public.{STAGING} FROM %s',
                       CASE WHEN g.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(g.grantee::regrole::name) END);
    END LOOP;
    FOR g IN
        SELECT a.grantee, a.privilege_type, a.is_grantable FROM pg_class t, aclexplode(t.relacl) a
        WHERE t.oid = 'public.{TABLE}'::regclass
    LOOP
        EXECUTE format('GRANT %s ON public.{STAGING} TO %s%s', g.privilege_type,
                       CASE WHEN g.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(g.grantee::regrole::name) END,
                       CASE WHEN g.is_grantable THEN ' WITH GRANT OPTION' ELSE '' END);
    END LOOP;
END
$off$;
COMMIT;
"""


def finalize_sql(version: str, min_rows: int, allow_shrink: bool) -> str:
    """Check the load, build the search indexes, then swap in one short transaction."""
    shrink_check = "" if allow_shrink else f"""
    IF loaded < 0.8 * (SELECT count(*) FROM public.{TABLE}) THEN
        RAISE EXCEPTION 'refusing to swap: {STAGING} has % rows, under 80%% of {TABLE}. Re-run with --allow-shrink if that is expected', loaded;
    END IF;"""
    return f"""
DO $off$
DECLARE
    loaded bigint := (SELECT count(*) FROM public.{STAGING});
BEGIN
    IF loaded < {int(min_rows)} THEN
        RAISE EXCEPTION 'refusing to swap: {STAGING} has only % rows (minimum {int(min_rows)})', loaded;
    END IF;
    IF EXISTS (SELECT 1 FROM public.{STAGING} WHERE source_version <> {sql_text(version)}) THEN
        RAISE EXCEPTION 'refusing to swap: {STAGING} holds rows from another export';
    END IF;{shrink_check}
END
$off$;

-- The search indexes, copied from off_foods so V12 stays their only
-- definition. Built once, after the load, which is far cheaper than keeping
-- three GIN indexes up to date through every insert.
DO $off$
DECLARE
    i record;
    target text;
BEGIN
    FOR i IN
        SELECT x.indexname, x.indexdef FROM pg_indexes x
        WHERE x.schemaname = 'public' AND x.tablename = '{TABLE}'
          AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = format('public.%I', x.indexname)::regclass)
    LOOP
        IF left(i.indexname, {len(TABLE) + 1}) <> '{TABLE}_' THEN
            RAISE EXCEPTION 'index % on {TABLE} does not start with {TABLE}_, so the swap cannot rename it', i.indexname;
        END IF;
        target := '{STAGING}' || substr(i.indexname, {len(TABLE) + 1});
        IF to_regclass(format('public.%I', target)) IS NULL THEN
            RAISE NOTICE 'building %', target;
            EXECUTE replace(
                i.indexdef,
                format('INDEX %s ON public.{TABLE} ', i.indexname),
                format('INDEX %I ON public.{STAGING} ', target)
            );
        END IF;
    END LOOP;
END
$off$;

ANALYZE public.{STAGING};

-- The swap. Renames only touch the catalog, so the exclusive lock lasts
-- milliseconds; the lock timeout stops a long-running query from making
-- every search queue behind it. The DROP frees the old files at commit.
BEGIN;
SET LOCAL lock_timeout = '15s';
LOCK TABLE public.{TABLE} IN ACCESS EXCLUSIVE MODE;
DO $off$
DECLARE
    i record;
BEGIN
    ALTER TABLE public.{TABLE} RENAME TO {OLD};
    FOR i IN SELECT c.relname FROM pg_index x JOIN pg_class c ON c.oid = x.indexrelid
             WHERE x.indrelid = 'public.{OLD}'::regclass
    LOOP
        EXECUTE format('ALTER INDEX public.%I RENAME TO %I', i.relname, '{OLD}' || substr(i.relname, {len(TABLE) + 1}));
    END LOOP;
    ALTER TABLE public.{STAGING} RENAME TO {TABLE};
    FOR i IN SELECT c.relname FROM pg_index x JOIN pg_class c ON c.oid = x.indexrelid
             WHERE x.indrelid = 'public.{TABLE}'::regclass
    LOOP
        EXECUTE format('ALTER INDEX public.%I RENAME TO %I', i.relname, '{TABLE}' || substr(i.relname, {len(STAGING) + 1}));
    END LOOP;
END
$off$;
DROP TABLE public.{OLD};
COMMIT;
"""


def reload_sql(path: Path, version: str, stats: Stats, args):
    """The whole reload as (sql, rows) chunks: session settings, staging, batches, swap."""
    yield "-- Generated by scripts/import_off_foods.py from the Open Food Facts export (ODbL).\n", 0
    yield f"-- Export {version}. Loads {STAGING}, then swaps it in for {TABLE}.\n", 0
    yield session_sql(args.maintenance_work_mem), 0
    yield prepare_sql(version), 0
    batch: dict[str, Product] = {}
    for get in export_rows(path):
        stats.rows += 1
        product = product_from(get, stats)
        if product is None:
            continue
        stats.with_portion += bool(product.portions)
        stats.with_fiber += product.fiber is not None
        stats.with_micros += bool(product.micros)
        stats.with_country += bool(product.countries)
        kept = batch.get(product.barcode)
        if kept is not None:
            # One statement can't upsert a barcode twice. Keep the newer.
            stats.same_batch_duplicates += 1
            if (product.modified or 0) <= (kept.modified or 0):
                continue
        batch[product.barcode] = product
        if len(batch) >= BATCH_SIZE:
            stats.sent += len(batch)
            yield batch_sql(list(batch.values()), version), len(batch)
            batch.clear()
    if batch:
        stats.sent += len(batch)
        yield batch_sql(list(batch.values()), version), len(batch)
    log(f"  read {stats.rows:,} rows; building indexes and swapping")
    yield finalize_sql(version, args.min_rows, args.allow_shrink), 0


class NullOutput:
    def write(self, text: str) -> None:
        pass

    def close(self) -> None:
        pass


# ─── Input ──────────────────────────────────────────────────────────────────

def download_export(url: str, dest: Path) -> str:
    """Download the export into `dest`. Returns its date (YYYY-MM-DD) from Last-Modified, else today."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    total = 0
    started = time.monotonic()
    last = started
    with urllib.request.urlopen(request, timeout=120) as response, open(dest, "wb") as out:
        stamp = response.headers.get("Last-Modified")
        while True:
            chunk = response.read(4 * 1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_DOWNLOAD_BYTES:
                raise RuntimeError(f"{url} is larger than {MAX_DOWNLOAD_BYTES} bytes, refusing")
            out.write(chunk)
            if time.monotonic() - last >= 30:
                log(f"  {total / 1e9:.2f} GB downloaded")
                last = time.monotonic()
    log(f"  downloaded {total / 1e9:.2f} GB in {time.monotonic() - started:.0f}s")
    try:
        when = parsedate_to_datetime(stamp) if stamp else None
    except (TypeError, ValueError):
        when = None
    return (when or datetime.now(timezone.utc)).astimezone(timezone.utc).date().isoformat()


def database_report(db: Psql) -> list[str]:
    rows = db.query(
        f"SELECT count(*), min(source_version), max(source_version), "
        f"pg_size_pretty(pg_total_relation_size('public.{TABLE}')), "
        f"pg_size_pretty(pg_relation_size('public.{TABLE}')), "
        f"pg_size_pretty(pg_indexes_size('public.{TABLE}')) FROM public.{TABLE}"
    )
    count, low, high, total, heap, indexes = rows[0]
    version = low if low == high else f"{low} to {high}"
    return [
        "", f"`{TABLE}` now holds {int(count):,} products from the {version} export.",
        f"It takes {total} with indexes and TOAST (table {heap}, indexes {indexes}).",
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--csv", type=Path, help="read this already-downloaded products.csv.gz instead of downloading")
    parser.add_argument("--url", default=EXPORT_URL, help=f"export to download (default {EXPORT_URL})")
    parser.add_argument("--source-version", help="the export date, YYYY-MM-DD (default: Last-Modified of the "
                                                 "download, or the --csv file's modification date)")
    out = parser.add_mutually_exclusive_group()
    out.add_argument("--psql", nargs="?", const="", metavar="CONNINFO",
                     help="stream the reload into this database; with no value, read CATALOG_DATABASE_URL")
    out.add_argument("--output", type=Path, help="write the reload as gzipped SQL here instead")
    out.add_argument("--dry-run", action="store_true", help="read and filter the export, print the counts, write nothing")
    parser.add_argument("--swap-only", action="store_true",
                        help="with --psql: skip the load and only build indexes and swap a finished off_foods_next")
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--min-rows", type=int, default=DEFAULT_MIN_ROWS,
                        help=f"refuse to swap in fewer rows than this (default {DEFAULT_MIN_ROWS:,})")
    parser.add_argument("--allow-shrink", action="store_true",
                        help="allow swapping in under 80%% of the live table's rows")
    parser.add_argument("--maintenance-work-mem", default="128MB",
                        help="for the index builds (default 128MB, safe on a 1 GB instance)")
    parser.add_argument("--summary", type=Path, help="append a markdown summary here (e.g. $GITHUB_STEP_SUMMARY)")
    args = parser.parse_args()

    if args.source_version and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.source_version):
        raise SystemExit("--source-version takes a date, YYYY-MM-DD")

    db: Psql | None = None
    if args.psql is not None:
        conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
        if not conninfo:
            raise SystemExit("--psql needs a connection string, or CATALOG_DATABASE_URL set")
        db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
        # Fail before a 1.5 GB download, not after it.
        if db.query(f"SELECT to_regclass('public.{TABLE}') IS NOT NULL") != [["t"]]:
            raise SystemExit(f"{TABLE} is missing: run the server migrations (V12) against this database first")

    if args.swap_only:
        if db is None:
            raise SystemExit("--swap-only needs --psql")
        found = db.query(f"SELECT DISTINCT source_version FROM public.{STAGING}")
        if len(found) != 1:
            raise SystemExit(f"{STAGING} should hold exactly one export, found {[r[0] for r in found]}")
        started = time.monotonic()
        stream([(session_sql(args.maintenance_work_mem), 0),
                (finalize_sql(found[0][0], args.min_rows, args.allow_shrink), 0)], PsqlOutput(db))
        text = "\n".join([f"Swapped in {STAGING} in {time.monotonic() - started:.0f}s."] + database_report(db)) + "\n"
        log(text)
        return 0

    started = time.monotonic()
    stats = Stats()
    workdir = Path(tempfile.mkdtemp(prefix="opson-off-"))
    try:
        if args.csv:
            path, origin = args.csv, f"local file {args.csv.name}"
            version = args.source_version or datetime.fromtimestamp(
                path.stat().st_mtime, timezone.utc).date().isoformat()
        else:
            log(f"downloading {args.url}")
            path, origin = workdir / "products.csv.gz", args.url
            downloaded = download_export(args.url, path)
            version = args.source_version or downloaded
        log(f"export {version} ({origin})")
        if args.dry_run:
            sink = NullOutput()
        elif db is not None:
            sink = PsqlOutput(db)
        else:
            fooddb = Path(__file__).resolve().parent.parent / "apps" / "server" / "fooddb"
            sink = FileOutput(args.output or fooddb / "off_foods.sql.gz")
        stream(reload_sql(path, version, stats, args), sink)
    finally:
        shutil.rmtree(workdir, ignore_errors=True)

    summary = [
        "### Open Food Facts import", "", f"Export: {version} ({origin})", "",
        "| | count |", "|---|---:|",
        f"| rows in the export | {stats.rows:,} |",
        f"| skipped: energy or a macro missing | {stats.missing_macros:,} |",
        f"| skipped: a value out of range | {stats.out_of_range:,} |",
        f"| skipped: macros over {MAX_MACRO_SUM:g} g | {stats.macro_sum:,} |",
        f"| skipped: not an 8 to 14 digit code | {stats.bad_barcode:,} |",
        f"| skipped: no name | {stats.no_name:,} |",
        f"| **usable (the packet's filter)** | **{stats.usable:,}** |",
        f"| then dropped: placeholder (mostly zeros) or non-ASCII barcode | {stats.placeholder_barcode:,} |",
        f"| then dropped: name blank once cleaned | {stats.name_blank_after_cleaning:,} |",
        f"| sent (stored as GTIN / as published) | {stats.sent:,} ({stats.gtin:,} / {stats.as_published:,}) |",
        f"| duplicate barcodes within a batch (newest kept) | {stats.same_batch_duplicates:,} |",
        f"| with a portion / fiber / micros / a country | {stats.with_portion:,} / {stats.with_fiber:,} / "
        f"{stats.with_micros:,} / {stats.with_country:,} |",
        "", f"Took {time.monotonic() - started:.0f}s.",
    ]
    if db is not None:
        summary += database_report(db)
    text = "\n".join(summary) + "\n"
    log(text)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as f:
            f.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
