#!/usr/bin/env python3
"""Load the Dutch food composition table, NEVO (RIVM), into the generic catalog.

    python3 -I scripts/import_nevo_foods.py --file NEVO2025_v9.0.csv            # counts only
    python3 -I scripts/import_nevo_foods.py --file NEVO2025_v9.0.zip --psql     # load CATALOG_DATABASE_URL
    python3 -I scripts/import_nevo_foods.py --url '<link from RIVM>' --output /tmp/nevo.sql.gz

Where the file comes from. RIVM gives the dataset away free, but only after
the conditions of use are accepted on its request form
(https://www.rivm.nl/en/dutch-food-composition-database/use-of-nevo-online/request-dataset),
which hands out the download link. So this never fetches on its own: pass the
file with --file (the CSV, or the zip it came in), or the link the form gave
with --url.

What RIVM allowed (docs/food-data-sources.md, "Replies to the permission
requests", 2026-10-27), and how this follows it:

  - Values may be converted to our format as long as the amounts don't
    change. Every number is copied as published: decimal commas become
    points, nothing is rounded, scaled or derived. Each nutrient column is
    matched on its code and its unit, and a unit that isn't the one our key
    names stops the run. Carbohydrate is NEVO's CHO, available carbohydrate
    (fibre not included), and is stored as it is.
  - Leave out "foods for special nutritional use" (infant and toddler
    formula): they are per 100 ml with no density. That food group is
    skipped, and so is any other row not given per 100 g.
  - Cite "Based on data from NEVO online version 2025/9.0, RIVM, Bilthoven".
    The app carries that sentence with the version in it, so a file of any
    other version is refused until the app's strings and CITED_VERSION move
    with it.

Rows go into `foods` as source `nevo` (tier 1, like USDA's generic tables,
from V20) with the NEVO-code as `source_id` and the release in
`source_version`. Ids are derived from the code, so a food keeps its id
across releases. The English name is the food's name, with NEVO's
abbreviations "w", "wo" and "av" written out. `foods` has no place for an
alias, so the Dutch name isn't stored. NEVO's beverage groups get the
`beverage` category (V17). NEVO has no household portions; the app weighs.

Idempotent: an upsert on (source, source_id) that only rewrites a row when
something in it changed, so a second run changes nothing. A food a later
release drops stays in the catalog, as USDA foods do.

`--psql` reads the connection string from CATALOG_DATABASE_URL when given no
value and hands it to psql through libpq environment variables, so it never
appears in a process list. Without --psql or --output it only counts.

The file is untrusted data: it is read, never executed, through size caps,
and a zip's members are checked for unsafe paths and links. Run this with
`python3 -I` so nothing in the current directory can shadow a stdlib module.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import shutil
import sys
import tempfile
import time
import uuid
import zipfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

# `-I` leaves the script's own directory off sys.path. This is the repo's
# scripts/ directory, never the download directory, so adding it back is safe.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import (  # noqa: E402
    BEVERAGE,
    ID_NAMESPACE,
    FileOutput,
    Psql,
    PsqlOutput,
    download,
    find_psql,
    log,
    pg_env,
    sql_number,
    sql_text,
    stream,
)

SOURCE = "nevo"
# The release the app's citation names (strings `search_badge_nevo`,
# `logger_source_nevo`, `foods_credit`). Loading another release without
# changing those would cite the wrong one.
CITED_VERSION = "2025/9.0"

# The 2025/9.0 CSV is about 3 MB. The caps refuse something absurd.
MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024
MAX_MEMBER_BYTES = 100 * 1024 * 1024
BATCH_SIZE = 500

VERSION = "NEVO-versie/NEVO-version"
GROUP = "Food group"
DUTCH_GROUP = "Voedingsmiddelgroep"
CODE = "NEVO-code"
DUTCH_NAME = "Voedingsmiddelnaam/Dutch food name"
NAME = "Engelse naam/Food name"
QUANTITY = "Hoeveelheid/Quantity"
REQUIRED = [VERSION, GROUP, CODE, DUTCH_NAME, NAME, QUANTITY]

# Infant and toddler formula, per 100 ml with no density. Matched on the
# English group and the Dutch one, in case either is renamed.
SPECIAL_GROUPS = {"foods for special nutritional use", "voedingsmiddelen voor bijzondere voeding"}
BEVERAGE_GROUPS = {"non-alcoholic beverages", "alcoholic beverages"}

# Our column -> (NEVO component code, the unit it must be given in).
MACROS = {
    "kcal": ("ENERCC", "kcal"),
    "protein": ("PROT", "g"),
    "carbs": ("CHO", "g"),
    "fat": ("FAT", "g"),
    "fiber": ("FIBT", "g"),
}

# foods.micros key -> (NEVO component code, unit). The keys are the USDA
# importer's MICROS, so the app reads them the same way. Where NEVO gives
# more than one form, the one matching USDA's definition is used: vitamin E
# as alpha-tocopherol (TOCPHA, not VITE's equivalents), vitamin K as
# phylloquinone (VITK1, not VITK's K1 + K2), niacin preformed (NIA, not
# NIAEQ). NEVO has no added sugars, manganese or choline.
MICROS = {
    "sugars_g": ("SUGAR", "g"),
    "saturated_fat_g": ("FASAT", "g"),
    "trans_fat_g": ("FATRS", "g"),
    "cholesterol_mg": ("CHORL", "mg"),
    "sodium_mg": ("NA", "mg"),
    "potassium_mg": ("K", "mg"),
    "calcium_mg": ("CA", "mg"),
    "iron_mg": ("FE", "mg"),
    "magnesium_mg": ("MG", "mg"),
    "phosphorus_mg": ("P", "mg"),
    "zinc_mg": ("ZN", "mg"),
    "copper_mg": ("CU", "mg"),
    "selenium_ug": ("SE", "ug"),
    "vitamin_a_rae_ug": ("VITA_RAE", "ug"),
    "vitamin_c_mg": ("VITC", "mg"),
    "vitamin_d_ug": ("VITD", "ug"),
    "vitamin_e_mg": ("TOCPHA", "mg"),
    "vitamin_k_ug": ("VITK1", "ug"),
    "thiamin_mg": ("THIA", "mg"),
    "riboflavin_mg": ("RIBF", "mg"),
    "niacin_mg": ("NIA", "mg"),
    "vitamin_b6_mg": ("VITB6", "mg"),
    "vitamin_b12_ug": ("VITB12", "ug"),
}
# Folate DFE is food folate plus 1.7 x folic acid. Working that out would be
# a derived amount, so only a food with no folic acid gets it, where DFE is
# its food folate exactly.
FOOD_FOLATE = ("FOLFD", "ug")
FOLIC_ACID = ("FOLAC", "ug")

COLUMNS = [
    "id", "source", "source_id", "name", "brand", "barcode",
    "kcal_100g", "protein_100g", "carbs_100g", "fat_100g", "fiber_100g",
    "micros", "portions", "category", "source_version",
]
UPDATE_COLUMNS = [c for c in COLUMNS if c not in ("id", "source", "source_id")]

HEADER_UNIT = re.compile(r"^\s*([A-Za-z0-9_:]+)\s*\(([^)]*)\)\s*$")
ABBREVIATIONS = {"w": "with", "wo": "without", "av": "average"}


@dataclass
class Food:
    code: str
    name: str
    version: str
    kcal: float | None = None
    protein: float | None = None
    carbs: float | None = None
    fat: float | None = None
    fiber: float | None = None
    micros: dict[str, float] = field(default_factory=dict)
    category: str | None = None


@dataclass
class Stats:
    rows: int = 0
    special: int = 0
    not_per_100g: int = 0
    no_name: int = 0
    no_nutrients: int = 0
    duplicate_code: int = 0
    unreadable_numbers: int = 0
    imported: int = 0
    folate_left_out: int = 0
    missing_micros: list[str] = field(default_factory=list)


def unit_key(raw: str) -> str:
    """'µg', 'μg', 'mcg' and 'ug' are one unit."""
    unit = raw.strip().lower().replace("µ", "u").replace("μ", "u")
    return "ug" if unit == "mcg" else unit


def column_index(header: list[str]) -> tuple[dict[str, int], dict[str, tuple[int, str]]]:
    """(plain column -> index, component code -> (index, unit)) for a NEVO header."""
    plain: dict[str, int] = {}
    components: dict[str, tuple[int, str]] = {}
    for i, name in enumerate(header):
        name = name.strip().lstrip("\ufeff")
        plain[name] = i
        match = HEADER_UNIT.match(name)
        if match:
            components[match.group(1).upper()] = (i, unit_key(match.group(2)))
    return plain, components


def component(components: dict[str, tuple[int, str]], code: str, unit: str, required: bool) -> int | None:
    """The column of `code`, checked to be in `unit`. A different unit would change every amount, so it stops the run."""
    found = components.get(code)
    if found is None:
        if required:
            raise SystemExit(f"the NEVO file has no {code} column")
        return None
    index, actual = found
    if actual != unit:
        raise SystemExit(f"NEVO gives {code} in {actual!r}, expected {unit!r}; refusing to load converted amounts")
    return index


def parse_number(raw: str | None) -> float | None:
    """NEVO's '12,3' (or '12.3'). Blank is unknown, which is not zero."""
    text = (raw or "").strip().strip('"').replace(" ", "").replace(",", ".")
    if not text:
        return None
    try:
        value = float(text)
    except ValueError:
        return None
    if value != value or value in (float("inf"), float("-inf")) or value < 0:
        return None
    return value


