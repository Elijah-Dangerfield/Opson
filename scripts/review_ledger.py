#!/usr/bin/env python3
"""Regenerate docs/review/LEDGER.md from `git ls-files`, keeping existing ticks.

Usage: python3 scripts/review_ledger.py
"""
import re
import subprocess
from pathlib import Path

LEDGER = Path("docs/review/LEDGER.md")
REVIEWED_EXTENSIONS = (
    ".kt", ".kts", ".swift", ".sql", ".py", ".sh", ".yml", ".yaml", ".ts", ".astro", ".mjs",
    ".toml", ".properties", ".pro", ".xcconfig",
)
# Config that carries no reviewed extension: the release lanes, the server image,
# the git hooks, the Grafana boards (JSON whose queries run against production)
# and release-please's version rules.
REVIEWED_NAMES = {"Dockerfile", "Fastfile", "Appfile", "release-please-config.json", ".release-please-manifest.json"}
REVIEWED_PREFIXES = (".githooks/", "ops/grafana/")

# First matching prefix wins, so narrower prefixes come first.
SLICES = [
    ("scripts-ops-ci", [".github/workflows/", ".githooks/", "apps/ios/fastlane/", "ops/", "scripts/"]),
    ("apps-shells", ["apps/admin/", "apps/compose/", "apps/ios/"]),
    ("build-config", [
        "apps/baselineprofile/", "build-logic/", "build.gradle.kts", "config/", "detekt-rules/", "gradle/",
        "gradle.properties", "settings.gradle.kts", "telemetry.properties", "versions.properties",
        "release-please-config.json", ".release-please-manifest.json",
    ]),
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


def is_reviewed(path):
    if "/build/" in path:
        return False
    return (
        path.endswith(REVIEWED_EXTENSIONS)
        or path.rsplit("/", 1)[-1] in REVIEWED_NAMES
        or (path.startswith(REVIEWED_PREFIXES) and not path.endswith(".md"))
    )


def tracked_files():
    out = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout
    return [f for f in out.splitlines() if is_reviewed(f)]


def existing_ticks(text):
    return set(re.findall(r"- \[x\] `(.+)`", text))


def slice_of(path):
    for name, prefixes in SLICES:
        if any(path.startswith(p) for p in prefixes):
            return name
    return None


def render(paths, ticked):
    files = {name: [] for name, _ in SLICES}
    for path in paths:
        name = slice_of(path)
        if name:
            files[name].append(path)
    lines = [HEADER]
    for name, _ in SLICES:
        in_slice = sorted(files[name])
        lines.append(f"\n## {name} ({len(in_slice)} files)\n")
        lines += [f"- [{'x' if p in ticked else ' '}] `{p}`" for p in in_slice]
    return "\n".join(lines) + "\n", sum(len(v) for v in files.values())


def main():
    ticked = existing_ticks(LEDGER.read_text()) if LEDGER.exists() else set()
    text, count = render(tracked_files(), ticked)
    LEDGER.write_text(text)
    print(f"{count} files in {len(SLICES)} slices")


if __name__ == "__main__":
    main()
