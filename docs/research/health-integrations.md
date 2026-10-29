# Health integrations: the hubs first, direct APIs later

Written 2026-10-29. Facts about third-party programs are as of this date and from memory of their public docs, not a fresh check; confirm terms, prices and approval steps with each vendor before starting one.

## Recommendation

Keep reading the two hubs and nothing else for 0.2. Apple Health on iOS and Health Connect on Android are where almost every watch, ring, scale and fitness app already writes, so reading more of them reaches Garmin, Oura, Whoop, Strava, Withings, Peloton, Polar, COROS, Samsung and (on Android) Fitbit with no accounts, no server and no partner approval. 0.2 now reads workouts, weigh-ins, height, birth date and sex from them, and names the apps the data came from.

The one gap worth a direct integration is **Fitbit on iPhone**: Fitbit does not write to Apple Health. Do that first if people ask for it, then Garmin if its hub data turns out thin. Reach for a paid aggregator only once more than two direct integrations are wanted.

## What the hubs give Opson today

| Data | Apple Health | Health Connect | Used for |
|---|---|---|---|
| Active and resting energy | yes | yes (resting derived from total) | Expended so far, Burned, the Activity card |
| Workouts, with the app that wrote them | yes (no title) | yes (title when the app sets one) | Activity card rows |
| Weight | yes | yes | Opt-in "Add weigh-ins" into the weight log |
| Height | yes | yes | Profile, "Fill in from" |
| Birth date, sex | yes | no such types | Profile, "Fill in from" |
| Source app names | yes (HKSourceQuery) | yes (data origins, labelled through package visibility) | "Data from" on Watch and health apps |

Who writes into them, roughly: Apple Watch, Garmin Connect, Oura, WHOOP, Strava, Withings, Peloton, Polar Flow, COROS, Zepp and MyFitnessPal write to Apple Health. On Android, Fitbit, Samsung Health, Google's own apps, Oura, Withings, Strava, Polar and others write to Health Connect; Garmin's Health Connect support is newer and narrower than its Apple Health export, so check it before relying on it. The Integrations page says so in a line: turn on sharing in the watch's own app.

Limits of the hub route, which are what a direct API would fix:

- **Sync delay.** Data arrives when the vendor's app syncs to the hub, often minutes, sometimes only when that app is opened. Opson re-reads on every foreground and observes HealthKit while open, which is as fast as the hub allows.
- **Coverage per vendor.** Each app chooses what it writes. Some skip workouts' energy, some write only summaries.
- **History on Android.** Health Connect returns 30 days before the first grant unless the app holds `READ_HEALTH_DATA_HISTORY`, which Opson does not ask for. Weigh-in import therefore reaches back 30 days on Android and 90 on iPhone.
- **Fitbit on iPhone** is not in Apple Health at all.

## Direct integrations

All of these move health data off the phone: OAuth tokens and the data live on Opson's server. That changes the App Store privacy label (Health and Fitness becomes collected), `legal/privacy.md` and `legal/health-data.md`, the Play data safety form, and the stance on the Integrations page ("stays on this phone"). Budget for that as much as for the code.

What every one of them needs on the server (`apps/server`): an OAuth client per vendor, a callback route, encrypted token storage with refresh, a webhook or polling worker, a mapping into Opson's own types, deletion when the person disconnects or deletes their account, and per-vendor rate limiting. The client needs a "Connect Fitbit" row that opens the browser flow (`Router.openWebLink`) and a way to show the result. Roughly two to three weeks for the first vendor including the legal and review work, then about a week for each next one on the same plumbing.

| Vendor | Access | Auth | Approval | Cost | Worth it? |
|---|---|---|---|---|---|
| **Fitbit Web API** (Google) | Activity, energy, sleep, weight, heart rate | OAuth 2.0 | Self-serve for standard data; intraday data needs an application | Free, per-user rate limits | **Yes, first, for iPhone users.** Watch for Google moving Fitbit developers to a new Google health API; build against whatever Google currently points new apps at. |
| **Garmin Connect Developer Program** (Health API, Activity API) | Dailies, activities, sleep, body composition, pushed by webhook | OAuth (Garmin has been moving partners from 1.0a to 2.0 with PKCE) | Application and approval as a business; review of the use case | Free once approved | Second, only if Garmin users report missing workouts or energy through the hubs. |
| **Oura API v2** | Sleep, readiness, activity, workouts | OAuth 2.0 | App registration; data needs the user's Oura membership | Free | No. Oura writes well to both hubs. |
| **WHOOP Developer Platform** | Cycles, recovery, sleep, workouts | OAuth 2.0 | App registration, review before wide release | Free | No, for the same reason. |
| **Strava API** | Activities | OAuth 2.0 | New apps are limited to one athlete until Strava reviews them; the 2024 terms restrict showing a user's data to others and using it for AI | Free, tight rate limits | No. Strava writes activities to both hubs, and the terms are the most restrictive here. |
| **Withings Public API** | Weight, body composition, blood pressure, sleep | OAuth 2.0 | Self-serve developer account | Free | No. Withings writes weight to both hubs, which is what Opson uses. |
| **Google Fit APIs** | | | Deprecated in 2024, closed to new developers, being shut down | | **Never.** Health Connect is its replacement on Android. |
| **Aggregators** (Terra, Junction (formerly Vital), Spike, Thryve, ROOK) | Dozens of vendors behind one API and one webhook format | Their SDK or hosted OAuth | Their onboarding; they hold the vendor agreements | Paid per connected user, usually with a monthly minimum; get quotes | Only past two direct integrations, or if Opson ever wants many devices without owning the plumbing. Adds a processor to the privacy policy. |

## Order

1. **Done in 0.2:** read workouts, weight, height, birth date, sex and source names from the hubs; refresh on foreground and on HealthKit's observer; pull to refresh on Today.
2. **Next, still on the phone:** nothing new until a screen needs it. Steps, sleep or heart rate each add a permission App Review and Play will ask about (minimum necessary data), so read them only with a feature that shows them.
3. **First direct integration:** Fitbit, for iPhone users, behind its own release flag, after the privacy policy, App Privacy label and data safety changes are drafted.
4. **Then Garmin,** if the hub data is not enough.
5. **Aggregator** only if the list grows past that.

Before 3, find out whether anyone is missing: the support inbox and reviews asking for Fitbit or Garmin are the signal. "Data from" shows people which apps already reach Opson, which should answer most "does it work with my watch" questions without any of this.
