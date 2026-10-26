# Design sync and preview screenshots

Every `@Preview` in the project is also a screenshot. `:apps:screenshots`
renders them to PNGs, and `scripts/design_sync.main.kts` moves those PNGs to
and from Claude Design. The skill in `skills/design-sync/` tells an
agent how to drive both, in Claude Code or in Claude Design.

## Rendering previews

```bash
./gradlew :apps:screenshots:recordRoborazziDebug    # write the PNGs
./gradlew :apps:screenshots:verifyRoborazziDebug    # fail on any change since the last record
./gradlew :apps:screenshots:compareRoborazziDebug   # write diff images, don't fail
```

One parameterized Robolectric test (`PreviewScreenshotTest`) finds every
JetBrains `@Preview` under the project's root package, including private ones,
and renders each in native graphics mode at 390x844dp, xxhdpi (1170x2532 px),
SDK 35, with `LocalInspectionMode` on and the clock held and stepped a fixed
second. The same code renders the same bytes, which is what makes the diff
useful. Previews already wrap themselves in `PreviewContent`, so the test adds
no theme of its own.

A plain `./gradlew testDebugUnitTest` skips this test. It is the slowest thing
in the sweep, so it only runs when a Roborazzi task asks for it.

**A preview that throws does not fail the run.** It is recorded with its
exception in a per-preview result file and in the exported manifest, and every
other screen still renders. Roborazzi's own verify failure is the exception:
that one fails, because failing is the point of verify.

**Opting out.** Annotate the preview with `@NoScreenshot` (in `:libraries:ui`)
and say why next to it. Use it for previews that cannot render off-device or
mean nothing to a designer, not to hide one that is merely broken.

**What is not rendered.** Previews in `:apps:compose`. An Android library
cannot depend on an Android application module, so keep screens and their
previews in feature modules. Every module under `:features:` and
`:libraries:` is picked up automatically.

### Where the images live

| What | Where |
|---|---|
| Recorded PNGs (the baseline) | `apps/screenshots/build/outputs/roborazzi/` |
| Diffs from verify / compare | `apps/screenshots/build/outputs/roborazzi-compare/` |
| Per-preview results | `apps/screenshots/build/outputs/screenshot-results/` |

Under `build/` on purpose: renders change every time a screen does, and
committing them would grow the repo by megabytes per UI change. The catch is
that `./gradlew clean` deletes the baseline, so a before/after check is
record, change code, verify, with no clean in between.

To keep a baseline in git instead (for diffs across machines or in CI), set
`screenshots.commitBaseline=true` in `gradle.properties`. Recorded PNGs then go
to `apps/screenshots/baseline/`, which is not ignored. Diffs stay under
`build/` either way.

## Screen ids

A screen id names one preview. It is the PNG's file name and the key that
pairs an app render with a design, so it has to be stable and readable.

```
<file>__<preview>[--<@Preview name>][--<parameter index>]
```

- `<file>`: the Kotlin file the preview is in, without `.kt`, kebab-cased. For
  a preview declared inside a class, the class name.
- `<preview>`: the preview function's name, kebab-cased, with every word
  `preview` removed. If nothing is left, `default`.
- `--<@Preview name>`: only when the annotation sets `name`.
- `--<parameter index>`: only for a `@PreviewParameter` preview, one per value.
- Kebab-case splits on case changes and anything that is not a letter or digit,
  then lowercases: `HTMLText2Row` is `html-text2-row`.
- Two previews that still collide get `-2`, `-3` in sorted order.

| Preview | Screen id |
|---|---|
| `FeedbackScreen.kt`, `FeedbackScreenPreview()` | `feedback-screen__feedback-screen` |
| `FeedbackScreen.kt`, `FeedbackScreenPreviewDisabled()` | `feedback-screen__feedback-screen-disabled` |
| `Switch.kt`, `Checked()` | `switch__checked` |
| `BarChart.kt`, `@Preview(name = "Dense") BarChartDensePreview()` | `bar-chart__bar-chart-dense--dense` |

Renaming a preview function or its file changes its id, and the design for it
stops pairing. The code is `ScreenIds.kt` in `:apps:screenshots`; change this
section with it.

## App to design

```bash
./scripts/design_sync.main.kts export             # design-sync/app/ + design-sync/app.zip
./scripts/design_sync.main.kts export --publish   # also push it to the design-sync branch
```

`design-sync/app/` holds one PNG per rendered screen and `manifest.json`: app
name, commit, whether the tree had uncommitted changes, `generatedAt`, the
render size, and per screen its `id`, `file`, `module`, `preview` (the
function), `previewName`, and `failure` if it did not render.

`--publish` force-pushes `design-sync/app/` as the only content of a fresh,
parentless commit to an orphan branch named `design-sync` on `origin`. It uses
a temporary index and git plumbing, so your branch, index and working tree are
untouched, and the branch never accumulates image history. The manifest is then
at

```
https://raw.githubusercontent.com/<owner>/<repo>/design-sync/app/manifest.json
```

with each image next to it. That URL only works for a public repo. For a
private one, give Claude Design `design-sync/app.zip`.

## Design to app

Claude Design exports one PNG per screen, named by screen id, plus an optional
`manifest.json` (`{"screens": [{"id": "...", "file": "...", "title": "...", "notes": "..."}]}`), usually zipped. Designer notes show up in the compare README.

```bash
./scripts/design_sync.main.kts import ~/Downloads/design.zip   # or a folder
```

The PNGs land in `design-sync/design/`. Each one is paired with an app render:
exact id first, then a loose match that ignores case, separators and the word
"preview", used only when exactly one app screen fits. For every pair,
`design-sync/compare/<id>.png` shows the app on the left and the design on the
right at the same height. `design-sync/compare/README.md` lists the pairs, the
design screens with no app screen, and the app screens with no design.

Run `export` before `import`: pairing reads `design-sync/app/manifest.json`.
`status` prints what is on disk and when it was made.

All of `design-sync/` is gitignored.
