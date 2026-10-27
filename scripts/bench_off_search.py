#!/usr/bin/env python3
"""Time name search against `off_foods`, the way the server would run it.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/bench_off_search.py
    python3 -I scripts/bench_off_search.py --country en:france --passes 3
    python3 -I scripts/bench_off_search.py --compare              # and result quality
    python3 -I scripts/bench_off_search.py --statement unbounded  # the 0.1 statement, for the before numbers

`bounded` (the default) mirrors OpenFoodFactsSearch.SQL in the server: it
caps the candidates of each kind of match before it scores them, and skips
queries under 3 letters and digits. Change the two together. `unbounded` is
the 0.1 catalog statement aimed at `off_foods`, which scores every row a
prefix matches. Both rank the same way: match tier, a name match before a
brand-only one, products sold in the caller's country first (rank by
country, never filter by it), whole words, scans. LIMIT 25.

`--compare` runs both and reports how much of the unbounded top 10 the
bounded statement keeps, which is what the caps cost in quality.

Each query runs under EXPLAIN (ANALYZE, TIMING OFF), so the times are the
server's planning plus execution, without the network. The first pass warms
the cache and is reported separately; the percentiles come from the passes
after it.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_usda_foods import Psql, find_psql, pg_env  # noqa: E402

# Common foods, brands, misspellings, short prefixes and non-English names.
QUERIES = [
    # common foods
    "banana", "apple", "milk", "whole milk", "eggs", "bread", "white bread", "rice", "brown rice", "pasta",
    "chicken breast", "chicken", "beef", "ground beef", "salmon", "tuna", "cheese", "cheddar", "mozzarella",
    "butter", "peanut butter", "almond butter", "yogurt", "greek yogurt", "oat milk", "almond milk", "soy milk",
    "orange juice", "apple juice", "coffee", "tea", "green tea", "chocolate", "dark chocolate", "milk chocolate",
    "cereal", "granola", "oats", "oatmeal", "muesli", "crackers", "chips", "potato chips", "tortilla chips",
    "popcorn", "cookies", "biscuits", "ice cream", "pizza", "frozen pizza", "hummus", "salsa", "ketchup",
    "mayonnaise", "mustard", "olive oil", "honey", "jam", "strawberry jam", "maple syrup", "sugar", "flour",
    "tofu", "lentils", "chickpeas", "black beans", "baked beans", "tomato sauce", "pesto", "soup",
    "chicken soup", "noodles", "instant noodles", "ramen", "sausage", "bacon", "ham", "turkey", "salami",
    "protein bar", "energy drink", "sparkling water", "water", "cola", "lemonade", "beer", "wine", "kombucha",
    "skyr", "kefir", "cottage cheese", "cream cheese", "sour cream", "croissant", "bagel", "tortilla",
    "avocado", "spinach", "frozen peas", "sweet corn", "olives", "pickles", "almonds", "walnuts", "cashews",
    "trail mix", "dried mango", "raisins", "smoothie", "vanilla ice cream", "chocolate milk", "protein powder",
    # brands and branded products
    "nutella", "coca cola", "coke zero", "pepsi", "pepsi max", "kitkat", "kit kat", "snickers", "twix", "mars",
    "oreo", "doritos", "pringles", "lays", "cheerios", "special k", "kelloggs", "heinz ketchup", "barilla",
    "danone", "activia", "alpro", "oatly", "chobani", "fage", "lindt", "milka", "ferrero rocher", "kinder bueno",
    "haribo", "red bull", "monster energy", "starbucks", "nescafe", "lipton", "ben and jerrys", "haagen dazs",
    "quaker oats", "clif bar", "kind bar", "quest bar", "nature valley", "philadelphia", "president", "lurpak",
    "kerrygold", "babybel", "laughing cow", "tropicana", "innocent", "huel", "yakult", "carrefour", "tesco",
    "lidl", "aldi", "trader joes", "kirkland", "great value", "marks and spencer", "bonne maman", "lu",
    "st michel", "cadbury", "toblerone", "ritter sport", "m&m", "skittles", "tic tac", "evian", "perrier",
    # misspellings
    "bananna", "yoghurt", "yougurt", "brocoli", "spagetti", "nutela", "coca cols", "chocolat", "mozarella",
    "parmesan chese", "humus", "avacado", "cheeerios", "oreos", "snikers", "brokoli", "lasagne", "zuchini",
    "cappucino", "expresso", "tumeric", "strawbery", "rasberry", "blueberies", "croisant",
    # short prefixes, as typed
    "b", "ch", "mi", "yo", "ba", "chi", "app", "bre", "pa", "co", "ap", "egg", "nu", "cho", "che", "pe", "sa",
    "to", "ri", "ka",
    # non-English names
    "pain de mie", "pain au chocolat", "fromage blanc", "yaourt nature", "lait demi ecreme", "jambon",
    "camembert", "baguette", "compote", "confiture", "brioche", "madeleine", "quark", "vollkornbrot",
    "apfelschorle", "leberwurst", "schokolade", "mineralwasser", "brezel", "frischkase", "leche", "galletas",
    "aceite de oliva", "jamon serrano", "queso", "chorizo", "magdalenas", "pan integral", "biscotti",
    "mozzarella di bufala", "grana padano", "prosciutto", "passata", "taralli", "latte", "yaourt", "lait",
    "pomme", "fraise", "kase", "milch", "brot", "pao", "arroz", "feijao", "zumo de naranja", "agua con gas",
    "melk", "kaas", "hagelslag", "stroopwafel", "knackebrod", "filmjolk", "leverpastej", "smor", "ost",
    "onigiri", "natto", "miso", "umeboshi", "gochujang", "kimchi",
]

LIMIT = 25
WORD = re.compile(r"[^\W_]+")
BRAND_FILLER = [
    "the", "and", "of", "s", "inc", "llc", "ltd", "co", "corp", "corporation", "company", "companies",
    "foods", "food", "brand", "brands", "sales", "usa", "us", "international", "products", "group",
]

# Mirrors PostgresFoodCatalogRepository: OFF_MIN_QUERY and the OFF_* caps.
MIN_QUERY = 3
CAP_EXACT = 100
CAP_WORDS = 300
CAP_SIMILAR = 50


def variants(word: str) -> list[str]:
    """SearchTerms.variantsOf: the word plus a naive singular or plural."""
    out = [word]
    if len(word) > 4 and word.endswith("ies"):
        out.append(word[:-3] + "y")
    elif len(word) > 4 and word.endswith("oes"):
        out.append(word[:-2])
    elif len(word) > 3 and word.endswith("s") and not word.endswith("ss"):
        out.append(word[:-1])
    elif len(word) > 2 and not word.endswith("s") and word.isalpha():
        out.append(word + "s")
    return list(dict.fromkeys(out))


def lit(text: str) -> str:
    return "'" + text.replace("'", "''") + "'"


def terms(query: str) -> dict | None:
    """SearchTerms, as literals."""
    normalized = re.sub(r"\s+", " ", query.lower().strip())[:100]
    words = WORD.findall(normalized)[:8]
    if not words:
        return None
    phrase = [" ".join(words[:-1] + [v]) for v in variants(words[-1])]
    return {
        "normalized": normalized,
        "chars": sum(len(w) for w in words),
        "like": normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%",
        "prefix_ts": " & ".join("(" + " | ".join(f"{v}:*" for v in variants(w)) + ")" for w in words),
        "whole_ts": " & ".join("(" + " | ".join(variants(w)) + ")" for w in words),
        "query_words": "|".join(dict.fromkeys(v for w in words for v in variants(w))),
        "brand_words": "|".join(dict.fromkeys([v for w in words for v in variants(w)] + BRAND_FILLER)),
        "regex": "^(" + "|".join(phrase) + ")($|[^[:alnum:]])",
        "variants": "|".join(phrase),
    }


def unbounded_sql(query: str, country: str) -> str | None:
    """The 0.1 statement aimed at off_foods: scores every row a prefix matches. The quality reference."""
    t = terms(query)
    if t is None:
        return None
    return f"""
