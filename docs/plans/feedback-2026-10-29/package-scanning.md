# Package scanning: how to make it great

## Recommendation

Opson already has the hard half: a server that reads front and label photos
with Claude, converts units in code, runs arithmetic checks, lets the person
correct the reading, saves it as their food at once and queues it for review.
What it lacks is the front half (a camera that helps you take a usable photo)
and the back half (a review pipeline that scales past one person approving
every row).

So the plan is:

1. **Guided capture on the device.** A live camera that finds the barcode, the
   front and the nutrition panel, checks sharpness, glare and framing before it
   takes the shot, and runs on-device OCR so the form can pre-fill instantly
   and work offline.
2. **Keep the cloud LLM as the reader of record, but cascade it.** Read first
   with a cheaper Claude model plus the on-device OCR text, escalate to the
   current top model (`claude-fable-5-1`) only when checks fail, confidence is
   low, or the two readings disagree. That should cut the average cost per
   scan by well over half without lowering what reaches the catalog.
3. **Ground every number.** A value the model reports that does not appear in
   the OCR tokens is flagged. This is the cheapest strong check available.
4. **Triage review by risk and demand.** Auto-approve only when independent
   evidence agrees; send everything else to the queue ordered by how many
   people are scanning that barcode.
5. **Stay free for everyone,** as the owner decided. Keep the costs bounded by
   the existing per-user limits and by the cascade.

The phased plan is at the end.

## What exists today

From `docs/plans/screens/submit-food.md`, `docs/decisions.md` (2026-10-27 and
2026-10-28) and the server code:

| Piece | Where | State |
|---|---|---|
| Entry points | Barcode miss, empty search, "Can't find it?" under results, + sheet "Packaged food", Foods › Add a food | Built, `release.submitFood` |
| Capture | Two slots (front, label), Take photo or Choose from library, Retake. Shrunk to a 1568 px upright JPEG (`MealPhotoPicker`) | Built. No live guidance, no quality check, no barcode or ingredients slot |
| Reading | `POST /v1/foods/labels/parse`, `AnthropicLabelParser`, model `claude-fable-5-1` at effort high, structured JSON with a per-field confidence, fallback to the photo model on a 400 | Built. About 10 to 15 cents a read by the code's own estimate, up to a minute |
| Conversions | `LabelOutput.kt`: kJ to kcal, salt to sodium (÷ 2.5), %DV to amounts on US panels, ounces to grams, carb basis US or EU | Built |
| Checks | `LabelChecks` (client and server, same tests): energy within 15 kcal or 12% of macros, per serving against per 100, sodium a thousand times off, impossible amounts, kJ read as kcal | Built |
| Limits | `FoodLabelLimiter`: 10 reads per 10 minutes, 40 a day; 5 submissions per 10 minutes, 20 a day | Built |
| Storage | Pending `foods` row (source `user_submitted`, tier 4), photos in `food_submission_photos`, deleted 30 days after review | Built |
| Review | `apps/admin` ReviewView: both photos, every field as read, as sent and as now, checks, approve or reject with reason; barcode link suggestions from miss-then-search pairs | Built, one reviewer |
| Marks | Unverified for the submitter until approved, then "From Opson's food database" | Built |

## 1. Capture UX

### The flow

One guided sequence, each step skippable:

1. **Barcode.** Already scanned on a barcode miss; otherwise the live camera
   reads it while the person frames the front. No separate step.
2. **Front.** Name and brand. Accept a slightly soft image.
3. **Nutrition panel.** The step that matters. Strict quality gate.
4. **Ingredients (optional).** Useful later for allergens and for review.
   Never required.

A curved can or bag: let the panel step take two overlapping shots and send
both. Don't attempt panorama stitching.

### Quality checks before the shutter

Run on the live preview, auto-capture when all pass for about half a second,
with a manual shutter always available:

