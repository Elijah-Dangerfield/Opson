# 0.2 release checklist

Things that can't live on `main` while it has to stay releasable as 0.1.x,
or that need the owner. Do them when 0.2 is cut, before flipping the
`release.*` flags.

## Health (`release.health`)

- [ ] Register the HealthKit capability on `com.opson.Opson` (App Store
      Connect API `POST /v1/bundleIdCapabilities`, type `HEALTHKIT`), or the
      next signed build fails.
- [ ] Put back the entitlement and capability held out of `main`:
      `com.apple.developer.healthkit` = true and
      `com.apple.developer.healthkit.access` = [] in
      `apps/ios/iosApp/iosApp.entitlements`, and `SystemCapabilities` →
      `com.apple.HealthKit` enabled in `project.pbxproj`. They were left out
      because an entitlement with no HealthKit UI risks guideline 2.5.1 on a
      0.1.x hotfix. The framework link and `NSHealthShareUsageDescription`
      are already in.
- [ ] Play Console, App content › Health apps: declare the four Health
      Connect read permissions before any Android upload that includes them.
- [ ] Add the privacy paragraph proposed in
      `docs/plans/screens/integrations.md` to `legal/privacy.md`. Health data
      stays on the device, so the App Store label doesn't change.
- [ ] Check the rationale activity's privacy URL
      (`https://nightjarlabs.llc/opson/privacy`).
- [ ] Try the real permission flows on an iPhone and an Android phone.

## Still to build for Health

- "Should exercise change your range?" (No / Some of it / All of it, range
  only ever goes up) and "Days left out of the math" from `integrations.png`.
  Left out of the first pass because they move targets; the design already
  states the safety rule, so build it to the design and mark the numbers
  `TODO(clinician)`.
- Wear OS / Apple Watch quick-log rows (with the watch work, wave 2).

## Barcode (`release.barcode`)

- [ ] Push the new Food catalog Grafana panels (`ops/grafana/foods.json`)
      after prod runs V12 and V13; until then their prod queries fail.
- [ ] App Privacy: check whether barcode misses (product interaction, not
      linked to the user) need declaring, and add the wording from
      `docs/plans/screens/barcode.md` to `legal/privacy.md`.
- [ ] Try a real scan on an iPhone and an Android phone: a hit, a miss and a
      denied camera.

## Photo estimate (`release.photo`)

- [ ] Create an Anthropic API key for the server, and check the org's data
      settings match the privacy text below (API inputs not used for
      training, retention as short as the org allows).
- [ ] Set it on dev first, try it, then on prod. With no key the route
      answers 503 and the app shows "Photo estimates aren't available right
      now", so nothing breaks before this. Each line prompts and refuses an
      empty value:

      ```bash
      stty -echo; printf "Anthropic API key: "; read -r AK; stty echo; printf "\n"
      if [ -n "$AK" ]; then fly secrets set ANTHROPIC_API_KEY="$AK" -a opson-server-dev; else echo "No key entered, nothing set."; fi; unset AK
      ```

      Then the same with `-a opson-server-prod` once 0.1 is through review.
      `ANTHROPIC_PHOTO_MODEL` is optional (default `claude-opus-5-5`).
- [ ] Add to `legal/privacy.md`, under "What else the app sends": "**Meal
      photos**, only when you use Photo estimate, are sent with your note to
      [Anthropic](https://www.anthropic.com/legal/privacy), which reads them
      and returns a list of foods. We don't save the photo or keep a copy on
      our server, and it is not linked to your account. Anthropic does not
      use it to train its models." Keep "No contacts, photos or microphone
      access unless you choose a feature that needs it".
- [ ] App Store Connect, App Privacy: add **Photos or Videos**, used for
      **App Functionality**, **not linked** to the user, **not used for
      tracking**. Play Console Data safety: Photos, collected, not shared
      for advertising, processed ephemerally, not linked.
- [ ] `NSCameraUsageDescription` already mentions meal photos (it is on
      `main`, harmless with the flag off). No `NSPhotoLibraryUsageDescription`
      is needed: the library side is PHPicker. Android adds no permission;
      it gains a `FileProvider` (`${applicationId}.mealphoto`) for the
      camera's output file.
- [ ] Try it on an iPhone and an Android phone: camera, library, a denied
      camera, a plate it gets wrong (swap, resize, remove, add), Blind mode,
      and the 503 with the key unset.
- [ ] Plus: when it has a price, its gate goes where the code says
      `Plus gate` (`PhotoEstimateViewModel` before the camera and library,
      `PhotoEstimateLimiter` on the server), and the design's Plus badge goes
      on the sheet header.

## Voice log (`release.voice`)

No entitlement or capability: Speech and the microphone need only their
usage strings, which are already in `Info.plist` (the binary references
them as soon as `:libraries:speech:impl` links, like the camera).

- [ ] App Privacy: audio is not collected by Opson (it goes to the platform
      recognizer and is not stored), so the label should not change. Confirm,
      and say so in `legal/privacy.md`: on iOS, Apple processes the audio when
      the phone cannot recognise the language on the device; on Android, the
      phone's speech service does when there is no on-device recognizer.
- [ ] Play Console: `RECORD_AUDIO` is declared from the build that links the
      speech library. Check the Data safety form ("Audio" is not collected or
      shared, processed ephemerally on the device or by the system service).
- [ ] Try it on an iPhone and an Android phone: a spoken meal, a refused
      mic, airplane mode, and a phone with no on-device model.

## Server, before any 0.2 build reaches people

- [ ] Deploy the server to prod once 0.1 is through review. It carries the
      settings merge (a 0.1 phone saving settings must not erase what a 0.2
      phone added), V12 to V14, and the new routes (including
      `POST /v1/describe/photo`, which needs `ANTHROPIC_API_KEY`).

## Search quality

- [ ] After the prod deploy (V15 adds partial indexes; a few seconds of held
      writes), run the default-portions backfill on prod. Command in
      `docs/plans/search-quality.md`; it prompts for the URL, dry-runs first.
- [ ] Open Food Facts: grow prod's disk (2 GB now; the table is 2.3 GB and a
      reload holds two copies), load it, credit Open Food Facts wherever its
      foods appear, then set `FOOD_SEARCH_OFF_ENABLED=true`.
