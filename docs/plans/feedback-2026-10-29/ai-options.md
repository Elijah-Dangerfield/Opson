# AI options for reading packages and checking food data

Researched 2026-10-29. Builds on `package-scanning.md` (capture UX, the
validation rules table, the review triage) and does not repeat it. Prices are
from the vendors' pages on that date unless a row says otherwise.

## Recommendation

1. **Read the label on the phone first, with plain OCR.** Apple Vision
   (`RecognizeDocumentsRequest` on iOS 26, text recognition below that) and ML
   Kit Text Recognition v2 on Android. Free, under a second, offline, and on
   every device Opson supports.
2. **Turn OCR text into numbers with code, not a model.** A deterministic
   parser for the panel, then the existing `LabelChecks` (Atwater, units, per
   serving against per 100). This pre-fills the form instantly and is what
   "manual entry accepted" builds on.
3. **Send one cheap cloud read per scan: Claude Haiku 5.5, blind to the OCR.**
   About 0.15 cents a scan. Because it reads the pixels without seeing the
   phone's OCR, it is a second, independent read, and agreement between the two
   is the strongest trust signal Opson can get for the money.
4. **Escalate only on disagreement or failed checks**, to Sonnet 5.5 and then
   Fable 5.1 (today's model). Expected average cost is about 1 cent a scan
   instead of 10 to 15.
5. **Score confidence from measured signals, not the model's own number.**
   OCR confidence, check residuals, image quality, the two reads agreeing, and
   the contributor's track record.
6. **Skip downloadable on-device models for now.** A useful vision model is 1.8
   GB or more, needs two runtimes, covers a fraction of phones and has no
   measured accuracy on labels. Revisit with the decision rule at the end.
7. **"The backend AI doesn't need double checking" is not safe as a blanket
   rule.** It becomes safe for one narrow lane (two reads agree, every check
   passes, a trusted contributor) once a labelled sample shows that lane's
   error rate is under 1%, and only with ongoing random audits.

Human review time, not AI spend, is the cost that grows fastest. At 10,000
scans a month, reviewing everything is about 80 hours; the cascade's whole AI
bill is about $100. The auto-approval lane is the lever worth engineering.

## What Opson runs today

From the server code:

- `AnthropicLabelParser`: `claude-fable-5-1` at effort `high`, both photos in
  one request, structured JSON with a per-field `confidence` map. The code's
  own estimate is 10 to 15 cents a read. `ANTHROPIC_LABEL_MODEL` changes it
  without a release.
- `AnthropicEstimateProvider` (meal photos): `claude-opus-5-5` at effort `low`,
  about 2 cents a photo.
- Photos are at most 1568 px on the long edge. Claude counts an image as
  roughly width × height ÷ 750 tokens
  ([vision docs](https://platform.claude.com/docs/en/build-with-claude/vision)),
  so a 1568 × 1176 photo is about 2,450 tokens and a scan's two photos about
  4,900.
- No on-device OCR yet. iOS scans barcodes with `AVCaptureMetadataOutput`
  (no frame access); Android uses CameraX `ImageAnalysis` with ML Kit barcode
  scanning, so Android already has frames in hand.

## On-device options

| Option | Accuracy on labels | Cost | Latency | Offline | Privacy | Effort | Coverage |
|---|---|---|---|---|---|---|---|
| **Apple Vision text recognition** (`VNRecognizeTextRequest`, `RecognizeTextRequest`) | Unmeasured on labels. Text only, no meaning | Free | Under 1 s | Yes | Stays on phone | Low to medium (Kotlin cinterop, as the camera already is) | iOS 13+, every device ([levels](https://developer.apple.com/documentation/vision/vnrequesttextrecognitionlevel)) |
| **Apple `RecognizeDocumentsRequest`** | Unmeasured. Returns tables as rows and cells; Apple names nutrition labels as a target use ([docs](https://developer.apple.com/documentation/vision/recognizedocumentsrequest), [WWDC25 272](https://developer.apple.com/videos/play/wwdc2025/272/)) | Free | Under 1 s (not published) | Yes | Stays on phone | Medium | iOS 26+, 26 languages |
| **VisionKit `DataScannerViewController`** | Same engine as Live Text | Free | Live | Yes | Stays on phone | Medium; replaces the AVFoundation scanner with a UIKit controller and less control | iOS 16+, A12 or later ([isSupported](https://developer.apple.com/documentation/visionkit/datascannerviewcontroller/issupported)) |
| **Apple Foundation Models** | Unmeasured. Apple publishes no OCR or document scores ([2025 report](https://machinelearning.apple.com/research/apple-foundation-models-2025-updates)) | Free | Seconds | Yes | Stays on phone | Medium | Apple Intelligence devices only: iPhone 15 Pro, 16 and later, Air ([Apple](https://support.apple.com/121115)) |
| **Core ML or MLX with a downloaded VLM** | See the open-model table below. Nothing measured on labels | Free per scan; download and storage | Seconds; heat and battery | Yes | Stays on phone | High | RAM-limited; recent phones only |
| **ML Kit Text Recognition v2** | Unmeasured on labels | Free ([ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2)) | Under 1 s | Yes | On phone; Google gets usage metrics, not images | Low | Android: 260 KB unbundled via Play services, or 4 MB per script bundled ([Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)). Latin, Chinese, Devanagari, Japanese, Korean |
| **Gemini Nano, ML Kit Prompt API** | Unmeasured | Free | Seconds | Yes | On phone | Medium | Beta. Pixel 9 to 11, Galaxy S26, some foldables ([ML Kit GenAI](https://developers.google.com/ml-kit/genai)) |
| **MediaPipe LLM Inference / LiteRT-LM** | Unmeasured | Free | 16 to 57 tokens a second on 2024 to 2026 flagships | Yes | On phone | High | 2.6 to 4.2 GB models ([LiteRT-LM](https://developers.google.com/edge/litert-lm/overview)) |

### Apple Foundation Models in detail

- On iOS 26 it is a roughly 3B-parameter model, text in and text out, with guided
  generation: mark a Swift struct `@Generable` and the framework guarantees an
  instance of it ([docs](https://developer.apple.com/documentation/foundationmodels)).
  The context window is 4,096 tokens, shared by instructions, prompt and
  output ([TN3193](https://developer.apple.com/documentation/technotes/tn3193-managing-the-on-device-foundation-model-s-context-window)).
  A panel's OCR text is a few hundred tokens, so "OCR text in, `LabelFacts`
  struct out" fits today.
- The iOS 27 betas add image `Attachment` input
  ([docs](https://developer.apple.com/documentation/foundationmodels/attachment)),
  a Vision-backed `OCRTool` Apple says helps with "really fine or dense text"
  ([WWDC26 237](https://developer.apple.com/videos/play/wwdc2026/237/)), and a
  `contextSize` that can be 8,192 on newer hardware
  ([WWDC26 241](https://developer.apple.com/videos/play/wwdc2026/241/)).
  Apple's lab engineers still said 4,096 on the panel, so budget for 4,096 and
  read the size at runtime.
- Foreground use is not rate limited; background tasks "may" be
  ([WWDC26 lab](https://developer.apple.com/videos/play/wwdc2026/8121/)).
  Apple Intelligence has to be on, so coverage is a subset of iOS users.
- For Opson it would be a parser for odd layouts and languages the deterministic
  parser can't handle. It adds no independence, because it reads the same OCR
  text the parser does.

### Gemini Nano on Android

- The Prompt API takes text and images, under 4,000 input tokens, up to 10
  images, with structured output since July 2026
  ([get started](https://developers.google.com/ml-kit/genai/prompt/android/get-started),
  [release notes](https://developers.google.com/ml-kit/release-notes)).
- Beta, foreground only (`BACKGROUND_USE_BLOCKED` otherwise), and a per-app
  battery quota with no published numbers.
- Device list is short: Pixel 9, 10 and 11, the Galaxy S26 series and some
  foldables. The Galaxy S25 has the task APIs but not the Prompt API.
- For Opson it plays the same role as Foundation Models, on fewer phones. Not
  worth building before the iOS one proves useful.

### A downloadable model

| Model | Size | Licence | Published document scores | Phone feasibility |
|---|---|---|---|---|
| SmolVLM 256M / 500M | 145 / 287 MB at 4-bit | Apache-2.0 | DocVQA 58.3 / 70.5, OCRBench 52.6 / 61.0 ([paper](https://arxiv.org/html/2504.05299)) | Runs well (HuggingSnap uses SmolVLM2 through MLX). Too weak for exact digits |
| SmolVLM 2.2B | about 1.2 GB at 4-bit | Apache-2.0 | DocVQA 80.0, OCRBench 72.9 | 4.9 GB RAM at batch 1; marginal |
| Florence-2 230M / 770M | small | MIT | TextVQA 63.6 / 73.5; has an OCR-with-regions task ([card](https://huggingface.co/microsoft/Florence-2-large)) | Feasible, but it is an OCR engine, and Vision and ML Kit are already free OCR engines |
| PaliGemma 2 3B (448 px) | about 2 GB | Gemma terms | DocVQA 73.6 ([card](https://huggingface.co/google/paligemma2-3b-pt-448)) | Heavy for what it scores |
| Moondream 2 | about 1.9B params | Apache-2.0 | DocVQA 79.3, OCRBench 61.2 ([card](https://huggingface.co/vikhyatk/moondream2)) | Feasible on recent phones |
| Moondream 3 preview | 9B MoE, 2B active | BSL 1.1 | Preview | Licence rules it out |
| Qwen2.5-VL-3B | 3.07 GB at 4-bit | qwen-research, not commercial-friendly | DocVQA 93.9 ([card](https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct)) | Licence rules it out |
| **Qwen3-VL-2B** | 1.78 GB at 4-bit ([MLX build](https://huggingface.co/mlx-community/Qwen3-VL-2B-Instruct-4bit)) | Apache-2.0 | About DocVQA 93 from the tech report; column labels unverified ([report](https://arxiv.org/abs/2511.21631)) | The only strong, permissive candidate. Recent phones only |
| Gemma 3n E2B / Gemma 4 E2B | 2.6 to 4.5 GB | Gemma terms | None published for documents | Heavy |
| Open Food Facts nutrition-extractor (LayoutLMv3) | 0.36B | CC-BY-NC-SA, **non-commercial** | F1 0.96 on its own 199-image test set ([card](https://huggingface.co/openfoodfacts/nutrition-extractor)); 78.8% on an independent 100-card set ([FoodClockLabs](https://github.com/FoodClockLabs/etiquette-benchmark)) | Licence rules it out |

Delivery on iOS would be Background Assets, whose Apple-hosted asset packs
need iOS 26 ([docs](https://developer.apple.com/documentation/backgroundassets),
[limits](https://developer.apple.com/help/app-store-connect/reference/app-uploads/apple-hosted-asset-pack-size-limits/)).
On-Demand Resources is deprecated as of iOS 27
([App Store Connect](https://developer.apple.com/help/app-store-connect/reference/app-uploads/on-demand-resources-size-limits)).
The runtime would be MLX Swift, whose `mlx-swift-lm` ships Qwen3-VL and
SmolVLM2 ([repo](https://github.com/ml-explore/mlx-swift-lm)). Android needs a
second runtime (LiteRT-LM or llama.cpp) and a second model conversion.

The UX cost is real: a 1.8 GB download before the feature works, asked for
once, on Wi-Fi, with storage shown and a way to remove it. Most people adding
one food will never wait for it.

Not now, then. The best permissive candidate saves about 1 cent a scan
against the recommended cascade, costs weeks of work on two platforms, and has
no accuracy number on labels. The phone's job is OCR, quality gating and
pre-fill, which the free OCR engines already do.

## Backend options

Cost per scan assumes two photos at 1568 px, a 2,000-token prompt and a
1,500-token answer including thinking. Vendors count image tokens differently,
noted per row.

| Option | Accuracy on labels | Cost per scan | Latency | Privacy | Effort |
|---|---|---|---|---|---|
| **Claude Haiku 5.5** ($0.10 / $0.50 per M) | Unmeasured on Opson's labels. Haiku 4.5 trailed Sonnet 4.6 and Gemini 3 Flash on OCR benchmarks (below); 5.5 isn't on those boards | ~0.15¢ | Seconds (unmeasured) | Anthropic, no training on API data, as today | One env var |
| **Claude Sonnet 5.5** ($2 / $10) | Sonnet 4.6 led the vendor-run IDP board's table score; Sonnet 5 tops one OCRBench v2 board (below) | ~3 to 4¢ | Several seconds | Same | One env var |
| **Claude Opus 5.5** ($4 / $20) | Unmeasured | ~6¢ | Seconds at low effort | Same | One env var |
| **Claude Fable 5.1** ($10 / $50, effort high) | Best Opson has; the reader of record today | 10 to 15¢ (code's estimate) | 20 to 60 s | Same | Today |
| **Gemini 3.1 Flash-Lite** ($0.25 / $1.50; 1,120 tokens an image at the default `high`) | Not on the benchmark boards | ~0.35¢ | Seconds | Google; paid tier not used for training, prompts logged for abuse detection ([terms](https://ai.google.dev/gemini-api/terms)) | New vendor, key and privacy disclosure |
| **Gemini 3.8 Flash** ($0.75 / $3.75, doubling on 2027-01-01) | Gemini 3 Flash scores near Sonnet 4.6 on document boards | ~0.9¢, ~1.8¢ from 2027 | Seconds; 6.9 s in one 10-label student test | As above | As above |
| **GPT-5.4-nano / mini** ($0.20 / $1.25; $0.75 / $4.50; images in 32 px patches × 1.2) | GPT-5-mini behind Sonnet 4.6 and Gemini 3 Flash on IDP | ~0.35¢ / ~1.2¢ | Seconds | OpenAI; abuse logs kept up to 30 days ([data](https://developers.openai.com/api/docs/guides/your-data)) | New vendor |
| **GPT-4o-mini** ($0.15 / $0.60) | GPT-4o-mini scored 43.0 on OCRBench v2 | ~0.9¢: its images cost 2,833 tokens plus 5,667 per tile ([vision](https://developers.openai.com/api/docs/guides/images-vision)) | Seconds | As above | New vendor |
| **AWS Textract** | Cells, no nutrition meaning; still needs Opson's parser | 0.15¢ text, 1.5¢ with tables, per page ([pricing](https://aws.amazon.com/textract/pricing/)) | About 2 s (third party) | AWS | New vendor, IAM |
| **Google Document AI** | As Textract | 0.15¢ OCR, 3¢ Form Parser or Custom Extractor ([pricing](https://cloud.google.com/document-ai/pricing)) | Seconds | Google Cloud | New vendor |
| **Azure Document Intelligence** | As Textract | 0.15¢ Read, 1¢ Layout ([pricing](https://azure.microsoft.com/en-us/pricing/details/document-intelligence/)) | Seconds | Microsoft | New vendor |
| **Passio Nutrition AI** | Has a packaged-product extraction endpoint; no published accuracy | Token-metered from $99 a month for 1M tokens; tokens per label not published ([pricing](https://passio.gitbook.io/nutrition-ai/getting-started/token-system-and-pricing)) | Fast (vendor) | Third party; mixes Open Food Facts data into results | SDK or API |
| **Veryfi nutrition facts OCR** | Vendor claims only ([page](https://www.veryfi.com/product-nutrition-facts-ocr-api/)) | $500 a month minimum | Seconds | Third party | API |
| **VisionParser** | Vendor claims only | $100 a month for 5,000 scans, about 2¢ ([page](https://visionparser.com/documents/product-nutrition-facts)) | Seconds | Third party | API |

Claude prices are from Anthropic's
[pricing page](https://platform.claude.com/docs/en/about-claude/pricing);
Haiku 5.5's rate holds for prompts up to 100K tokens, far above a scan. Gemini
from [Google](https://ai.google.dev/gemini-api/docs/pricing) and its
[media resolution page](https://ai.google.dev/gemini-api/docs/media-resolution);
OpenAI from [OpenAI](https://developers.openai.com/api/docs/pricing). Edamam,
FatSecret, LogMeal and Calorie Mama recognise plates, not labels.

**Batch and caching.** Anthropic's Batch API is 50% off every token, cache
reads included
([batch](https://platform.claude.com/docs/en/build-with-claude/batch-processing)),
with results usually within an hour and always within 24. It suits the second
read for review, which nobody waits on, not the read that fills the form.
Prompt caching barely helps here: the cacheable part is the 2,000-token system
prompt and schema, the images change every request, and Haiku's minimum
cacheable prefix may be larger than the prompt
([caching](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)).
Cropping to the detected panel before upload does help: fewer image tokens and
less of someone's kitchen sent anywhere.

### What the benchmarks say, and don't

- **Nothing ranks current models on nutrition labels.** The closest is a 2025
  study of 294 English and Arabic labels where GPT-4o beat GPT-4V and Gemini
  (GPT-4V at about 88 to 90% correct on English name and value pairs) and Claude
  wasn't tested ([J. Imaging](https://doi.org/10.3390/jimaging11080271)).
- **General document boards** put Gemini 3 Flash and Claude Sonnet 4.6 close at
  the top of the cheap tiers, with Haiku 4.5 and GPT-5-mini behind
  ([IDP Core Bench](https://benchmarking.nanonets.com/benchmarks/idp), run by a
  vendor; [OCRBench v2 board](https://interfaze.ai/leaderboards/ocrbench-v2)).
  OCRBench v2 includes nutrition-label questions
  ([paper](https://arxiv.org/pdf/2501.00321)). None of these cover Haiku 5.5.
- **A model's self-reported confidence is poorly calibrated.** Models asked to
  state their confidence tend to be overconfident
  ([Xiong et al., ICLR 2024](https://arxiv.org/abs/2306.13063)). The parser's
  `confidence` map is a weak signal, not a gate.

So every accuracy figure that matters for Opson is unmeasured until the
evaluation below runs. That evaluation is the first piece of work.

## The hybrid architecture

```
 phone                                      server
 ─────                                      ──────
 live camera, quality gate
 OCR (Vision / ML Kit), tokens + boxes
 deterministic parser → draft LabelFacts
 LabelChecks on the draft
 form pre-filled; person edits or types  ─► submission: photos (panel crop),
                                             OCR tokens, draft, person's values
                                             │
                                             ├─ read A: phone parser (already done)
                                             ├─ read B: Haiku 5.5 from pixels only
                                             ├─ LabelChecks on A, B and the submitted values
                                             ├─ grounding: every submitted number in the OCR tokens
                                             ├─ agreement A vs B vs submitted, per headline field
                                             │
                                             ├─ agree + checks pass ───► confidence score → lane
                                             └─ disagree or a check fails
                                                  └─ read C: Sonnet 5.5, then Fable 5.1 if still split
```

### 1. On the phone

- On iOS, add an `AVCaptureVideoDataOutput` beside the metadata output in the
  capture step (not the barcode step; see `passive-label-capture.md`). Run
  `RecognizeDocumentsRequest` on iOS 26 and `RecognizeTextRequest` below it.
- On Android, run ML Kit Text Recognition v2, unbundled, on the same
  `ImageAnalysis` frames CameraX already delivers.
- Keep each token's text, box and recognition confidence. Send them with the
  submission; never log them.

### 2. Deterministic parsing and validation

A Kotlin parser in common code, shared by phone and server, with tests from
the labelled set. It does what the model does in `LabelOutput.kt` today,
from text:

- Find the panel's anchor ("Nutrition Facts", "Nutrition information",
  "Nährwerte", "Valeurs nutritionnelles") and the column headers (per 100 g or
  ml, per serving, as sold or as prepared).
- Map row labels to `NutrientKey` with a synonym table per language.
- Normalise numbers: decimal commas, "<1 g", "0.5g", "trace", %DV-only rows on
  US panels.
- Units: kJ and kcal both present (keep kcal, check kJ ÷ 4.184), salt to
  sodium (÷ 2.5), mg and µg.
- Per 100 against per serving: both columns present means serving grams are
  implied; check them against the stated serving.
- Then the existing `LabelChecks` and the rules `package-scanning.md` section 3
  lists (Atwater within 15 kcal or 12%, US rounding tolerances, carb basis).

When the parser can't place a value it leaves the field empty rather than
guessing. An empty field sends that scan down the slower path; a wrong one
poisons the agreement check.

### 3. Confidence from measurable signals

Each signal is computed, logged as a number, and calibrated on the labelled
set. Start with gates; fit a simple model (logistic regression) once there are
about 1,000 labelled outcomes.

| Signal | How it's measured | Why |
|---|---|---|
| Read agreement | Phone parser and blind Haiku read match on kcal, protein, carbs, fat, serving size, after unit normalisation, within label rounding | Two different systems rarely make the same mistake |
| Submitted agreement | The person's final values match the agreeing reads | Catches typos and invented numbers |
| Grounding | Each submitted headline number appears among the OCR tokens | Cheapest check that a number came from the photo |
| OCR confidence | Minimum recognition confidence over the tokens behind headline values | Low values mark blur and glare |
| Check residuals | Atwater gap as a share of kcal; per-100 vs per-serving gap | Continuous, so a near-miss counts against a scan |
| Image quality | Laplacian variance, median text height in px, blown-out share in the panel box | Predicts misreads before any model runs |
| Layout | Columns found; whether "as prepared" exists; dual language | Wrong-column is the most common plausible error |
| Independent source | Same barcode already in a tier 1 to 3 source, or in Open Food Facts, with close values | Agreement raises confidence; disagreement routes to review. Never copy OFF values into the submission (licence, `food-data-sources.md`) |
| Contributor record | Agreement with reviewers so far; see `builder-program.md` | Separates honest mistakes from patterns |

The model's own per-field `confidence` stays in the record as one weak input.

### 4. Routing

| Lane | Rule | What happens | Expected share (to measure) |
|---|---|---|---|
| **Agree** | Reads A and B and the submission agree on every headline field, all checks and grounding pass | No further model call. Lane decided by the contributor's trust level | Most clear, flat panels |
| **Escalate** | Any headline disagreement, a blocking check, grounding failure on a headline field, or two columns found | Read C on Sonnet 5.5; if C agrees with A or B, take that; if not, Fable 5.1 | 10 to 20% |
| **Human** | Still split after Fable, or C disagrees with what the person submitted | Review queue with every read side by side | A few percent |
| **No read** | Offline | The phone's draft and the person's values are saved; the outbox sends the submission later and the server reads it then | Offline scans |

Read B blind to the OCR text is a deliberate choice. `package-scanning.md`
suggested sending the OCR text to the model as a hint, which would make the
read better but no longer independent: the model would copy the OCR's misread
"8" for a "3". Send the photo alone, and use the OCR afterwards, for
grounding.

### 5. When the backend's answer can skip a person

The owner suggested that a backend AI's output may not need double checking.
Honestly: as a blanket rule, no.

- Models misread digits (3 and 8, 1 and 7, a lost decimal point), read the
  per-serving column when the form expects per 100, and fill in a plausible
  number for a cell they can't see.
- Atwater catches errors that move calories. It does not catch a misread sugar,
  sodium, fibre or any vitamin, or swapped carbs and protein (both 4 kcal a
  gram).
- A model reads a doctored photo faithfully. AI checks the photo, not the
  person.

What would justify skipping review for a lane:

1. **A labelled sample of at least 300 cases from that exact lane**, ground
   truth transcribed by two people independently. Zero headline-field errors in
   300 puts the true rate under 1% at 95% confidence
   ([rule of three](https://en.wikipedia.org/wiki/Rule_of_three_(statistics))).
   One error needs about 475 cases for the same claim.
2. **Two independent reads agree and every check passes**, measured on that
   sample, not assumed.
3. **The contributor qualifies** (trusted level in `builder-program.md`), so a
   fabricated photo isn't the input.
4. **Ongoing random audits** of 5 to 10% of the lane, with an alert when the
   audit error rate rises.
5. **Micronutrients held to a lower bar, shown honestly.** Headline fields
   decide approval; a vitamin that no check covers can ship marked as read by
   machine.

Even then the food shows a softer "Checked" mark, per open question 4 in
`package-scanning.md`, until a person or a second contributor confirms it.

## Cost model

Per scan means one label read of two photos. Escalation rates are assumptions
until the evaluation measures them.

| Pipeline | Per scan | 1k a month | 10k a month | 100k a month |
|---|---:|---:|---:|---:|
| Today: Fable 5.1 on every read | 12.5¢ | $125 | $1,250 | $12,500 |
| `package-scanning.md` cascade: Sonnet 5.5 first, 15% to Fable | ~5.4¢ | $54 | $540 | $5,400 |
| **Recommended.** OCR + blind Haiku 5.5, 15% to Sonnet 5.5, 3% on to Fable | ~1.05¢ | $11 | $105 | $1,050 |
| Recommended, with reads nobody waits on sent through the Batch API | ~0.6¢ | $6 | $60 | $600 |
| Gemini 3.1 Flash-Lite as read B instead of Haiku | ~1.25¢ | $13 | $125 | $1,250 |
| On-device only, no cloud read | 0 | $0 | $0 | $0 |

The arithmetic for the recommended row: 0.15¢ + 15% × 3.5¢ + 3% × 12.5¢. The Batch row assumes the phone's draft fills the form, so most cloud reads run after submission at half price.

Human review is the bigger number. At 30 seconds an item:

| Share reviewed by a person | 1k | 10k | 100k |
|---|---:|---:|---:|
| Everything (also the on-device-only pipeline, which has nothing to auto-approve on) | 8 h | 83 h | 833 h |
| Recommended with 60% auto-approved and 10% of those audited | 3.8 h | 38 h | 383 h |
| Recommended with 80% auto-approved and 5% audited | 2 h | 20 h | 200 h |

So the on-device-only pipeline is the most expensive one past a few hundred
scans a month, because it produces no independent read to auto-approve on. At
100k scans a month, review needs Builders reviewing each other (peer
confirmation in `builder-program.md`) on top of the cascade.

## Evaluation plan

### The labelled set

- Start with 300 labels (the `package-scanning.md` 200 plus 100 hard
  cases), growing to 1,000.
- The photos can't come from the review queue. The privacy policy
  promises submitted photos are deleted 30 days after review, so they can't
  sit in a test set. Use photos the owner takes, and photos Builders take
  under terms that ask for that use explicitly (`builder-program.md`).
- Cover US, EU, UK, Canada (bilingual), Australia, Japan or Korea; per 100
  only, per serving only, both; as sold and as prepared; %DV-only rows; drinks
  per 100 ml; curved cans and glossy bags; small panels on snack packs; one
  slightly blurred but legible shot of each of 30 products; ten photos that
  aren't nutrition panels.
- For ground truth, two people transcribe each panel independently into the
  `LabelFacts` shape; a third settles differences. Record which column is
  correct and the serving in grams or ml.
- Use 70% to tune parsers and prompts, 30% held out and only scored.

### Metrics

| Metric | Definition | Target to beat |
|---|---|---|
| Headline accuracy | Share of scans where kcal, protein, carbs, fat and serving size are all correct within label rounding | Fable 5.1's score on the same set |
| Per-field accuracy | Per nutrient, correct within rounding | Report all; gate on headline |
| Column choice | Read the column the ground truth names | Above 99% |
| Unreadable detection | Precision and recall of `readable = false` on the non-panel photos | No panel accepted as one |
| Check false alarms | Share of correct readings a check flags | Low enough that people don't learn to dismiss them |
| Calibration | Error rate per confidence bucket, as a reliability table | Monotonic: higher score, fewer errors |
| Lane error rate | Errors in the auto-approve lane, with the 95% upper bound | Under 1% |
| Escalation rate | Share of scans that reach Sonnet, and Fable | Feeds the cost model |
| Cost per scan | From `usage` on each response | As modelled |
| Latency | p50 and p95, capture to filled form, and per read | Form under 5 s on a clear label |

Run every option on the same set: phone parser alone, Haiku 5.5, Sonnet 5.5,
Opus 5.5, Fable 5.1, Gemini 3.1 Flash-Lite, and Foundation Models on the OCR
text where an iPhone supports it. The `claude-api` skill's `build-eval` flow
fits the Claude part. After launch, track one live metric: the share of
reviewed items where the reviewer changed a headline field.

## Decision rule for an on-device model

Revisit the downloadable model only if one of these holds:

- Monthly label-read spend passes about $1,000, which at the recommended
  cascade is roughly 100,000 scans.
- Full offline parsing becomes a product requirement, beyond the OCR draft.
- A permissive model under 2 GB comes within one point of Haiku 5.5's
  headline accuracy on the held-out set.

Foundation Models' iOS 27 image input is the cheapest experiment when it
ships: no download, one platform, pre-fill only.