def english_name(raw: str) -> str:
    """'Apple w skin av' -> 'Apple with skin average'. Only whole words, never inside one."""
    words = re.sub(r"\s+", " ", raw).strip().split(" ")
    return " ".join(ABBREVIATIONS.get(w, w) if i > 0 else w for i, w in enumerate(words))


def is_per_100g(raw: str) -> bool:
    return re.sub(r"\s+", "", raw.lower()) == "per100g"


class Reader:
    """Turns NEVO rows into Foods, counting what it leaves out and why."""

    def __init__(self, header: list[str], stats: Stats):
        self.stats = stats
        plain, components = column_index(header)
        missing = [c for c in REQUIRED if c not in plain]
        if missing:
            raise SystemExit(f"the NEVO file is missing columns {missing}")
        self.plain = plain
        self.macros = {key: component(components, code, unit, required=True) for key, (code, unit) in MACROS.items()}
        self.micros = {}
        for key, (code, unit) in MICROS.items():
            index = component(components, code, unit, required=False)
            if index is None:
                stats.missing_micros.append(code)
            else:
                self.micros[key] = index
        self.food_folate = component(components, *FOOD_FOLATE, required=False)
        self.folic_acid = component(components, *FOLIC_ACID, required=False)
        self.seen: set[str] = set()

    def _cell(self, row: list[str], index: int | None) -> str:
        return row[index] if index is not None and index < len(row) else ""

    def _number(self, row: list[str], index: int | None) -> float | None:
        raw = self._cell(row, index)
        value = parse_number(raw)
        if value is None and raw.strip().strip('"'):
            self.stats.unreadable_numbers += 1
        return value

    def food(self, row: list[str]) -> Food | None:
        if not any(cell.strip() for cell in row):
            return None
        self.stats.rows += 1
        def text(column: str) -> str:
            return self._cell(row, self.plain.get(column)).strip().strip('"').strip()

        group = text(GROUP).lower()
        dutch_group = text(DUTCH_GROUP).lower()
        if group in SPECIAL_GROUPS or dutch_group in SPECIAL_GROUPS:
            self.stats.special += 1
            return None
        if not is_per_100g(text(QUANTITY)):
            self.stats.not_per_100g += 1
            return None
        name = english_name(text(NAME))[:200]
        code = text(CODE)
        if not name or not code:
            self.stats.no_name += 1
            return None
        if code in self.seen:
            self.stats.duplicate_code += 1
            return None
        self.seen.add(code)
        food = Food(code=code, name=name, version=text(VERSION))
        for key, index in self.macros.items():
            setattr(food, key, self._number(row, index))
        if food.kcal is None and food.protein is None and food.carbs is None and food.fat is None:
            self.stats.no_nutrients += 1
            return None
        for key, index in self.micros.items():
            value = self._number(row, index)
            if value is not None:
                food.micros[key] = value
        food_folate = self._number(row, self.food_folate)
        if food_folate is not None and self._number(row, self.folic_acid) == 0:
            food.micros["folate_dfe_ug"] = food_folate
        elif food_folate is not None:
            self.stats.folate_left_out += 1
        if group in BEVERAGE_GROUPS:
            food.category = BEVERAGE
        self.stats.imported += 1
        return food


