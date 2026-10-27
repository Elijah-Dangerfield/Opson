# Trying the dev build on your iPhone

The Debug build talks to the **dev** server (`opson-server-dev`), where every
0.2 flag is on, so it shows everything built so far. TestFlight builds are
Release builds and talk to prod, where the flags are off.

## Run it

1. Plug in the iPhone (or pair it over Wi-Fi in Xcode › Devices).
2. `open apps/ios/iosApp.xcodeproj`, pick the **iosApp** scheme and your phone.
3. Run. Signing is automatic with team `MSMDV43SUS`. The first run builds the
   Kotlin framework and takes a few minutes; later runs are quick.
4. The first time, iOS asks you to trust the developer: Settings › General ›
   VPN & Device Management.

If the Kotlin side fails to compile, Xcode now stops with the error instead of
running the previous build, so what you see is always the current code.

Debug builds carry their own entitlements (`iosAppDebug.entitlements`) with
HealthKit, so Apple Health connects. Release builds don't, until 0.2.

## What works and what doesn't yet

| Feature | On the dev build |
|---|---|
| Logging, search (with ranking fixes), Today, Trends, Foods, modes | Yes |
| Barcode scan, typed codes | Yes |
| Voice in Describe | Yes (real mic; the simulator says unavailable) |
| Reminders and notifications | Yes |
| Apple Health and Expended so far | Yes |
| Fasting (Full numbers and Sprint, after turning the timer on) | Yes |
| Photo estimate | Says "not available" until `ANTHROPIC_API_KEY` is set on dev |
| Open Food Facts products in search | Yes: pick it under search options, or see it mixed into All |
| Today's summary card, multi-select (Copy, Move, Save as meal), swipe to delete | Yes |
| Close the day check-in, and its switch in Reminders | Yes |
| Progress photos row in Modes, Expended card in Trends (with a watch connected) | Yes |
| Home screen widgets | The in-app Widgets page only; the native widget is held for 0.2 |
| More › Testing › QA menu (debug and TestFlight only) | Fire each reminder in 5 s, open today's check-in, reset onboarding, clear caches, override config flags, copy the user id |
| Shake the phone | The bug report dialog; on debug builds it also opens the network log |

## Where the QA menu comes from

The menu is its own module, `features/qa` and `features/qa/impl`. Gradle leaves
both out of any build whose release channel is `store` (`release.yml`, the
Fastfile's `release` lane), so an App Store or Play build has no QA code, no
Testing card on More and no QA route. A Debug build from Xcode uses
`versions.properties` (`dev`) and a TestFlight build from the `beta` lane uses
`beta`, so both carry it.

A TestFlight build promoted to the App Store keeps the code, since it is the
same binary. There the menu hides itself at run time: the App Store receipt
turns `QaAccess` off, which drops the row and the route.

To check which way a build will go before building it:

```bash
RELEASE_CHANNEL_OVERRIDE=store ./gradlew :apps:compose:verifyQaExclusion   # expects no QA
./gradlew :apps:compose:verifyQaExclusion                                    # expects QA
```

Store builds run the same check on their own and fail if QA is in the graph.

## Set the photo key on dev (when you want photo estimates)

```bash
stty -echo; printf "Anthropic API key: "; read -r AK; stty echo; printf "\n"
if [ -n "$AK" ]; then fly secrets set ANTHROPIC_API_KEY="$AK" -a opson-server-dev; else echo "No key entered, nothing set."; fi; unset AK
```
