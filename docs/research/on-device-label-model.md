# Reading nutrition labels on the phone: train a model, improve the parser, or use the OS model?

Written 2026-10-30, when v1 dropped server AI (docs/decisions.md). The question
from the owner: could Opson train its own model, better than plain OCR, to read
the front and back of a pack on the phone, and send only what it can't read to
a person?

## Recommendation

Don't train a model yet. Spend the next month on the free things, in this
order, and collect the data a model would need while doing it:

1. **Measure first.** Nobody has scored the phone read on real labels. Run the
   50-label benchmark from homework 9 against the on-device path
   (`PhoneLabelReader`), not against cloud models.
2. **Improve the OCR and parser where the benchmark says.** On iPhone, try
   Vision's iOS 26 document request (`RecognizeDocumentsRequest`), which
   returns table rows and cells; the parser today rebuilds rows from loose
   lines, which is where most misplaced numbers come from.
3. **Use the OS model as a second opinion, not the reader.** Apple's
   Foundation Models is prototyped behind `labels.onDeviceModel` (below). It
   reads the OCR text, never the photo, and a merge keeps only numbers that
   are printed in that text. Turn it on for yourself, compare.
4. **Train only if 1 to 3 leave a gap worth closing**, starting with a small
   nutrition-panel detector, not an end-to-end model (phase 3 below).

The reasons are below. The short version: the hard part of a label is not
reading characters, which Vision and ML Kit already do well and for free, but
placing each number in the right row and column. A deterministic parser plus a
second opinion that can only point at printed text gets most of that without a
training pipeline, and fails visibly, which matters when a person reviews
everything anyway.

## What the phone does today

- **OCR:** Vision (`VNRecognizeTextRequest`, accurate, no language correction)
  on iOS; ML Kit Latin text recognition on Android. Both free and offline.
- **Parser:** `LabelTextParser` in `:libraries:foods`. Deterministic: finds
  the panel, the columns (per serving, per 100, as prepared, per container),
  the serving, then each row's numbers. Converts kJ, salt, ounces. Leaves a
  field empty rather than guess. Each field carries the OCR confidence of the
  line behind it, so a hazy digit is marked "Check this" in the form.
- **Front of the pack:** `PackFrontText` guesses the name from the largest
  printed line (and a second line of the same size below it). Always marked
  for a look. Brands are usually a logo, so it doesn't try.
- **Grounding:** `LabelConfidence` checks every submitted headline number is
  printed somewhere in the OCR text, and scores the read. The server repeats
  the grounding and keeps the capture for the reviewer.
- **Everyone** gets this path since v1. Before, only Builders did; everyone
  else's label went to a cloud model.

## The options

### A. Improve OCR and the parser (deterministic)

| | |
|---|---|
| Cost | Engineering time only |
| Size | 0 |
| Accuracy | Unmeasured. Likely good on clean US and EU panels, weak on curved cans, glare, two-language panels and tiny snack labels |
| Effort | Days per improvement, each testable with a fixture (`LabelTextFixtures`) |

Concrete next steps: Vision's document request on iOS 26 for table structure;
more fixtures from real failures (the review queue shows them every week:
each rejected or corrected submission whose capture was wrong is a free test
case, as long as only the OCR text, not the photo, is kept as the fixture);
deskew and glare hints at capture time ("tilt the pack a little").

### B. Apple Foundation Models (iOS 26, on device, free)

- A roughly 3B-parameter model behind Apple Intelligence. Runs on Apple
  Intelligence hardware (iPhone 15 Pro and later) with Apple Intelligence on;
  `SystemLanguageModel.default.availability` says so at run time.
- Context window 4,096 tokens per session (input and output together). A label's
  OCR text is a few hundred tokens, so it fits.
- **Guided generation** (`@Generable`, `@Guide`) returns a Swift struct, not
  free text, so the output is always well-formed.
- Text in. It is not given the image here: the prototype asks it to copy
  numbers out of the OCR text.
- No cost, no network, nothing leaves the phone. No Android equivalent in this
  framework.

Feasibility for structured extraction: good fit for "copy these numbers into
these fields" from text, weak at arithmetic and at anything not in the text,
so it must never be trusted to fill a number on its own. That is why the
prototype merges deterministically (`LabelModelMerge`): the model can fill a
field the parser left empty only with a number printed in the OCR text, and
can flag a field where it disagrees with the parser; it never overrides the
parser.

### C. Gemini Nano on Android (ML Kit GenAI Prompt API)

- On-device Gemini Nano through AICore. Device support is narrower than
  Apple's: recent Pixels (best on Pixel 10, nano-v3), Galaxy S25, a handful of
  others. Prompts under about 4,000 tokens, foreground only.
- Text, and text plus image on some devices. Google's own advice for images
  with text is ML Kit text recognition first, then the Prompt API over the
  text, which is the same shape as the iOS prototype.
- The Prompt API was alpha in late 2025. Worth wiring the same
  `LabelTextModel` interface once it is stable; `NoLabelTextModel` is the
  Android binding until then.

