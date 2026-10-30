"""Unit tests for review_bundle.py.

    python3 -I -m unittest discover -s scripts -p 'test_review_bundle.py'
"""

import json
import tempfile
import unittest
from pathlib import Path

from review_bundle import FORMAT, Missing, check, decision_problem, download_photos

URL = "https://opson.example/v1/admin/food-submissions/s1/photos/"


def bundle(*items):
    return {"format": FORMAT, "env": "Dev", "exportedAt": "2026-11-02T09:00:00.000Z", "items": list(items)}


def item(item_id="s1", decision=None):
    return {
        "id": item_id,
        "name": "Granola bar",
        "photos": {kind: URL + kind for kind in ("front", "label", "ingredients")},
        "decision": decision or {"action": None},
    }


class PhotosTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = Path(self.dir.name) / "opson-review-dev-2026-11-02.json"
        self.path.write_text(json.dumps(bundle(item())))
        self.asked = []

    def tearDown(self):
        self.dir.cleanup()

    def fake(self, url, token):
        self.asked.append((url, token))
        if url.endswith("ingredients"):
            raise Missing(url)
        return b"jpeg:" + url.rsplit("/", 1)[1].encode()

    def test_photos_land_beside_the_bundle_and_are_recorded(self):
        written, problems = download_photos(self.path, "secret", get=self.fake)

        self.assertEqual(written, 2)
        self.assertEqual(problems, [], "a missing ingredients photo is not a problem")
        saved = json.loads(self.path.read_text())["items"][0]["localPhotos"]
        self.assertEqual(saved, {
            "front": "opson-review-dev-2026-11-02-photos/s1-front.jpg",
            "label": "opson-review-dev-2026-11-02-photos/s1-label.jpg",
        })
        self.assertEqual((Path(self.dir.name) / saved["label"]).read_bytes(), b"jpeg:label")
        self.assertTrue(all(token == "secret" for _, token in self.asked))

    def test_a_missing_label_photo_is_reported(self):
        def no_label(url, token):
            if url.endswith("label") or url.endswith("ingredients"):
                raise Missing(url)
            return b"x"

        _, problems = download_photos(self.path, "secret", get=no_label)
        self.assertEqual(problems, ["s1: no label photo on the server"])

    def test_a_file_that_is_not_a_bundle_is_refused(self):
        self.path.write_text(json.dumps({"format": "something else"}))
        with self.assertRaises(SystemExit):
            download_photos(self.path, "secret", get=self.fake)


class CheckTest(unittest.TestCase):

    def test_decisions_are_counted_and_problems_named(self):
        counts, problems = check(bundle(
            item("a", {"action": "approve"}),
            item("b", {"action": "Reject", "reason": "Not a nutrition label", "code": "not_food"}),
            item("c", {"action": "reject"}),
            item("d"),
            item("a", {"action": "skip"}),
        ))
        self.assertEqual(counts, {"approve": 1, "reject": 1, "skip": 0, "undecided": 1})
        self.assertEqual(problems, [
            "c (Granola bar): A reject needs a reason.",
            "a (Granola bar): Listed twice.",
        ])

    def test_the_rules_match_the_console(self):
        self.assertIsNone(decision_problem({"action": "approve", "food": {"perServing": {"energy_kcal": 200}}}))
        self.assertIn("Unknown action", decision_problem({"action": "maybe"}))
        self.assertIn("Unknown reject code", decision_problem({"action": "reject", "reason": "x", "code": "nope"}))
        self.assertEqual(decision_problem({"action": "approve", "code": "blurry"}), "A code only goes with a reject.")
        self.assertEqual(decision_problem({"action": "reject", "reason": "x" * 501}), "The reason is over 500 characters.")


if __name__ == "__main__":
    unittest.main()
