#!/usr/bin/env python3
"""Time name search against `off_foods`, the way the server would run it.

    CATALOG_DATABASE_URL=postgresql://... python3 -I scripts/bench_off_search.py
    python3 -I scripts/bench_off_search.py --country en:france --passes 3

The statement mirrors SEARCH_SQL in PostgresFoodCatalogRepository (prefix
LIKE and prefix full text as direct matches, trigram similarity only when
those find fewer than the limit, match tiers, brand named), aimed at
`off_foods` and with one more ranking key: products sold in the caller's
country first. Rank by country, never filter by it. LIMIT 25.

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


def search_sql(query: str, country: str) -> str | None:
    normalized = re.sub(r"\s+", " ", query.lower().strip())[:100]
    words = WORD.findall(normalized)[:8]
    if not words:
        return None
    phrase = [" ".join(words[:-1] + [v]) for v in variants(words[-1])]
    like = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    prefix_ts = " & ".join("(" + " | ".join(f"{v}:*" for v in variants(w)) + ")" for w in words)
    whole_ts = " & ".join("(" + " | ".join(variants(w)) + ")" for w in words)
    query_words = "|".join(dict.fromkeys(v for w in words for v in variants(w)))
    brand_words = "|".join(dict.fromkeys([v for w in words for v in variants(w)] + BRAND_FILLER))
    regex = "^(" + "|".join(phrase) + ")($|[^[:alnum:]])"
    variants_text = "|".join(phrase)
    return f"""
WITH direct AS (
    SELECT f.* FROM off_foods f
    WHERE lower(f.name) LIKE {lit(like)}
       OR f.search @@ to_tsquery('simple', {lit(prefix_ts)})
),
candidates AS (
    SELECT * FROM direct
    UNION ALL
    SELECT f.* FROM off_foods f
    WHERE (SELECT count(*) FROM direct) < {LIMIT}
      AND lower(f.name) OPERATOR(extensions.%) {lit(normalized)}
      AND NOT (lower(f.name) LIKE {lit(like)} OR f.search @@ to_tsquery('simple', {lit(prefix_ts)}))
),
scored AS (
    SELECT c.id, c.name, c.brand, c.barcode,
           c.kcal_100g, c.protein_100g, c.carbs_100g, c.fat_100g, c.fiber_100g,
           c.micros::text AS micros, c.portions::text AS portions, c.tier, c.verified,
           CASE
               WHEN lower(c.name) = ANY (string_to_array({lit(variants_text)}, '|')) THEN 0
               WHEN btrim(split_part(lower(c.name), ',', 1)) = ANY (string_to_array({lit(variants_text)}, '|')) THEN 1
               WHEN lower(c.name) ~ {lit(regex)} THEN 2
               WHEN c.search @@ to_tsquery('simple', {lit(prefix_ts)}) THEN 3
               ELSE 4
           END AS match_tier,
           c.search @@ to_tsquery('simple', {lit(whole_ts)}) AS whole_words,
           coalesce((
               SELECT cardinality(b.words) > 0
                      AND b.words <@ string_to_array({lit(brand_words)}, '|')
                      AND b.words && string_to_array({lit(query_words)}, '|')
               FROM (SELECT array_remove(regexp_split_to_array(lower(c.brand), '[^[:alnum:]]+'), '') AS words) b
           ), false) AS brand_hit,
           c.countries_tags @> ARRAY[{lit(country)}] AS in_country,
           extensions.similarity(lower(c.name), {lit(normalized)}) AS sim
    FROM candidates c
)
SELECT s.*
FROM scored s
ORDER BY (s.match_tier = 4),
         s.in_country DESC,
         s.brand_hit DESC,
         s.match_tier,
         s.whole_words DESC,
         round(s.sim::numeric, 1) DESC,
         length(s.name),
         s.name
LIMIT {LIMIT}"""


def run_pass(db: Psql, queries: list[str], country: str) -> list[tuple[str, float, int]]:
    script = ["\\set ON_ERROR_STOP 1", "SET statement_timeout = '60s';"]
    for i, q in enumerate(queries):
        sql = search_sql(q, country)
        script.append(f"\\echo @@{i}")
        script.append(f"EXPLAIN (ANALYZE, TIMING OFF) {sql};")
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
                out.append((queries[current], ms, rows))
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
    args = parser.parse_args()

    conninfo = (args.psql or os.environ.get("CATALOG_DATABASE_URL", "")).strip()
    if not conninfo:
        raise SystemExit("set CATALOG_DATABASE_URL or pass --psql")
    db = Psql(find_psql(args.psql_bin), pg_env(conninfo))

    passes = [run_pass(db, QUERIES, args.country) for _ in range(max(args.passes, 2))]
    warm = [ms for p in passes[1:] for _, ms, _ in p]
    cold = [ms for _, ms, _ in passes[0]]
    print(f"{len(QUERIES)} queries, country {args.country}, {len(passes) - 1} measured pass(es) after a warm-up")
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
