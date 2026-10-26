#!/usr/bin/env python3
"""Build the Opson food catalog seed from USDA FoodData Central.

Downloads the Foundation, SR Legacy and FNDDS (survey) CSV releases, maps each
food to the `foods` table (nutrients per 100 g, household portions), dedupes
names across the three datasets and writes a gzipped SQL file of batched,
idempotent upserts:

    INSERT INTO foods (...) VALUES (...), ... ON CONFLICT (source, source_id) DO UPDATE ...

Standard library only. Run it with `python3 -I` so nothing in the current
directory can shadow a stdlib module:

    python3 -I scripts/import_usda_foods.py
    python3 -I scripts/import_usda_foods.py --output /tmp/usda_foods.sql.gz

Apply the result to a database (it is safe to run again):

    gunzip -c apps/server/fooddb/usda_foods.sql.gz | psql "$DATABASE_URL" --single-transaction -v ON_ERROR_STOP=1

`--branded` imports FoodData Central Branded Foods instead: about 440,000
packaged US products with a barcode, the nutrition label per 100 g and the
label's serving. It is too big to commit, so it is written to the gitignored
`apps/server/fooddb/usda_branded.sql.gz`, or streamed straight into a database
with `--psql`, which reads the connection string from `CATALOG_DATABASE_URL`
when no value is given (so it never shows up in a process list):

    python3 -I scripts/import_usda_foods.py --branded
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/import_usda_foods.py --branded --psql

Branded rows are keyed on the barcode, not on the FDC id, because FDC issues a
new id every time a manufacturer resubmits a product: re-importing a newer
release refreshes the product in place and keeps the food id devices hold.

The downloads are treated as untrusted data. They go into a fresh temporary
directory, only the CSV files the importer needs are read, every archive
member is checked for path traversal, links and size, and nothing from the
archives is ever executed. The directory is deleted when the run ends.

Every row gets a `category` (restaurant, beverage, baby_food, supplement or
none) from USDA's own: `food_category` for Foundation and SR Legacy, the WWEIA
category for FNDDS, `branded_food_category` for Branded. The database needs
migration V17 first. scripts/backfill_food_categories.py fills it in for rows
loaded before it existed.

USDA FoodData Central data is public domain (CC0).
"""

from __future__ import annotations

import argparse
import csv
import gzip
import io
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid
import zipfile
from array import array
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath

USER_AGENT = "Opson/0.1 (food logger; USDA catalog import)"

# (source column value, release url, FDC data_type rows to keep)
DATASETS = [
    (
        "usda_foundation",
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_csv_2025-04-24.zip",
        "foundation_food",
    ),
    (
        "usda_fndds",
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_survey_food_csv_2024-10-31.zip",
        "survey_fndds_food",
    ),
    (
        "usda_sr",
        "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip",
        "sr_legacy_food",
    ),
]

# When the same food appears in more than one dataset, keep the earliest one in
# this order. Foundation has the best analytical data, FNDDS has the household
# portions people actually log with, SR Legacy fills the long tail.
SOURCE_PRIORITY = {"usda_foundation": 0, "usda_fndds": 1, "usda_sr": 2}

NEEDED_FILES = {"food.csv", "food_nutrient.csv", "food_portion.csv", "measure_unit.csv", "nutrient.csv"}

MAX_DOWNLOAD_BYTES = 200 * 1024 * 1024
MAX_MEMBER_BYTES = 600 * 1024 * 1024
BATCH_SIZE = 500
MAX_PORTIONS = 12

# Stable UUIDs: the same USDA food gets the same id on every run and in every
# environment, so a food id cached on a device stays valid across reloads.
ID_NAMESPACE = uuid.UUID("6f1c1a52-5b8e-4f0e-9b7a-0a5d3c8e2f41")

# Nutrients are matched on the legacy SR nutrient number (`nutrient_nbr`).
# Foundation and SR Legacy reference nutrients by FDC id, FNDDS by number; the
# importer converts ids to numbers through each release's nutrient.csv.
ENERGY_KCAL = "208"
ENERGY_ATWATER_SPECIFIC = "958"
ENERGY_ATWATER_GENERAL = "957"
ENERGY_KJ = "268"
PROTEIN = "203"
FAT = "204"
FAT_NLEA = "298"
CARBS = "205"
CARBS_SUMMATION = "205.2"
FIBER = "291"

# micros key -> nutrient number. The key carries its unit so the client never
# has to guess. Amounts are per 100 g, in that unit.
MICROS = {
    "sugars_g": "269",
    "added_sugars_g": "539",
    "saturated_fat_g": "606",
    "cholesterol_mg": "601",
    "sodium_mg": "307",
    "potassium_mg": "306",
    "calcium_mg": "301",
    "iron_mg": "303",
    "magnesium_mg": "304",
    "phosphorus_mg": "305",
    "zinc_mg": "309",
    "copper_mg": "312",
    "manganese_mg": "315",
    "selenium_ug": "317",
    "vitamin_a_rae_ug": "320",
    "vitamin_c_mg": "401",
    "vitamin_d_ug": "328",
    "vitamin_e_mg": "323",
    "vitamin_k_ug": "430",
    "thiamin_mg": "404",
    "riboflavin_mg": "405",
    "niacin_mg": "406",
    "vitamin_b6_mg": "415",
    "folate_dfe_ug": "435",
    "vitamin_b12_ug": "418",
    "choline_mg": "421",
}
SUGARS_NLEA = "269.3"

COLUMNS = [
    "id", "source", "source_id", "name", "brand", "barcode",
    "kcal_100g", "protein_100g", "carbs_100g", "fat_100g", "fiber_100g",
    "micros", "portions", "category",
]
UPDATE_COLUMNS = [c for c in COLUMNS if c not in ("id", "source", "source_id")]


