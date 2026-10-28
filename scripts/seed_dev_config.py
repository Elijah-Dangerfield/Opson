#!/usr/bin/env python3
"""Gives the dev server every release flag the app knows about, turned on.

    ADMIN_TOKEN=... python3 -I scripts/seed_dev_config.py https://opson-server-dev.fly.dev

Every `release.*` flag in apps/admin/config-manifest-registry.json defaults to
off, and nothing put new ones into dev, so a feature merged behind a flag
stayed dark on dev until someone set it by hand (four were missing on
2026-10-27). server-deploy.yml runs this after each dev deploy, and the
registry lives under apps/admin/**, which triggers that workflow, so a new
flag reaches dev on the merge that adds it.

Only flags dev has no row for are written. A flag someone set to false on dev
to test the off path stays false.

Also sets config.refreshThrottleMs to 0 when dev has no row for it, so a dev
client refetches config on every foreground instead of every five minutes
(libraries/config/README.md).

Dev only. Never point this at prod, where a release flag turns on when its
release ships.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "apps" / "admin" / "config-manifest-registry.json"
DEV_DEFAULTS = {"config.refreshThrottleMs": 0}
TIMEOUT_SECONDS = 30


def missing_values(registry: list[dict], existing: set[str]) -> dict[str, object]:
    """Path -> value for every dev default with no row on the server yet."""
    wanted = {
        entry["path"]: True
        for entry in registry
        if entry.get("type") == "boolean" and entry["path"].startswith("release.")
    }
    wanted.update(DEV_DEFAULTS)
    return {path: value for path, value in wanted.items() if path not in existing}


def call(method: str, url: str, token: str, body: dict | None = None) -> dict:
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("X-Admin-Token", token)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return json.loads(response.read() or b"{}")


def seed(base_url: str, token: str, registry: list[dict]) -> list[str]:
    """Writes the missing values and returns the paths it wrote."""
    base = base_url.rstrip("/")
    existing = {flag["path"] for flag in call("GET", f"{base}/v1/admin/config", token)["flags"]}
    written = []
    for path, value in missing_values(registry, existing).items():
        call("PUT", f"{base}/v1/admin/config/flags/{urllib.parse.quote(path, safe='')}", token, {"value": value})
        written.append(path)
    return written


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("base_url", help="the dev server, e.g. https://opson-server-dev.fly.dev")
    parser.add_argument("--registry", type=Path, default=REGISTRY)
    args = parser.parse_args()

    token = os.environ.get("ADMIN_TOKEN", "").strip()
    if not token:
        print("::error::ADMIN_TOKEN is empty, so there is no way into the dev config.")
        return 1
    registry = json.loads(args.registry.read_text(encoding="utf-8"))
    try:
        written = seed(args.base_url, token, registry)
    except urllib.error.HTTPError as error:
        # The URL and status, never the token or the body sent.
        print(f"::error::{error.code} from {error.url}. A 401 or 404 means the token and the server's ADMIN_API_TOKEN differ.")
        return 1
    except urllib.error.URLError as error:
        print(f"::error::Could not reach {args.base_url}: {error.reason}")
        return 1
    for path in written:
        print(f"::notice::dev: set {path}")
    print(f"Dev config: {len(written)} new value(s), everything else left as it was.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