WITH direct AS (
    SELECT f.* FROM off_foods f
    WHERE lower(f.name) LIKE {lit(t['like'])}
       OR f.search @@ to_tsquery('simple', {lit(t['prefix_ts'])})
),
candidates AS (
    SELECT * FROM direct
    UNION ALL
    SELECT f.* FROM off_foods f
    WHERE (SELECT count(*) FROM direct) < {LIMIT}
      AND lower(f.name) OPERATOR(extensions.%) {lit(t['normalized'])}
      AND NOT (lower(f.name) LIKE {lit(t['like'])} OR f.search @@ to_tsquery('simple', {lit(t['prefix_ts'])}))
),
scored AS (
    SELECT c.id, c.name, c.brand, c.scans,
           CASE
               WHEN lower(c.name) = ANY (string_to_array({lit(t['variants'])}, '|')) THEN 0
               WHEN btrim(split_part(lower(c.name), ',', 1)) = ANY (string_to_array({lit(t['variants'])}, '|')) THEN 1
               WHEN lower(c.name) ~ {lit(t['regex'])} THEN 2
               WHEN c.search @@ to_tsquery('simple', {lit(t['prefix_ts'])}) THEN 3
               ELSE 4
           END AS match_tier,
           c.search @@ to_tsquery('simple', {lit(t['whole_ts'])}) AS whole_words,
           c.brand IS NULL OR to_tsvector('simple', c.name) @@ to_tsquery('simple', {lit(t['prefix_ts'])}) AS name_words,
           c.countries_tags @> ARRAY[{lit(country)}] AS in_country,
           extensions.similarity(lower(c.name), {lit(t['normalized'])}) AS sim
    FROM candidates c
)
SELECT s.id, s.name, s.brand, s.scans
FROM scored s
ORDER BY (s.match_tier = 4), (s.match_tier <= 2 OR (s.match_tier = 3 AND s.name_words)) DESC, s.in_country DESC, s.match_tier, s.whole_words DESC,
         s.scans DESC, round(s.sim::numeric, 1) DESC, length(s.name), s.name
