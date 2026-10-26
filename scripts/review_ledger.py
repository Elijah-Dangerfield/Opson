#!/usr/bin/env python3
"""Regenerate docs/review/LEDGER.md from `git ls-files`, keeping existing ticks.

Usage: python3 scripts/review_ledger.py
"""
import re
import subprocess
from pathlib import Path

LEDGER = Path("docs/review/LEDGER.md")
REVIEWED_EXTENSIONS = (".kt", ".kts", ".swift", ".sql", ".py", ".sh", ".yml", ".yaml", ".ts", ".astro", ".mjs")

# First matching prefix wins, so narrower prefixes come first.
SLICES = [
    ("scripts-ops-ci", [".github/workflows/", "ops/", "scripts/"]),
    ("apps-shells", ["apps/admin/", "apps/compose/", "apps/ios/iosApp/"]),
    ("build-config", ["apps/baselineprofile/", "build-logic/", "build.gradle.kts", "config/", "detekt-rules/", "settings.gradle.kts"]),
    ("test-apps", ["apps/integration/", "apps/leaktest/", "apps/screenshots/"]),
    ("server", ["apps/server/"]),
    ("onboarding-foods", ["features/foods/", "features/onboarding/"]),
    ("features-other", ["features/home/"]),
    ("logger", ["features/logger/"]),
    ("settings", ["features/settings/"]),
    ("today", ["features/today/"]),
    ("trends", ["features/trends/"]),
    ("domain-diary-nutrition", ["libraries/diary/", "libraries/nutrition/"]),
    ("platform-libraries", ["libraries/foods/", "libraries/health/", "libraries/reminders/", "libraries/scanner/", "libraries/speech/", "libraries/widgets/"]),
    ("ui-library", ["libraries/resources/", "libraries/ui/"]),
    ("core-libraries", ["libraries/"]),
    ("website", ["website/src/", "website/astro.config", "website/tests/"]),
]

HEADER = """# Review ledger

Each reviewer ticks the files in its slice as it reviews them. Findings go in
`findings/<slice>.md`. Regenerate with `python3 scripts/review_ledger.py`; it
keeps existing ticks.
"""


def tracked_files():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout
    return [f for f in out.splitlines() if f.endswith(REVIEWED_EXTENSIONS) and "/build/" not in f]


def existing_ticks():
    if not LEDGER.exists():
        return set()
    return set(re.findall(r"- \[x\] `(.+)`", LEDGER.read_text()))


def slice_of(path):
    for name, prefixes in SLICES:
        if any(path.startswith(p) for p in prefixes):
            return name
    return None


def main():
    ticked = existing_ticks()
    files = {name: [] for name, _ in SLICES}
    for path in tracked_files():
        name = slice_of(path)
        if name:
            files[name].append(path)
    lines = [HEADER]
    for name, _ in SLICES:
        paths = sorted(files[name])
        lines.append(f"\n## {name} ({len(paths)} files)\n")
        lines += [f"- [{'x' if p in ticked else ' '}] `{p}`" for p in paths]
    LEDGER.write_text("\n".join(lines) + "\n")
    print(f"{sum(len(v) for v in files.values())} files in {len(SLICES)} slices")


if __name__ == "__main__":
    main()
