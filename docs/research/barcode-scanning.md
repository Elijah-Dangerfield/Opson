# Barcode scanning: what makes it quicker and more accurate

The owner asked what processing could make scans quicker and more accurate,
and whether raising contrast behind the scenes or stabilising the camera would
help. Short answers:

- **Contrast: no.** Neither decoder lets us touch its frames on iOS, and on
  Android ML Kit already normalises the image itself. A contrast or
  binarisation pass costs a copy of every frame and can throw away the edge
  detail the decoder reads between bars.
- **Stabilisation: no.** It shifts whole frames to cancel shake, which smooths
  the preview but does nothing for the blur inside one frame, which is what
  costs reads. Every stabilisation mode but the newest also adds latency.
- What does help is where the camera looks, how close it can focus, how much
  light there is, and taking the first good read. Most of that was already in
  place after the iPhone Pro testing in October. This pass fixed a real gap on
  Android and added a low-light hint on both.

## What each platform uses

| | iOS | Android |
|---|---|---|
| Camera | `AVCaptureSession`, Kotlin/Native bindings, no Swift | CameraX 1.5.3 (`Preview` + `ImageAnalysis`) |
| Decoder | `AVCaptureMetadataOutput` (Apple's built-in detector) | ML Kit barcode scanning 17.3.0, bundled model |
| Code | `libraries/scanner/impl/src/iosMain/.../IosBarcodeCamera.kt` | `libraries/scanner/impl/src/androidMain/.../AndroidBarcodeCamera.kt` |
| Accepting a read | `BarcodeScanViewModel.codeRead` (shared) | same |

Not used: Vision's `VNDetectBarcodesRequest` and VisionKit's
`DataScannerViewController`. The metadata output runs the detector inside the
capture pipeline with no frame copy and honours a region of interest, so
Vision would be more code for no faster read. `DataScannerViewController`
brings its own UI and needs iOS 16 and an A12, and we would lose the frame,
the torch button and the copy.

## The levers, and where each stands

| Lever | iOS | Android |
|---|---|---|
| Only food symbologies | Done before (EAN-13, EAN-8, UPC-E; UPC-A arrives as EAN-13) | Done before (EAN-13, EAN-8, UPC-A, UPC-E) |
| Region of interest matching the frame | Done before (`rectOfInterest`) | **New**: results outside the frame are dropped |
| Analysis frame covering what the preview shows | Not an issue | **Fixed**: it covered only about 75% of the width |
| Nearest code to the middle when there are two | **New** | **New** |
| Resolution | 1080p preset, done before | 1280x960, was 1280x720 (see below) |
| Near autofocus range | Done before | No API for it |
| Smooth autofocus off | Done before | Not applicable |
| Focus and exposure point | Centre by default, tap to move, done before | Centre-weighted by default, tap to move, done before |
| Macro: lens that focuses close | Done before (triple or dual wide virtual camera, plus zoom from minimum focus distance) | Not done (see below) |
| Torch | Button, done before | Button, done before |
| Low-light hint | **New** | **New** |
| Accept the first read whose check digit passes | Done before (shared) | Done before (shared) |
| Duplicate suppression | Done before: reads are ignored unless idle | same |
| Haptic on the accepted read | Done before, before the lookup starts | same |
| Debug timings | **New** | **New** |
| Contrast or binarisation | No | No |
| Video stabilisation | No | No |
| Skipping blurred frames | No | No |
| ITF-14 | No | No |

### Symbologies

ML Kit says restricting formats speeds the detector up
([ML Kit, barcode scanning on Android](https://developers.google.com/ml-kit/vision/barcode-scanning/android)).
Apple says the same of `metadataObjectTypes`. Both were already limited to the
retail codes on consumer packs. ITF-14 is printed on outer cases, not on the
pack someone holds in a kitchen, and its interleaved bars are the easiest
to misread as a shorter code, so it stays out.

### Region of interest

On iOS, `rectOfInterest` was already set to the drawn frame plus a margin.
Apple: codes whose bounds don't intersect it aren't returned, and it "may
improve detection performance"
([AVCaptureMetadataOutput.rectOfInterest](https://developer.apple.com/documentation/avfoundation/avcapturemetadataoutput/rectofinterest)).

ML Kit has no region parameter. Cropping each frame before decoding would
mean converting it out of the camera's buffer, which costs more than the
smaller search saves. So Android now decodes the whole frame and drops any
code whose middle lies outside the frame on screen (`ScanGeometry.kt`,
`insideScanArea`). That is about accuracy, not speed: a neighbouring pack on a
shelf, or a code the window crops off, no longer gets read.

### Android's analysis frame did not cover the frame on screen

This was a real bug. CameraX's `Preview` defaults to 4:3, and the analysis
asked for 16:9. On a 4:3 sensor, 16:9 cuts the long sides, and in portrait
the long side runs across the screen. So the decoder saw about three quarters
of the preview's width, while the frame drawn on it is 78% of the width and
the scan area 90%. A code held across the full frame lost its ends. CameraX's
own docs warn that mismatched aspect ratios crop twice
([ViewPort](https://developer.android.com/reference/androidx/camera/core/ViewPort)).

Both use cases now ask for 4:3, so they see the same part of the sensor.
Analysis moved from 1280x720 to 1280x960: the same 960 pixels across the
preview's width as before, now over all of it. ML Kit's guidance is at least
2 pixels per bar and 1280x720 or more for live scanning, and no more than
about 2 megapixels. A full EAN-13 filling the frame gets about 6 pixels per
bar. The cost is about a third more pixels per frame; the debug log below
measures it.

A `ViewPort` shared by both use cases would match them too, but it needs the
view's size at bind time and a rebind when it changes. Matching aspect ratios
does the same job with nothing to keep in sync.

### Two codes in the area

Both platforms used to take whichever code the decoder listed first. They now
take the one nearest the middle, since that is the one being aimed at.

### Focus, close focus and the macro lens

Already done on iOS after the iPhone Pro tests: the triple or dual wide
virtual camera (which hands over to the ultra wide for close focus, as the
Camera app does for macro), a zoom worked out from `minimumFocusDistance`
after Apple's AVCamBarcode sample
([WWDC21, What's new in camera capture](https://developer.apple.com/videos/play/wwdc2021/10047/)),
near autofocus range, smooth autofocus off, continuous autofocus and exposure.
The main camera on recent iPhones cannot focus closer than about 20 cm, which
is where people hold a pack, so this was the biggest single fix there.

Android has no autofocus range restriction, and CameraX already runs
continuous autofocus weighted to the centre, where the frame is. The same zoom
trick could be applied from Camera2's `LENS_INFO_MINIMUM_FOCUS_DISTANCE`. Not
done in this pass: the numbers would zoom most phones close to the 2x cap, it
could stop phones that switch to a macro lens on their own (Pixels) from doing
so, and none of that can be checked without phones in hand. It is the next
thing to try if Android scans feel slow close up. ML Kit's own
`ZoomSuggestionOptions` (zoom in when a code is too far to decode) is the
other half of that and belongs in the same experiment.

One note for later on iOS: the zoom maths uses `videoFieldOfView`, the
sensor's long side, against the preview's width, which in portrait is the
short side. Apple's sample does the same. It zooms a little more than strictly
needed, and the cap of 2x keeps it modest, so it stays as it is.

### Light

Dim light is the other common reason for slow reads: the camera stretches
the shutter (motion blur) and raises gain (noise). There was already a torch
button. Now the camera says when the scene has been dark for a second, and
while the light is off the hint under the camera reads "It's a bit dark here.
Try the light." It goes back to the usual hint once the scene is lit or the
light is on.

The camera's own exposure is the measure, not the brightness of the frame,
which auto exposure keeps mid grey until it runs out of range. From aperture,
shutter time and ISO: EV100 = log2(N² / t) - log2(ISO / 100) (`ScanLight.kt`).
A lit kitchen is about 6 to 7, a dim room 3 to 4. Below 3 for a second counts
as dark, and it takes 4.5 for a second to count as lit again, so a passing
shadow doesn't flicker the hint. On Android the numbers come from each frame's
capture result (`Camera2Interop` session callback); on iOS from the device's
`ISO`, `exposureDuration` and `lensAperture`, read four times a second on the
capture queue. The torch is never turned on automatically: it is the person's
call, and a light that switches itself on is startling.

iOS's `automaticallyEnablesLowLightBoostWhenAvailable` was considered and left
off. It only exists on a few older devices, and it works by lengthening the
exposure, which is the blur we want to avoid.

### Taking the first good read

The view model already takes the first read whose check digit passes, with
no debounce. Waiting for two identical reads would add at least one frame
(33 ms) and often several while the person holds still. EAN and UPC check
digits catch every single-digit error and most swapped pairs, and both
decoders run their own checks before they report a code. A wrong
product with a valid check digit is rare, and the person sees the product
name before logging anything. `GtinTest` covers the check digit, and
`BarcodeScanViewModelTest` covers misreads being skipped and the first valid
read being taken.

### Contrast, grayscale and binarisation

- **iOS**: `AVCaptureMetadataOutput` takes no frames from us. Preprocessing
  would mean a video data output, our own pass, and Vision to decode it: more
  work per frame than the built-in detector does in total.
- **Android**: ML Kit reads the luminance plane straight from the camera
  buffer (`InputImage.fromMediaImage`), which is already grayscale. Its model
  handles uneven lighting itself. Stretching contrast needs a copy of every
  frame first, and thresholding to black and white loses the in-between
  values the decoder uses to place bar edges precisely. Neither Google nor
  Apple recommends it; the levers both name are resolution, focus and
  symbology.

### Stabilisation

Optical stabilisation runs in the lens on every phone that has it, with no
setting. Electronic stabilisation (`preferredVideoStabilizationMode` on iOS)
moves whole frames to cancel shake between them. It does not sharpen a frame
that blurred during its exposure, and that blur is what costs a read. Apple's
docs say stabilisation may add latency, the cinematic modes much more, and
they narrow the field of view
([AVCaptureVideoStabilizationMode](https://developer.apple.com/documentation/avfoundation/avcapturevideostabilizationmode)).
A lagging preview makes aiming harder. The preview layer and metadata output
keep the default, off. On Android CameraX leaves it off too.

What actually reduces blur is more light (shorter exposure), which is the
low-light hint above.

### Skipping blurred frames

Not done. On Android, `STRATEGY_KEEP_ONLY_LATEST` already drops frames while
the decoder is busy, and ML Kit returns nothing quickly on a blurred frame. A
sharpness check (variance of a Laplacian over the frame's middle) would save
at most one decode, roughly a frame's time, and its threshold would need
tuning on real phones or it starts throwing away readable frames. On iOS the
detector runs inside the capture pipeline, with nothing to skip.

## Measuring on a phone

There is no camera on the simulator, so the numbers have to come from a
device. Debug builds log two or three lines per scan at debug level, tag
`Scanner`:

```
Android camera up 412 ms after opening
Android first read 5012345678900 690 ms after the camera came up, 1102 ms after opening, 21 frames decoded at 31 ms each
Android closed after 1350 ms, 23 frames decoded at 30 ms each
```

iOS logs the same without the frame counts (the metadata output doesn't
report them).

- **Android**: `adb logcat | grep Scanner`.
- **iOS**: Xcode's console with the device attached, filter "Scanner".

### On-device test steps

1. Scan a few packs in a lit kitchen. Note "first read ... ms after the
   camera came up". Under a second is good; the old Android build is the
   comparison if you have it installed.
2. **Android, edges**: hold an EAN-13 so it spans the whole frame. It should
   read. Before this change the decoder could not see its ends.
3. **Two packs**: put two packs side by side with only one inside the frame.
   It should read the one in the frame, never the other.
4. **Off screen**: on Android, hold a pack so its barcode is just above the
   camera window (visible to the camera, cropped off screen). It should not
   read.
5. **Dark**: turn the kitchen lights off, or cup a hand over the pack.
   Within a second or two the hint should change to "It's a bit dark here.
   Try the light." Tap the light: the hint goes back. Turn it off and the
   lights on: the hint goes back within a couple of seconds. A shadow passing
   over should not change it.
6. **Speed per frame (Android)**: check "frames decoded at N ms each". Under
   50 ms on a mid-range phone means 1280x960 costs nothing that matters. Much
   more on an older phone is the reason to try 1280x720 at 4:3 (960x720).
7. The usual checks still apply: tap to focus, torch, the buzz on a read,
   and a small EAN-8.

## Not done, and why

| Not done | Why |
|---|---|
| Contrast, grayscale or binarisation passes | No hook on iOS; on Android a per-frame copy that can lose edge detail, with no gain either vendor documents |
| Video stabilisation | Adds latency, narrows the view, does not fix blur inside a frame |
| Blur detection to skip frames | Saves at most one decode; needs per-device tuning |
| Android close-focus zoom and ML Kit zoom suggestions | Needs phones to tune; may fight automatic macro switching |
| Automatic torch | The person decides; a hint instead |
| iOS low-light boost | Few devices, and it lengthens exposure |
| ITF-14 | Case codes, not consumer packs; easily misread as a shorter code |
| Two identical reads before accepting | Slower; the check digit and the decoders' own checks already guard |
| Vision or VisionKit on iOS | No faster than the metadata output; VisionKit brings its own UI |
