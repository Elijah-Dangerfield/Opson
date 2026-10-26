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
      phone added), V12 to V14, and the new routes.
