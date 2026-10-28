#!/usr/bin/env python3
"""Prints the store "What's New" text for a version.

    python3 -I scripts/store_release_notes.py 0.2.0 --max-chars 500

release-notes/<version>.txt when a person wrote one, otherwise "Small fixes.",
cut to the store's limit. Play, TestFlight and the App Store all read it from
here, so the three can't drift.

Never the GitHub release body. That is release-please's changelog: markdown
headings the stores don't render, commit shas and repo links, written for
whoever maintains the app. Moving Eyes shipped it verbatim to the App Store on
2026-09-25 and the listing showed a literal "##", and the App Store text can't
be edited once a version is in review.

Keyed by version so a forgotten file ships the generic line rather than the
previous release's text.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FALLBACK = "Small fixes."


def notes_for(version: str, max_chars: int, root: Path = ROOT) -> str:
    path = root / "release-notes" / f"{version}.txt"
    text = path.read_text(encoding="utf-8").strip() if path.is_file() else ""
    return (text or FALLBACK)[:max_chars].rstrip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("version", help="the version without a leading v, e.g. 0.2.0")
    parser.add_argument("--max-chars", type=int, required=True, help="the store's limit (Play: 500)")
    args = parser.parse_args()
    print(notes_for(args.version.removeprefix("v"), args.max_chars))
    return 0


if __name__ == "__main__":
    sys.exit(main())
