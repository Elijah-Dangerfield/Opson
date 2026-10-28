"""Unit tests for seed_dev_config.py.

    python3 -I -m unittest discover -s scripts -p 'test_seed_dev_config.py'
"""

import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.error import HTTPError

from seed_dev_config import REGISTRY, missing_values, seed

REGISTRY_SAMPLE = [
    {"path": "release.barcode", "type": "boolean", "default": False},
    {"path": "release.voice", "type": "boolean", "default": False},
    {"path": "upgrade.maintenanceMode", "type": "string", "default": "off"},
    {"path": "telemetry.enabled", "type": "boolean", "default": True},
    {"path": "config.refreshThrottleMs", "type": "long", "default": 300000},
]


class MissingValuesTest(unittest.TestCase):

    def test_new_release_flags_go_on_and_the_throttle_goes_to_zero(self):
        self.assertEqual(
            missing_values(REGISTRY_SAMPLE, set()),
            {"release.barcode": True, "release.voice": True, "config.refreshThrottleMs": 0},
        )

    def test_a_row_dev_already_has_is_left_alone(self):
        # Someone set release.voice false on dev to test the off path.
        existing = {"release.voice", "config.refreshThrottleMs"}
        self.assertEqual(missing_values(REGISTRY_SAMPLE, existing), {"release.barcode": True})

    def test_non_release_booleans_are_not_touched(self):
        self.assertNotIn("telemetry.enabled", missing_values(REGISTRY_SAMPLE, set()))

    def test_the_committed_registry_has_release_flags_to_seed(self):
        registry = json.loads(REGISTRY.read_text())
        self.assertTrue(any(path.startswith("release.") for path in missing_values(registry, set())))


class FakeAdminServer(BaseHTTPRequestHandler):
    existing = []
    requests = []
    token = "dev-token"

    def log_message(self, *args):
        pass

    def _reply(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _authorized(self):
        if self.headers.get("X-Admin-Token") == self.token:
            return True
        self._reply(401, {"code": "unauthorized"})
        return False

    def do_GET(self):
        if not self._authorized():
            return
        FakeAdminServer.requests.append(("GET", self.path, None))
        self._reply(200, {"flags": [{"path": path, "value": False, "rules": []} for path in self.existing]})

    def do_PUT(self):
        if not self._authorized():
            return
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        FakeAdminServer.requests.append(("PUT", self.path, body))
        self._reply(200, {})


class SeedTest(unittest.TestCase):

    def setUp(self):
        FakeAdminServer.requests = []
        FakeAdminServer.existing = ["release.voice"]
        self.server = HTTPServer(("127.0.0.1", 0), FakeAdminServer)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def test_writes_only_what_dev_lacks(self):
        written = seed(self.base, "dev-token", REGISTRY_SAMPLE)
        self.assertEqual(written, ["release.barcode", "config.refreshThrottleMs"])
        self.assertEqual(
            [request for request in FakeAdminServer.requests if request[0] == "PUT"],
            [
                ("PUT", "/v1/admin/config/flags/release.barcode", {"value": True}),
                ("PUT", "/v1/admin/config/flags/config.refreshThrottleMs", {"value": 0}),
            ],
        )

    def test_a_wrong_token_raises_rather_than_reporting_nothing_to_do(self):
        with self.assertRaises(HTTPError) as caught:
            seed(self.base, "stale-token", REGISTRY_SAMPLE)
        caught.exception.close()
        self.assertEqual(caught.exception.code, 401)
        self.assertEqual(FakeAdminServer.requests, [])


if __name__ == "__main__":
    unittest.main()
