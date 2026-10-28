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
- [x] The privacy paragraph is in `legal/privacy.md` ("Features that may not
      be in your version yet"), checked against the code on 2026-10-27. Health
      data stays on the device, so the App Store label doesn't change.
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
      linked to the user) need declaring. The wording is in
      `legal/privacy.md` already, including Nutritionix and ML Kit.
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
- [x] The photo paragraph is in `legal/privacy.md` and Anthropic is in its
      processor table and in `legal/health-data.md`. If the org's retention
      setting turns out longer than "short", say so there.
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

## Add this food (`release.submitFood`)

- [ ] Deploy a server with V25 (`food_submissions`) to dev, then prod.
      Flyway runs it on boot; it is additive and 0.1 is unaffected.
- [ ] `ANTHROPIC_API_KEY` is set (as for Photo estimate above). The label
      model is `claude-fable-5-1`, which needs the organisation's API data
      retention at 30 days (not zero data retention). If it refuses, the
      server retries on `ANTHROPIC_PHOTO_MODEL` and logs
      `label parse model rejected the request`; to pick another model
      without a release:

      ```bash
      printf "Label model [claude-fable-5-1]: "; read -r LM; LM="${LM:-claude-fable-5-1}"
      fly secrets set ANTHROPIC_LABEL_MODEL="$LM" -a opson-server-dev; unset LM
      ```

      With no key at all the form still works, typed in from the photos.
- [ ] `ADMIN_API_TOKEN` is set on the environment, or the review routes are
      not mounted and nothing gets approved. Review in the admin console's
      Review tab.
- [ ] Push `ops/grafana/food-review.json` after prod runs V25.
- [x] `legal/privacy.md` and `legal/health-data.md` describe the pack
      photos (seen by the submitter and the reviewer, deleted 30 days after
      review and with the account) and that approved food data, not the
      photos, is shared with everyone. They publish when they reach `main`.
- [ ] App Store Connect, App Privacy: **Photos or Videos**, App Functionality,
      **linked** to the user (the submission belongs to the account), not
      for tracking. Play Data safety: Photos, collected, linked, not shared.
      The meal photo estimate's "not linked, not kept" row stays as it is.
- [ ] Try it on an iPhone and an Android phone: a scan miss, both photos,
      a retake, the library, a denied camera, a label with a wrong figure
      (the check and its fix), No numbers mode, a search with no results,
      airplane mode at Add (logged, "Added to your foods", then Try again),
      then approve it in the admin console and scan it from another account.
- [ ] Flip `release.submitFood` on.

## Voice log (`release.voice`)

No entitlement or capability: Speech and the microphone need only their
usage strings, which are already in `Info.plist` (the binary references
them as soon as `:libraries:speech:impl` links, like the camera).

- [ ] App Privacy: audio is not collected by Opson (it goes to the platform
      recognizer and is not stored), so the label should not change. Confirm.
      `legal/privacy.md` already says Apple or the phone's speech service
      processes the audio when it can't be recognised on the device.
- [ ] Play Console: `RECORD_AUDIO` is declared from the build that links the
      speech library. Check the Data safety form ("Audio" is not collected or
      shared, processed ephemerally on the device or by the system service).
- [ ] Try it on an iPhone and an Android phone: a spoken meal, a refused
      mic, airplane mode, and a phone with no on-device model.

## Find support (`release.findSupport`)

Design sync 2's helpline page, Support's crisis row and the pause sheet's
"Talk to someone". The list ships in the app (`SupportDirectory`). Nothing
is held back: the only platform change is `LSApplicationQueriesSchemes`
(`tel`, `sms`) in the iOS Info.plist, which is not a capability.

- [x] Every number, text keyword and source URL in `SupportDirectory` was
      checked on the organisations' own sites on 2026-10-27, and the
      `TODO(launch)` notes are gone. EDANZ's old domain redirected to an
      unrelated site and Ireland's 50808 was renamed; both sources are fixed.
- [ ] Recheck the list each quarter (next: January 2027).
- [x] The US "Find a treatment provider" line opens findEDhelp, the
      Alliance's directory, and the 0.1 helpline link opens its Find
      treatment page.