@dataclass
class Food:
    source: str
    source_id: str
    name: str
    kcal: float | None = None
    protein: float | None = None
    carbs: float | None = None
    fat: float | None = None
    fiber: float | None = None
    micros: dict[str, float] = field(default_factory=dict)
    portions: list[dict] = field(default_factory=list)
    category: str | None = None


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def download(url: str, dest: Path, limit: int = MAX_DOWNLOAD_BYTES) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    total = 0
    with urllib.request.urlopen(request, timeout=120) as response, open(dest, "wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise RuntimeError(f"{url} is larger than {limit} bytes, refusing")
            out.write(chunk)
    log(f"  downloaded {total / 1e6:.1f} MB")


def safe_extract(archive: Path, dest: Path) -> dict[str, Path]:
    """Extract only the CSVs we need, flattened into `dest`. Returns name -> path."""
    found: dict[str, Path] = {}
    with zipfile.ZipFile(archive) as zf:
        for info in zf.infolist():
            member = PurePosixPath(info.filename)
            if member.is_absolute() or ".." in member.parts or "\\" in info.filename:
                raise RuntimeError(f"unsafe path in archive: {info.filename!r}")
            # Unix mode bits live in the high 16 bits; refuse symlinks outright.
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise RuntimeError(f"symlink in archive: {info.filename!r}")
            if info.is_dir() or member.name not in NEEDED_FILES:
                continue
            if info.file_size > MAX_MEMBER_BYTES:
                raise RuntimeError(f"{info.filename!r} is {info.file_size} bytes uncompressed, refusing")
            target = dest / member.name
            written = 0
            with zf.open(info) as src, open(target, "wb") as out:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk:
                        break
                    written += len(chunk)
                    if written > MAX_MEMBER_BYTES:
                        raise RuntimeError(f"{info.filename!r} expanded past {MAX_MEMBER_BYTES} bytes")
                    out.write(chunk)
            found[member.name] = target
    missing = NEEDED_FILES - found.keys()
    if missing:
        raise RuntimeError(f"{archive.name} is missing {sorted(missing)}")
    return found


def read_csv(path: Path):
    with open(path, newline="", encoding="utf-8") as f:
        yield from csv.DictReader(f)


def to_float(raw: str | None) -> float | None:
    if raw is None or raw.strip() == "":
        return None
    try:
        value = float(raw)
    except ValueError:
        return None
    if value != value or value in (float("inf"), float("-inf")):
        return None
    return value


def normalize_nbr(raw: str) -> str:
    raw = raw.strip()
    return raw[:-2] if raw.endswith(".0") else raw


def rounded(value: float | None, digits: int = 2) -> float | None:
    if value is None:
        return None
    value = round(max(value, 0.0), digits)
    return 0.0 if value == 0 else value


def number_text(value: float) -> str:
    return f"{value:g}"


def portion_label(row: dict, units: dict[str, str], source: str) -> str | None:
    description = (row.get("portion_description") or "").strip()
    if description and description.lower() != "quantity not specified":
        return description
    if source == "usda_fndds":
        # FNDDS puts a numeric code in `modifier`; without a description there is no label.
        return None
    amount = to_float(row.get("amount")) or 1.0
    modifier = (row.get("modifier") or "").strip()
    unit = units.get((row.get("measure_unit_id") or "").strip(), "")
    if unit and unit != "undetermined":
        label = f"{number_text(amount)} {unit}"
        if modifier:
            label += f", {modifier}"
        return label
    if modifier:
        return f"{number_text(amount)} {modifier}"
    return None


def load_dataset(source: str, files: dict[str, Path], data_type: str) -> dict[str, Food]:
    foods: dict[str, Food] = {}
    for row in read_csv(files["food.csv"]):
        if row["data_type"] != data_type:
            continue
        name = re.sub(r"\s+", " ", row["description"]).strip()
        if name:
            foods[row["fdc_id"]] = Food(
                source=source,
                source_id=row["fdc_id"],
                name=name[:200],
                category=generic_category(source, row.get("food_category_id", ""), name),
            )

    id_to_nbr = {row["id"]: normalize_nbr(row["nutrient_nbr"]) for row in read_csv(files["nutrient.csv"])}

    values: dict[str, dict[str, float]] = {}
    for row in read_csv(files["food_nutrient.csv"]):
        fdc_id = row["fdc_id"]
        if fdc_id not in foods:
            continue
        amount = to_float(row["amount"])
        if amount is None:
            continue
        raw = row["nutrient_id"].strip()
        nbr = id_to_nbr.get(raw, normalize_nbr(raw))
        values.setdefault(fdc_id, {})[nbr] = amount

    for fdc_id, food in foods.items():
        v = values.get(fdc_id, {})
        food.protein = v.get(PROTEIN)
        food.fat = v.get(FAT, v.get(FAT_NLEA))
        food.carbs = v.get(CARBS, v.get(CARBS_SUMMATION))
        food.fiber = v.get(FIBER)
        kcal = v.get(ENERGY_KCAL, v.get(ENERGY_ATWATER_SPECIFIC, v.get(ENERGY_ATWATER_GENERAL)))
        if kcal is None and ENERGY_KJ in v:
            kcal = v[ENERGY_KJ] / 4.184
        if kcal is None and None not in (food.protein, food.fat, food.carbs):
            kcal = 4 * food.protein + 4 * food.carbs + 9 * food.fat
        food.kcal = kcal
        micros = {}
        for key, nbr in MICROS.items():
            amount = v.get(nbr)
            if amount is None and key == "sugars_g":
                amount = v.get(SUGARS_NLEA)
            if amount is not None:
                micros[key] = rounded(amount, 3)
        food.micros = micros

    units = {row["id"]: row["name"].strip() for row in read_csv(files["measure_unit.csv"])}
    portion_rows: dict[str, list[tuple[float, dict]]] = {}
    for row in read_csv(files["food_portion.csv"]):
        if row["fdc_id"] not in foods:
            continue
        grams = to_float(row.get("gram_weight"))
        if grams is None or grams <= 0:
            continue
        label = portion_label(row, units, source)
        if not label:
            continue
        seq = to_float(row.get("seq_num")) or 0.0
        portion_rows.setdefault(row["fdc_id"], []).append(
            (seq, {"label": label[:80], "grams": rounded(grams, 1)})
        )
    for fdc_id, rows in portion_rows.items():
        rows.sort(key=lambda pair: pair[0])
        foods[fdc_id].portions = dedupe_portions([p for _, p in rows])

    # A food with no energy and no macros is of no use to a logger.
    kept = {
        k: f for k, f in foods.items()
        if not (f.kcal is None and f.protein is None and f.carbs is None and f.fat is None)
    }
    log(f"  {source}: {len(foods)} foods, {len(kept)} with energy or macros")
    return kept


def dedupe_portions(portions: list[dict]) -> list[dict]:
    seen: set[str] = set()
    result = []
    for portion in portions:
        key = portion["label"].lower()
        if key in seen:
            continue
        seen.add(key)
        result.append(portion)
        if len(result) >= MAX_PORTIONS:
            break
    return result


# The app logs a food's first portion unless the user picks another, so the
# first portion is the default. FDC lists portions in its own order, which for
# small things starts with one of them: "1 berry" (2 g, 1 kcal), "1 grape",
# "1 nut", "1 fl oz" of coffee. For dense foods it can start with a cup of
# almonds or butter (800 to 1,600 kcal). default_portion_first moves a typical
# serving to the front in those two cases and leaves every other list alone.
MIN_DEFAULT_GRAMS = 20.0
TINY_KCAL = 40.0
BULK_KCAL = 500.0
# Measures that are small on purpose: a teaspoon of sugar, a pat of butter, a
# slice of bacon, a packet. Never a crumb, whatever they weigh.
SMALL_ON_PURPOSE = re.compile(r"\b(tsp|teaspoons?|tbsp|tablespoons?|pat|slices?|packets?)\b", re.IGNORECASE)
SIP = re.compile(r"^1 fl\.? ?oz\b", re.IGNORECASE)
GUIDELINE = re.compile(r"^guideline\b", re.IGNORECASE)
BULK = re.compile(r"^1 (cups?|pints?|quarts?|lbs?|pounds?)\b", re.IGNORECASE)
# What a person logs, best first. A portion matching none of these ranks last.
TYPICAL = [
    re.compile(r"^1 (oz|ounce)\b", re.IGNORECASE),
    re.compile(r"^1 (cup|can|bottle|carton|container|package|packet|pouch|bar|bowl|serving|drink|glass|mug|order)\b"
               r"|\bsingle serving\b|^1 individual\b", re.IGNORECASE),
    re.compile(r"\b(medium|regular)\b", re.IGNORECASE),
    re.compile(r"\bslices?\b", re.IGNORECASE),
    re.compile(r"\bnlea serving\b", re.IGNORECASE),
    re.compile(r"^1 (tbsp|tablespoon|tsp|teaspoon)\b", re.IGNORECASE),
]


def default_portion_first(portions: list[dict], kcal_100g: float | None) -> list[dict]:
    """`portions` with a sensible default first; the others keep their order.

    The first portion stays unless it is a crumb (under MIN_DEFAULT_GRAMS and
    TINY_KCAL, and not a spoon, slice or packet), a sip (1 fl oz), a USDA
    "Guideline amount", or a cup or more of something dense (over BULK_KCAL).
    Then the first portion that is none of those wins, by TYPICAL; failing
    that, for a crumb, any portion that isn't one. Applying it twice changes
    nothing, which is what lets the backfill re-run.
    """
    if len(portions) < 2:
        return portions

    def kcal(p: dict) -> float | None:
        grams = p.get("grams")
        return None if grams is None or kcal_100g is None else grams * kcal_100g / 100

    def crumb(p: dict) -> bool:
        grams, label = p.get("grams"), p["label"]
        if SIP.match(label):
            return True
        if grams is None or grams >= MIN_DEFAULT_GRAMS or SMALL_ON_PURPOSE.search(label):
            return False
        energy = kcal(p)
        return energy is None or energy < TINY_KCAL

    def bulk(p: dict) -> bool:
        energy = kcal(p)
        return energy is not None and energy > BULK_KCAL and bool(BULK.match(p["label"]))

    def usable(p: dict) -> bool:
        return not GUIDELINE.match(p["label"]) and not crumb(p) and not bulk(p)

    def rank(p: dict) -> int:
        return next((i for i, pattern in enumerate(TYPICAL) if pattern.search(p["label"])), len(TYPICAL))

    first = portions[0]
    if usable(first):
        return portions
    candidates = [p for p in portions[1:] if usable(p)]
    if not candidates and (crumb(first) or GUIDELINE.match(first["label"])):
        # Better a big default than a crumb.
        candidates = [p for p in portions[1:] if not GUIDELINE.match(p["label"]) and not crumb(p)]
    if not candidates:
        return portions
    best = min(candidates, key=rank)  # min keeps the first of equals, so FDC's order breaks ties
    return [best] + [p for p in portions if p is not best]


# ─── Categories ─────────────────────────────────────────────────────────────
#
# foods.category (V17): a small normalised set the Logger's tabs filter on.
# Restaurant and beverage are what the tabs need; baby food and supplements
# are the two kinds that most often crowd a search, and cost one line each.

RESTAURANT = "restaurant"
BEVERAGE = "beverage"
BABY_FOOD = "baby_food"
SUPPLEMENT = "supplement"

# SR Legacy and Foundation share one food_category table (food_category.csv, by id).
SR_CATEGORIES = {
    "21": RESTAURANT,  # Fast Foods
    "25": RESTAURANT,  # Restaurant Foods
    "14": BEVERAGE,  # Beverages
    "28": BEVERAGE,  # Alcoholic Beverages
    "3": BABY_FOOD,  # Baby Foods
}
SR_FRUITS = "9"  # Fruits and Fruit Juices
SR_DAIRY = "1"  # Dairy and Egg Products
NOT_A_DRINK = re.compile(r"\b(dry|dried|powder|powdered|condensed|evaporated)\b")

# FNDDS uses What We Eat in America categories (wweia_food_category.csv).
WWEIA_MILK = {"1002", "1004", "1006", "1008", "1202", "1204", "1206", "1208", "1402", "1902"}
WWEIA_BABY = {"9002", "9004", "9006", "9007", "9008", "9010", "9012", "9202", "9204", "9402", "9404", "9602"}
WWEIA_SUPPLEMENT = {"9802"}
FROM_RESTAURANT = re.compile(r"\b(fast food|restaurant)\b")


def generic_category(source: str, category_id: str, name: str) -> str | None:
    """foods.category for a Foundation, SR Legacy or FNDDS food, from its own category and name."""
    category_id = (category_id or "").strip()
    lowered = name.lower()
    if source == "usda_fndds":
        if FROM_RESTAURANT.search(lowered):
            return RESTAURANT
        if (category_id.isdigit() and 7000 <= int(category_id) < 8000) or category_id in WWEIA_MILK:
            return BEVERAGE
        if category_id in WWEIA_BABY:
            return BABY_FOOD
        if category_id in WWEIA_SUPPLEMENT:
            return SUPPLEMENT
        return None
    if category_id in SR_CATEGORIES:
        return SR_CATEGORIES[category_id]
    if category_id == SR_FRUITS and "juice" in lowered:
        return BEVERAGE
    if category_id == SR_DAIRY and lowered.startswith("milk,") and not NOT_A_DRINK.search(lowered):
        return BEVERAGE
    return None


# Branded Foods' branded_food_category, as normalised by _category_key. The
# release spells one category several ways ("Non Alcoholic Beverages - Ready
# to Drink", "...  Ready to Drink", "... – Ready to Drink").
BRANDED_BEVERAGES = {
    "fruit & vegetable juice, nectars & fruit drinks", "soda", "water", "packaged water", "milk", "plant based milk",
    "milk/milk substitutes", "other drinks", "powdered drinks", "energy, protein & muscle recovery drinks",
    "iced & bottle tea", "non alcoholic beverages ready to drink", "non alcoholic beverages not ready to drink",
    "tea bags", "tea and infusions/tisanes", "tea bags, loose leaf, speciality", "coffee", "coffee/tea/substitutes",
    "coffee/coffee substitutes", "coffee instant, roast and ground", "alcohol", "alcoholic beverages", "beer",
    "sport drinks", "liquid water enhancer", "plant based water", "breakfast drinks", "drinks flavoured ready to drink",
    "drinks juices, drinks and cordials", "drinks soft drinks", "drinks", "drinks powdered", "drinks energy drinks",
    "ready to drink", "fruit juice ready to drink (shelf stable)", "fruit juice not ready to drink (frozen)",
}
BRANDED_BABY = {"baby/infant foods/beverages", "infant formula"}
BRANDED_SUPPLEMENTS = {
    "specialty formula supplements", "meal replacement supplements", "green supplements",
    "digestive & fiber supplements", "herbal supplements", "children's nutritional supplements", "vitamins",
    "fatty acid supplements", "health supplements and vitamins", "antioxidant supplements",
    "vitamins/minerals/nutritional supplements", "amino acid supplements", "ayurvedic supplements",
    "nutritional supplements",
}


def _category_key(raw: str) -> str:
    return " ".join(raw.lower().replace("\u2013", " ").replace("-", " ").split())


def branded_category(raw: str | None) -> str | None:
    """foods.category for a Branded product, from its branded_food_category."""
    key = _category_key(raw or "")
    if key in BRANDED_BEVERAGES:
        return BEVERAGE
    if key in BRANDED_BABY:
        return BABY_FOOD
    if key in BRANDED_SUPPLEMENTS:
        return SUPPLEMENT
    return None


def dedupe_key(name: str) -> str:
    """'Bananas, raw' and 'Banana, raw' are the same food to a person searching."""
    words = re.findall(r"[a-z0-9]+", name.lower())
    singular = [w[:-1] if len(w) > 3 and w.endswith("s") and not w.endswith("ss") else w for w in words]
    return " ".join(singular)


def dedupe(all_foods: list[Food]) -> list[Food]:
    best: dict[str, Food] = {}
    for food in sorted(all_foods, key=lambda f: (SOURCE_PRIORITY[f.source], f.source_id)):
        key = dedupe_key(food.name)
        kept = best.get(key)
        if kept is None:
            best[key] = food
            continue
        # The duplicate loses, but its portions (FNDDS and SR carry the household
        # measures Foundation lacks) are still worth keeping.
        kept.portions = dedupe_portions(kept.portions + food.portions)
        kept.category = kept.category or food.category
    return sorted(best.values(), key=lambda f: (SOURCE_PRIORITY[f.source], f.name.lower(), f.source_id))


def sql_text(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_number(value: float | None) -> str:
    return "NULL" if value is None else repr(float(value))


def sql_row(food: Food) -> str:
    food_id = uuid.uuid5(ID_NAMESPACE, f"{food.source}:{food.source_id}")
    micros = json.dumps(food.micros, separators=(",", ":"), sort_keys=True)
    portions = json.dumps(food.portions, separators=(",", ":"), ensure_ascii=False)
    parts = [
        f"'{food_id}'::uuid",
        sql_text(food.source),
        sql_text(food.source_id),
        sql_text(food.name),
        "NULL",
        "NULL",
        sql_number(rounded(food.kcal, 1)),
        sql_number(rounded(food.protein)),
        sql_number(rounded(food.carbs)),
        sql_number(rounded(food.fat)),
        sql_number(rounded(food.fiber)),
        sql_text(micros) + "::jsonb",
        sql_text(portions) + "::jsonb",
        sql_text(food.category) if food.category else "NULL",
    ]
    return "(" + ",".join(parts) + ")"


def generic_sql(foods: list[Food]):
    """The generic seed as (sql, rows) chunks. Shared by the file and `--psql` outputs."""
    update = ", ".join(f"{c} = EXCLUDED.{c}" for c in UPDATE_COLUMNS)
    yield "-- Generated by scripts/import_usda_foods.py from USDA FoodData Central (public domain).\n", 0
    yield "-- Idempotent: re-running updates rows in place and never touches popularity.\n", 0
    yield f"-- {len(foods)} foods.\n", 0
    for start in range(0, len(foods), BATCH_SIZE):
        batch = foods[start:start + BATCH_SIZE]
        yield (
            f"INSERT INTO foods ({', '.join(COLUMNS)}) VALUES\n"
            + ",\n".join(sql_row(f) for f in batch)
            + f"\nON CONFLICT (source, source_id) DO UPDATE SET {update};\n"
        ), len(batch)


# ─── Branded Foods ──────────────────────────────────────────────────────────

BRANDED_SOURCE = "usda_branded"
DOWNLOAD_PAGE = "https://fdc.nal.usda.gov/download-datasets"
DATASETS_PREFIX = "https://fdc.nal.usda.gov/fdc-datasets/"
BRANDED_RELEASE = re.compile(r"FoodData_Central_branded_food_csv_(\d{4}-\d{2}-\d{2})\.zip")
# Used when the download page cannot be read or lists no CSV release. The
# monthly refresh normally finds a newer one on the page.
BRANDED_FALLBACK_URL = DATASETS_PREFIX + "FoodData_Central_branded_food_csv_2026-04-30.zip"

# The 2026-04 release is 450 MB compressed and 3 GB of CSV. The members are
# read straight out of the archive, never extracted, so the caps are about
# refusing something absurd rather than about disk.
BRANDED_MAX_DOWNLOAD_BYTES = 2 * 1024 ** 3
BRANDED_MAX_MEMBER_BYTES = 8 * 1024 ** 3
# CSV compresses about 8:1. Far past that is a zip bomb, not a food table.
MAX_COMPRESSION_RATIO = 60
BRANDED_FILES = {"branded_food.csv", "food.csv", "food_nutrient.csv", "nutrient.csv"}

# Labels also carry trans fat. Vitamin D is often given in IU (40 IU = 1 µg).
BRANDED_MICROS = {**MICROS, "trans_fat_g": "605"}
VITAMIN_D_IU = "324"

# Values no label can have per 100 g: almost always a per-serving number typed
# into the per-100 g field. A wrong food is worse than a missing one.
MAX_KCAL_100G = 950.0
MAX_MACROS_100G = 105.0

BRANDED_UPDATE_COLUMNS = ["source", "source_id"] + UPDATE_COLUMNS

GRAM_UNITS = {"g", "gm", "grm"}
ML_UNITS = {"ml", "mlt"}
UNHELPFUL_SERVING = re.compile(r"^((amount )?per serving|serving( size)?|n/?a|none|)$")
MEASURE_ONLY = re.compile(r"^\d+(\.\d+)?\s*(g|gm|grm|grams?|ml|mlt)\.?$")
MENTIONS_MEASURE = re.compile(r"\d\s*(g|gm|grams?|ml)\b")

# Words that stay lowercase inside a title ("Soup with Rice").
SMALL_WORDS = {"a", "an", "and", "as", "at", "by", "for", "from", "in", "n", "of", "on", "or", "the", "to", "with"}
# Unit spellings after a number ("12 FL OZ" -> "12 fl oz").
UNITS = {
    "OZ": "oz", "FL": "fl", "LB": "lb", "LBS": "lbs", "G": "g", "GR": "g", "MG": "mg", "MCG": "mcg", "KG": "kg",
    "ML": "ml", "L": "L", "LT": "L", "GAL": "gal", "QT": "qt", "PT": "pt", "CT": "ct", "PK": "pk", "PC": "pc",
    "PCS": "pcs", "CNT": "ct",
}
ABBREVIATIONS = {
    "BBQ", "BLT", "CBD", "DHA", "EPA", "GMO", "II", "III", "IPA", "IV", "MCT", "MSG", "NY", "NYC", "OJ", "PB",
    "PB&J", "TV", "UHT", "UK", "US", "USA", "USDA", "VI", "XL", "XXL",
}
HONORIFICS = {"MR": "Mr", "MRS": "Mrs", "MS": "Ms", "DR": "Dr", "ST": "St", "JR": "Jr", "SR": "Sr"}
NUMBER = re.compile(r"^[\d.,/]+$")
EDGES = re.compile(r"^([^A-Za-z0-9]*)(.*?)([^A-Za-z0-9]*)$")


def title_case(text: str) -> str:
    """Make an ALL CAPS label readable: "CAMPBELL'S CHUNKY SOUP W/ BEEF 18.8 OZ" -> "Campbell's Chunky Soup w/ Beef 18.8 oz".

    Text that already has lowercase letters was cased by a person and is left
    alone. Units after a number go lowercase, known abbreviations and short
    vowelless tokens (BBQ, PB, CHKN) stay capitals, small joining words go
    lowercase except at the start.
    """
    text = re.sub(r"\s+", " ", text).strip()
    if not text or any(c.islower() for c in text):
        return text
    out: list[str] = []
    previous = ""
    for index, token in enumerate(text.split(" ")):
        if token.upper() == "W/":
            out.append("w/")
            previous = token
            continue
        parts = re.split(r"([-/])", token)
        cased = []
        for part_index, part in enumerate(parts):
            if part in ("-", "/"):
                cased.append(part)
                continue
            first = index == 0 and part_index == 0
            after_number = part_index == 0 and (NUMBER.match(previous) is not None or previous.upper() in UNITS)
            cased.append(_case_word(part, first, after_number))
        out.append("".join(cased))
        previous = token
    return " ".join(out)


def _case_word(part: str, first: bool, after_number: bool) -> str:
    match = EDGES.match(part)
    lead, core, tail = match.group(1), match.group(2), match.group(3)
    if not core:
        return part
    upper = core.upper()
    if core[0].isdigit():
        cased = core.lower()  # 12OZ, 1ST, 2PK
    elif after_number and upper in UNITS:
        cased = UNITS[upper]
    elif upper in ABBREVIATIONS:
        cased = upper
    elif upper in HONORIFICS:
        cased = HONORIFICS[upper]
    elif upper.startswith("MC") and len(core) > 3 and core[2].isalpha():
        cased = "Mc" + core[2].upper() + core[3:].lower()
    elif re.fullmatch(r"[A-Z]'[A-Z]{2,}", upper):
        cased = upper[0] + "'" + upper[2] + upper[3:].lower()  # O'BRIEN -> O'Brien
    elif re.fullmatch(r"[B-DF-HJ-NP-TV-XZ]{2,4}", upper):
        cased = upper  # vowelless: BBQ, PB, CHKN
    elif not first and core.lower() in SMALL_WORDS:
        cased = core.lower()
    else:
        cased = core[0].upper() + core[1:].lower()
    return lead + cased + tail


# No assigned GTIN is mostly zeros. "00000000000000" and friends are
# placeholders that many unrelated products share.
MIN_SIGNIFICANT_DIGITS = 7


def gtin_check_digit(body: str) -> int:
    """GS1 check digit for the digits before it. Leading zeros do not change it."""
    total = sum(int(d) * (3 if i % 2 == 0 else 1) for i, d in enumerate(reversed(body)))
    return (10 - total % 10) % 10


def upc_e_to_upc_a(code: str) -> str | None:
    """Expand an 8-digit UPC-E (number system 0 or 1) to its 12-digit UPC-A, or None."""
    if len(code) != 8 or not code.isascii() or not code.isdecimal() or code[0] not in "01":
        return None
    n, d, check = code[0], code[1:7], code[7]
    last = d[5]
    if last in "012":
        body = d[0:2] + last + "0000" + d[2:5]
    elif last == "3":
        body = d[0:3] + "00000" + d[3:5]
    elif last == "4":
        body = d[0:4] + "00000" + d[4]
    else:
        body = d[0:5] + "0000" + last
    upc_a = n + body + check
    return upc_a if gtin_check_digit(upc_a[:-1]) == int(check) else None


def normalize_gtin(raw: str) -> tuple[str, bool] | None:
    """`gtin_upc` as a 14-digit GTIN, and whether it had to be repaired. None when unusable.

    A GTIN keeps its meaning with leading zeros added, so a UPC-A, its EAN-13
    form and its GTIN-14 form all become the same 14 digits, and the server
    matches a scan in any of those forms (and a UPC-E, which it expands).
    FDC's 10- and 11-digit values are UPC-As with the check digit dropped:
    restoring it lines 12,500 of them up with the same product listed under
    its full code in the 2026-04 release, while padding them as they are
    lines up about 560.
    """
    code = raw.strip()
    if not code.isascii() or not code.isdecimal():
        return None
    gtin = _gtin14(code)
    if gtin is None or len(gtin[0].lstrip("0")) < MIN_SIGNIFICANT_DIGITS:
        return None
    return gtin


def _gtin14(code: str) -> tuple[str, bool] | None:
    if len(code) in (10, 11):
        return (code + str(gtin_check_digit(code))).zfill(14), True
    if len(code) == 8:
        expanded = upc_e_to_upc_a(code)
        if expanded is not None:
            return expanded.zfill(14), False
    if len(code) in (8, 12, 13, 14) and gtin_check_digit(code[:-1]) == int(code[-1]):
        return code.zfill(14), False
    return None


def serving_portion(size_raw: str | None, unit_raw: str | None, household_raw: str | None) -> dict | None:
    """The label's serving as a portion, or None when the label gives no usable serving.

    Grams only ever come from a serving stated in grams. A serving stated in
    ml keeps its label and has no grams: converting ml to g needs a density
    the label does not give, and a wrong gram weight would scale every number
    logged with it. The client falls back to weighing for those.
    """
    size = to_float(size_raw)
    unit = (unit_raw or "").strip().lower()
    if size is None or size <= 0 or size > 5000:
        return None
    amount = number_text(rounded(size, 1))
    if unit in GRAM_UNITS:
        measure, grams = f"{amount} g", rounded(size, 1)
    elif unit in ML_UNITS:
        measure, grams = f"{amount} ml", None
    else:
        return None  # mg, IU and blank units are supplements or bad data
    household = re.sub(r"\s+", " ", household_raw or "").strip()
    if not any(c.islower() for c in household):
        household = household.lower()
    lowered = household.lower()
    if UNHELPFUL_SERVING.match(lowered) or MEASURE_ONLY.match(lowered):
        label = f"1 serving ({measure})"
    elif MENTIONS_MEASURE.search(lowered):
        label = household
    else:
        label = f"{household} ({measure})"
    return {"label": label[:80], "grams": grams}


def latest_branded_url() -> str:
    """The newest Branded CSV release listed on the FDC download page."""
    request = urllib.request.Request(DOWNLOAD_PAGE, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            page = response.read(5 * 1024 * 1024).decode("utf-8", "replace")
    except (OSError, ValueError) as e:
        log(f"could not read {DOWNLOAD_PAGE} ({e}); using {BRANDED_FALLBACK_URL}")
        return BRANDED_FALLBACK_URL
    dates = BRANDED_RELEASE.findall(page)
    if not dates:
        log(f"no Branded CSV release listed on {DOWNLOAD_PAGE}; using {BRANDED_FALLBACK_URL}")
        return BRANDED_FALLBACK_URL
    # Only the date comes from the page; the host and file name are ours.
    return f"{DATASETS_PREFIX}FoodData_Central_branded_food_csv_{max(dates)}.zip"


class _CappedReader(io.RawIOBase):
    """Counts what is read from an archive member and stops past `limit` bytes."""

    def __init__(self, raw, limit: int, name: str):
        self._raw, self._limit, self._name, self._total = raw, limit, name, 0

    def readable(self) -> bool:
        return True

    def readinto(self, buffer) -> int:
        n = self._raw.readinto(buffer)
        self._total += n
        if self._total > self._limit:
            raise RuntimeError(f"{self._name!r} expanded past {self._limit} bytes")
        return n


def branded_members(zf: zipfile.ZipFile) -> dict[str, zipfile.ZipInfo]:
    found: dict[str, zipfile.ZipInfo] = {}
    for info in zf.infolist():
        member = PurePosixPath(info.filename)
        if member.is_absolute() or ".." in member.parts or "\\" in info.filename:
            raise RuntimeError(f"unsafe path in archive: {info.filename!r}")
        if (info.external_attr >> 16) & 0o170000 == 0o120000:
            raise RuntimeError(f"symlink in archive: {info.filename!r}")
        if info.is_dir() or member.name not in BRANDED_FILES:
            continue
        if member.name in found:
            raise RuntimeError(f"archive holds {member.name} twice")
        if info.file_size > BRANDED_MAX_MEMBER_BYTES:
            raise RuntimeError(f"{info.filename!r} is {info.file_size} bytes uncompressed, refusing")
        if info.compress_size and info.file_size / info.compress_size > MAX_COMPRESSION_RATIO:
            raise RuntimeError(f"{info.filename!r} compresses {info.file_size / info.compress_size:.0f}:1, refusing")
        found[member.name] = info
    missing = BRANDED_FILES - found.keys()
    if missing:
        raise RuntimeError(f"archive is missing {sorted(missing)}")
    return found


def csv_rows(zf: zipfile.ZipFile, info: zipfile.ZipInfo):
    """(header index, rows) of one member, read from the archive without extracting it."""
    raw = zf.open(info)
    capped = io.BufferedReader(_CappedReader(raw, BRANDED_MAX_MEMBER_BYTES, info.filename), 1024 * 1024)
    text = io.TextIOWrapper(capped, encoding="utf-8", newline="")
    reader = csv.reader(text)
    header = {name: i for i, name in enumerate(next(reader))}
    return header, reader


@dataclass
class BrandedStats:
    rows: int = 0
    bad_barcode: int = 0
    repaired_barcode: int = 0
    products: int = 0
    no_name: int = 0
    no_nutrients: int = 0
    implausible: int = 0
    imported: int = 0
    ml_servings: int = 0
    gram_servings: int = 0


def branded_rank(repaired: bool, available_date: str, modified_date: str, fdc_id: str) -> tuple:
    """Which of a barcode's records wins: the newest, and one stated correctly over one we repaired."""
    return (not repaired, available_date, modified_date, int(fdc_id))


def branded_sql(zf: zipfile.ZipFile, stats: BrandedStats):
    """Branded Foods as (sql, rows) chunks, one batch at a time.

    Three passes over the archive, each streamed: branded_food.csv picks one
    record per barcode, food.csv names them, food_nutrient.csv fills in the
    numbers. Only the winners are held in memory, as compact tuples.
    """
    members = branded_members(zf)
    csv.field_size_limit(16 * 1024 * 1024)

    header, rows = csv_rows(zf, members["nutrient.csv"])
    id_to_nbr = {r[header["id"]]: normalize_nbr(r[header["nutrient_nbr"]]) for r in rows}

    # Pass 1: one record per barcode, the newest. A barcode we had to repair
    # loses to one stated correctly.
    header, rows = csv_rows(zf, members["branded_food.csv"])
    col = {k: header[k] for k in (
        "fdc_id", "brand_owner", "brand_name", "gtin_upc", "serving_size", "serving_size_unit",
        "household_serving_fulltext", "modified_date", "available_date", "branded_food_category",
    )}
    best: dict[str, tuple] = {}
    for r in rows:
        stats.rows += 1
        gtin = normalize_gtin(r[col["gtin_upc"]])
        if gtin is None:
            stats.bad_barcode += 1
            continue
        code, repaired = gtin
        fdc_id = r[col["fdc_id"]].strip()
        if not fdc_id.isascii() or not fdc_id.isdecimal():
            continue
        rank = branded_rank(repaired, r[col["available_date"]], r[col["modified_date"]], fdc_id)
        kept = best.get(code)
        if kept is None or rank > kept[0]:
            brand = r[col["brand_name"]].strip() or r[col["brand_owner"]].strip()
            best[code] = (
                rank, fdc_id, repaired, brand,
                r[col["serving_size"]], r[col["serving_size_unit"]], r[col["household_serving_fulltext"]],
                branded_category(r[col["branded_food_category"]]),
            )
    stats.products = len(best)
    stats.repaired_barcode = sum(1 for v in best.values() if v[2])
    log(f"  {stats.rows:,} records, {stats.products:,} barcodes ({stats.bad_barcode:,} records without a usable barcode)")
    by_fdc = {v[1]: code for code, v in best.items()}

    # Pass 2: names.
    header, rows = csv_rows(zf, members["food.csv"])
    i_id, i_desc = header["fdc_id"], header["description"]
    names: dict[str, str] = {}
    for r in rows:
        code = by_fdc.get(r[i_id])
        if code is not None:
            names[code] = r[i_desc]
    log(f"  named {len(names):,} products")

    # Pass 3: nutrients, into one fixed-size array per product.
    slots = [ENERGY_KCAL, ENERGY_ATWATER_SPECIFIC, ENERGY_ATWATER_GENERAL, ENERGY_KJ, PROTEIN, FAT, FAT_NLEA,
             CARBS, CARBS_SUMMATION, FIBER, SUGARS_NLEA, VITAMIN_D_IU, *sorted(set(BRANDED_MICROS.values()))]
    slots = list(dict.fromkeys(slots))
    slot_of_nbr = {nbr: i for i, nbr in enumerate(slots)}
    slot_of_id = {nid: slot_of_nbr[nbr] for nid, nbr in id_to_nbr.items() if nbr in slot_of_nbr}
    nan = float("nan")
    values: dict[str, array] = {}
    header, rows = csv_rows(zf, members["food_nutrient.csv"])
    i_id, i_nid, i_amount = header["fdc_id"], header["nutrient_id"], header["amount"]
    for r in rows:
        slot = slot_of_id.get(r[i_nid])
        if slot is None:
            continue
        code = by_fdc.get(r[i_id])
        if code is None:
            continue
        amount = to_float(r[i_amount])
        if amount is None:
            continue
        v = values.get(code)
        if v is None:
            v = values[code] = array("d", [nan]) * len(slots)
        v[slot] = amount
    log(f"  nutrients for {len(values):,} products")

    def get(v: array, nbr: str) -> float | None:
        x = v[slot_of_nbr[nbr]]
        return None if x != x else x

    update = ", ".join(f"{c} = EXCLUDED.{c}" for c in BRANDED_UPDATE_COLUMNS)
    # Rewrite a row only when something in it changed. Postgres writes a new
    # copy of every updated row, so refreshing 430,000 unchanged products
    # every month would double the table on disk until vacuum caught up.
    changed = (
        "(" + ", ".join(f"foods.{c}" for c in BRANDED_UPDATE_COLUMNS) + ") IS DISTINCT FROM ("
        + ", ".join(f"EXCLUDED.{c}" for c in BRANDED_UPDATE_COLUMNS) + ")"
    )
    yield "-- Generated by scripts/import_usda_foods.py --branded from USDA FoodData Central (public domain).\n", 0
    yield "-- Idempotent and keyed on barcode: re-running refreshes products in place and never touches popularity.\n", 0
    batch: list[tuple[str, str, str]] = []

    def flush():
        pairs = ",".join(f"({sql_text(fdc)},{sql_text(code)})" for fdc, code, _ in batch)
        # FDC gives a resubmitted product a new fdc_id, so the barcode is the
        # key. An fdc_id that moved to another barcode would collide on
        # (source, source_id) and fail the whole import; the old row gives the
        # id up instead.
        # One transaction per batch, not per run: the UPDATE frees a source_id
        # the INSERT may take, so the pair commits together, and a dropped
        # connection costs one batch instead of the whole import. Re-running
        # finishes the job, because unchanged rows are skipped.
        sql = (
            "BEGIN;\n"
            "UPDATE foods f SET source_id = f.source_id || ':superseded:' || f.id::text\n"
            f"FROM (VALUES {pairs}) AS v(source_id, barcode)\n"
            f"WHERE f.source = {sql_text(BRANDED_SOURCE)} AND f.source_id = v.source_id"
            " AND f.barcode IS DISTINCT FROM v.barcode;\n"
            f"INSERT INTO foods ({', '.join(COLUMNS)}) VALUES\n"
            + ",\n".join(row for _, _, row in batch)
            + f"\nON CONFLICT (barcode) DO UPDATE SET {update}\nWHERE {changed};\n"
            "COMMIT;\n"
        )
        rows_in_batch = len(batch)
        batch.clear()
        return sql, rows_in_batch

    for code in sorted(best):
        _, fdc_id, _, brand, size, unit, household, category = best[code]
        name = title_case(names.get(code, ""))[:200]
        if not name:
            stats.no_name += 1
            continue
        v = values.get(code)
        if v is None:
            stats.no_nutrients += 1
            continue
        protein, carbs, fiber = get(v, PROTEIN), get(v, CARBS), get(v, FIBER)
        fat = get(v, FAT) if get(v, FAT) is not None else get(v, FAT_NLEA)
        if carbs is None:
            carbs = get(v, CARBS_SUMMATION)
        kcal = next((get(v, n) for n in (ENERGY_KCAL, ENERGY_ATWATER_SPECIFIC, ENERGY_ATWATER_GENERAL)
                     if get(v, n) is not None), None)
        if kcal is None and get(v, ENERGY_KJ) is not None:
            kcal = get(v, ENERGY_KJ) / 4.184
        if kcal is None and None not in (protein, fat, carbs):
            kcal = 4 * protein + 4 * carbs + 9 * fat
        if kcal is None and protein is None and carbs is None and fat is None:
            stats.no_nutrients += 1
            continue
        if (kcal or 0) > MAX_KCAL_100G or sum(x or 0 for x in (protein, carbs, fat)) > MAX_MACROS_100G:
            stats.implausible += 1
            continue
        micros = {}
        for key, nbr in BRANDED_MICROS.items():
            amount = get(v, nbr)
            if amount is None and key == "sugars_g":
                amount = get(v, SUGARS_NLEA)
            if amount is None and key == "vitamin_d_ug" and get(v, VITAMIN_D_IU) is not None:
                amount = get(v, VITAMIN_D_IU) / 40
            if amount is not None:
                micros[key] = rounded(amount, 3)
        portion = serving_portion(size, unit, household)
        if portion is not None:
            if portion["grams"] is None:
                stats.ml_servings += 1
            else:
                stats.gram_servings += 1
        food_id = uuid.uuid5(ID_NAMESPACE, f"{BRANDED_SOURCE}:gtin:{code}")
        brand_text = title_case(brand)[:200]
        row = "(" + ",".join([
            f"'{food_id}'::uuid",
            sql_text(BRANDED_SOURCE),
            sql_text(fdc_id),
            sql_text(name),
            sql_text(brand_text) if brand_text else "NULL",
            sql_text(code),
            sql_number(rounded(kcal, 1)),
            sql_number(rounded(protein)),
            sql_number(rounded(carbs)),
            sql_number(rounded(fat)),
            sql_number(rounded(fiber)),
            sql_text(json.dumps(micros, separators=(",", ":"), sort_keys=True)) + "::jsonb",
            sql_text(json.dumps([portion] if portion else [], separators=(",", ":"), ensure_ascii=False)) + "::jsonb",
            sql_text(category) if category else "NULL",
        ]) + ")"
        stats.imported += 1
        batch.append((fdc_id, code, row))
        if len(batch) >= BATCH_SIZE:
            yield flush()
    if batch:
        yield flush()


# ─── Output ─────────────────────────────────────────────────────────────────

class FileOutput:
    """A gzipped SQL file, written next to its final name and moved into place at the end."""

    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = path
        self._tmp = path.with_suffix(path.suffix + ".tmp")
        self._raw = open(self._tmp, "wb")
        self._gz = gzip.GzipFile(fileobj=self._raw, mode="wb", mtime=0, filename="")

    def write(self, text: str) -> None:
        self._gz.write(text.encode("utf-8"))

    def close(self) -> None:
        self._gz.close()
        self._raw.close()
        os.replace(self._tmp, self.path)
        log(f"wrote {self.path} ({self.path.stat().st_size / 1e6:.2f} MB)")


PG_KEYS = {
    "host": "PGHOST", "hostaddr": "PGHOSTADDR", "port": "PGPORT", "user": "PGUSER", "password": "PGPASSWORD",
    "dbname": "PGDATABASE", "sslmode": "PGSSLMODE", "sslrootcert": "PGSSLROOTCERT",
    "connect_timeout": "PGCONNECT_TIMEOUT", "options": "PGOPTIONS", "application_name": "PGAPPNAME",
}


def pg_env(conninfo: str) -> dict[str, str]:
    """libpq environment variables for a postgresql:// URL or a key=value string.

    psql gets the connection through its environment rather than its argument
    list, so the password never shows up in `ps`. A remote host without an
    explicit sslmode gets `require`.
    """
    if conninfo.startswith(("postgresql://", "postgres://")):
        url = urllib.parse.urlsplit(conninfo)
        pairs = {
            "host": url.hostname or "",
            "port": str(url.port or ""),
            "user": urllib.parse.unquote(url.username or ""),
            "password": urllib.parse.unquote(url.password or ""),
            "dbname": urllib.parse.unquote(url.path.lstrip("/")),
        }
        pairs.update(urllib.parse.parse_qsl(url.query))
    else:
        pairs = dict(item.split("=", 1) for item in shlex.split(conninfo) if "=" in item)
    env: dict[str, str] = {}
    for key, value in pairs.items():
        if key not in PG_KEYS:
            raise SystemExit(f"unsupported connection parameter {key!r}")
        if value:
            env[PG_KEYS[key]] = value
    if "PGHOST" not in env:
        raise SystemExit("the connection string names no host")
    if env["PGHOST"] not in ("localhost", "127.0.0.1", "::1") and "PGSSLMODE" not in env:
        env["PGSSLMODE"] = "require"
    return env


def find_psql(explicit: str | None) -> str:
    for candidate in (explicit, os.environ.get("PSQL"), shutil.which("psql"), "/opt/homebrew/opt/libpq/bin/psql"):
        if candidate and Path(candidate).is_file():
            return candidate
    raise SystemExit("psql not found; install libpq or pass --psql-bin")


# TCP keepalives on every connection. A connection the network drops silently
# otherwise leaves psql, and the import feeding it, waiting forever at 0% CPU
# (it happened on the Open Food Facts load). libpq has no environment variable
# for these, so they go in a conninfo string; the host, user and password still
# come from the environment, out of `ps`.
KEEPALIVES = "keepalives=1 keepalives_idle=30 keepalives_interval=10 keepalives_count=6"


class Psql:
    """One database, reached through the psql binary."""

    def __init__(self, binary: str, env: dict[str, str]):
        self.binary, self.env = binary, {**os.environ, **env}

    def query(self, sql: str) -> list[list[str]]:
        result = subprocess.run(
            [self.binary, "-X", "-A", "-t", "-F", "\t", "-v", "ON_ERROR_STOP=1", "-d", KEEPALIVES, "-c", sql],
            env=self.env, capture_output=True, text=True,
        )
        if result.returncode != 0:
            raise SystemExit(f"psql query failed: {result.stderr.strip()}")
        return [line.split("\t") for line in result.stdout.splitlines() if line]


class PsqlOutput:
    """Streams SQL into psql, stopping at the first error.

    No run-wide transaction: the branded set is about 430,000 rows and eight
    minutes over the network, and one connection blip near the end used to
    throw all of it away. The SQL commits per batch instead (see the branded
    chunker), and every batch is an idempotent upsert, so a failed run is
    finished by running it again.
    """

    def __init__(self, db: Psql):
        self._proc = subprocess.Popen(
            [db.binary, "-X", "-q", "-v", "ON_ERROR_STOP=1", "-d", KEEPALIVES, "-f", "-"],
            stdin=subprocess.PIPE, env=db.env,
        )

    def write(self, text: str) -> None:
        try:
            self._proc.stdin.write(text.encode("utf-8"))
        except BrokenPipeError:
            self._proc.wait()
            raise SystemExit(f"psql stopped (exit {self._proc.returncode}); batches sent before this point are committed, so re-run to finish")

    def close(self) -> None:
        try:
            self._proc.stdin.close()
        except BrokenPipeError:
            pass
        log("  waiting for psql to finish")
        if self._proc.wait() != 0:
            raise SystemExit(f"psql failed (exit {self._proc.returncode}); batches sent before this point are committed, so re-run to finish")


def stream(chunks, out, total_rows: int | None = None) -> None:
    started = time.monotonic()
    done = 0
    last_report = started
    for sql, rows in chunks:
        out.write(sql)
        done += rows
        now = time.monotonic()
        if rows and now - last_report >= 10:
            of = f"/{total_rows:,}" if total_rows else ""
            log(f"  {done:,}{of} rows sent, {now - started:.0f}s")
            last_report = now
    out.close()
    log(f"  {done:,} rows in {time.monotonic() - started:.0f}s")


def database_report(db: Psql) -> list[str]:
    counts = db.query("SELECT source, count(*) FROM foods GROUP BY source ORDER BY source")
    size = db.query(
        "SELECT pg_size_pretty(pg_total_relation_size('foods')), pg_size_pretty(pg_relation_size('foods'))"
    )
    lines = ["", "| source | foods |", "|---|---:|"]
    lines += [f"| {source} | {int(n):,} |" for source, n in counts]
    lines += ["", f"`foods` takes {size[0][0]} with its indexes (table alone {size[0][1]})."]
    return lines


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    fooddb = repo_root / "apps" / "server" / "fooddb"
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--branded", action="store_true", help="import Branded Foods instead of the generic datasets")
    parser.add_argument(
        "--output",
        type=Path,
        help="where to write the gzipped SQL (default: apps/server/fooddb/usda_foods.sql.gz, "
             "or the gitignored apps/server/fooddb/usda_branded.sql.gz with --branded)",
    )
    parser.add_argument(
        "--psql",
        nargs="?",
        const="",
        metavar="CONNINFO",
        help="stream the upsert into this database instead of writing a file; "
             "with no value, read the connection string from CATALOG_DATABASE_URL",
    )
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--branded-url", help="Branded CSV release to download (default: the newest on the FDC page)")
    parser.add_argument("--archive", type=Path, help="read this already-downloaded Branded release zip instead")
    parser.add_argument("--summary", type=Path, help="append a markdown summary here (e.g. $GITHUB_STEP_SUMMARY)")
    args = parser.parse_args()

    db: Psql | None = None
    if args.psql is not None:
        conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
        if not conninfo:
            raise SystemExit("--psql needs a connection string, or CATALOG_DATABASE_URL set")
        db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
        # Fail before a 450 MB download, not after it.
        has_columns = db.query(
            "SELECT count(*) FROM information_schema.columns "
            "WHERE table_schema = 'public' AND table_name = 'foods' AND column_name IN ('tier', 'category')"
        )
        if has_columns != [["2"]]:
            raise SystemExit(
                "foods.tier or foods.category is missing: run the server migrations (V10, V17) against this database first"
            )

    started = time.monotonic()
    summary: list[str] = []
    workdir = Path(tempfile.mkdtemp(prefix="opson-usda-"))
    try:
        if args.branded:
            if args.archive:
                archive, url = args.archive, f"local file {args.archive.name}"
            else:
                url = args.branded_url or latest_branded_url()
                log(f"{BRANDED_SOURCE}: {url}")
                archive = workdir / "release.zip"
                download(url, archive, BRANDED_MAX_DOWNLOAD_BYTES)
            stats = BrandedStats()
            out = PsqlOutput(db) if db else FileOutput(args.output or fooddb / "usda_branded.sql.gz")
            with zipfile.ZipFile(archive) as zf:
                stream(branded_sql(zf, stats), out)
            summary += [
                "### USDA Branded Foods import", "", f"Release: {url}", "",
                "| | count |", "|---|---:|",
                f"| records in the release | {stats.rows:,} |",
                f"| records without a usable barcode | {stats.bad_barcode:,} |",
                f"| distinct barcodes (newest record kept) | {stats.products:,} |",
                f"| of those, check digit restored | {stats.repaired_barcode:,} |",
                f"| skipped: no energy or macros | {stats.no_nutrients:,} |",
                f"| skipped: implausible per-100 g values | {stats.implausible:,} |",
                f"| skipped: no name | {stats.no_name:,} |",
                f"| **imported** | **{stats.imported:,}** |",
                f"| with a serving in grams / in ml | {stats.gram_servings:,} / {stats.ml_servings:,} |",
            ]
        else:
            all_foods: list[Food] = []
            for source, url, data_type in DATASETS:
                log(f"{source}: {url}")
                dataset_dir = workdir / source
                dataset_dir.mkdir()
                archive = dataset_dir / "release.zip"
                download(url, archive)
                extracted = dataset_dir / "csv"
                extracted.mkdir()
                files = safe_extract(archive, extracted)
                all_foods.extend(load_dataset(source, files, data_type).values())
            foods = dedupe(all_foods)
            for food in foods:
                food.portions = default_portion_first(food.portions, food.kcal)
            by_source: dict[str, int] = {}
            for food in foods:
                by_source[food.source] = by_source.get(food.source, 0) + 1
            log(f"after dedupe: {len(foods)} foods {by_source}")
            out = PsqlOutput(db) if db else FileOutput(args.output or fooddb / "usda_foods.sql.gz")
            stream(generic_sql(foods), out, len(foods))
            summary += ["### USDA generic foods import", "", f"{len(foods):,} foods: {by_source}"]
    finally:
        shutil.rmtree(workdir, ignore_errors=True)

    summary += ["", f"Took {time.monotonic() - started:.0f}s."]
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