### D. Train our own (TFLite, Core ML, ML Kit custom model)

Two shapes:

1. **Panel detector + existing OCR + parser.** A small object detector
   (a YOLO-style model, a few MB as TFLite or Core ML) finds the nutrition
   table, the app crops to it, OCR reads only the crop. Fixes "OCR read the
   whole pack and the parser got lost", and helps with glare by letting the
   app ask for a retake when no panel is found. Labelling: one box per photo,
   fast (a few seconds a photo).
2. **Layout-aware extraction** (LayoutLM-style token classification over OCR
   tokens and their boxes, or an end-to-end image-to-JSON model). Much
   stronger at placing numbers, much bigger. Open Food Facts trained exactly
   this (`openfoodfacts/nutrition-extractor`, LayoutLMv3-large, F1 about 0.96
   on their set), but it is about 1.4 GB, trained on Google Cloud Vision OCR,
   and licensed CC BY-NC-SA, so it can't ship in Opson. End-to-end document
   models (Donut, Pix2Struct class) are hundreds of MB and slow on a phone.

| | Panel detector | Layout extraction |
|---|---|---|
| Size | 3 to 10 MB | 100 MB to 1.4 GB |
| Labelled photos needed | ~1,000 boxes to start | ~2,000+ fully labelled panels |
| Labelling time | hours | weeks |
| Effort | 1 to 2 weeks plus data | 1 to 2 months plus data |
| Better than OCR + parser? | Only on cluttered photos | Likely yes, if the data is there |

### Dataset

- **Builders' retained photos**, with consent: Builders can opt in to keeping
  label photos up to two years "to test how well Opson reads labels"
  (privacy policy). That consent covers testing; training a model is close
  but should be named explicitly before it is used for that. Add the word
  "train" to the Builder photo choice before collecting for phase 3.
- **The review queue itself is a labelling tool.** Every approved food is a
  photo with reviewed numbers, and the capture holds the OCR boxes. The 30-day
  photo rule means only the retained Builder photos can be kept; for everyone
  else, keep only the OCR text and the reviewed numbers (no photo), which is
  enough for parser fixtures and for training a text-side model, not a
  detector.
- **Open Food Facts images** of nutrition tables are public (CC BY-SA images,
  ODbL data). Usable for a detector, with attribution in `credits.md`; check
  each dataset's licence before training, as the OFF extractor model above
  shows the licences differ.

## The prototype in this repo

Behind `labels.onDeviceModel` (remote config, off by default, not seeded on
dev because it is not a release flag):

- `LabelTextModel` (`:libraries:foods`) is the interface; `LabelModelMerge`
  the deterministic merge, with tests.
- iOS: `IOSLabelModelBridge` + `LabelModelExtractor.swift` call Foundation
  Models with a `@Generable` `LabelReading` (serving, servings per container,
  per serving and per 100 columns of eight nutrients), greedy sampling, and
  return JSON. `IosLabelTextModel` parses it, with a 15 s timeout. On iOS
  before 26, on a phone without Apple Intelligence, or while the model
  downloads, it answers "unavailable" and the parser reads alone.
- Android: `NoLabelTextModel`, always unavailable.
- `PhoneLabelReader` asks the model only when the flag is on. A field the
  model filled is marked "Check this" (confidence 0.5); a field the two
  disagree on keeps the parser's number and is marked too (0.4).

Checked: the Swift typechecks against the iOS 27.1 simulator SDK in Xcode
27.1 with a deployment target of 18.2 (the Debug target; Release is 26.1), the
framework links, and the app builds for the simulator. Not checked: a real
read on an Apple Intelligence iPhone. The simulator only runs the model on a
Mac that has Apple Intelligence, so try it on a phone: turn the flag on for
your user in the console, add a packaged food, and compare.

## Phased plan

1. **Now (v1):** phone read for everyone, weekly human review, the FM
   prototype off. Collect: OCR text, reviewed numbers and corrections per
   submission (already stored).
2. **Next 4 weeks:** benchmark 50 labels on device. Turn the FM flag on for
   yourself and compare on the same 50. Fix the parser's worst cases and try
   Vision's document request. If FM's second opinion catches real misreads,
   turn it on for everyone on iOS.
3. **When there are ~1,000 retained label photos (with training consent):**
   train a panel detector, ship as Core ML / TFLite (a few MB), measure again.
   Only then consider a layout model, and only if the numbers say the parser
   is the bottleneck.
4. **Android:** wire Gemini Nano through the same interface when the Prompt
   API is stable on enough devices.

## Sources

- Apple: Foundation Models, guided generation, TN3193 on the context window
  (4,096 tokens per session, confirmed on the developer forums:
  https://developer.apple.com/forums/thread/806542).
- Google: ML Kit GenAI Prompt API, https://developers.google.com/ml-kit/genai/prompt/android
  and the alpha announcement, https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html.
- Open Food Facts nutrition extractor: https://huggingface.co/openfoodfacts/nutrition-extractor
  (LayoutLMv3-large, CC BY-NC-SA 4.0).
