#!/usr/bin/env python3
"""Gets a food review bundle ready for an offline pre-check, and checks it after.

    ADMIN_TOKEN=... python3 -I scripts/review_bundle.py photos opson-review-prod-2026-11-02.json
    python3 -I scripts/review_bundle.py check opson-review-prod-2026-11-02.json

The admin console's Review tab exports the pending queue as a bundle
(docs/practices/food-review-runbook.md). Its photo URLs need the admin token,
which should not go to an AI tool. `photos` downloads every photo next to the
bundle, into `<bundle stem>-photos/<id>-<kind>.jpg`, and writes those paths
into each item's `localPhotos`, so the tool reads files instead. An
ingredients photo the submission never had is left out.

`check` validates the decisions someone filled in, by the same rules the
console's Import uses, and prints a summary. It needs no token and makes no
request.

The photos folder holds people's photos: keep it out of git and delete it
after the pass.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Callable

FORMAT = "opson-review-bundle/1"
TIMEOUT_SECONDS = 30
ACTIONS = {"approve", "reject", "skip"}
REJECT_CODES = {"blurry", "not_food", "duplicate", "wrong_column", "wrong_values", "fake_data", "copied_data", "other"}
MAX_REASON = 500
OPTIONAL_KINDS = {"ingredients"}

Fetch = Callable[[str, str], bytes]


class Missing(Exception):
    """The server has no such photo (404)."""


def fetch(url: str, token: str) -> bytes:
    request = urllib.request.Request(url)
    request.add_header("X-Admin-Token", token)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise Missing(url) from error
        raise


def load(path: Path) -> dict:
    bundle = json.loads(path.read_text())
    if bundle.get("format") != FORMAT:
        raise SystemExit(f"{path} is not an {FORMAT} file (format: {bundle.get('format')!r}).")
    return bundle


def download_photos(bundle_path: Path, token: str, get: Fetch = fetch) -> tuple[int, list[str]]:
    """Downloads every photo and records it in `localPhotos`. Returns (files written, problems)."""
    bundle = load(bundle_path)
    folder = bundle_path.parent / f"{bundle_path.stem}-photos"
    folder.mkdir(exist_ok=True)
    written = 0
    problems = []
    for item in bundle.get("items", []):
        local = {}
        for kind, url in item.get("photos", {}).items():
            target = folder / f"{item['id']}-{kind}.jpg"
            try:
                target.write_bytes(get(url, token))
            except Missing:
                if kind not in OPTIONAL_KINDS:
                    problems.append(f"{item['id']}: no {kind} photo on the server")
                continue
            except (urllib.error.URLError, OSError) as error:
                problems.append(f"{item['id']}: {kind} photo failed ({error})")
                continue
            local[kind] = str(target.relative_to(bundle_path.parent))
            written += 1
        item["localPhotos"] = local
    bundle_path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n")
    return written, problems


def decision_problem(decision: dict) -> str | None:
    """Same rules as the console's Import (ReviewBundle.problem)."""
    action = str(decision.get("action") or "").strip().lower()
    reason = (decision.get("reason") or "").strip()
    code = (decision.get("code") or "").strip()
    if action not in ACTIONS:
        return f'Unknown action "{action}". Use approve, reject or skip.'
    if action == "reject" and not reason:
        return "A reject needs a reason."
    if len(reason) > MAX_REASON:
        return f"The reason is over {MAX_REASON} characters."
    if code and code not in REJECT_CODES:
        return f'Unknown reject code "{code}".'
    if code and action != "reject":
        return "A code only goes with a reject."
    food = decision.get("food")
    if food is not None and not isinstance(food, dict):
        return "The food corrections aren't readable."
    return None


def check(bundle: dict) -> tuple[dict[str, int], list[str]]:
    """Counts by action, plus one line per problem. Undecided items are counted, not flagged."""
    counts = {"approve": 0, "reject": 0, "skip": 0, "undecided": 0}
    problems = []
    seen = set()
    for item in bundle.get("items", []):
        decision = item.get("decision") or {}
        if not decision.get("action"):
            counts["undecided"] += 1
            continue
        item_id = item.get("id") or ""
        problem = "No id." if not item_id else ("Listed twice." if item_id in seen else decision_problem(decision))
        seen.add(item_id)
        if problem:
            problems.append(f"{item_id or '?'} ({item.get('name') or 'no name'}): {problem}")
            continue
        counts[str(decision["action"]).strip().lower()] += 1
    return counts, problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    photos = sub.add_parser("photos", help="download the photos next to the bundle (needs ADMIN_TOKEN)")
    photos.add_argument("bundle", type=Path)
    checked = sub.add_parser("check", help="validate the filled-in decisions")
    checked.add_argument("bundle", type=Path)
    args = parser.parse_args()

    if args.command == "check":
        counts, problems = check(load(args.bundle))
        print(", ".join(f"{count} {name}" for name, count in counts.items()))
        for problem in problems:
            print(f"  {problem}")
        return 1 if problems else 0

    token = os.environ.get("ADMIN_TOKEN", "").strip()
    if not token:
        print("Set ADMIN_TOKEN to the admin token of the environment the bundle came from.", file=sys.stderr)
        return 2
    written, problems = download_photos(args.bundle, token)
    print(f"Wrote {written} photo(s) to {args.bundle.stem}-photos/. Delete the folder after the pass.")
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
