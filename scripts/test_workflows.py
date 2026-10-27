"""Checks on workflows that only run on a release or a push to main.

release.yml has a job that only runs on a real release, so a mistake in it
waits until the day it matters, and a deploy workflow's mistake shows up on
main after the merge. These read the workflow files and run the helpers they
call, so the mistake shows up on the PR instead.

    python3 -I -m unittest discover -s scripts -p 'test_workflows.py' -v
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import store_release_notes
import verify_release_config

ROOT = Path(__file__).resolve().parent.parent
WORKFLOWS = ROOT / ".github" / "workflows"


def workflow(name: str) -> str:
    return (WORKFLOWS / name).read_text()


class RubyVersionTest(unittest.TestCase):
    # Gemfile.lock resolves excon 1.8.0, whose gemspec needs Ruby >= 3.3.0. A job
    # on an older Ruby dies in `bundle install` before fastlane starts.
    MINIMUM = (3, 3)

    def test_every_fastlane_job_runs_a_ruby_the_bundle_installs_on(self):
        found = []
        for path in sorted(WORKFLOWS.glob("*.yml")):
            for version in re.findall(r"ruby-version:\s*['\"]?([0-9.]+)", path.read_text()):
                found.append((path.name, version))
                major, minor = (int(part) for part in version.split(".")[:2])
                self.assertGreaterEqual((major, minor), self.MINIMUM, f"{path.name} sets up Ruby {version}")
        self.assertTrue(found, "no workflow sets up Ruby; this test is reading the wrong files")

    def test_release_and_beta_agree_on_one_ruby(self):
        versions = {
            version
            for name in ("release.yml", "beta.yml")
            for version in re.findall(r"ruby-version:\s*['\"]?([0-9.]+)", workflow(name))
        }
        self.assertEqual(len(versions), 1, f"fastlane runs on more than one Ruby: {sorted(versions)}")


class StoreReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)

    def write(self, version: str, text: str):
        (self.root / "release-notes").mkdir(exist_ok=True)
        (self.root / "release-notes" / f"{version}.txt").write_text(text, encoding="utf-8")

    def test_a_written_file_is_the_notes(self):
        self.write("0.2.0", "Barcode scanning.\n")
        self.assertEqual(store_release_notes.notes_for("0.2.0", 500, self.root), "Barcode scanning.")

    def test_no_file_ships_the_generic_line_not_another_version(self):
        self.write("0.1.0", "Last release's notes.")
        self.assertEqual(store_release_notes.notes_for("0.2.0", 500, self.root), "Small fixes.")

    def test_an_empty_file_ships_the_generic_line(self):
        self.write("0.2.0", "  \n")
        self.assertEqual(store_release_notes.notes_for("0.2.0", 500, self.root), "Small fixes.")

    def test_cut_to_the_limit_in_characters_not_bytes(self):
        self.write("0.2.0", "é" * 600)
        notes = store_release_notes.notes_for("0.2.0", 500, self.root)
        self.assertEqual(notes, "é" * 500)

    def test_no_store_text_comes_from_the_github_release_body(self):
        # release-please's changelog: markdown, shas and repo links, and
        # ios-submit used to hand it to the App Store "What's New".
        release = workflow("release.yml")
        self.assertNotIn("--json body", release)
        self.assertEqual(release.count("python3 -I scripts/store_release_notes.py"), 3, "Play, TestFlight and the App Store")


class VerifyReleaseConfigTest(unittest.TestCase):
    GOOD_SUPABASE = "supabase.prod.projectId=abc\nsupabase.prod.anonKey=sb_publishable_x\n"

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)

    def write(self, name: str, text: str):
        (self.root / name).write_text(text, encoding="utf-8")

    def test_the_committed_files_pass(self):
        self.assertEqual(verify_release_config.problems(ROOT, {}), [])

    def test_a_committed_dsn_and_project_pass(self):
        self.write("telemetry.properties", "# comment\nsentry.dsn=https://key@o1.ingest.sentry.io/2\n")
        self.write("supabase.properties", self.GOOD_SUPABASE)
        self.assertEqual(verify_release_config.problems(self.root, {}), [])

    def test_a_blank_dsn_fails(self):
        self.write("telemetry.properties", "sentry.dsn=\n")
        self.write("supabase.properties", self.GOOD_SUPABASE)
        found = verify_release_config.problems(self.root, {})
        self.assertEqual(len(found), 1)
        self.assertIn("Sentry DSN", found[0])

    def test_the_dsn_can_come_from_the_environment(self):
        self.write("supabase.properties", self.GOOD_SUPABASE)
        self.assertEqual(verify_release_config.problems(self.root, {"SENTRY_DSN": "https://k@o1/2"}), [])

    def test_no_supabase_properties_fails_both_values(self):
        self.write("telemetry.properties", "sentry.dsn=https://k@o1/2\n")
        found = verify_release_config.problems(self.root, {})
        self.assertEqual(len(found), 2)
        self.assertTrue(all("supabase" in problem for problem in found))

    def test_the_build_logic_placeholder_fails(self):
        self.write("telemetry.properties", "sentry.dsn=https://k@o1/2\n")
        self.write(
            "supabase.properties",
            "supabase.prod.projectId=set-supabase-projectId-in-local-properties\nsupabase.prod.anonKey=k\n",
        )
        found = verify_release_config.problems(self.root, {})
        self.assertEqual(len(found), 1)
        self.assertIn("supabase.prod.projectId", found[0])

    def test_missing_grafana_credentials_warn_and_name_each_one(self):
        home = self.root / "home"
        found = verify_release_config.warnings(self.root, {"GRAFANA_OTLP_BASE_URL": "https://otlp"}, home)
        self.assertEqual(len(found), 1)
        self.assertIn("GRAFANA_OTLP_INSTANCE_ID, GRAFANA_LOGS_WRITE_TOKEN", found[0])
        self.assertNotIn("GRAFANA_OTLP_BASE_URL", found[0])

    def test_grafana_credentials_in_local_properties_count(self):
        self.write(
            "local.properties",
            "grafana.otlpBaseUrl=https://otlp\ngrafana.otlpInstanceId=1\ngrafana.logsWriteToken=glc_x\n",
        )
        self.assertEqual(verify_release_config.warnings(self.root, {}, self.root / "home"), [])

    def test_the_guard_steps_are_handed_the_grafana_secrets(self):
        for name in ("release.yml", "beta.yml"):
            guard = workflow(name).split("run: python3 -I scripts/verify_release_config.py")[0].rsplit("- name:", 1)[1]
            for secret in ("GRAFANA_OTLP_BASE_URL", "GRAFANA_OTLP_INSTANCE_ID", "GRAFANA_LOGS_WRITE_TOKEN"):
                self.assertIn(f"secrets.{secret}", guard, name)

    def test_every_store_build_runs_it(self):
        for path in (WORKFLOWS / "release.yml", WORKFLOWS / "beta.yml", ROOT / "scripts" / "beta_ios.sh"):
            self.assertIn("scripts/verify_release_config.py", path.read_text(), path.name)


class AndroidSentryTest(unittest.TestCase):
    def test_a_missing_token_is_a_warning_not_a_silent_skip(self):
        release = workflow("release.yml")
        warning = re.search(r"if: env\.SENTRY_AUTH_TOKEN == ''\s+run: echo \"::warning::SENTRY_AUTH_TOKEN", release)
        self.assertIsNotNone(warning, "the Sentry step skips on a missing token with nothing to say so")

    def test_the_mapping_is_left_to_the_gradle_plugin(self):
        # A hand-run upload-proguard has no ProGuard UUID to match against, so
        # it associates with nothing and still reports success.
        self.assertNotRegex(workflow("release.yml"), r"sentry/cli@\S+ upload-proguard")


class LegalSyncTest(unittest.TestCase):
    def test_every_step_that_needs_the_site_token_waits_for_it(self):
        # Unset, the checkout died with "Input required and not supplied: token"
        # on every push to legal/.
        steps = re.split(r"\n      - ", workflow("legal-sync.yml"))
        using = [step for step in steps if "secrets.NIGHTJAR_SITE_TOKEN" in step and "id: token" not in step]
        self.assertEqual(len(using), 2, "the site checkout and the pull request")
        for step in using:
            self.assertIn("if: steps.token.outputs.configured == 'true'", step, step.splitlines()[0])


FASTFILE = ROOT / "apps" / "ios" / "fastlane" / "Fastfile"

# Loads the Fastfile's helpers without fastlane: the DSL calls at its top level
# become no-ops and UI prints what it was told, so a helper can run on its own.
FASTFILE_HARNESS = """
def default_platform(*); end
def platform(*); end
module UI
  def self.important(message) = puts("important: #{message}")
  def self.user_error!(message) = raise(message)
