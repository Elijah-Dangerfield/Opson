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
| Open Food Facts products in search | After the dev load finishes and its search is switched on |
| Home screen widgets | The in-app Widgets page only; the native widget is held for 0.2 |
| Shake the phone | Opens the QA menu: flags, config overrides, network log |

## Set the photo key on dev (when you want photo estimates)

```bash
stty -echo; printf "Anthropic API key: "; read -r AK; stty echo; printf "\n"
if [ -n "$AK" ]; then fly secrets set ANTHROPIC_API_KEY="$AK" -a opson-server-dev; else echo "No key entered, nothing set."; fi; unset AK
```
