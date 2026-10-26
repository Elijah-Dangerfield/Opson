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

## Widgets (`release.widgets`)

The shared Kotlin, the Glance UI, the app's link handling and the Swift
`IOSWidgetBridge` are on `main` and inert while the flag is off. Two commits
are held back, because each one shows up for people the moment it ships,
flag or not: the Android widget receiver puts Opson in the launcher's widget
picker, and the iOS extension needs a new App ID, an App Group and a second
profile before anything signs.

### Apple, before merging the held iOS commit

Order matters: a profile made before the App Group is on the App ID won't
carry it.

- [ ] Register the App Group `group.com.opson.Opson` (developer portal ›
      Identifiers › App Groups, or Xcode with the team signed in). The App
      Store Connect API can't create App Groups.
- [ ] On the App ID `com.opson.Opson`, enable **App Groups** and tick
      `group.com.opson.Opson`.
- [ ] Register the App ID `com.opson.Opson.Widgets` (explicit, name "Opson
      Widgets"), with **App Groups** on and `group.com.opson.Opson` ticked.
      `POST /v1/bundleIds` can create the identifier; ticking the group is
      portal only.
- [ ] Regenerate the App Store profile for `com.opson.Opson` (it is invalid
      once its App ID changes). `fastlane beta` does this itself through
      `get_provisioning_profile(readonly: false)`; delete the old one in the
      portal if it doesn't.
- [ ] Let the first `fastlane beta` create the App Store profile for
      `com.opson.Opson.Widgets` (same call, second identifier). The ASC API
      key needs App Manager or Admin.
- [ ] Merge the held iOS commit. It adds, and nothing else on `main` has:
  - the `OpsonWidgetsExtension` target in `project.pbxproj` (bundle id
    `com.opson.Opson.Widgets`, sources in `apps/ios/OpsonWidgets/`, embedded
    by the app's "Embed Foundation Extensions" phase, a target dependency
    from `iosApp`), and its shared scheme
    `xcshareddata/xcschemes/OpsonWidgetsExtension.xcscheme`;
  - `com.apple.security.application-groups` = [`group.com.opson.Opson`] in
    `apps/ios/iosApp/iosApp.entitlements` and in
    `apps/ios/OpsonWidgets/OpsonWidgets.entitlements`;
  - the Fastfile's `WIDGETS_IDENTIFIER`, its `get_provisioning_profile`
    call, and its entry in the export's `provisioningProfiles`.
- [ ] Check one TestFlight build: the widget is in the gallery, the app and
      the extension report the same version and build, and after logging a
      meal with the flag on the widget changes within a few seconds.
- [ ] App Privacy: nothing new leaves the device. The snapshot sits in the
      App Group container on the phone.

### Android, before merging the held Android commit

- [ ] Merge the held Android commit: the `<receiver>` for
      `OpsonWidgetReceiver` in
      `libraries/widgets/impl/src/androidMain/AndroidManifest.xml`,
      `res/xml/opson_widget_info.xml` and the picker strings.
- [ ] Nothing to declare in Play Console: no permission, no data leaving
      the device.

### Both

- [ ] Flip `release.widgets` on. The app writes a snapshot at launch and on
      every change from then on; until it has, a placed widget says "Open
      Opson to start."
- [ ] Try it on a phone in Full and in Blind: Full shows the amount and the
      bar, Blind the four meal circles and no number. Switch modes in the
      app and watch the widget follow. Tap + and land in the Logger on the
      next meal.
- [ ] Leave a phone overnight with the app closed: the widget shows an empty
      day after midnight.

## Server, before any 0.2 build reaches people

- [ ] Deploy the server to prod once 0.1 is through review. It carries the
      settings merge (a 0.1 phone saving settings must not erase what a 0.2
      phone added), V12 to V14, and the new routes (including
      `POST /v1/describe/photo`, which needs `ANTHROPIC_API_KEY`).

## Search quality

- [ ] After the prod deploy (V15 adds partial indexes; a few seconds of held
      writes), run the default-portions backfill on prod. Command in
      `docs/plans/search-quality.md`; it prompts for the URL, dry-runs first.
- [x] Grow prod's disk: 2 GB to 12 GB on 2026-10-26 (it was 83% used before
      any Open Food Facts data). Supabase allows one resize per 6 hours and
      never shrinks.
- [ ] Open Food Facts on prod, after 0.1 clears review and prod is deployed
      (V12 creates `off_foods`, V15 adds its search indexes):
      1. Check prod's disk is under 40% used (Supabase API
         `GET /v1/projects/qmftnivpklhhozthpxej/config/disk/util`).
      2. `scripts/load_off_watched.sh qmftnivpklhhozthpxej apps/server/.env.prod`
         (the same watched load dev got). It stops itself above 75%.
      3. Ship the app's Open Food Facts credit wherever its foods appear
         (ODbL requires it), then set `FOOD_SEARCH_OFF_ENABLED=true` on
         `opson-server-prod`.
      4. Set `OFF_IMPORT_ENABLED=true` and the `CATALOG_DATABASE_URL` secret
         so the monthly reload keeps it current.

## Search options (`release.searchOptions`)

- [ ] After the prod deploy (V16 adds `foods.category`, a catalog-only
      change plus one read of the table for its CHECK), fill the category in
      on prod. Dev got the same on 2026-10-26: 2,129 generic and 50,547
      Branded rows, 395 to 431 MB, and a second run changed nothing. This
      prompts for prod's session pooler URL, dry-runs, then writes generic
      and Branded (a 450 MB download):

      ```bash
      stty -echo; printf "Prod CATALOG_DATABASE_URL (session pooler): "; read -r V; stty echo; printf "\n"
      if [ -n "$V" ]; then
        CATALOG_DATABASE_URL="$V" python3 -I scripts/backfill_food_categories.py --dry-run &&
        CATALOG_DATABASE_URL="$V" python3 -I scripts/backfill_food_categories.py &&
        CATALOG_DATABASE_URL="$V" python3 -I scripts/backfill_food_categories.py --branded
      else echo "No URL entered, nothing run."; fi; unset V
      ```
- [ ] Flip `release.searchOptions` with or before `FOOD_SEARCH_OFF_ENABLED`:
      its source badge and the line under results are the Logger's Open Food
      Facts credit (step 3 above).

## Held native commits

The widget receiver and WidgetKit extension are on branch
`hold/0.2-widgets-native` (commits `31b4e8a`, `6771fe0`), not on main.
Cherry-pick them when 0.2 is cut, after the App ID, App Group and profile
steps above.
