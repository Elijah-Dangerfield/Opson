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

The downloads are treated as untrusted data. They go into a fresh temporary
directory, only the five CSV files the importer needs are extracted, every
archive member is checked for path traversal, links and size, and nothing from
the archives is ever executed. The directory is deleted when the run ends.

USDA FoodData Central data is public domain (CC0).
"""

from __future__ import annotations

import argparse
import csv
import gzip
import json
import os
import re
import shutil
import sys
import tempfile
import urllib.request
import uuid
import zipfile
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
    "micros", "portions",
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


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def download(url: str, dest: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    total = 0
    with urllib.request.urlopen(request, timeout=120) as response, open(dest, "wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_DOWNLOAD_BYTES:
                raise RuntimeError(f"{url} is larger than {MAX_DOWNLOAD_BYTES} bytes, refusing")
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
            foods[row["fdc_id"]] = Food(source=source, source_id=row["fdc_id"], name=name[:200])

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
    ]
    return "(" + ",".join(parts) + ")"


def write_sql(foods: list[Food], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    tmp = output.with_suffix(output.suffix + ".tmp")
    update = ", ".join(f"{c} = EXCLUDED.{c}" for c in UPDATE_COLUMNS)
    with open(tmp, "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0, filename="") as gz:
        def emit(text: str) -> None:
            gz.write(text.encode("utf-8"))

        emit("-- Generated by scripts/import_usda_foods.py from USDA FoodData Central (public domain).\n")
        emit("-- Idempotent: re-running updates rows in place and never touches popularity.\n")
        emit(f"-- {len(foods)} foods.\n")
        for start in range(0, len(foods), BATCH_SIZE):
            batch = foods[start:start + BATCH_SIZE]
            emit(f"INSERT INTO foods ({', '.join(COLUMNS)}) VALUES\n")
            emit(",\n".join(sql_row(f) for f in batch))
            emit(f"\nON CONFLICT (source, source_id) DO UPDATE SET {update};\n")
    os.replace(tmp, output)


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument(
        "--output",
        type=Path,
        default=repo_root / "apps" / "server" / "fooddb" / "usda_foods.sql.gz",
        help="where to write the gzipped SQL (default: apps/server/fooddb/usda_foods.sql.gz)",
    )
    args = parser.parse_args()

    workdir = Path(tempfile.mkdtemp(prefix="opson-usda-"))
    try:
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
        by_source: dict[str, int] = {}
        for food in foods:
            by_source[food.source] = by_source.get(food.source, 0) + 1
        log(f"after dedupe: {len(foods)} foods {by_source}")
        write_sql(foods, args.output)
        log(f"wrote {args.output} ({args.output.stat().st_size / 1e6:.2f} MB)")
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