- [ ] Onboarding's statement and the big-day card link to `FindSupportRoute`
      only with the flag on.
- [ ] Try Call and Text on an iPhone and an Android phone, and the
      "couldn't open" line on an iPad or a phone with no SIM.
- [ ] Flip `release.findSupport` on.

## Widgets (`release.widgets`)

The iOS extension (`OpsonWidgetsExtension`, `com.opson.Opson.Widgets`) ships in
every build, so Opson is in the widget gallery whatever the flag says. The app writes the snapshot whatever the flag says
too (`WidgetRefresher` no longer reads it); `release.widgets` only decides the
More row and the Widgets page. The Android receiver is still held on
`hold/0.2-widgets-native`, because it puts Opson in the launcher's picker.

### Apple (done 2026-10-27)

- [x] App ID `com.opson.Opson.Widgets` ("Opson Widgets"), registered with
      `POST /v1/bundleIds`; **App Groups** enabled on it and on
      `com.opson.Opson` with `POST /v1/bundleIdCapabilities`.
- [x] App Group `group.com.opson.Opson` registered and ticked on both App IDs.
      The ASC API can't do this part. A Release archive with
      `-allowProvisioningUpdates` and the Apple account signed in to Xcode did
      it (the API key alone fails Xcode's auth with "bearer token", see below).
- [x] App Store profiles regenerated by `get_provisioning_profile(readonly:
      false)`: `com.opson.Opson AppStore 1793132462` and
      `com.opson.Opson.Widgets AppStore`. The two older `com.opson.Opson`
      AppStore profiles are INVALID now and can be deleted in the portal.
- [x] A Release archive signed, and an App Store export (no upload) signed both
      bundles with Apple Distribution, each carrying the App Group.
- [ ] Check one TestFlight build: the widget is in the gallery, the app and
      the extension report the same version and build, and after logging a
      meal the widget changes within a few seconds.
- [ ] App Privacy: nothing new leaves the device. The snapshot sits in the
      App Group container on the phone.

Xcode's automatic signing through the ASC API key answers "Authentication
failed: Make sure a bearer token was provided" on this machine (Xcode 27.1),
for the app target too, so the beta lane's archive has been signing with the
profiles Xcode already had. Locally that is fine now the profiles carry the
group. A CI runner has no such profiles, so check `beta.yml` before relying on
it.

### Android, before merging the held Android commit

- [ ] Merge the held Android commit: the `<receiver>` for
      `OpsonWidgetReceiver` in
      `libraries/widgets/impl/src/androidMain/AndroidManifest.xml`,
      `res/xml/opson_widget_info.xml` and the picker strings.
- [ ] Nothing to declare in Play Console: no permission, no data leaving
      the device.

### Both

- [ ] Flip `release.widgets` on. That only shows the More row and the Widgets
      page now; the snapshot is written regardless.
- [ ] Try it on a phone in Full and in Blind: Full shows the amount and the
      bar, Blind the four meal circles and no number. Switch modes in the
      app and watch the widget follow. Tap + and land in the Logger on the
      next meal.
- [ ] Leave a phone overnight with the app closed: the widget shows an empty
      day after midnight.

## Progress photos (`release.progressPhotos`)

No entitlement or capability: LocalAuthentication and BiometricPrompt need
none. Already on `main`: `NSFaceIDUsageDescription`, the reworded
`NSCameraUsageDescription`, and `USE_BIOMETRIC` (a normal permission).

- [x] The progress photos paragraph is in `legal/privacy.md`. Nothing is
      collected, so the App Store label doesn't change.
- [x] Settings: the Show / Hide switch for photos in Modes and numbers
      (Blind, Mindful, Rhythm and Fuel default to Hide), behind the pause and
      `release.progressPhotos`.
- [ ] Still to build: the optional reminder (off by default, at most every
      2 weeks); `ReminderKey` has no slot for it yet.
- [ ] Try it on an iPhone with Face ID, one with a passcode only, and an
      Android phone on 9 and on 13: the prompt each time the grid opens, again
      after leaving the app, and the "no lock" panel with the passcode off.
- [ ] Check an iCloud backup and an Android backup leave the photos out.

## Server, before any 0.2 build reaches people

- [ ] Deploy the server to prod once 0.1 is through review. It carries the
      settings merge (a 0.1 phone saving settings must not erase what a 0.2
      phone added), V12 to V14, and the new routes (including
      `POST /v1/describe/photo`, which needs `ANTHROPIC_API_KEY`).

## Today and Day close, design sync 2 (`release.todaySummary`, `release.todaySelect`, `release.checkIn`)

- [ ] Deploy V18 (`day_records.check_in`) before flipping `release.checkIn`.
      A server without it drops the check-in silently (the row still syncs).
- [ ] TODO(clinician): approve "Why not cut back?" (`today_dayclose_why_body`)
      and pick its citation; no study is named until then.
- [ ] Patterns reading the check-in, and the Reminders switch that turns the
      check-in card off, are Trends' and Settings' to build before the flag flips.
- [ ] Try swipe to delete with TalkBack and VoiceOver (the row's Delete action)
      and Undo on a phone.

## Goals and energy (`release.goalsAndEnergy`)

- [ ] TODO(dietitian): confirm the macro presets (`MacroPreset`: Balanced
      20/50/30, Higher protein 30/40/30, Lower carb 30/25/45) and the everyday
      activity factors Burned uses with no health app (`EverydayActivity`, the
      standard Mifflin–St Jeor ones). An engineer picked both from other
      trackers (`docs/research/goals-ux.md`).
- [ ] TODO(dietitian): macros can be set to 0 g or 0%. Decide whether any
      macro needs a minimum before launch.
- [ ] TODO(clinician): set `MAX_SURPLUS_KCAL` (null today, so a surplus can't
      grow past where it started, as the deficit can't while
      `MAX_DEFICIT_KCAL` is null; the owner's call, 2026-10-27).
- [ ] TODO(clinician): confirm `ONE_NUMBER_LEEWAY_KCAL` (100 kcal either side
      of one number before Close the day calls it above) and
      `UsualMealSize`'s split of the aim across meals for someone with no
      history. An engineer picked both.
- [ ] Privacy: Burned's calculation adds birth year, height and sex to synced
      settings (`birthYear`, `heightCm`, `bodySex`, new top-level keys). Check
      the App Privacy label and `legal/privacy.md` before the flag flips.
- [ ] Nothing on the server: every new setting is a top-level key, which the
      shallow merge keeps through a 0.1 save (`SettingsMergeAcrossVersionsTest`).
- [ ] TODO(clinician): the summary card's Deficit main number and Equation
      preset put eaten minus Burned front and centre, which `balanceRingValue`'s
      note held back until review. Confirm before the flag flips.
- [ ] Look at the goal style list, Resting energy and Energy today on a phone,
      and one number and a surplus on Today, Close the day, Trends and the
      widget, before flipping the flag.

## Exercise (`release.exercise`, needs `release.goalsAndEnergy`)

- [ ] Deploy V24 (`exercises`) before flipping the flag. A server without it
      refuses every exercise as `unknown_entity` and the phone rolls it back.
- [ ] TODO(clinician): confirm exercise is never offered to someone who said
      numbers have been hard. Everyday activity (Mostly sitting, resting ×
      0.2, by default) is under goals and energy now, not this flag. Rule and
      sources in `docs/research/energy-expenditure.md`.
- [ ] A phone that synced on a build without exercise has its cursor past
      exercises logged meanwhile on another phone, and does not pull those
      after updating. Flip the flag only once both phones are likely on the new
      build, or accept that gap.
- [ ] Log, edit and delete an entry on two phones, with and without a health
      app connected, and check Burned on Today and Energy today.

## Today actions, design sync 4 (`release.todaySelect`, `release.foodActions`)

- [ ] Drag foods between meals on a phone, with the list scrolling under the
      finger near both edges, on iOS and Android. Previews hold one still frame.
- [ ] Move picked foods with TalkBack and VoiceOver: the row's "Move to another
      meal" action and Move on the bottom bar.
- [ ] Edit a copy end to end on two devices: the copy shows in Your foods and
      search on the second, and the entry points at it.

## Search quality

- [ ] After the prod deploy (V15 adds partial indexes; a few seconds of held
      writes), run the default-portions backfill on prod. Command in
      `docs/plans/search-quality.md`; it prompts for the URL, dry-runs first.
- [x] Grow prod's disk: 2 GB to 12 GB on 2026-10-26 (it was 83% used before
      any Open Food Facts data). Supabase allows one resize per 6 hours and
      never shrinks.
- [x] **Loaded 2026-10-27:** prod's `off_foods` holds 3,290,890 products (2.3 GB
      with indexes; disk peaked at 34%). The session dropped after the load and
      `--swap-only` finished it, as designed. Search stays off on prod
      (`FOOD_SEARCH_OFF_ENABLED` unset) until a build with the credit ships, step 3.
- [ ] Open Food Facts on prod, after 0.1 clears review and prod is deployed
      (V12 creates `off_foods`, V15 adds its search indexes):
      1. Check prod's disk is under 40% used (Supabase API
         `GET /v1/projects/qmftnivpklhhozthpxej/config/disk/util`).
      2. `scripts/load_off_watched.sh qmftnivpklhhozthpxej apps/server/.env.prod`
         (the same watched load dev got). It stops itself above 75%.
         On dev (2026-10-26) Postgres restarted partway through the index
         build, which rolled back every index and left psql hanging on the
         pooler. Check the importer builds indexes one per statement before
         running this on prod (the scripts review is fixing it). Dev now has
         3,290,890 products and `FOOD_SEARCH_OFF_ENABLED=true` in `fly.toml`.
      3. Ship the app's Open Food Facts credit wherever its foods appear
         (ODbL requires it), then set `FOOD_SEARCH_OFF_ENABLED=true` on
         `opson-server-prod`. The Foods tab, the Logger, the website footer
         and `legal/credits.md` (linked from Support) carry it already.
      4. Set `OFF_IMPORT_ENABLED=true` and the `CATALOG_DATABASE_URL` secret
         so the monthly reload keeps it current.

