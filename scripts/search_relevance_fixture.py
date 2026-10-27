#!/usr/bin/env python3
"""Write the Branded fixture for the server's search relevance suite.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/search_relevance_fixture.py

SearchRelevanceTest runs the queries in
apps/server/src/test/resources/search-relevance.tsv against the committed
USDA generic seed. Generic foods alone can't show a Branded product crowding
out a food, so the test also loads the Branded rows that compete for those
queries, taken from a database that holds the full Branded load (dev, or a
local copy of it). This script picks them and writes
apps/server/src/test/resources/search-relevance-branded.sql.gz.

What competes, per query, from the rows search would consider (name prefix,
full text over name and brand, trigram similarity for typos): the 40 whose
name starts with the query, the 40 shortest names with every query word
whole, the 40 most similar names, and up to 40 whose brand is made only of
query words. That covers what any ranking of ours would put in a top 3, and
does not depend on the ranking being tested. Running the suite against the
full database (SEARCH_RELEVANCE_JDBC_URL) and the fixture should give the
same answer; check that when you regenerate this.

USDA Branded Foods is public domain (CC0), so the rows can be committed. It
only reads from the database.
"""

from __future__ import annotations

import argparse
import gzip
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from bench_off_search import BRAND_FILLER, WORD, lit, variants  # noqa: E402
from import_usda_foods import COLUMNS, Psql, find_psql, pg_env  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SUITE = ROOT / "apps/server/src/test/resources/search-relevance.tsv"
OUTPUT = ROOT / "apps/server/src/test/resources/search-relevance-branded.sql.gz"
PER_KIND = 40


def queries(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    return [line.split("\t", 1)[0] for line in lines if line.strip() and not line.startswith("#")]


def competitors_sql(query: str) -> str | None:
    normalized = " ".join(query.lower().split())[:100]
    words = WORD.findall(normalized)[:8]
    if not words:
        return None
    like = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    prefix_ts = " & ".join("(" + " | ".join(f"{v}:*" for v in variants(w)) + ")" for w in words)
    whole_ts = " & ".join("(" + " | ".join(variants(w)) + ")" for w in words)
    query_words = "|".join(dict.fromkeys(v for w in words for v in variants(w)))
    brand_words = "|".join(dict.fromkeys([v for w in words for v in variants(w)] + BRAND_FILLER))
    return f"""
WITH c AS MATERIALIZED (
    SELECT f.id, f.name, f.brand FROM foods f
    WHERE f.tier > 1
      AND (lower(f.name) LIKE {lit(like)}
           OR f.search @@ to_tsquery('simple', {lit(prefix_ts)})
           OR lower(f.name) OPERATOR(extensions.%) {lit(normalized)})
)
SELECT id FROM (SELECT id FROM c WHERE lower(name) LIKE {lit(like)} ORDER BY length(name), name LIMIT {PER_KIND}) a
UNION SELECT id FROM (SELECT id FROM c WHERE to_tsvector('simple', name) @@ to_tsquery('simple', {lit(whole_ts)})
                      ORDER BY length(name), name LIMIT {PER_KIND}) b
UNION SELECT id FROM (SELECT id FROM c ORDER BY extensions.similarity(lower(name), {lit(normalized)}) DESC, name
                      LIMIT {PER_KIND}) s
UNION SELECT id FROM (SELECT id FROM c
                      WHERE array_remove(regexp_split_to_array(lower(brand), '[^[:alnum:]]+'), '')
                                <@ string_to_array({lit(brand_words)}, '|')
                        AND array_remove(regexp_split_to_array(lower(brand), '[^[:alnum:]]+'), '')
                                && string_to_array({lit(query_words)}, '|')
                      ORDER BY length(name), name LIMIT {PER_KIND}) r"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--psql", default="", metavar="CONNINFO", help="database (default: CATALOG_DATABASE_URL)")
    parser.add_argument("--psql-bin")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()

    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))

    ids: set[str] = set()
    for query in queries(SUITE):
        sql = competitors_sql(query)
        if sql:
            ids.update(row[0] for row in db.query(sql))
    if not ids:
        raise SystemExit("no Branded rows found; is this a database with the Branded load?")

    id_list = ",".join(lit(i) for i in sorted(ids))
    select = ", ".join(c if c not in ("micros", "portions") else f"{c}::text" for c in COLUMNS)
    rows = db.query(f"SELECT {select} FROM foods WHERE id IN ({id_list}) ORDER BY id")

    def value(column: str, raw: str) -> str:
        if raw == "":
            return "NULL" if column in ("brand", "barcode") or column.endswith("_100g") else "''"
        if column.endswith("_100g"):
            return raw
        if column == "id":
            return f"'{raw}'::uuid"
        if column in ("micros", "portions"):
            return lit(raw) + "::jsonb"
        return lit(raw)

    with gzip.open(args.output, "wt", encoding="utf-8", compresslevel=9) as out:
        out.write("-- Generated by scripts/search_relevance_fixture.py from USDA Branded Foods (public domain).\n")
        out.write(f"-- {len(rows)} Branded rows that compete for the queries in search-relevance.tsv.\n")
        for start in range(0, len(rows), 500):
            batch = rows[start:start + 500]
            out.write(f"INSERT INTO foods ({', '.join(COLUMNS)}) VALUES\n")
            out.write(",\n".join("(" + ",".join(value(c, v) for c, v in zip(COLUMNS, row)) + ")" for row in batch))
            out.write("\nON CONFLICT DO NOTHING;\n")
    print(f"wrote {len(rows)} rows to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
