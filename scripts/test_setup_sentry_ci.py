"""Unit tests for setup_sentry_ci.sh's token check.

    python3 -I -m unittest discover -s scripts -p 'test_setup_sentry_ci.py'

The script is interactive, so this lifts its token_status function out and runs
it against a fake curl that records what it was given.
"""

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "setup_sentry_ci.sh"
FAKE_CURL = """#!/bin/sh
printf '%s\\n' "$@" > "$RECORD_DIR/argv"
cat > "$RECORD_DIR/stdin"
printf '200'
"""


class TokenStatusTest(unittest.TestCase):

    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.dir)
        bin_dir = self.dir / "bin"
        bin_dir.mkdir()
        curl = bin_dir / "curl"
        curl.write_text(FAKE_CURL)
        curl.chmod(0o755)
        self.path = f"{bin_dir}:{os.environ['PATH']}"

    def token_status(self, token, org):
        function = re.search(r"^token_status\(\) \{.*?^\}", SCRIPT.read_text(), re.S | re.M).group(0)
        result = subprocess.run(
            ["bash", "-c", f'{function}\ntoken_status "$1" "$2"', "bash", token, org],
            env={"PATH": self.path, "RECORD_DIR": str(self.dir)}, capture_output=True, text=True, timeout=30,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_the_token_never_reaches_curls_command_line(self):
        self.assertEqual(self.token_status("sntrys_secret_value", "my-org"), "200")
        argv = (self.dir / "argv").read_text()
        self.assertNotIn("sntrys_secret_value", argv)
        self.assertIn("https://sentry.io/api/0/organizations/my-org/chunk-upload/", argv)
        self.assertIn('header = "Authorization: Bearer sntrys_secret_value"', (self.dir / "stdin").read_text())


if __name__ == "__main__":
    unittest.main()
