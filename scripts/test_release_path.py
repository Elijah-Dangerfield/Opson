"""Checks on the release path that no build runs until a tag is pushed.

release.yml has a job that only runs on a real release, so a mistake in it
waits until the day it matters. These read the workflow files and run the
helpers they call, so the mistake shows up on the PR instead.

    python3 -I -m unittest discover -s scripts -p 'test_release_path.py' -v
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