| Check | iOS | Android |
|---|---|---|
| Panel found | `RecognizeDocumentsRequest` (iOS 26) returns table structure ([WWDC25 session 272](https://developer.apple.com/videos/play/wwdc2025/272/)); or text recognition finding "Nutrition Facts", "Nutrition information", "Nährwerte", "Valeurs nutritionnelles" | ML Kit Text Recognition v2 (on-device, free) with the same keywords |
| Whole panel in frame | The table's bounding box is inside the frame with a margin | Text block bounds |
| Sharp enough | Variance of the Laplacian on the panel crop; iOS 26 Vision also detects a smudged lens (same session) | Laplacian variance |
| Text big enough | Median recognised line height over about 20 px at capture resolution | Same |
| Glare | Share of blown-out pixels inside the panel box | Same |
| Steady | Device motion low | Same |

Copy is a short coaching line, one at a time: "Move closer", "Tilt to cut the
glare", "Hold still". No scores.

### After capture

- Show the crop with the recognised text faintly overlaid so the person can
  see it was read.
- **Retake** stays one tap away. If the server later reports a blocking check
  on a value, offer "Retake the label" next to it rather than only "edit".
- Keep the current library import for people adding foods from photos taken
  earlier.

## 2. Where extraction runs

### Options compared

Costs assume two photos at about 5,000 image tokens, a 2,000 token prompt and
a 1,500 token answer (more with thinking). Claude prices from Anthropic's
current list (Fable 5.1 $10/$50, Opus 5.5 $4/$20, Sonnet 5.5 $2/$10, Haiku 5.5
$0.10/$0.50 per million input/output tokens).

| Option | Accuracy on labels | Cost per scan | Latency | Privacy | Offline |
|---|---|---|---|---|---|
| **Apple Vision OCR / RecognizeDocuments** | Good text, no meaning; tables on iOS 26 | Free | Under 1 s | Stays on phone | Yes |
| **ML Kit Text Recognition v2** | Good text, no meaning | Free | Under 1 s | Stays on phone | Yes |
| **Apple Foundation Models with images** | Unknown on dense tables; 4K to 8K context shared with image tokens | Free | Seconds | Stays on phone | Yes. iOS 27 betas only, Apple Intelligence devices ([WWDC26 session 241](https://developer.apple.com/videos/play/wwdc2026/241/), [Callstack](https://www.callstack.com/blog/on-device-ai-after-wwdc-2026-whats-new)) |
| **Gemini Nano via ML Kit Prompt API** | Unknown; 4,000 input and 255 output tokens reported | Free | Seconds | On phone | Yes, but few devices (Pixel 10 best) ([Android blog](https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html)) |
| **Claude Fable 5.1** (today) | Best available; already proven on Opson's labels | About 10 to 15¢ | 20 to 60 s with high effort | Photos to Anthropic | No |
| **Claude Sonnet 5.5** | Very good on clear photos | About 4¢ | Several seconds | Photos to Anthropic | No |
| **Claude Haiku 5.5** | Good on clear, flat panels | Under 1¢ | Fast | Photos to Anthropic | No |
| **Gemini 2.5 Flash** | Good | About 1¢ ($0.30 in / $2.50 out per M) ([pricing summary](https://pricepertoken.com/pricing-page/model/google-gemini-2.5-flash)) | Fast | Photos to Google; second vendor | No |
| **AWS Textract Tables** | Table cells, no nutrition meaning | 1.5¢ a page ([AWS](https://aws.amazon.com/textract/pricing/)) | Seconds | AWS | No |
| **Google Document AI Form Parser** | As Textract | About 3¢ a page, plus processor hosting fees ([summary](https://www.erpresearch.com/erp-add-ons/ocr/google-document-ai/pricing)) | Seconds | Google | No |
| **Passio Nutrition AI** | Label endpoint returns structured nutrients | $99/month for 1M tokens; a photo uses 20k to 30k tokens, so about 2 to 3¢ ([Passio](https://www.passio.ai/pricing)) | Fast | Third party; mixes OFF data into results | No |
| **Nutritionix** | No label OCR found; database API from $499/month ([CalorieAPI](https://calorieapi.com/blog/nutritionix-api-alternative)) | n/a | n/a | n/a | n/a |
| **FatSecret** | Image recognition for plates, priced on request ([FatSecret editions](https://platform.fatsecret.com/api-editions)) | Quote | n/a | n/a | n/a |

Open Food Facts itself runs Google Cloud Vision OCR and then a fine-tuned
LayoutLMv3 model ("Nutri-Sight") over each uploaded photo, with human
validation for low-confidence guesses
([Robotoff docs](https://openfoodfacts.github.io/robotoff/references/predictions/nutrient-extraction/)).
It is a good reference design, and it confirms that OCR plus a
layout-aware model plus people is the shape that works at scale.

### What to do

**Hybrid, with the phone doing the cheap work and Claude doing the judging.**

1. **On the phone:** OCR the panel (Vision on iOS, ML Kit on Android). Use it
   for the quality gate, an instant draft of the form, and offline entry. Send
   the OCR tokens with their positions alongside the photos.
2. **First read:** `claude-sonnet-5-5` (or `claude-haiku-5-5` once an eval
   shows it holds), given both photos and the OCR text, same structured schema
   as today.
3. **Code checks** (existing `LabelChecks`) plus the new **grounding check**:
   every number in the reading should match an OCR token after normalising
   decimal commas and units.
4. **Escalate to `claude-fable-5-1`** when any of: a blocking check fails; any
   headline field (kcal, protein, carbs, fat, serving) is under 0.7
   confidence; grounding fails on a headline field; the OCR found a second
   column ("as prepared", per 100 and per serving both present) and the read
   used the wrong one. Expect a minority of scans to escalate.
5. **Never block on the cloud.** Offline, the person fills the form from the
   OCR draft and their own photo; the submission waits in the outbox
   (`docs/practices/outbox.md`) and is read when the phone is back online.

Measure before switching defaults: build an eval of 200 real labels from the
review queue (photos plus approved values), score field accuracy and check
pass rate per model, and pick the cheapest first-read model whose headline
field accuracy matches Fable's within a point. The `claude-api` skill's
`build-eval` flow fits.

**On-device LLMs later.** When iOS 27 ships, try Foundation Models with the
image attachment as an offline structurer. Use it only to pre-fill; the
catalog still takes the cloud read.

**Second vendor.** Gemini Flash is cheap and good, but adding a second
processor of health-adjacent photos means another privacy disclosure and
another key. Not worth it while the Claude cascade costs a few cents.

## 3. Data validation

Much of this already exists; listed so the gaps are visible.

| Rule | Status |
|---|---|
| Atwater: energy ≈ 4 × protein + 4 × carbs + 9 × fat (+ 2 × fiber in the EU, 7 × alcohol, 2.4 × polyols) within 15 kcal or 12% | Built (`MacroEnergyCheck`; polyols at 2.4) |
| kJ vs kcal (1 kcal = 4.184 kJ); kJ read as kcal | Built |
| Per serving vs per 100 g agree with the serving size | Built |
| Salt to sodium (÷ 2.5) on EU and UK panels | Built |
| US total carbohydrate includes fiber; EU carbohydrate excludes it | Built (`CarbBasis`) |
| Sodium a thousand times off (g vs mg) | Built |
| Impossible amounts (part above whole, over 100 g per 100 g) | Built |
| Decimal comma ("2,5 g") | Check the parser handles it from OCR text too |
| US rounding: calories to 5 under 50 and to 10 above; fat to 0.5 g up to 5 g, then 1 g; under 5 kcal may read 0 ([FDA Food Labeling Guide](https://www.fda.gov/files/food/published/Food-Labeling-Guide-(PDF).pdf)) | Add: widen tolerances for small servings, so a 0 kcal drink with 0.4 g carbs is not flagged |
| "As sold" vs "as prepared" columns | Add: schema field for which column was read; default "as sold" |
| Multiple serving columns (per bar, per 2 bars) | Add: keep the column the person chose; store both when present |
| Dual-language panels (Canada) | Add: prompt note; grounding check covers it |
| Serving in household units only ("1 cup") | Add: require grams or ml, or flag for review |
| Barcode checksum (EAN-13, UPC-A, EAN-8) | Add on the phone before submission |
| Same barcode submitted twice with different values | Add: route both to review side by side |

## 4. Moderation and review

The current queue is right for a few submissions a day. It needs triage to
survive launch.

**Auto-approve, narrowly.** Approve without a person only when all hold:

- No blocking check and no grounding failure.
- Two independent reads agree on every headline field (the first read and the
  escalation read, or two different people's submissions of the same barcode).
- The submitter has a history of approved foods and no rejections in the last
  30 days.
- The barcode is not already in USDA Branded or another tier 1 to 3 source.

Even then, mark it "Checked automatically" internally, and sample 5% of
auto-approvals for human review each week.

**Order the queue by impact.** Sort by how many people have scanned or
searched for that barcode (the miss-then-search pairs already exist in
`barcode_link_suggestions`), then by age. A food 40 people want is worth more
than one person's one-off.

**Reviewer tools to add:**

- Highlight on the photo where each value was read (the OCR boxes give this).
- One-key approve, and approve with edits.
- Reject reasons from a short list (blurry, not a food, duplicate, wrong
  column, offensive), which feed back to the submitter in plain words.
- A "Something looks wrong with this food" report from any food detail goes to
  the same queue (product ideas #19).
- An edit history per food, so a correction can be undone.

**Abuse.** Keep the per-user limits. Reject and rate-limit accounts that send
non-food images; a cheap classifier (the first-read model can answer "is this
a food label") runs before the expensive read.

## 5. Licensing

- **Facts are not copyrightable in the US;** a label's numbers are facts. In
  the EU a database can carry a sui generis right, which protects the
  database, not each fact. Opson reading a label photo its own user took is
  independent creation. Have counsel confirm before launch
  (`docs/legal-review-2026-10.md`).
- **Photos are the user's.** The terms need a licence from the user to store
  and use submitted photos for review and to publish the resulting food data.
  Check `legal/privacy.md` and the terms say so.
- **Keep Open Food Facts out of OPDB.** ODbL is share-alike
  (`docs/food-data-sources.md`, "Licence: ODbL, kept contained"). Never
  pre-fill a submission from an OFF row, never copy OFF values into a
  `user_submitted` row, and resolve the open product-notes item by caching
  live OFF answers into `off_foods`, not `foods`.
- **Contributing back (optional, later).** An opt-in "Also share with Open Food
  Facts" would help the commons. Data sent there becomes ODbL there; Opson's
  own copy stays Opson's because it came from the user, not from OFF. Ask
  counsel before building it.

## 6. Privacy

- Strip EXIF and location from every photo. Android re-encodes through
  `AndroidMealPhotoPicker` (reads EXIF for orientation, then re-encodes); confirm
  the output carries no GPS, and check the iOS path the same way.
- Faces or hands can end up in frame. Crop to the detected panel before upload
  where the panel was found, and keep deleting photos 30 days after review.
- Telemetry: send which model answered, whether it escalated, latency and
  check codes. Never the values or the OCR text.

## 7. Incentives

Opson's own rules rule out streaks, scores and rewards for eating
(`docs/review/product-ideas-2026-10-27.md`). Contribution is different from
eating, but the same restraint fits the brand.

- **The food is yours immediately.** This is the main incentive and it already
  works: the food is logged and saved before review.
- **A quiet thank-you when approved:** "Your Brand X granola is now in Opson's
  food database. 23 people have used it." Only when the count is above a
  small threshold, never a leaderboard.
- **A "Foods you've added" count** on the Foods tab, opt-in.
- **Founding contributor recognition.** If the "first X users get lifetime
  Pro" plan happens (`pricing-and-pro.md`), approved submissions are a fair
  tie-breaker or a second route into it. Don't pay per food; that invites junk.
- **Ask at the right moment.** The best prompt is a barcode miss. A second is
  the empty search. Both exist. A third: after a manual custom food entry,
  "Got the pack? Add it from the label so everyone gets it."

## Phased plan

| Phase | Scope | Done when |
|---|---|---|
| **1. Measure** (days) | Eval set of 200 approved labels with photos. Score Fable 5.1, Opus 5.5, Sonnet 5.5 and Haiku 5.5 on headline fields, check pass rate, latency and cost. Log per-read cost and escalation in Grafana. | A table of accuracy and cost per model on Opson's own labels. |
| **2. Cascade** (a week) | First read on the cheapest model that holds accuracy; escalate on failed checks or low confidence; keep `ANTHROPIC_LABEL_MODEL` as the escalation model. | Average cost per scan down, headline accuracy unchanged on the eval. |
| **3. Guided capture** (two weeks) | Live camera with panel detection, quality checks, auto-capture, retake prompts from server checks, optional ingredients slot, barcode checksum. Vision on iOS, ML Kit on Android. | Fewer "blurry" rejects and fewer escalations; capture-to-form under 5 s on a clear label. |
| **4. On-device OCR draft and grounding** (a week) | OCR tokens sent with photos; grounding check on the server; offline form pre-fill and outbox. | A label can be added on a plane and reads correctly when the phone lands. |
| **5. Review at scale** (two weeks) | Queue sorted by demand, OCR box highlights, reject reasons sent to the submitter, narrow auto-approval with weekly sampling, "Something looks wrong" reports, edit history. | One reviewer clears a day's queue in under 30 minutes at launch volumes. |
| **6. Later** | Foundation Models image input on iOS 27 as an offline structurer; allergen and ingredient parsing; opt-in sharing with Open Food Facts after counsel; multiple serving columns. | Each its own decision. |

## Open questions

1. How much review time can the owner give each week at launch? That sets how
   wide auto-approval has to be.
2. Is a second AI vendor acceptable at all, if Gemini Flash turns out cheaper
   at equal accuracy?
3. Ingredients and allergens: in scope for OPDB v1 or later?
4. Should foods auto-approved by two agreeing users show the verified check, or
   a softer "Checked" mark?
