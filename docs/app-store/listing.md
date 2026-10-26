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
> Log meals fast and choose how much you see. Full numbers, soft words, numbers saved for later, or none at all. One setting, applied everywhere.

**Description** (4000):
> Opson is a food log for everyone, including people who find calorie counting hard.
>
> Log the way you like: search thousands of foods, copy yesterday's breakfast, save meals you eat often, describe a meal in your own words, or mark a meal as eaten with no details. Pick several foods at once and add them in one tap.
>
> Then decide what you see. Opson has modes for different relationships with numbers:
>
> • Full numbers: calories, protein, carbs, fat and fiber, with targets.
> • Delayed reveal: log all day, see totals in the evening.
> • Nutrient focus: protein and fiber as simple progress words, no calories.
> • Blind: no numbers anywhere, just your meals.
> • Rhythm, Explorer, Mindful, Fuel, Sprint and Coast for other ways of eating and checking in.
>
> Whatever you pick applies to every screen. A hidden number stays hidden everywhere, label included. You can turn on a short pause before showing yourself more numbers, and showing fewer always applies right away.
>
> Opson never sends "calories left" reminders, never lowers tomorrow's target after a big day, and has no streaks.
>
> Your log is saved on your phone and backed up to your account. No ads. Export or delete everything from the Support page.
>
> Opson is not medical advice. If you are working on your relationship with food, the Support page lists places to get help.

**Keywords** (100): `food log,meal tracker,calorie counter,macros,protein,nutrition,diary,mindful eating,intuitive,diet`

**Support URL**: `https://nightjarlabs.llc/contact`

**Marketing URL**: `https://nightjarlabs.llc/apps`

**Privacy Policy URL**: `https://nightjarlabs.llc/opson/privacy`

**Category**: Health & Fitness (secondary: Food & Drink)

**Copyright**: `2026 Nightjar Labs`

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
| Usage Data → Product interaction | Yes | No | Analytics |
| Diagnostics → Crash data, Performance data | Yes | No | App functionality |
| Contact info → Email | Only if the user signs in with email | Yes | App functionality |

## App Review information

**Sign-in required**: No. The app creates an anonymous account on first launch.

**Notes for the reviewer**:
> No sign-in is needed. On first launch, answer or skip the four questions, then log food from the + button in the middle of the tab bar.
>
> The app's core feature is a visibility setting that hides or softens nutrition numbers for people who find them hard. To see full numbers, go to More → Modes and numbers and pick "Full numbers".
>
> Account deletion: More → Support → Delete everything.

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