## Search options (`release.searchOptions`)

- [ ] After the prod deploy (V17 adds `foods.category`, a catalog-only
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

## Adding (design sync 4)

- [ ] Deploy the server with V21 (`log_entries` takes precision `label`)
      before a build with the new food detail reaches anyone. Until then the
      old server rejects a "From label" item, so it does not sync.

## NEVO, the Dutch food table

RIVM's terms and the mapping are in `docs/food-data-sources.md` ("NEVO
(Netherlands): imported"). Search only gives NEVO foods to an app that cites
them, so loading prod before 0.2 ships changes nothing 0.1 sees.

- [ ] **You:** download NEVO-online 2025/9.0 from RIVM's
      [request form](https://www.rivm.nl/en/dutch-food-composition-database/use-of-nevo-online/request-dataset)
      (it asks you to accept the conditions of use).
- [ ] Load it into dev, then prod, once each has V20 (deployed with this
      change). This prompts for the file and the database URL, counts first,
      then loads; run it once with dev's session pooler URL and once with
      prod's. A second run changes nothing.

      ```bash
      printf "Full path to the NEVO CSV or zip: "; read -r F
      stty -echo; printf "CATALOG_DATABASE_URL (session pooler): "; read -r V; stty echo; printf "\n"
      if [ -n "$V" ] && [ -f "$F" ]; then
        python3 -I scripts/import_nevo_foods.py --file "$F" &&
        CATALOG_DATABASE_URL="$V" python3 -I scripts/import_nevo_foods.py --file "$F" --psql
      else echo "No URL, or no file at that path. Nothing run."; fi; unset V F
      ```

## Held native commits

The widget receiver and WidgetKit extension are on branch
`hold/0.2-widgets-native` (commits `31b4e8a`, `6771fe0`), not on main.
Cherry-pick them when 0.2 is cut, after the App ID, App Group and profile
steps above.

## Website go-live (on your own domain, through Cloudflare Pages)

Decided 2026-10-27: the site moves off GitHub Pages so the repo can go private.
It goes to Cloudflare Pages, uploaded from the existing workflow with
`wrangler pages deploy`, on the domain you own at Porkbun.

Live 2026-10-27 at https://www.opsontracker.com. DNS stays at Porkbun: `www`
is a CNAME to `opson.pages.dev`, and the bare domain URL-forwards (302, path
kept) to `https://www.opsontracker.com`. Every push to main that touches
`website/` or `legal/` redeploys.

- [x] Prod has the waitlist route and Open Food Facts loaded (3.7 million
      foods across USDA and OFF).
- [x] Pages project `opson` with `www.opsontracker.com` attached (active).
- [x] Repo secrets `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`; variables
      `SITE_URL=https://www.opsontracker.com`, `SITE_BASE=/`,
      `SITE_ENABLED=true`.
- [x] `WAITLIST_ALLOWED_ORIGINS` on `opson-server-prod` lists both hosts. A
      preflight from the site's origin gets 200; any other origin gets 403.
- [ ] Join the waitlist once from the live site with your own address and
      check it shows in admin.
- [ ] Prod search shows OFF foods only once a 0.2 build with the credit ships
      (`FOOD_SEARCH_OFF_ENABLED`). The site's food count is about the catalog,
      so it holds either way.
- [ ] Before the repo goes private: the design-sync manifest is published to
      a public `raw.githubusercontent.com` URL that Claude Design reads. After
      that, hand Claude Design `design-sync/app.zip` instead.

## Legal and health safety (docs/legal-review-2026-10.md)

The review on 2026-10-27 fixed what it could in the repo. These need you, a
lawyer, or a change outside this review's lane. None of it is legal advice.

### Before merging the legal changes to main (merging publishes them)

- [x] Governing law is New York, and the contact carries the postal address
      418 Broadway, Albany, NY 12207 (2026-10-27).
- [ ] **Lawyer:** read `legal/privacy.md`, `legal/terms.md` and
      `legal/health-data.md` once before they go live. The items marked
      "lawyer" in the review are the ones that matter.
- [ ] **You:** on the Nightjar homepage (the `nightjar` repo), link
      `/opson/health-data` from the footer. Washington wants the link on the
      regulated entity's homepage; the Opson site's footer has it already.

### Crisis lines

- [ ] Flip `release.findSupport` once Call and Text are tried on a phone (see
      Find support above). With it off, Support has the Alliance's helpline
      and findEDhelp but no 24/7 crisis line.

### Code, small, outside this review's lane

- [x] Telemetry: strip query strings from Sentry breadcrumbs and event
      messages. Done in `SentryUrlScrubbing.kt` (`:libraries:opson:impl`):
      breadcrumbs, messages, tags, extras, exception messages and the feedback
      log lose every URL query and fragment, and the iOS failed-request event
      is off. The sentence is gone from `legal/privacy.md` and
      `legal/health-data.md`. 0.1 still sends the URL, so the App Store label
      keeps Search history as linked until 0.1 is off the store.
- [x] The phone's copy of the log is out of device backups. On iOS the
      database and every settings file live in `Application Support/no_backup`,
      a folder flagged `NSURLIsExcludedFromBackupKey` on every open, and older
      installs move across from `Documents` on their first launch
      (`NoBackupDirectory.kt`). Android has `allowBackup="false"` and data
      extraction rules that also stop phone-to-phone transfer. The privacy
      policy says so.
- [x] Account deletion revokes the Sign in with Apple token. The app sends
      Apple's authorization code after an Apple sign-in, the server keeps the
      refresh token (V23), and `DELETE /v1/me` revokes it once the account is
      gone. A failed or skipped revocation never blocks deletion.
- [ ] **You:** give the server a Sign in with Apple key, or revocation is
      skipped with a warning in the logs. In the Apple developer site,
      Certificates, Identifiers & Profiles › Keys, create a key with Sign in
      with Apple enabled for `com.opson.Opson` and download the `.p8`. Then
      set the three secrets on each server app (one deploy each):

      ```bash
      printf "Fly app (opson-server-dev or opson-server-prod): "; read -r APP
      printf "Apple team id: "; read -r TEAM
      printf "Key id: "; read -r KID
      printf "Full path to the .p8 file: "; read -r P8
      if [ -n "$APP" ] && [ -n "$TEAM" ] && [ -n "$KID" ] && [ -f "$P8" ]; then
        fly secrets set -a "$APP" APPLE_TEAM_ID="$TEAM" APPLE_KEY_ID="$KID" APPLE_PRIVATE_KEY="$(cat "$P8")"
      else echo "Something was empty, or no file at that path. Nothing set."; fi; unset APP TEAM KID P8
      ```

      People who signed in with Apple before this build have no stored token
      until they next sign in with Apple, so their deletion can't revoke.
- [x] The Lust Script font is gone; the wordmark uses Atkinson Hyperlegible
      (`260fb927`).
- [x] `apps/ios/iosApp/PrivacyInfo.xcprivacy` declares no tracking, the
      required-reason APIs (UserDefaults, file timestamps, system boot time,
      disk space, with the callers in its comment) and the App Privacy data
      types. The `iosApp` folder is synchronized, so it needs no
      `project.pbxproj` entry.
- [x] Support › About Opson › Credits and licenses is a screen in the app,
      generated from the build (AboutLibraries), with every license's full
      text. After changing dependencies, run
      `./gradlew :apps:compose:exportLibraryDefinitions` and commit
      `libraries/resources/.../files/licenses.json`.
- [ ] Export: include notes, feelings, settings, custom foods, saved meals
      and fasts in the CSV, or keep the policy's "email us for the rest".

### Accounts and settings at providers

- [x] Retention checked 2026-10-27, all inside the policy's limits, nothing to
      set: Sentry's Developer plan keeps events 30 days (fixed by the plan);
      Grafana Cloud Pro keeps logs and traces 30 days and metrics 13 months
      (counts with no account id); Supabase keeps daily backups 7 days. If
      Sentry moves to a paid plan its retention becomes 90 days, still inside.
- [ ] Confirm a data processing agreement is in place with Fly.io,
      Supabase, Sentry, Grafana Labs and Anthropic (most are part of their
      standard terms; accept or download each).
- [ ] App Store Connect, App Privacy: add Customer support and Search
      history, per `docs/app-store/listing.md`.

### Lawyer

- [ ] GDPR and UK GDPR: whether Opson needs an explicit consent step for
      health data before EU or UK users can use it, and an EU and UK
      representative. Until then, consider limiting the App Store
      availability to the US.
- [ ] Washington, Nevada and Connecticut: whether the onboarding flow needs a
      separate consent for collecting consumer health data, or whether
      "necessary to provide the service" covers everything Opson collects.
      `docs/plans/onboarding-v2.md` ("Data and consent") has where a consent
      step would go.
- [ ] Terms: the US$50 liability cap, and whether to add an arbitration or
      indemnity clause. Left out on purpose; they need a lawyer's call.
- [ ] The Open Food Facts share-alike question (already tracked in
      `docs/food-data-sources.md`).

### When NEVO lands

- [x] Add "Based on data from NEVO online version 2025/9.0, RIVM, Bilthoven"
      to `legal/credits.md` and the in-app food credit, wherever NEVO foods
      appear. NEVO data must stay free to users, including after Plus, and
      its values may not be changed.
- [ ] When 0.2 is the store build (0.1 no longer sends search URLs to Sentry): re-apply `5a1cd02a` (`git revert` the revert), which drops the "failed search" sentence from `legal/privacy.md` and `legal/health-data.md` and the Search history label.
- [ ] When 0.2 is the store build (0.1 no longer sends search URLs to Sentry): re-apply `5a1cd02a`, which drops the "failed search" sentence from `legal/privacy.md` and `legal/health-data.md` and the Search history label.