LIMIT {LIMIT}"""


def bounded_sql(query: str, country: str) -> str | None:
    """OFF_SEARCH_SQL in PostgresFoodCatalogRepository, with literals. None when the query is too short."""
    t = terms(query)
    if t is None or t["chars"] < MIN_QUERY:
        return None
    return f"""
WITH exact AS (
    SELECT o.ctid FROM off_foods o
    WHERE lower(o.name) = ANY (string_to_array({lit(t['variants'])}, '|'))
    LIMIT {CAP_EXACT}
),
words AS (
    SELECT o.ctid FROM off_foods o
    WHERE o.search @@ to_tsquery('simple', {lit(t['prefix_ts'])})
    LIMIT {CAP_WORDS}
),
exact_here AS (
    SELECT o.ctid FROM off_foods o
    WHERE lower(o.name) = ANY (string_to_array({lit(t['variants'])}, '|'))
      AND o.countries_tags @> ARRAY[{lit(country)}]
    LIMIT {CAP_EXACT}
),
words_here AS (
    SELECT o.ctid FROM off_foods o
    WHERE o.search @@ to_tsquery('simple', {lit(t['prefix_ts'])})
      AND o.countries_tags @> ARRAY[{lit(country)}]
    LIMIT {CAP_WORDS}
),
hits AS (
    SELECT ctid FROM exact UNION SELECT ctid FROM words UNION SELECT ctid FROM exact_here UNION SELECT ctid FROM words_here
),
typos AS (
    SELECT o.ctid FROM off_foods o
    WHERE (SELECT count(*) FROM hits) < {LIMIT}
      AND {lit(t['normalized'])} OPERATOR(extensions.<%) lower(o.name)
    LIMIT {CAP_SIMILAR}
),
candidates AS (
    SELECT o.* FROM off_foods o
    WHERE o.ctid = ANY (ARRAY(SELECT ctid FROM hits UNION SELECT ctid FROM typos))
),
scored AS (
    SELECT c.id, c.name, c.brand, c.scans,
           CASE
               WHEN lower(c.name) = ANY (string_to_array({lit(t['variants'])}, '|')) THEN 0
               WHEN btrim(split_part(lower(c.name), ',', 1)) = ANY (string_to_array({lit(t['variants'])}, '|')) THEN 1
               WHEN lower(c.name) ~ {lit(t['regex'])} THEN 2
               WHEN c.search @@ to_tsquery('simple', {lit(t['prefix_ts'])}) THEN 3
               ELSE 4
           END AS match_tier,
           c.search @@ to_tsquery('simple', {lit(t['whole_ts'])}) AS whole_words,
           c.brand IS NULL OR to_tsvector('simple', c.name) @@ to_tsquery('simple', {lit(t['prefix_ts'])}) AS name_words,
           c.countries_tags @> ARRAY[{lit(country)}] AS in_country,
           extensions.similarity(lower(c.name), {lit(t['normalized'])}) AS sim
    FROM candidates c
)
SELECT s.id, s.name, s.brand, s.scans
FROM scored s
ORDER BY (s.match_tier = 4), (s.match_tier <= 2 OR (s.match_tier = 3 AND s.name_words)) DESC, s.in_country DESC, s.match_tier, s.whole_words DESC,
         s.scans DESC, round(s.sim::numeric, 1) DESC, length(s.name), s.name
