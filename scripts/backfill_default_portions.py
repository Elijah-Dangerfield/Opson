#!/usr/bin/env python3
"""Reorder portions already in `foods` so the first one is a sensible default.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_default_portions.py --dry-run
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/backfill_default_portions.py

The app logs a food's first portion unless the user picks another. The USDA
importer now puts a typical serving first (`default_portion_first` in
import_usda_foods.py: a cup of blueberries, not one berry). This applies the
same function to the USDA generic rows a database already holds, so it
doesn't need a reload.

Only the order of `portions` changes, and only on rows where the function
moves something. Each update also checks the row still holds what was read,
so it never overwrites a concurrent change. Running it again finds nothing
to do. Branded rows have one portion, the label's, and are not touched.

Reads the connection string from CATALOG_DATABASE_URL (or --psql) and hands
it to psql through libpq environment variables, so the password never shows
in a process list.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import (  # noqa: E402
    Psql,
    PsqlOutput,
    default_portion_first,
    find_psql,
    log,
    pg_env,
    sql_text,
)

GENERIC_SOURCES = ("usda_foundation", "usda_sr", "usda_fndds")
BATCH_SIZE = 500


def changes(rows: list[list[str]]) -> list[tuple[str, str, str]]:
    """(id, portions as read, reordered portions) for each row whose default moves."""
    out = []
    for food_id, kcal, portions in rows:
        items = json.loads(portions)
        reordered = default_portion_first(items, float(kcal) if kcal else None)
        if reordered != items:
            out.append((food_id, portions, json.dumps(reordered, separators=(",", ":"), ensure_ascii=False)))
    return out


def update_sql(batch: list[tuple[str, str, str]]) -> str:
    values = ",\n".join(f"('{food_id}'::uuid, {sql_text(old)}::jsonb, {sql_text(new)}::jsonb)"
                        for food_id, old, new in batch)
    return (
        "BEGIN;\n"
        "UPDATE public.foods f SET portions = v.new\n"
        f"FROM (VALUES\n{values}\n) AS v(id, old, new)\n"
        "WHERE f.id = v.id AND f.portions = v.old;\n"
        "COMMIT;\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--psql", default="", metavar="CONNINFO", help="database (default: CATALOG_DATABASE_URL)")
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--dry-run", action="store_true", help="count and show what would change, write nothing")
    args = parser.parse_args()

    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))

    sources = ", ".join(sql_text(s) for s in GENERIC_SOURCES)
    rows = db.query(
        "SELECT id, coalesce(kcal_100g::text, ''), portions::text FROM public.foods "
        f"WHERE source IN ({sources}) AND jsonb_array_length(portions) > 1 ORDER BY id"
    )
    todo = changes(rows)
    log(f"{len(rows):,} generic foods with more than one portion; {len(todo):,} get a new default")
    for _, old, new in todo[:10]:
        log(f"  {json.loads(old)[0]['label']!r} -> {json.loads(new)[0]['label']!r}")
    if args.dry_run or not todo:
        return 0

    out = PsqlOutput(db)
    for start in range(0, len(todo), BATCH_SIZE):
        out.write(update_sql(todo[start:start + BATCH_SIZE]))
    out.close()
    left = changes(db.query(
        "SELECT id, coalesce(kcal_100g::text, ''), portions::text FROM public.foods "
        f"WHERE source IN ({sources}) AND jsonb_array_length(portions) > 1 ORDER BY id"
    ))
    log(f"updated; {len(left):,} rows still to change (0 unless something wrote to them meanwhile)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