def read_foods(text: io.TextIOBase, stats: Stats) -> list[Food]:
    """Every food a NEVO CSV holds that we keep, in file order."""
    csv.field_size_limit(1024 * 1024)
    rows = csv.reader(text, delimiter="|", quotechar='"')
    header = next(rows, None)
    if header is None:
        raise SystemExit("the NEVO file is empty")
    reader = Reader(header, stats)
    foods = [food for row in rows if (food := reader.food(row)) is not None]
    versions = {food.version for food in foods}
    if len(versions) != 1:
        raise SystemExit(f"expected one NEVO release in the file, found {sorted(versions)}")
    (version,) = versions
    if version != CITED_VERSION:
        raise SystemExit(
            f"this is NEVO {version}, but the app cites {CITED_VERSION}. Update the citation strings and "
            "CITED_VERSION together, then load it."
        )
    return foods


def decode(data: bytes) -> str:
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        log("  not UTF-8, reading it as Windows-1252")
        return data.decode("cp1252")


def is_nevo_table(text: str) -> bool:
    first = text.split("\n", 1)[0]
    return CODE in first and NAME in first and "ENERCC" in first


def csv_text(path: Path) -> str:
    """The main NEVO table from a CSV, or from the zip RIVM ships it in."""
    if path.stat().st_size > MAX_MEMBER_BYTES:
        raise SystemExit(f"{path.name} is larger than {MAX_MEMBER_BYTES} bytes, refusing")
    if not zipfile.is_zipfile(path):
        text = decode(path.read_bytes())
        if not is_nevo_table(text):
            raise SystemExit(f"{path.name} doesn't look like the NEVO table (no NEVO-code, food name and ENERCC columns)")
        return text
    found = []
    with zipfile.ZipFile(path) as zf:
        for info in zf.infolist():
            member = PurePosixPath(info.filename)
            if member.is_absolute() or ".." in member.parts or "\\" in info.filename:
                raise SystemExit(f"unsafe path in archive: {info.filename!r}")
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise SystemExit(f"symlink in archive: {info.filename!r}")
            if info.is_dir() or member.suffix.lower() not in (".csv", ".txt"):
                continue
            if info.file_size > MAX_MEMBER_BYTES:
                raise SystemExit(f"{info.filename!r} is {info.file_size} bytes uncompressed, refusing")
            with zf.open(info) as member_file:
                data = member_file.read(MAX_MEMBER_BYTES + 1)
            if len(data) > MAX_MEMBER_BYTES:
                raise SystemExit(f"{info.filename!r} expanded past {MAX_MEMBER_BYTES} bytes")
            text = decode(data)
            if is_nevo_table(text):
                found.append((info.filename, text))
    if len(found) != 1:
        names = [name for name, _ in found]
        raise SystemExit(f"expected one NEVO table in {path.name}, found {names or 'none'}")
    log(f"  reading {found[0][0]}")
    return found[0][1]


