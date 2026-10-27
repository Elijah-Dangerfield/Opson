"""Unit tests for review_ledger.py.

    python3 -I -m unittest discover -s scripts -p 'test_review_ledger.py'
"""

import unittest

from review_ledger import is_reviewed, render, slice_of


class ReviewedFilesTest(unittest.TestCase):

    def test_config_without_a_source_extension_is_reviewed(self):
        for path in (
            "apps/server/Dockerfile", "apps/server/fly.prod.toml", "apps/ios/fastlane/Fastfile",
            "gradle/libs.versions.toml", "gradle.properties", "apps/compose/proguard-rules.pro",
            ".githooks/pre-push", "ops/grafana/foods.json", "apps/ios/Configuration/Config.xcconfig",
            "release-please-config.json",
        ):
            self.assertTrue(is_reviewed(path), path)

    def test_generated_and_data_files_are_not(self):
        for path in (
            "libraries/storage/impl/schemas/com.opson.libraries.storage.impl.db.AppDatabase/9.json",
            "website/package-lock.json", "docs/design/v3/manifest.json", "apps/compose/build/x.kt",
            "ops/grafana/README.md",
        ):
            self.assertFalse(is_reviewed(path), path)

    def test_release_and_build_config_land_in_a_slice(self):
        self.assertEqual("scripts-ops-ci", slice_of("apps/ios/fastlane/Fastfile"))
        self.assertEqual("scripts-ops-ci", slice_of(".githooks/commit-msg"))
        self.assertEqual("apps-shells", slice_of("apps/ios/Configuration/Config.xcconfig"))
        self.assertEqual("build-config", slice_of("gradle/libs.versions.toml"))
        self.assertEqual("build-config", slice_of("gradle.properties"))
        self.assertEqual("server", slice_of("apps/server/fly.toml"))


class RenderTest(unittest.TestCase):

    def test_ticks_are_kept_and_counts_follow_the_files(self):
        text, count = render(["scripts/a.py", "scripts/b.py", "apps/server/Dockerfile"], {"scripts/b.py"})
        self.assertEqual(3, count)
        self.assertIn("## scripts-ops-ci (2 files)", text)
        self.assertIn("- [ ] `scripts/a.py`", text)
        self.assertIn("- [x] `scripts/b.py`", text)
        self.assertIn("- [ ] `apps/server/Dockerfile`", text)


if __name__ == "__main__":
    unittest.main()
