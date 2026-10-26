#!/usr/bin/env python3
"""Fill in `foods.category` for USDA rows loaded before the column existed.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_food_categories.py --dry-run
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_food_categories.py
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_food_categories.py --branded
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_food_categories.py --branded --archive branded.zip

The importer now writes a category on every row (`generic_category` and
`branded_category` in import_usda_foods.py). This reads the same USDA
releases and applies the same functions to the rows a database already holds,
so the catalog doesn't need a reload. The generic run downloads the three
pinned releases (about 13 MB); `--branded` reads the Branded release (about
450 MB), from `--archive` when it has already been downloaded.

Only `category` changes, and only on rows where it differs. Each update also
checks the row still holds the category that was read, so it never
overwrites a concurrent change. Running it again finds nothing to do. Rows
are matched on (source, source_id) for generic foods and on the barcode for
Branded ones, the keys the importer upserts on.

Run the server migrations (V16) first. Reads the connection string from
CATALOG_DATABASE_URL (or --psql) and hands it to psql through libpq
environment variables, so the password never shows in a process list.
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import (  # noqa: E402
    BRANDED_MAX_DOWNLOAD_BYTES,
    BRANDED_SOURCE,
    DATASETS,
    Psql,
    PsqlOutput,
    branded_category,
    branded_members,
    branded_rank,
    csv_rows,
    download,
    find_psql,
    generic_category,
    latest_branded_url,
    log,
    normalize_gtin,
    pg_env,
    read_csv,
    safe_extract,
    sql_text,
)

BATCH_SIZE = 1000


def generic_categories(workdir: Path) -> dict[tuple[str, str], str | None]:
    """(source, fdc_id) -> category for every food in the three generic releases."""
    found: dict[tuple[str, str], str | None] = {}
    for source, url, data_type in DATASETS:
        log(f"{source}: {url}")
        dataset_dir = workdir / source
        dataset_dir.mkdir()
        archive = dataset_dir / "release.zip"
        download(url, archive)
        extracted = dataset_dir / "csv"
        extracted.mkdir()
        files = safe_extract(archive, extracted)
        for row in read_csv(files["food.csv"]):
            if row["data_type"] == data_type:
                found[(source, row["fdc_id"])] = generic_category(source, row.get("food_category_id", ""), row["description"])
    return found


def branded_categories(zf: zipfile.ZipFile) -> dict[str, str | None]:
    """barcode -> category, from the record per barcode the importer keeps."""
    members = branded_members(zf)
    header, rows = csv_rows(zf, members["branded_food.csv"])
    col = {k: header[k] for k in ("fdc_id", "gtin_upc", "modified_date", "available_date", "branded_food_category")}
    best: dict[str, tuple[tuple, str | None]] = {}
    for r in rows:
        gtin = normalize_gtin(r[col["gtin_upc"]])
        fdc_id = r[col["fdc_id"]].strip()
        if gtin is None or not fdc_id.isascii() or not fdc_id.isdecimal():
            continue
        code, repaired = gtin
        rank = branded_rank(repaired, r[col["available_date"]], r[col["modified_date"]], fdc_id)
        kept = best.get(code)
        if kept is None or rank > kept[0]:
            best[code] = (rank, branded_category(r[col["branded_food_category"]]))
    return {code: category for code, (_, category) in best.items()}


def changes(rows: list[list[str]], wanted: dict) -> list[tuple[str, str | None, str | None]]:
    """(key, category as read, new category) for each row whose category moves. Unknown rows are left alone."""
    out = []
    for key, current in rows:
        if key not in wanted:
            continue
        old = current or None
        new = wanted[key]
        if new != old:
            out.append((key, old, new))
    return out


def _text(value: str | None) -> str:
    return "NULL::text" if value is None else sql_text(value) + "::text"


def update_sql(batch: list[tuple[str, str | None, str | None]], key_column: str, key_type: str, where: str) -> str:
    values = ",\n".join(f"({sql_text(key)}::{key_type}, {_text(old)}, {_text(new)})" for key, old, new in batch)
    return (
        "BEGIN;\n"
        "UPDATE public.foods f SET category = v.new\n"
        f"FROM (VALUES\n{values}\n) AS v(key, old, new)\n"
        f"WHERE {where} AND f.{key_column} = v.key AND f.category IS NOT DISTINCT FROM v.old;\n"
        "COMMIT;\n"
    )


def apply(db: Psql, todo: list, key_column: str, key_type: str, where: str) -> None:
    out = PsqlOutput(db)
    for start in range(0, len(todo), BATCH_SIZE):
        out.write(update_sql(todo[start:start + BATCH_SIZE], key_column, key_type, where))
    out.close()


def summarize(label: str, rows: list, todo: list) -> None:
    by_category: dict[str, int] = {}
    for _, _, new in todo:
        by_category[new or "none"] = by_category.get(new or "none", 0) + 1
    log(f"{label}: {len(rows):,} rows, {len(todo):,} get a new category {dict(sorted(by_category.items()))}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--psql", default="", metavar="CONNINFO", help="database (default: CATALOG_DATABASE_URL)")
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--dry-run", action="store_true", help="count what would change, write nothing")
    parser.add_argument("--branded", action="store_true", help="backfill Branded Foods instead of the generic datasets")
    parser.add_argument("--archive", type=Path, help="read this already-downloaded Branded release zip")
    parser.add_argument("--branded-url", help="Branded CSV release to download (default: the newest on the FDC page)")
    args = parser.parse_args()

    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
    has_category = db.query(
        "SELECT count(*) FROM information_schema.columns "
        "WHERE table_schema = 'public' AND table_name = 'foods' AND column_name = 'category'"
    )
    if has_category != [["1"]]:
        raise SystemExit("foods.category is missing: run the server migrations (V16) against this database first")

    workdir = Path(tempfile.mkdtemp(prefix="opson-categories-"))
    try:
        if args.branded:
            if args.archive:
                archive = args.archive
            else:
                url = args.branded_url or latest_branded_url()
                log(f"{BRANDED_SOURCE}: {url}")
                archive = workdir / "release.zip"
                download(url, archive, BRANDED_MAX_DOWNLOAD_BYTES)
            with zipfile.ZipFile(archive) as zf:
                wanted = branded_categories(zf)
            log(f"  {len(wanted):,} barcodes in the release")
            query = (
                "SELECT barcode, coalesce(category, '') FROM public.foods "
                f"WHERE source = {sql_text(BRANDED_SOURCE)} AND barcode IS NOT NULL ORDER BY barcode"
            )
            key_column, key_type, where = "barcode", "text", f"f.source = {sql_text(BRANDED_SOURCE)}"
        else:
            generic = generic_categories(workdir)
            wanted = {f"{source}:{fdc_id}": category for (source, fdc_id), category in generic.items()}
            sources = ", ".join(sql_text(s) for s, _, _ in DATASETS)
            query = (
                "SELECT source || ':' || source_id, coalesce(category, '') FROM public.foods "
                f"WHERE source IN ({sources}) ORDER BY 1"
            )
            key_column, key_type, where = "id", "uuid", "true"
            # Generic rows are updated by id; map each (source, source_id) key to it.
            ids = dict(db.query(f"SELECT source || ':' || source_id, id::text FROM public.foods WHERE source IN ({sources})"))

        rows = db.query(query)
        todo = changes(rows, wanted)
        summarize("Branded" if args.branded else "generic", rows, todo)
        if args.dry_run or not todo:
            return 0
        if not args.branded:
            todo = [(ids[key], old, new) for key, old, new in todo]
        apply(db, todo, key_column, key_type, where)
        left = changes(db.query(query), wanted)
        log(f"updated; {len(left):,} rows still to change (0 unless something wrote to them meanwhile)")
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
