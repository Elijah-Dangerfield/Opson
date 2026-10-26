# Design polish: v3 against the app

A pass over the shipped screens against `docs/design/v3/`, so they match the
design as closely as the design system allows. Scope: Onboarding, Trends,
Patterns, Nutrients, Weight, Foods, Day close, Modes and the pause, Support,
More, Look and feel, Water settings. Today, the logger, Targets,
Integrations, Reminders, Fasting and the scanner belong to other passes.

Renders come from `./scripts/design_sync.main.kts export`
(`design-sync/app/<id>.png`). The v3 images are named by design id, so the
pairs below are by hand.

**Status key.** *matched*: no difference worth a change. *fixed*: changed in
this pass. *deliberate*: the app differs on purpose (CHANGES.md, the 0.1
decisions, or a release flag). *deferred*: real, but not this pass; the reason
is given. *owner*: needs a decision.

## Across every screen

- **The design has a status bar and the previews don't.** Every v3 image
  starts about 44 pt lower. Not a difference in the app. *deliberate*
- **Ranges read "1,900 to 2,300", v3 writes "1,900–2,300".** The app's
  formatter spells "to" everywhere (Today, Trends, Day close). *deliberate*
- **Times follow the device's clock format** ("8:10 AM" in the render,
  "8:10 am" in v3). *deliberate*
- **Amounts read as decimals ("0.25 cup", "1.5 × 1 cup", "2 bar").** Not in a
  v3 image, found in the app. *fixed*: `HouseholdAmount` in `:libraries:diary`
  is now the one formatter for food amounts: fractions for household
  measures, a plural unit past one, metric stays decimal. Entries logged
  before this keep the text they were saved with.

## Pairs

### Onboarding

