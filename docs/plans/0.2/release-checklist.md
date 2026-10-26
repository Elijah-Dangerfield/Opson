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