end
def sh(command) = puts("sh: #{command}")
load ARGV.fetch(0)
"""


@unittest.skipUnless(shutil.which("ruby"), "needs ruby")
class FastfileTest(unittest.TestCase):
    def ruby(self, code: str, env: dict[str, str]) -> str:
        result = subprocess.run(
            ["ruby", "-e", FASTFILE_HARNESS + code, str(FASTFILE)],
            env={"PATH": os.environ["PATH"], **env}, capture_output=True, text=True, timeout=60,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_sentry_release_is_the_one_the_app_reports_under(self):
        # The app tags events opson@<versionName>+<buildNumber> (AppTelemetry.kt).
        # The release used to carry the CFBundleVersion timestamp instead, so it
        # had the commits and none of the events.
        output = self.ruby(
            "puts sentry_release_name",
            {"VERSION_NAME_OVERRIDE": "0.2.0", "BUILD_NUMBER_OVERRIDE": "702"},
        )
        self.assertEqual(output.strip(), "opson@0.2.0+702")

    def test_a_missing_sentry_token_says_so(self):
        output = self.ruby("upload_sentry_release", {"SENTRY_AUTH_TOKEN": ""})
        self.assertIn("important: SENTRY_AUTH_TOKEN not set", output)
        self.assertNotIn("sh:", output)


if __name__ == "__main__":
    unittest.main()