| v3 | App | Differences | Status |
|---|---|---|---|
| `onboarding-welcome` | `onboarding-screen__onboarding-welcome` | Plan card has no Plus row and no barcode in the free line. | *deliberate* (no price or "Coming later" in 0.1; barcode is a 0.2 flag) |
| | | Headline is `display` (32 sp) and wraps to four short lines; v3's is smaller and fits "Write down what you" on one line. | *fixed*: `h1` |
| `onboarding-why` | `onboarding-screen__onboarding-why` | Matches. | *matched* |
| `onboarding-numbers` | `onboarding-screen__onboarding-numbers` | The footnote ("This only picks a starting point") is caption size; v3 sets it at body size. | *fixed* |
| `onboarding-statement` | `onboarding-screen__onboarding-statement` | Explainer is caption size; v3 uses body. | *fixed* |
| | | Copy: v3 says "the answer stays on this phone"; the app says "is used for nothing else". Settings sync to the account, so v3's line isn't true. | *deliberate* |
| `onboarding-recommendation` | `onboarding-screen__onboarding-recommendation` | Matches, bar chip shape (v3's "Or start with" chips are squarer). | *matched* |
| `onboarding-account` | `onboarding-screen__onboarding-account-ios` | Google and email buttons, the coach line and "add an account later in More". | *deliberate* (`SignInMethods`; b74c277) |

### Trends

| v3 | App | Differences | Status |
|---|---|---|---|
| `trends` | `trends-previews__trends` | Bars are wide with thin gaps; v3 draws narrower bars with clear gaps between days. The chart had a 30 dp cap that never applied (modifier order). | *fixed* in `IntakeBarChart` |
| | | The tune button is 58 dp; v3's is about 44. | *fixed*: 48 dp, the touch-target floor |
| | | Recap rows are 48 dp tall and the values are 16 sp ExtraBold; v3's rows are about 40 pt with values a step smaller. | *fixed* (`FactRow`) |
| `trends--full` | `trends-previews__trends-below` | Expended card missing. | *deliberate* (`release.health`, 0.2) |
| `trends-blind` | `trends-previews__trends-blind` | New-food chips are large (body bold, 12/7 padding); v3's are compact tags. Recap rows as above. | *fixed* |
| | | The week card carries "Patterns ›"; v3's Blind card doesn't. Blind has no Recap card to hold the link, so this is the only way in. | *deliberate* |
| `trends-coast` | `trends-previews__trends-coast` | Matches, rows as above. | *fixed* (rows) |

### Patterns

| v3 | App | Differences | Status |
|---|---|---|---|
| `patterns`, `patterns--full` | `trends-previews__patterns`, `-below` | Card titles use `sheetTitle` (20 sp); v3's are card titles, the same size as Trends' "Recap". | *fixed* |
| | | Fact rows and their values as on Trends. | *fixed* |
| | | Hunger chips and new-food chips are large; v3 uses compact tags. | *fixed* (size); v3's tags are squarer than `PillTag`'s pill, left as the system draws it |
| `patterns-blind` | `trends-previews__patterns-blind` | v3's Blind timing card has only "Breakfast, usually"; the app also shows "Dinner on Fri and Sat, about 45 min later" and "Longest gap". Both are times, not amounts. | *owner*: drop them in Blind or keep them |

### Nutrients

| v3 | App | Differences | Status |
|---|---|---|---|
| `nutrients-day` | `trends-previews__nutrients-day` | Macro values read "53 g of 120 g"; v3 reads "88 of 120 g". The text comes from the shared policy formatter, which Today's tests also pin. | *deferred*: a formatter change for whoever owns `:libraries:nutrition` and Today |
| | | Energy line has a double space before "· range". | *fixed* |
| | | The energy bar is about 14 dp thick; v3's is about 8. `RangeBar` is shared with Today's summary. | *deferred* (Today's owner) |
| `nutrients-week`, `--full` | `trends-previews__nutrients-week`, `-below` | Caption ends "than a day."; v3 "than a day does." | *fixed* |

### Weight

| v3 | App | Differences | Status |
|---|---|---|---|
| `weight` | `trends-previews__weight` | Trend figure prints "73.4 kg" all at figure size; v3 sets the unit small and muted next to the number. | *fixed* |
| | | The weigh-in field's "kg" is caption size; v3 sets it at body size. | *fixed* in `InputBox`, so every field suffix ("kcal", "g") follows, as v3's Add a food also shows |
| | | The 1W / 1M / 3M control is 48 dp tall; v3's is about 34. | *deliberate* (48 dp touch targets) |
| `weight-trend` | `trends-previews__weight-trend` | Matches (the word comes from the fixture's slope). | *matched* |
| `weight-hidden` | `trends-previews__weight-hidden` | No "Send weigh-ins to my provider · Coming later" row. | *deliberate* (no "Coming later" rows in 0.1) |

### Foods

| v3 | App | Differences | Status |
|---|---|---|---|
| `foods` | `foods-previews__foods` | Data attribution footer under the list. | *deliberate* (licence) |
| | | Saved meal subtitles capitalise every item ("Banana, Coffee with milk"); v3 writes them as a sentence. Lower-casing names would break brand names. | *deferred* |
| `foods-create-meal` | `foods-previews__foods-create-meal` | Matches. | *matched* |
| `foods-add-food` | `foods-previews__foods-add-food` | No "Scan the label instead"; a Fiber field v3 doesn't draw. | *deliberate* (label scan is not in 0.2; fiber is the Nutrient focus field) |
| `foods-add-food-blind` | `foods-previews__foods-add-food-blind` | No "Photograph the label", so the explainer is one line. | *deliberate* |

### Day close

| v3 | App | Differences | Status |
|---|---|---|---|
| `day-close` | `today-previews__day-close` | Date sits on its own line under the bar; v3 puts it under the title in the top bar, as Nutrients does. | *fixed* |
| | | The kcal card has a loose gap between the figure and "Inside your range". | *fixed* |
| | | No Expended row. | *deliberate* (`release.health`) |
| `day-close-big-day` | `today-previews__day-close-big-day` | Matches, bar the header. | *fixed* (header) |
| `day-close-big-day-committed` | `today-previews__day-close-big-day-committed` | After committing, v3 drops the "Tomorrow: ..." line; the app keeps it. | *fixed* |
| `day-close-revealed` | `today-previews__day-close-revealed` | Matches, bar the header. | *fixed* (header) |
| `day-close-blind` | `today-previews__day-close-blind` | Feeling chips are solid accent pills; v3 uses the outlined choice chip with a check, as everywhere else. | *fixed* |

### Settings

| v3 | App | Differences | Status |
|---|---|---|---|
| `modes`, `modes--full` | `modes-screen__modes` | Preset lines for Full numbers and Rhythm drop "Watch sync" and "with reminders". | *deliberate* (0.2 flags) |
| `modes-pause` | `pause-sheet-content__modes-pause` | Matches. | *matched* |
| `support` | `support-screen__support` | No "Coming later" rows; "Send feedback" added; data line says it syncs. | *deliberate* |
| `support-delete` | `support-screen__support-delete` | Matches; copy says the server is wiped too, which is true. | *matched* |
| `more` | `more-screen__more` | Reminders, Watch, Fasting and Widgets rows are flagged off; Look and feel is an extra row. | *deliberate* |
| | | Every row icon is accent; v3 colours only the first group's icons and draws the rest muted. | *fixed* |
| none | `look-and-feel-screen__*` | No v3 design. Reads as finished. | *matched* |
| none | `water-settings-screen__*` | No v3 design. Before settings load the goal line reads "0 glasses,  a day". | *fixed*: the line waits for the amount |

## Before and after

Checked by re-running `export` and laying v3, the old render and the new one
side by side.

- **Amounts.** Before: "0.25 cup", "1.5 × 1 cup", "2 bar", "1.5 medium" on
  the amount sheet. After: "¼ cup", "1½ cups", "2 bars", "1½ medium". Grams
  unchanged ("150 g"). Describe lines that arrive as "1 1/2 cups" are logged
  as "1½ cups"; text that doesn't start with an amount ("a handful") is kept.
- **Trends.** Before: bars filled each day's slot, recap rows 48 dp with
  16 sp values. After: bars capped at 30 dp with clear gaps, rows 40 dp with
  15 sp ExtraBold values. Blind's new-food
  chips now fit on one line, as in v3.
- **Patterns.** Before: card titles at 20 sp, so each card read as a page
  heading. After: card titles match Trends' "Recap"; fact rows and tags as on
  Trends. More of the page fits above the fold.
- **Nutrients.** Before: "1,130 kcal  · range". After: one space. Caption
  copy matches v3.
- **Weight.** Before: "73.4 kg" all at 36 sp. After: "73.4" at 36 sp with a
  small muted "kg", as v3. The trend arrives bare from the policy
  (`FormatContext.bare` now covers weight); today's reading keeps its unit.
- **Day close.** Before: the date was a loose line under the top bar and the
  kcal card had a gap under the figure. After: the date is the top bar's
  subtitle and the range line sits under the figure, matching v3 almost
  exactly. Committed: the "Tomorrow" line goes once the acknowledgement shows.
  Blind: the feeling chips are the outlined `ChoiceChip` with a check, and
  the logged-meal tiles are 40 dp, as v3.
- **Onboarding.** Welcome headline at `h1` now breaks like v3 (three lines,
  not four). The statement's explainer and the numbers footnote are body
  size; the quote is `heroTitle`, so "have been" stays on the first line.
- **More.** Water, Support and Plan icons are muted; the first group keeps
  the accent.
- **Water settings.** The goal line no longer renders as "0 glasses,  a day"
  before settings load.

## Left for the owner

- **Patterns in Blind**: drop "Dinner on Fri and Sat" and "Longest gap" to
  match v3, or keep them as times that reveal no amount.
- **"53 g of 120 g" or "88 of 120 g"** on Nutrients: a change to the shared
  formatter that Today's tests pin.
- **Entries logged before the amount fix** keep "0.25 cup". A display-time
  rewrite with `HouseholdAmount.tidy` on Today's item rows would cover them;
  Today is outside this pass.