def food_id(code: str) -> uuid.UUID:
    return uuid.uuid5(ID_NAMESPACE, f"{SOURCE}:{code}")


def sql_row(food: Food) -> str:
    micros = json.dumps(food.micros, separators=(",", ":"), sort_keys=True)
    return "(" + ",".join([
        f"'{food_id(food.code)}'::uuid",
        sql_text(SOURCE),
        sql_text(food.code),
        sql_text(food.name),
        "NULL",
        "NULL",
        sql_number(food.kcal),
        sql_number(food.protein),
        sql_number(food.carbs),
        sql_number(food.fat),
        sql_number(food.fiber),
        sql_text(micros) + "::jsonb",
        "'[]'::jsonb",
        sql_text(food.category) if food.category else "NULL",
        sql_text(food.version),
    ]) + ")"


def upsert_sql(foods: list[Food]):
    """(sql, rows) chunks: batched upserts that leave an unchanged row alone."""
    update = ", ".join(f"{c} = EXCLUDED.{c}" for c in UPDATE_COLUMNS)
    # Postgres writes a new copy of every row an upsert updates, changed or
    # not; this keeps a re-run from touching any.
    changed = (
        "(" + ", ".join(f"foods.{c}" for c in UPDATE_COLUMNS) + ") IS DISTINCT FROM ("
        + ", ".join(f"EXCLUDED.{c}" for c in UPDATE_COLUMNS) + ")"
    )
    yield "-- Generated by scripts/import_nevo_foods.py. Based on data from NEVO online version " \
          f"{CITED_VERSION}, RIVM, Bilthoven.\n", 0
    for start in range(0, len(foods), BATCH_SIZE):
        batch = foods[start:start + BATCH_SIZE]
        yield (
            f"INSERT INTO foods ({', '.join(COLUMNS)}) VALUES\n"
            + ",\n".join(sql_row(f) for f in batch)
            + f"\nON CONFLICT (source, source_id) DO UPDATE SET {update}\nWHERE {changed};\n"
        ), len(batch)