LIMIT {LIMIT}"""


STATEMENTS = {"bounded": bounded_sql, "unbounded": unbounded_sql}


def run_pass(db: Psql, queries: list[str], country: str, statement) -> list[tuple[str, float, int]]:
    """(query, server ms, rows) per query the statement runs for; a query it skips is left out."""
    script = ["\\set ON_ERROR_STOP 1", "SET statement_timeout = '60s';"]
    ran = []
    for q in queries:
        sql = statement(q, country)
        if sql is None:
            continue
        script.append(f"\\echo @@{len(ran)}")
        script.append(f"EXPLAIN (ANALYZE, TIMING OFF) {sql};")
        ran.append(q)
    result = subprocess.run(
        [db.binary, "-X", "-q", "-A", "-t", "-f", "-"],
        input="\n".join(script), env=db.env, capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed: {result.stderr.strip()}")
    out: list[tuple[str, float, int]] = []
    current, ms, rows, first = None, 0.0, 0, True
    for line in result.stdout.splitlines() + ["@@end"]:
        if line.startswith("@@"):
            if current is not None:
                out.append((ran[current], ms, rows))
            current = None if line == "@@end" else int(line[2:])
            ms, rows, first = 0.0, 0, True
            continue
        if first and "actual rows=" in line:
            rows = int(re.search(r"actual rows=(\d+)", line).group(1))
            first = False
        m = re.search(r"(Planning|Execution) Time: ([\d.]+) ms", line)
        if m:
            ms += float(m.group(2))
    return out


def results(db: Psql, queries: list[str], country: str, statement) -> dict[str, list[str]]:
    """The ids each query returns, in order."""
    script = ["\\set ON_ERROR_STOP 1", "SET statement_timeout = '60s';"]
    ran = []
    for q in queries:
        sql = statement(q, country)
        if sql is None:
            continue
        script.append(f"\\echo @@{len(ran)}")
        script.append(f"SELECT id FROM ({sql}) r;")
        ran.append(q)
    result = subprocess.run(
        [db.binary, "-X", "-q", "-A", "-t", "-f", "-"],
        input="\n".join(script), env=db.env, capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed: {result.stderr.strip()}")
    out: dict[str, list[str]] = {}
    current = None
    for line in result.stdout.splitlines():
        if line.startswith("@@"):
            current = ran[int(line[2:])]
            out[current] = []
        elif current is not None and line.strip():
            out[current].append(line.strip())
    return out


def compare(db: Psql, queries: list[str], country: str) -> None:
    """How much of the unbounded statement's top 10 the bounded one keeps."""
    bounded = results(db, queries, country, bounded_sql)
    reference = results(db, [q for q in queries if q in bounded], country, unbounded_sql)
    overlaps, same_first, worst = [], 0, []
    for q, ref in reference.items():
        got = bounded.get(q, [])
        if not ref:
            continue
        top = ref[:10]
        overlap = len(set(top) & set(got[:10])) / len(top)
        overlaps.append(overlap)
        same_first += bool(got) and got[0] == ref[0]
        worst.append((overlap, q))
    print(f"quality, {len(overlaps)} queries: the bounded top 10 keeps {100 * sum(overlaps) / len(overlaps):.1f}% "
          f"of the unbounded top 10; same first result {100 * same_first / len(overlaps):.1f}%")
    print("lowest overlap: " + ", ".join(f"{q!r} {o:.0%}" for o, q in sorted(worst)[:12]))


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    k = (len(ordered) - 1) * p
    lo, hi = int(k), min(int(k) + 1, len(ordered) - 1)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (k - lo)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--psql", default="", metavar="CONNINFO", help="database (default: CATALOG_DATABASE_URL)")
    parser.add_argument("--psql-bin")
    parser.add_argument("--country", default="en:united-states", help="the caller's country tag (default en:united-states)")
    parser.add_argument("--passes", type=int, default=2, help="passes over the queries, the first one a warm-up (default 2)")
    parser.add_argument("--slow-ms", type=float, default=200.0, help="list queries slower than this (default 200)")
    parser.add_argument("--statement", choices=sorted(STATEMENTS), default="bounded",
                        help="bounded: the server's statement (default); unbounded: the 0.1 statement, for comparison")
    parser.add_argument("--compare", action="store_true",
                        help="also compare the bounded statement's results with the unbounded one's")
    args = parser.parse_args()

    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))
    statement = STATEMENTS[args.statement]

    passes = [run_pass(db, QUERIES, args.country, statement) for _ in range(max(args.passes, 2))]
    skipped = len(QUERIES) - len(passes[0])
    warm = [ms for p in passes[1:] for _, ms, _ in p]
    cold = [ms for _, ms, _ in passes[0]]
    print(f"{args.statement}: {len(passes[0])} of {len(QUERIES)} queries run ({skipped} too short), "
          f"country {args.country}, {len(passes) - 1} measured pass(es) after a warm-up")
    print(f"warm: p50 {percentile(warm, .5):.1f} ms, p95 {percentile(warm, .95):.1f} ms, "
          f"p99 {percentile(warm, .99):.1f} ms, max {max(warm):.1f} ms")
    longer = [ms for p in passes[1:] for q, ms, _ in p if len(q) >= 3]
    print(f"warm, queries of 3+ characters: p50 {percentile(longer, .5):.1f} ms, "
          f"p95 {percentile(longer, .95):.1f} ms, p99 {percentile(longer, .99):.1f} ms")
    print(f"first pass: p50 {percentile(cold, .5):.1f} ms, p95 {percentile(cold, .95):.1f} ms, "
          f"p99 {percentile(cold, .99):.1f} ms, max {max(cold):.1f} ms")
    worst: dict[str, tuple[float, int]] = {}
    for p in passes[1:]:
        for q, ms, rows in p:
            if ms > worst.get(q, (0.0, 0))[0]:
                worst[q] = (ms, rows)
    slow = sorted(((ms, q, rows) for q, (ms, rows) in worst.items() if ms >= args.slow_ms), reverse=True)
    print(f"slower than {args.slow_ms:g} ms when warm: {len(slow)}")
    for ms, q, rows in slow:
        print(f"  {ms:8.1f} ms  {q!r} ({rows} results)")
    empty = [q for q, (_, rows) in worst.items() if rows == 0]
    print(f"no results: {empty}")
    if args.compare:
        compare(db, QUERIES, args.country)
    return 0


if __name__ == "__main__":
    sys.exit(main())
