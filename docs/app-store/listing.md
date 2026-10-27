# App Store listing, version 0.1

Everything App Store Connect asks for, ready to paste. Character limits are
Apple's. Check each claim against the build before you submit: a listing that
promises a feature the build hides is a rejection.

## App record (My Apps → New App)

| Field | Value |
|---|---|
| Platform | iOS |
| Name | Opson (if taken: "Opson Food Log") |
| Primary language | English (U.S.) |
| Bundle ID | `com.opson.Opson` (already registered, with Sign in with Apple) |
| SKU | `opson-ios` |
| User access | Full access |

## Version information

**Subtitle** (30): `Food log with gentle numbers`

**Promotional text** (170):
> A food log that shows you all the numbers, some of them, or none. Your call.

**Description** (4000, kept to five plain sentences on purpose):
> Opson is a food log where you decide how many numbers you see. You can track everything like a regular calorie counter, hide calories and just log meals, or save the totals for the end of the day. Logging is quick: search a food, copy yesterday's breakfast, reuse a saved meal, or just mark that you ate. Whatever you pick applies everywhere in the app, and there are no streaks or "calories left" nags. Your log syncs to your account, and you can export or delete it whenever you want.

**Keywords** (100): `food log,meal tracker,calorie counter,macros,protein,nutrition,diary,mindful eating,intuitive,diet`

**Support URL**: `https://nightjarlabs.llc/contact`

**Marketing URL**: `https://nightjarlabs.llc/apps`

**Privacy Policy URL**: `https://nightjarlabs.llc/opson/privacy`

**Category**: Health & Fitness (secondary: Food & Drink)

**Copyright**: `2026 Nightjar Labs LLC`

## Age rating

Answer "None" to everything except:

| Question | Answer | Why |
|---|---|---|
| Medical or treatment information | Infrequent / mild | Nutrition values and a link to eating disorder support resources |

Expected result: 12+ (Apple may compute 9+; either is fine).

## App Privacy (nutrition labels)

Data used to track you: **No**.

| Data type | Collected | Linked to the user | Purpose |
|---|---|---|---|
| Health & Fitness → Health (food log, weight, how meals felt) | Yes | Yes | App functionality |
| Identifiers → User ID (anonymous account id) | Yes | Yes | App functionality |
| Usage Data → Product interaction | Yes | Yes | Analytics |
| Diagnostics → Crash data, Performance data | Yes | Yes | App functionality |
| User Content → Customer support (feedback text, sent to Sentry) | Yes | Yes | App functionality |
| Search History → Search history (food searches, stored without the account; can appear in a crash report about a failed search) | Yes | Yes | App functionality, Analytics |
| Contact info → Email | Only if the user signs in with Apple | Yes | App functionality |

Diagnostics count as linked because `TelemetryUserBinder` attaches the anonymous account id to crash reports and feedback. It never sends the email. Usage events carry an install id rather than the account id, but server logs pair the two, so they are declared as linked too. Search history is declared as linked for the same conservative reason, until crash reports stop carrying failed search URLs (docs/legal-review-2026-10.md).

## App Review information

**Sign-in required**: No. The app creates an anonymous account on first launch.

**Notes for the reviewer**:
> No sign-in is needed. On first launch, answer or skip the four questions, then log food from the + button in the middle of the tab bar.
>
> The app's core feature is a visibility setting that hides or softens nutrition numbers for people who find them hard. To see full numbers, go to More → Modes and numbers and pick "Full numbers".
>
> Account deletion: More → Support and sharing → Delete everything.
>
> Opson is a food log and makes no medical claims. Onboarding's first screen says it isn't medical advice, and More → Support and sharing has an About Opson card with the same note and links to the privacy policy and terms. The same page lists eating disorder helplines.

**Contact**: your name, phone and email (Apple requires a reachable person).

## Screenshots

6.9" display (iPhone 16 Pro Max / 17 Pro Max simulator, 1320 × 2868) is required; Apple scales it for smaller phones. Five shots, in this order:

1. Today in Full numbers with a few meals logged.
2. The logger with two foods picked.
3. Today in Blind mode (meal tiles, no numbers).
4. Modes and numbers.
5. Trends, week view.

They are generated from the iOS simulator; see `docs/app-store/screenshots/`.

## Build

Upload through the `beta` lane (`apps/ios/fastlane/Fastfile`) or the Beta workflow on GitHub Actions once the app record exists. Then attach the build to version 0.1 and submit.