def summary_lines(stats: Stats, origin: str, foods: list[Food]) -> list[str]:
    beverages = sum(1 for f in foods if f.category == BEVERAGE)
    lines = [
        "### NEVO import", "", f"File: {origin}", "",
        "| | count |", "|---|---:|",
        f"| foods in the file | {stats.rows:,} |",
        f"| left out: foods for special nutritional use | {stats.special:,} |",
        f"| left out: other foods not per 100 g | {stats.not_per_100g:,} |",
        f"| left out: no name or code | {stats.no_name:,} |",
        f"| left out: repeated NEVO-code | {stats.duplicate_code:,} |",
        f"| left out: no energy or macros | {stats.no_nutrients:,} |",
        f"| **imported** | **{stats.imported:,}** |",
        f"| of those, beverages | {beverages:,} |",
        f"| folate left out (folic acid added or unknown) | {stats.folate_left_out:,} |",
        f"| cells that weren't numbers (stored as unknown) | {stats.unreadable_numbers:,} |",
    ]
    if stats.missing_micros:
        lines += ["", f"Columns not in this file: {', '.join(stats.missing_micros)}."]
    return lines


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--file", type=Path, help="the NEVO CSV, or the zip RIVM ships it in")
    source.add_argument("--url", help="the download link RIVM's request form gives")
    parser.add_argument("--output", type=Path, help="write the gzipped upsert SQL here instead of loading it")
    parser.add_argument(
        "--psql",
        nargs="?",
        const="",
        metavar="CONNINFO",
        help="stream the upsert into this database; with no value, read the connection string from CATALOG_DATABASE_URL",
    )
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--summary", type=Path, help="append a markdown summary here (e.g. $GITHUB_STEP_SUMMARY)")
    args = parser.parse_args()
    if args.output and args.psql is not None:
        parser.error("--output and --psql are separate runs")

    db: Psql | None = None
    if args.psql is not None:
        conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
        if not conninfo:
            raise SystemExit("--psql needs a connection string, or CATALOG_DATABASE_URL set")
        db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
        ready = db.query(
            "SELECT (SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' "
            "AND table_name = 'foods' AND column_name IN ('tier', 'category', 'source_version')), "
            "public.food_source_tier('nevo')"
        )
        if ready != [["3", "1"]]:
            raise SystemExit("this database doesn't take NEVO rows yet: run the server migrations (V20) against it first")

    started = time.monotonic()
    stats = Stats()
    workdir = Path(tempfile.mkdtemp(prefix="opson-nevo-"))
    try:
        if args.file:
            path, origin = args.file, f"local file {args.file.name}"
        else:
            path, origin = workdir / "nevo-download", "the link from RIVM's request form"
            download(args.url, path, MAX_DOWNLOAD_BYTES)
        foods = read_foods(io.StringIO(csv_text(path), newline=""), stats)
    finally:
        shutil.rmtree(workdir, ignore_errors=True)

    if db is not None or args.output:
        out = PsqlOutput(db) if db is not None else FileOutput(args.output)
        stream(upsert_sql(foods), out, len(foods))
    lines = summary_lines(stats, origin, foods)
    if db is not None:
        counts = db.query(f"SELECT count(*) FROM foods WHERE source = {sql_text(SOURCE)}")
        lines += ["", f"`foods` now holds {int(counts[0][0]):,} NEVO rows."]
    elif not args.output:
        lines += ["", "Counted only: pass --psql or --output to write anything."]
    lines += ["", f"Took {time.monotonic() - started:.0f}s."]
    text = "\n".join(lines) + "\n"
    log(text)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as f:
            f.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
