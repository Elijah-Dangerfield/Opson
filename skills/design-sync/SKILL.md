---
name: design-sync
description: Sync app screens with Claude Design in either direction. App to design means the app's real screens (every Compose @Preview rendered to a PNG) go to Claude Design so the design matches the app. Design to app means screens exported from Claude Design are compared side by side with the app's renders and the code is changed to match. Use when the user mentions Claude Design together with screens, previews, mockups or screenshots, or asks to sync, match, compare or update the design or the app from the other one. Works in Claude Code with the repo, and in Claude Design or claude.ai without one.
---

# Claude Design sync

Two sides, one key. The app side is every Compose `@Preview` rendered to a PNG.
The design side is the screens in Claude Design. A screen on one side pairs
with a screen on the other by its **screen id**.

## First, where are you?

- **Claude Code**: you have a shell and the app's repo. Check with
  `ls scripts/design_sync.main.kts`. If it exists, follow "In Claude Code".
- **Claude Design or claude.ai**: no shell, no repo, but you can view images,
  fetch URLs or read uploaded files. Follow "In Claude Design".

If the repo has no `scripts/design_sync.main.kts`, the app was not built from a
template that has this tool. Say so and stop.

## Which direction?

- **App to design**: "update the designs to match the app", "here's the app".
- **Design to app**: "build what I designed", "make the app match the design".

If the user's words don't settle it, ask which one. Don't do both unasked.

## Screen ids

`<file>__<preview>[--<@Preview name>][--<parameter index>]`, kebab-case.
`<file>` is the Kotlin file holding the preview (`FeedbackScreen.kt` gives
`feedback-screen`). `<preview>` is the preview function's name with the word
"preview" dropped (`FeedbackScreenPreviewDisabled` gives
`feedback-screen-disabled`). So: `feedback-screen__feedback-screen-disabled`.

The app's `manifest.json` lists every id. Copy ids from it; derive one by the
rule only for a screen the app does not have yet.

## Rules for both environments

- Work one screen at a time: look at both images, decide, change, move on.
- After each screen, say what changed in a line or two. Name the id.
- Never invent a screen the other side lacks without saying so. List unmatched
  screens and ask what to do with them.
- A manifest entry with a `failure` has no image. Say it could not be compared;
  don't guess what it looks like.
- Images are 390x844 points rendered at 3x (1170x2532 px). Compare layout,
  spacing, type, colour, copy and component choice, not pixel noise.
- Finish with a short summary: screens changed, screens left alone and why,
  screens with no partner.

## In Claude Code

### App to design

1. `./scripts/design_sync.main.kts export --publish`
   - It renders every preview and prints a manifest URL on the `design-sync`
     branch. Give the user that URL to paste into Claude Design.
   - If it says the repo is private or it cannot publish, run plain
     `export` and give the user `design-sync/app.zip` to upload instead.
2. Report how many screens rendered and list any failures from the output,
   with the preview each one came from, so the user knows what is missing.

### Design to app

1. Get the Claude Design export (a zip or folder) from the user, then run
   `./scripts/design_sync.main.kts export` (skip it if `status` shows a fresh
   export for the current commit) and
   `./scripts/design_sync.main.kts import <path>`.
2. Read `design-sync/compare/README.md`. It lists pairs, design screens with no
   app screen, app screens with no design, and any notes the designer left.
3. For each pair, open `design-sync/compare/<id>.png` (app left, design right).
   Write down the concrete differences: "title is 28sp in design, 22sp in app",
   "button is full width in design", "copy says X, app says Y". If a
   difference is unclear at that size, open the full images in
   `design-sync/app/` and `design-sync/design/`.
4. Find the code. The manifest's `preview` field is the preview function and
   `module` its Gradle module. The screen composable is what that preview calls.
5. Change the code to match, following the repo's AGENTS.md:
   - Use design-system tokens and components (colours, typography, spacing,
     shapes from the UI library). Never hardcode a value the design system
     already names. If the design needs a value the system lacks, ask before
     adding one.
   - Keep the screen's previews in step. A new state in the design gets a new
     preview built from fake state, so it renders next time.
   - Previews must use the JetBrains `@Preview` annotation the project already
     uses, and must render without a device.
6. Run `export` again (it re-renders), then `import` the same design and
   re-check the pairs you changed. Stop when the remaining differences are ones
   you have reported and the user can decide on.
7. Design screens with no app screen: list them. Do not build a new screen
   unless the user asks. If they do, name its preview so the id matches the
   design file.

## In Claude Design

### App to design

1. Get the app's screens. Either fetch the manifest URL the user gives you
   (`.../design-sync/app/manifest.json`; each screen's image is its `file` in
   the same folder, or under `imageBaseUrl`), or open the `app.zip` they
   upload (`manifest.json` plus one PNG per screen).
2. Match each app screen to a design screen by id, then by name if the design
   uses its own names. Tell the user which matches you assumed.
3. For each pair, view the app image and update the design to match the app:
   layout, copy, type, colour, components. The app is the source of truth in
   this direction.
4. App screens with no design: list them and ask before creating designs for
   them.

### Design to app

1. Export each screen the user wants built or changed as a PNG at 390x844,
   3x if possible (1170x2532 px), one screen per image, nothing around it.
2. Name each file `<screen id>.png`. Use the id from the app's manifest when
   the screen already exists in the app. For a new screen, make one up by the
   rule and say it is new.
3. Add a `manifest.json`:
   ```json
   {"screens": [
     {"id": "feedback-screen__feedback-screen", "file": "feedback-screen__feedback-screen.png",
      "title": "Feedback", "notes": "Send button moves under the field"}
   ]}
   ```
   `notes` is for anything an image can't show: interactions, states,
   animation, data rules.
4. Zip the PNGs and the manifest together and give the user the zip to
   download. Tell them to bring it to Claude Code and ask to sync the design
   into the app.
