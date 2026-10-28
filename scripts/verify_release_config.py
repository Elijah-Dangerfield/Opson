#!/usr/bin/env python3
"""Refuses a store build whose release configuration would ship broken.

    python3 -I scripts/verify_release_config.py

Run before every build that can reach a store: release.yml, beta.yml and
scripts/beta_ios.sh. One script, so a TestFlight build from a laptop gets the
same checks as one from CI. 0.1.0 and the first 0.2 TestFlight build came from
a laptop, where none of release.yml's checks ran.

What it checks, with the precedence the build itself uses (build-logic,
Versioning.kt and telemetry.properties): the environment first, then the
committed file.

  - A Sentry DSN. Blank is a supported value meaning "reporting off", so a
    build without one ships dark with no warning anywhere. Fix: run
    ./scripts/setup_sentry.main.kts and commit telemetry.properties.
  - The prod Supabase project and key a release build signs in with. Missing,
    the build falls back to a placeholder host and every sign-in fails on the
    phone. Fix: commit supabase.properties.

And it warns, without failing, when the Grafana credentials are missing.
SETUP.md calls them optional, but without them app events never leave the
phone and every client panel on the Grafana boards stays empty for that build.

It can't tell whether the Supabase project is the one the prod server trusts;
that needs the server to say which project it checks tokens against.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# Versioning.kt's fallback when nothing names a project. A build carrying it
# compiles and then fails every sign-in.
PLACEHOLDER_PREFIX = "set-supabase-"


def read_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def problems(root: Path, env: dict[str, str]) -> list[str]:
    telemetry = read_properties(root / "telemetry.properties")
    supabase = read_properties(root / "supabase.properties")
    found = []

    if not (env.get("SENTRY_DSN", "").strip() or telemetry.get("sentry.dsn", "")):
        found.append(
            "No Sentry DSN, so this build would ship with crash reporting off. "
            "Run ./scripts/setup_sentry.main.kts and commit telemetry.properties (or set SENTRY_DSN)."
        )

    for env_name, key in (
        ("SUPABASE_PROD_PROJECT_ID", "supabase.prod.projectId"),
        ("SUPABASE_PROD_ANON_KEY", "supabase.prod.anonKey"),
    ):
        value = env.get(env_name, "").strip() or supabase.get(key, "")
        if not value or value.startswith(PLACEHOLDER_PREFIX):
            found.append(
                f"No {key}, so a release build signs in against a placeholder and every sign-in fails. "
                f"Commit it in supabase.properties (or set {env_name})."
            )
    return found


GRAFANA = (
    ("GRAFANA_OTLP_BASE_URL", "grafana.otlpBaseUrl"),
    ("GRAFANA_OTLP_INSTANCE_ID", "grafana.otlpInstanceId"),
    ("GRAFANA_LOGS_WRITE_TOKEN", "grafana.logsWriteToken"),
)


def warnings(root: Path, env: dict[str, str], home: Path) -> list[str]:
    """Missing Grafana credentials, looked up where Versioning.kt looks."""
    sources = [
        read_properties(root / "local.properties"),
        read_properties(root / "telemetry.properties"),
        read_properties(root / "gradle.properties"),
        read_properties(home / ".gradle" / "gradle.properties"),
    ]
    missing = [
        env_name for env_name, key in GRAFANA
        if not (env.get(env_name, "").strip() or any(source.get(key) for source in sources))
    ]
    if not missing:
        return []
    return [
        f"No {', '.join(missing)}, so this build's app events stay on the phone and the Grafana "
        "boards show nothing from it. Set the GRAFANA_* repo secrets (./scripts/setup_github_secrets.main.kts)."
    ]


def main() -> int:
    on_ci = os.environ.get("GITHUB_ACTIONS") == "true"
    for warning in warnings(ROOT, dict(os.environ), Path.home()):
        print(("::warning::" if on_ci else "warning: ") + warning)
    found = problems(ROOT, dict(os.environ))
    for problem in found:
        print(("::error::" if on_ci else "error: ") + problem)
    if found:
        return 1
    print("Release config: Sentry DSN and prod Supabase project are set.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
