# Reading the nutrition panel while scanning a barcode

Researched 2026-10-29. This is a policy summary for planning, not legal advice.
The questions a lawyer should settle are at the end.

## The answer

- **Reading camera frames on the phone, while the camera is already open for a
  scan, is allowed** by Apple's and Google's rules, provided the camera
  purpose string and the privacy policy say so. Data processed only on the
  device doesn't count as collected for App Privacy or Data safety.
- **Taking a photo and uploading it without a visible prompt is not allowed,**
  whatever it's called. It breaks Apple's consent and purpose-string rules,
  Apple's rule on sharing personal data with third-party AI, Play's prominent
  disclosure rule, GDPR's transparency duty, and Opson's own published privacy
  policy. Don't build the quiet version.
- **The recommendation is no passive reading in the everyday scanner for now.**
  Build an explicit, visible "Also read the label" control in Builder mode
  (`builder-program.md`): the camera outlines the panel when it sees one, and
  a tap takes the photo. Nothing leaves the phone without that tap.
- **It probably isn't worth much anyway.** The scanner zooms in on the barcode
  and closes within about a second, so a legible panel will rarely be in
  frame. Measure that by hand before building anything.

## The rules

| Source | What it says | What it means here |
|---|---|---|
| App Review 5.1.1(ii) | Apps "must secure user consent for the collection" and should "ensure your purpose strings clearly and completely describe your use of the data" ([guidelines](https://developer.apple.com/app-store/review/guidelines/)) | Opson's `NSCameraUsageDescription` covers reading barcodes and photographing a label "when you add a food". Reading the panel during a scan isn't in it. Add it before shipping any version of this |
| App Review 5.1.1(iv) | Apps must not "manipulate, trick, or force people to consent to unnecessary data access" (same page) | A camera that does more than it shows fits this |
| App Review 5.1.2(i) | "You must clearly disclose where personal data will be shared with third parties, including with third-party AI, and obtain explicit permission before doing so" (same page) | A label photo goes to Anthropic. Today the person chose that by tapping through Add this food. A silent upload has no permission |
| App Privacy details | "Data that is processed only on device is not 'collected' and does not need to be disclosed... If you derive anything from that data and send it off device, the resulting data should be considered separately" ([Apple](https://developer.apple.com/app-store/app-privacy-details/)) | On-device reading changes no privacy label. Sending the numbers it read does |
| Play User Data policy | Camera data is personal and sensitive user data. When access "may not be within the reasonable expectation of the user", the app needs a prominent in-app disclosure, shown in normal use, right before any consent ([policy](https://support.google.com/googleplay/android-developer/answer/10144311)) | Nobody scanning a barcode expects the label to be read and sent |
| Play Data safety | "Collect" means "transmitting data from your app off a user's device"; data only processed locally needn't be disclosed ([Play](https://support.google.com/googleplay/android-developer/answer/10787469)) | Same as Apple. The page's guidance on ephemeral processing contradicts itself between sections, so don't lean on that part |
| GDPR and ePrivacy | No regulator statement found that on-device-only processing is outside GDPR. The EDPB says that once locally produced information "is made available to a third-party, for example sent back over the network to a server", that is gaining access under Article 5(3), and "the fact that this information is being produced locally does not preclude the application of Article 5(3)" ([Guidelines 2/2023](https://www.edpb.europa.eu/our-work-tools/our-documents/guidelines/guidelines-22023-technical-scope-art-53-eprivacy-directive_en)) | Sending extracted numbers from an EU phone likely needs consent, unless strictly necessary for the service the person asked for. They asked for a barcode lookup, not a database check |
| CCPA | "Collects" includes "receiving, or accessing any personal information... actively or passively, or by observing the consumer's behavior" ([statute](https://cppa.ca.gov/regulations/pdf/ccpa_statute.pdf)) | Uploaded numbers tied to an account are collected and need notice at collection. On-device-only reading is arguably not, which a lawyer should confirm |
| Washington My Health My Data Act | "Collect" includes to "access... infer, derive, or otherwise process"; consumer health data includes data "derived or extrapolated from nonhealth information" ([RCW 19.373.010](https://app.leg.wa.gov/RCW/default.aspx?cite=19.373.010)) | Broader than Apple's or Google's meaning of collect. Whether a calorie tracker is in scope at all is a question for the whole app, not just this feature |
| Opson's own privacy policy | "The barcode is sent to our server." "No camera, photos, microphone or Face ID unless you choose a feature that needs it, and then only for that feature." (`legal/privacy.md`) | A silent upload would make the published policy false. In the US, misrepresenting your own privacy practices is the deception the FTC acts on under section 5 of the [FTC Act](https://www.ftc.gov/legal-library/browse/statutes/federal-trade-commission-act). This is the clearest exposure |

A useful precedent: Apple's Live Text reads text in the camera view, and shows
it, with a "Show Detected Text" switch in Settings › Camera
([Apple](https://support.apple.com/guide/iphone/use-live-text-iphcf0b71b0e/ios)).
It is visible and the person controls it. Yuka and the Open Food Facts app
disclose nothing about reading labels passively; no evidence found that they do.

## The options

| Option | Allowed? | What it needs | What Opson gets |
|---|---|---|---|
| **A. Quiet photo upload** | No | n/a | n/a |
| **B. Read on the phone, send nothing** | Yes | A purpose string update | Only local use, such as "This label looks different from what Opson has. Send a photo?", which leads into D |
| **C. Read on the phone, send the numbers, no image** | Yes, with disclosure | A one-time opt-in, App Privacy and Data safety entries, a privacy policy line, EU consent | Mismatch counts as a queue-priority signal ("phones read 210 kcal where the catalog says 120"). Unreviewable without a photo, and glance-OCR is noisy |
| **D. "Also read the label", visible, in Builder mode** | Yes | The control and outline on screen; the existing Add this food disclosures; the purpose string update | The best evidence: a deliberate photo from someone holding the pack |

Build D first, with B as its trigger. Consider C only if D shows the mismatch
signal is worth the disclosure work.

## Is it worth it

- **Few frames.** The barcode usually reads in a fraction of a second and the
  scanner closes. Passive reading only gets the frames before that.
- **Wrong framing.** `ScanZoom` zooms in until a small code fills the scan area
  at a distance the lens can focus, after Apple's AVCamBarcode sample. A panel
  on the same face is usually partly out of frame and off the focal plane.
- **Different faces.** On cans, bottles and many multipacks, the barcode and
  the panel are on different sides. No published measurement of how often they
  share a face was found.
- **Too small.** iOS captures at 1920 × 1080. `package-scanning.md` wants a
  median line height of about 20 px for a reliable read, which panel text
  seldom reaches at barcode distance.
- **A real cost.** iOS scans through `AVCaptureMetadataOutput`, which hands
  over codes, not frames. Reading text needs an `AVCaptureVideoDataOutput` as
  well, and per-frame OCR during every scan, which costs battery, heat and scan
  speed. Android already receives CameraX frames, but OCR on each one still
  costs.
- **Little to gain.** On a barcode miss the person is about to photograph the
  label anyway. On a hit with wrong data, Report a problem already exists.

**Measure before building.** The owner scans 100 packs from a cupboard and a
supermarket shelf with a dev build that saves nothing and shows on screen
whether it found a legible panel before the code read. If fewer than about 1 in
5 are legible, drop passive reading and keep option D. Doing this by hand also
avoids sending any new data for the measurement itself.

## What a lawyer should confirm

1. That on-device analysis of camera frames, never sent anywhere, is outside
   "collection" for GDPR, CCPA and the Washington act as Opson would build it.
2. Whether sending extracted numbers tied to an account needs consent under
   Article 5(3) in the EU and UK, or whether legitimate interest is enough.
3. The privacy policy wording for option D, and whether "only for that
   feature" already covers it.
4. The App Privacy and Data safety categories for extracted numbers, if option
   C is ever built.
5. Whether Opson as a whole is processing consumer health data under the
   Washington act, and what consent that requires. This is bigger than this
   feature.
