# Pricing, Pro, and founding members

## Recommendation

- **Free is a complete tracker.** Logging, search, barcode, package scanning,
  all four Modes, targets and energy, trends, fasting, reminders, health sync,
  water, weight, check-ins, GLP-1 logging, export and the one-off provider
  summary. No ads, ever.
- **Pro is the things that cost Opson money per use, and the things that
  connect you to other people:** AI meal photo estimates, live sharing with a
  professional, sharing with friends, and the analysis features that come later
  (adaptive burn, long-range reports).
- **Price:** $6.99 a month or $39.99 a year in the US. Below MyFitnessPal, Lose
  It and MacroFactor, near Cronometer, and simple.
- **Founding members:** the first **1,000** accounts that become active users
  (food logged on 5 different days within their first 30) get Pro for life.
  Granted on the server against the account, not through a store purchase,
  with a fair-use cap on AI calls. Communicated from day one on the website,
  in onboarding and on More's Plan row.

The reasoning, numbers and mechanics are below.

## What competitors charge and gate

US list prices, autumn 2026. Most figures come from comparison blogs run by
competing apps, which agree with each other; check the official page before
quoting any of them publicly.

| App | Price | Free tier | Notable paywalls |
|---|---|---|---|
| [Cronometer](https://calorix.ai/compare/cronometer-price/) | Gold $10.99/mo or $59.99/yr; Pro (practitioners) about $35/mo | Generous: barcode, 84 nutrients, device sync, with ads, 7-day reports | Photo and voice logging, fasting, recipe import, long reports, ad-free. Practitioners pay for client access. |
| [MyFitnessPal](https://www.fitbudd.com/post/myfitnesspal-app-cost) | Premium $19.99/mo or $79.99/yr; Premium+ $99.99/yr | Manual search only, ads | Barcode scanner (since 2022), custom macros in grams, voice, photo scan, fasting, export. GLP-1 Support is free. |
| [MacroFactor](https://calorix.ai/compare/macrofactor-price/) | $11.99/mo, $71.99/yr | None, 7-day trial | Everything |
| [Lose It!](https://nutriscan.app/blog/posts/cheapest-nutrition-app-2026-pricing-compared-4c18a9205d) | Premium about $79.99/yr (raised from $39.99), lifetime about $299.99 | Basic logging | Barcode, macros, GLP-1 Logging, custom goals |
| [Yazio](https://nutriscan.app/blog/posts/yazio-pricing-2026-free-vs-pro-what-pro-unlocks-33b26f8fc7) | Pro $47.90/yr, often $23.90 on promotion | Basic logging | Fasting plans, recipes, analysis |
| [Carbon Diet Coach](https://nutriscan.app/blog/posts/carbon-diet-coach-pricing-2026-plans-7a3d15e78c) | $11.99/mo, $99.99/yr | None, 14-day trial | Everything |
| [Noom](https://bettercare.com/costs/noom-cost) | About $70/mo or $209/yr; Noom Med $99 to $279/mo | GLP-1 Companion free since June 2026 | Coaching program |

What stands out:

- **Barcode behind a paywall is the most resented move in the category.**
  MyFitnessPal's 2022 change still drives "alternatives" articles. Opson keeps
  it free, and package scanning free, which is also how OPDB grows.
- **Fasting is a common paywall** (Cronometer, MyFitnessPal, Yazio). Keeping it
  free is a small, visible point of difference.
- **GLP-1 features are moving to free** (MyFitnessPal, Noom). Opson's GLP-1 log
  should be free too.
- **Practitioner access is a paid product** at Cronometer, and it is the
  clearest precedent for "share with a professional" as Pro.

## Free and Pro

| Feature | Free | Pro | Why |
|---|---|---|---|
| Logging, search, quick add, Describe, voice | Yes | | The core. Voice uses the phone's own speech recognition, so it costs nothing to run. |
| Barcode scan | Yes | | Category expectation; paywalling it is the most hated move. |
| Package scanning (label photos) | Yes | | It builds OPDB. Each read costs 10 to 15 cents today; `package-scanning.md` brings that down. |
| Modes, targets, goals, energy | Yes | | Safety and personalisation are not upsells. |
| Trends, nutrients, patterns | Yes, all ranges available today | Future long-range reports | Don't take away what exists. |
| Fasting | Yes | | Owner's call. |
| Reminders, widgets, health sync | Yes | | |
| GLP-1 log | Yes | | Medication safety record; MyFitnessPal and Noom give it away. |
| Export and provider summary PDF | Yes | | People's own data. Apple also expects data export to be easy. |
| **AI meal photo estimate** | A few to try (for example 3 a week) | Yes, within fair use | Each estimate is a paid model call (`claude-opus-5-5`, roughly 4 to 5 cents). |
| **Share with a professional** | | Yes | Live, ongoing access for a dietitian or coach; see `ideas-backlog.md`. The one-off PDF stays free. |
| **Share with friends** | | Yes | Social is a cost and moderation burden; also a strong reason to subscribe. |
| Adaptive burn from intake and weight (future) | | Yes | Real analysis value, no per-use cost; product ideas #21. |
| Recipes with cooked weight, recipe import (future) | Recipes | Import from a URL | Import is an AI call. |
| Coach notifications and comments (future) | | Pro for the client; the professional's own account free | See backlog. |

**Who pays for professional sharing?** Make it the client's Pro, with the
professional's side free. Cronometer charges practitioners instead, which works
when practitioners bring clients. Opson has no practitioners yet; clients
asking their dietitian to "see my Opson" is the growth path. Revisit a
practitioner plan once there are some.

## Price points

| Plan | US price | Note |
|---|---|---|
| Monthly | $6.99 | Under Cronometer's $10.99 and MacroFactor's $11.99 |
| Yearly | $39.99 | About $3.33 a month; under Cronometer's $59.99 and MyFitnessPal's $79.99 |
| Lifetime (later, optional) | $129.99 | Lose It sells one at about $299.99. Only offer it once AI costs per user are known. |
| Free trial | 7 days of Pro, once | Store-managed introductory offer |

Use the stores' regional price tiers for other countries. Revisit after six
months of conversion data.

**Unit economics check.** A Pro user who takes three AI photo estimates a day
costs about $45 a year at 4 to 5 cents each, which exceeds the yearly price.
So Pro needs a fair-use limit. The server already caps estimates at 40 a day
(`PhotoEstimateLimiter`); add a monthly cap (for example 150) and say so on the
paywall ("about 5 a day"). Model costs keep falling, and the cascade in
`package-scanning.md` applies to meal estimates too.

## Founding members: lifetime Pro for the first X

### What X should be

**1,000.** Reasoning:

- Big enough to feel real on the website and to seed reviews, feedback and
  OPDB submissions; small enough that the worst-case AI bill is bounded. At
  a cap of 150 estimates a month, one user costs at most about $80 a year, so
  1,000 founders all at the cap would be about $80k. Real usage will be a small
  fraction of that, since most people don't log every meal by photo, and the
  cost per estimate keeps falling. Watch it monthly in Grafana.
- A round number people remember.
- Google Play's one-time product promo codes are limited to 500 a quarter
  ([Play Console help](https://support.google.com/googleplay/android-developer/answer/6321495?hl=en)),
  which rules out store codes as the main mechanism at this size anyway.

### Who qualifies

The first 1,000 accounts, by sign-up time, that **log food on 5 different days
within 30 days of signing up**. This filters out throwaway accounts and rewards
the people who actually use the app. Waitlist sign-ups (the website's
`POST /v1/waitlist`, V16) can get a head start: a waitlist email that signs up
within the first two weeks of launch counts from its waitlist date.

### How it is granted

**A server-side entitlement on the account is the source of truth.** Opson
requires accounts, so the grant follows the person across phones and
platforms, and survives a reinstall.

```
entitlements (new table, server)
  user_id       UUID  FK auth.users
  kind          TEXT  'founding' | 'pro_store' | 'pro_promo'
  source        TEXT  'founding_v1' | 'app_store' | 'play' | 'revenuecat' | 'manual'
  granted_at    TIMESTAMPTZ
  expires_at    TIMESTAMPTZ NULL     -- null for lifetime
  revoked_at    TIMESTAMPTZ NULL
  note          TEXT NULL
```

- `GET /v1/me` returns `pro: { active, kind, expiresAt }`. The client caches it
  like other account state and wipes it on a user change.
- A nightly job counts qualifying accounts in sign-up order and grants
  `founding` until 1,000 are granted. A counter in remote config
  (`pro.foundingRemaining`) feeds the website and the app copy.
- Store purchases (below) write `pro_store` rows from verified receipts or
  webhooks, so the app asks one question everywhere: does this account have
  an active entitlement?

**Store purchases for everyone else.** `features/upgrade` today is the app
guard (maintenance screen, version floor, update offer), and there is no
billing code anywhere in the repo; More's Plan row says "Free" and opens
nothing. Two ways to add billing:

| Option | For | Against |
|---|---|---|
| **RevenueCat** (it has a Kotlin Multiplatform SDK) | One SDK for StoreKit 2 and Play Billing, receipt validation, webhooks, promotional entitlements by API ([RevenueCat API v1](https://www.revenuecat.com/docs/api-v1/entitlements)), paywall experiments | A vendor and a fee above a revenue threshold; another place user ids live |
| **StoreKit 2 and Play Billing directly**, verified by the Ktor server | No vendor; Opson's server already owns accounts | Two billing integrations, App Store Server Notifications and Play RTDN to build and test |

Recommend **RevenueCat**, with the app user id set to the Supabase user id and
its webhooks writing `pro_store` rows. A solo developer should not hand-build
two billing stacks. The founding grant stays Opson's own row, not a RevenueCat
promotional, so it doesn't depend on the vendor.

**Apple's rules.** Guideline 3.1.1 says features must be unlocked with in-app
purchase and bans "own mechanisms" such as license keys
([App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)).
A founding grant is not something a user buys or types in; it is a
grandfathered benefit tied to an early account, which is closer to what 3.1.1
expects for existing users. Still, the risk is a reviewer's reading. Mitigate:

1. Keep every Pro feature also purchasable in-app on iOS, so the grant never
   replaces IAP.
2. Never show a code field outside the App Store's own offer code redemption.
3. Explain the founding program in the review notes for the version that
   launches Pro.
4. If a reviewer objects, the fallback is Apple's **offer codes**, which since
   late 2025 work for non-consumables and can be 100% off, up to a million a
   quarter ([Apple Developer news](https://developer.apple.com/news/?id=gf6mgrs6)).
   Create a $0-after-offer "Founding lifetime" non-consumable and email each
   founding member a one-time code. Android keeps the server grant.

### Abuse prevention

| Risk | Defence |
|---|---|
| One person, many accounts (Sign in with Apple's private relay makes this easy) | Count one grant per device: App Attest or DeviceCheck on iOS, Play Integrity on Android, sent at sign-up. A device that already holds a founding grant can't earn another. |
| Bots creating accounts | The 5-days-in-30 activity rule; rate limits already on sign-up paths; the waitlist honeypot. |
| Reselling accounts | Founding is tied to the account, which holds a person's food log, so resale is unattractive. The terms say it's personal and non-transferable. |
| AI cost blow-up | Fair-use caps apply to founding members exactly as to paying Pro. |
| Deleting the account and re-qualifying | The device check covers it; deleted accounts keep a hashed device id in a tombstone for this purpose only. Check with the privacy policy. |
| Refund on a store purchase made before the grant | A founding member who already paid gets an automatic note on how to cancel their subscription; Opson can't refund App Store purchases itself. |

### How it's communicated

- **Website:** "The first 1,000 people who use Opson get Pro free, for life.
  612 places left." The number comes from `pro.foundingRemaining`.
- **Onboarding (last step):** one line, not a screen. "You're early. Use Opson
  on 5 days this month and Pro is yours for life, while places last."
- **More › Plan row:** "Free", "Pro", or "Founding member". The Plan page
  explains what Pro includes and, for founders, "Thanks for being early. Pro is
  yours for as long as Opson exists."
- **When granted:** a quiet in-app note, no confetti, no push notification by
  default.
- **Fine print:** what "for life" means (the life of the product, with notice
  if Opson ever shuts down), that fair-use limits apply, and that it is not
  transferable.

## Open questions

1. Should AI meal estimates have a small free allowance, or be Pro only? A
   small allowance shows what it does.
2. Is $39.99 a year right, or start lower as "founding pricing" for the first
   year?
3. Does the 5-days-in-30 rule feel fair, or too much like a streak? It is a
   count, not a run, and is never shown as a counter.
4. Lifetime purchase: offer it at launch, or wait for cost data?
5. Professional sharing: client pays (recommended) or practitioner pays?
