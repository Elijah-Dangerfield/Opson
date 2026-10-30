#!/usr/bin/env python3
"""Judge whether each food's calories add up to its macros, for rows already loaded.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/check_food_energy.py --dry-run
    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/check_food_energy.py

V33 added `energy_consistent`, `energy_gap_kcal` and `energy_checked_at` to
`foods` and `off_foods`, and triggers that fill them on every insert, on every
change to a food's numbers and on every correction. Rows loaded before V33
have them empty. This fills them, through the migration's own
`food_energy_backfill()`, so the rule has one definition (V33, explained in
docs/food-data-sources.md).

Each batch is one short transaction over at most --batch-size rows, walked in
primary-key order, so no table is locked for more than a moment and a
dropped connection costs one batch. It only writes rows not judged yet, so
running it again resumes, and a finished run writes nothing. `--after` starts
from an id the log printed, to skip what is done without reading it again.
`--recheck-before TIMESTAMP` also re-judges rows judged before that time,
for when the rule changes; re-running with the same timestamp resumes that too.

Every row written leaves a dead copy until vacuum, so the run vacuums the
table every --vacuum-every rows (and at the end) to reuse the space instead of
growing the table by its own size. Supabase turns a database read-only at 95%
disk, so check the headroom before judging the 3.3 million Open Food Facts
rows. The monthly Open Food Facts reload judges every row it loads, so after
one reload `off_foods` needs nothing from this.

Reads the connection string from CATALOG_DATABASE_URL (or --psql) and hands
it to psql through libpq environment variables, so the password never shows
in a process list.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import Psql, find_psql, log, pg_env, sql_text  # noqa: E402

TABLES = ("foods", "off_foods")
BATCH_SIZE = 5000
VACUUM_EVERY = 250_000
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


def batch_sql(table: str, after: str | None, batch_size: int, recheck_before: str | None) -> str:
    """One batch. A lock wait gives up rather than queueing behind (and ahead of) the reload's swap."""
    if table not in TABLES:
        raise ValueError(table)
    after_sql = f"{sql_text(after)}::uuid" if after else "NULL"
    before_sql = f"{sql_text(recheck_before)}::timestamptz" if recheck_before else "NULL"
    return (
        "SET lock_timeout = '5s'; "
        "SELECT coalesce(last_id::text, ''), scanned, updated "
        f"FROM public.food_energy_backfill('public.{table}', {after_sql}, {int(batch_size)}, {before_sql})"
    )


def pending_sql(table: str, recheck_before: str | None) -> str:
    if table not in TABLES:
        raise ValueError(table)
    where = "energy_checked_at IS NULL"
    if recheck_before:
        where += f" OR energy_checked_at < {sql_text(recheck_before)}::timestamptz"
    return f"SELECT count(*) FROM public.{table} WHERE {where}"


def vacuum(db, table: str) -> None:
    """VACUUM in a session of its own: it can't share a statement string, and it may take past Supabase's 2 minutes."""
    if table not in TABLES:
        raise ValueError(table)
    proc = db.start(f"SET statement_timeout = 0;\nVACUUM public.{table};\n")
    if proc.wait() != 0:
        raise SystemExit(f"VACUUM public.{table} failed; what was judged is committed, so re-run to carry on")


def judge(db, table: str, batch_size: int, after: str | None = None, recheck_before: str | None = None,
          vacuum_every: int = VACUUM_EVERY, clock=time.monotonic) -> tuple[int, int]:
    """Runs batches until one looks at no rows. Returns (rows looked at, rows written)."""
    scanned_total = written_total = since_vacuum = 0
    started = last_report = clock()
    while True:
        rows = db.query(batch_sql(table, after, batch_size, recheck_before))
        last_id, scanned, updated = rows[-1][0], int(rows[-1][1]), int(rows[-1][2])
        if scanned == 0:
            break
        after = last_id
        scanned_total += scanned
        written_total += updated
        since_vacuum += updated
        if vacuum_every and since_vacuum >= vacuum_every:
            vacuum(db, table)
            since_vacuum = 0
        now = clock()
        if now - last_report >= 10:
            log(f"  {table}: {scanned_total:,} looked at, {written_total:,} judged, {now - started:.0f}s, after {after}")
            last_report = now
    if since_vacuum:
        vacuum(db, table)
    log(f"  {table}: {scanned_total:,} looked at, {written_total:,} judged in {clock() - started:.0f}s")
    return scanned_total, written_total


def summary_sql(table: str) -> str:
    if table not in TABLES:
        raise ValueError(table)
    return (
        "SELECT count(*) FILTER (WHERE energy_consistent), count(*) FILTER (WHERE NOT energy_consistent), "
        "count(*) FILTER (WHERE energy_checked_at IS NOT NULL AND energy_consistent IS NULL), "
        f"count(*) FILTER (WHERE energy_checked_at IS NULL) FROM public.{table}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--psql", default="", metavar="CONNINFO", help="database (default: CATALOG_DATABASE_URL)")
    parser.add_argument("--psql-bin", help="psql binary (default: $PSQL, then PATH, then Homebrew libpq)")
    parser.add_argument("--table", choices=TABLES, action="append", help="only this table (repeatable; default both)")
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE, help=f"rows per transaction (default {BATCH_SIZE})")
    parser.add_argument("--after", help="with one --table: start after this id, from the log of a stopped run")
    parser.add_argument("--recheck-before", metavar="TIMESTAMP",
                        help="also re-judge rows judged before this time, e.g. after the rule changed")
    parser.add_argument("--vacuum-every", type=int, default=VACUUM_EVERY,
                        help=f"vacuum after this many rows written (default {VACUUM_EVERY:,}; 0 leaves it to autovacuum)")
    parser.add_argument("--dry-run", action="store_true", help="count the rows to judge, write nothing")
    args = parser.parse_args()

    tables = args.table or list(TABLES)
    if args.after and (len(tables) != 1 or not UUID.fullmatch(args.after)):
        raise SystemExit("--after takes one id and one --table")
    if not 1 <= args.batch_size <= 50_000:
        raise SystemExit("--batch-size is 1 to 50000")
    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
    if db.query("SELECT to_regprocedure('public.food_energy_backfill(regclass,uuid,integer,timestamptz)') IS NULL")[0][0] == "t":
        raise SystemExit("food_energy_backfill() is missing: run the server migrations (V33) against this database first")

    for table in tables:
        log(f"{table}: {int(db.query(pending_sql(table, args.recheck_before))[0][0]):,} rows to judge")
        if not args.dry_run:
            judge(db, table, args.batch_size, args.after, args.recheck_before, args.vacuum_every)
        consistent, inconsistent, unknown, unchecked = (int(n) for n in db.query(summary_sql(table))[0])
        log(f"  {table}: {consistent:,} add up, {inconsistent:,} don't, {unknown:,} can't be judged, {unchecked:,} not judged")
    return 0


if __name__ == "__main__":
    sys.exit(main())
