"""Checks on the release path that no build runs until a tag is pushed.

release.yml has a job that only runs on a real release, so a mistake in it
waits until the day it matters. These read the workflow files and run the
helpers they call, so the mistake shows up on the PR instead.

    python3 -I -m unittest discover -s scripts -p 'test_release_path.py' -v
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
