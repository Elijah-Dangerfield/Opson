Pre-check a week of Opson food submissions.

Hand this to Claude Code (the assistant the privacy policy names, with "Help improve Claude" off) with the path to a review bundle, after `scripts/review_bundle.py photos` has downloaded its photos. It runs on your own subscription. Nothing here calls an API with a key, and nothing is applied until you import the result in the admin console and confirm.

The bundle is `opson-review-<env>-<date>.json`. Each item is one food someone added from photos of its pack:

- `localPhotos`: the front, the nutrition label and sometimes the ingredients, as files next to the bundle. Look at them. Never fetch the URLs in `photos`.
- `submitted`: what the person sent. Nutrients are per serving in `perServing` (`energy_kcal`, `protein_g`, `carbs_g`, `fat_g`, `sodium_mg`, ...), sometimes per 100 g or ml in `per100`.
- `ocrLines`: the text the phone read off the label, with its confidence. `phoneDraft` is what the phone made of it.
- `checks` and `reasons`: what the server already flagged.

For each item, fill in `decision`:

- **approve** when the label photo is a real nutrition panel and the name, serving size and every headline number (calories, protein, carbs, fat) match the photo within label rounding (5 kcal, 1 g). If something is plainly misread (a 6 read as 8, kJ taken as kcal, the per 100 column used as per serving), approve and put only the fixed fields in `food`, in the same shape as `submitted`. Example: `"food": {"perServing": {"energy_kcal": 230}}`.
- **reject** with a short, plain `reason` the submitter could read, and a `code`: `blurry` (can't read it), `not_food`, `duplicate`, `wrong_column`, `wrong_values`, `fake_data` (made up or doesn't match the pack at all), `copied_data` (a screenshot or another app's numbers), `other`. Only use `fake_data` or `copied_data` when it's clear: they end that person's chance of becoming Trusted.
- **skip** when you aren't sure. Skipping is fine. A person looks at every skip.

Rules:

- Never invent a number you can't see on the photo. If a value is cut off or unreadable, skip or reject as `blurry`.
- Don't change the name or brand to your own wording. Fix them only when the front photo plainly says otherwise.
- Add a one-line `note` saying what you checked or why, for the person who confirms.
- Keep every other field of the file as it is.

Write the filled file next to the original as `<same name>-decided.json`, then run `python3 -I scripts/review_bundle.py check <that file>` and fix anything it reports. Finish with a count: approved, corrected, rejected, skipped.
