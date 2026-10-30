# Recipes you can share (v1, behind `release.recipes`)

Owner, 2026-10-30: "If I'm showing a recipe online and want others to track it
easily, I could give them a QR code to scan to find it in the app." A recipe
can be made public; others find it by link, QR code or search; it is marked as
someone's own and not checked by Opson.

## What was there

| What | Where | Notes |
|---|---|---|
| Create a meal, saved meals | `features/foods/impl/CreateMeal*`, `libraries/diary` `SavedMeal`, `saved_meals` (V8) | A name, a usual meal and items (`LogDraft` snapshots). Logged whole: every item becomes its own entry at its own amount. No yield, no steps. |
| Custom foods | `CustomFood`, `custom_foods` (V8, V26) | One food with a serving and its numbers. |
| Diary sync | `DiarySyncer`, `POST /v1/sync`, `PostgresSyncRepository` | Outbox up, cursor pull down. Entities added after 0.1 (`fast`, `exercise`) come back only to a request whose `accepts` names them, and a client re-pulls from 0 when its `accepts` grows. A server that does not know an entity answers `unknown_entity` and the client keeps the row queued. |
| Deep links | `DeepLinkBridge`, `App.kt`, `MainActivity.handleLink`, iOS `.onOpenURL` | Only `opson://`. Android forwarded `opson://` only; no https intent filter. Reminders and widgets open screens through `router.navigate` after checking the app is someone's, which also survives a cold start. `routeDeepLink` is only used for the email confirmation. |
| Website | `website/` (Astro, static) on Cloudflare Pages at `https://www.opsontracker.com` | No server pages, no `.well-known`, no `_redirects`. `PUBLIC_API_URL` points at prod. |
| Universal links / App Links | none | No `com.apple.developer.associated-domains` in either entitlements file, no `autoVerify` https filter. iOS signs automatically (team `MSMDV43SUS`, `com.opson.Opson`), the App Store export signs with a profile sigh fetches. Android is `com.opson` (debug `com.opson.debug`); no signing fingerprint is in the repo. |
| Moderation | `ContributionGate`, `refuseIfRestricted`, `ContributionReverter`, admin Users › Moderation | Food submissions and reports are contributions. |
| QR | none | ML Kit and AVFoundation read only the four retail symbologies; no QR encoder in the build. |

## Decisions

**A recipe is its own thing, not a saved meal.** A saved meal logs every item
at the amount saved: right for "my usual breakfast", wrong for a pot of chili
where you eat one of six servings. A recipe is ingredients with amounts for
the whole batch, how many servings it makes, and optional notes or steps, and
it logs as one entry: "Chili, 1 serving" with a sixth of everything. Extending
saved meals would have put recipes in front of builds that don't know about
servings, and those log the whole pot. So `recipes` is a new synced entity
(Room 14, server V34) on the existing outbox, accepted only by clients that
say `accepts: recipe`. Ingredients are the same `LogDraft` snapshots a saved
meal holds, picked through search in pick-for-this-meal mode.

**Private by default.** A recipe is private until its owner turns on Share,
which opens a sheet listing exactly what becomes public: the name, every
ingredient and amount, the nutrition worked out from them, the notes, and who
made it, "an Opson user" unless they switch on their display name. Publishing
is online only (`POST /v1/recipes/{id}/publish`), so nothing goes public from
an outbox later without the person watching. Edits to a public recipe sync as
usual and show in the public copy, and the sheet says so. Stop sharing takes
it down at once; the code is kept, so a printed QR code works again if they
share it again.

**One source of truth.** The public copy is the synced row. `recipes` carries
server-owned columns (`public_code`, `public_state`, `public_show_name`,
`published_at`, `save_count`) that sync never writes. Publishing sets them,
which bumps `server_seq`, so the owner's other phones learn the recipe is
shared.

**Unverified, and numbers from the ingredients.** A public recipe always says
"Made by a person, not checked by Opson." The server works out its nutrition
itself from the ingredients when it is served: an ingredient from the catalog
with a weight is recomputed from the catalog's numbers per 100 g and counts as
checked when that food is verified; anything else (a custom food, a typed
name) uses the creator's numbers and counts as unchecked. The app shows which
ingredients came from checked foods. The creator's totals are never trusted.

**Moderation.** Publishing and reporting are contributions:
`refuseIfRestricted` first, with the `ContributionAttestation` headers. Per
user, 10 publishes an hour and 30 a day, and 10 reports in 10 minutes and 30
a day; per IP, 60 public reads a minute. Three open reports from different
people hide a recipe until someone looks (`public_state = hidden`). The admin
console's Recipes tab lists public recipes, reported first, and can remove
(with a reason), restore or dismiss reports. A moderation rollback takes every
public recipe of the account down (`RecipeReverter`). Account deletion
cascades.

**Sharing.** The code is 8 characters from `23456789abcdefghjkmnpqrstuvwxyz`.
The link is `https://www.opsontracker.com/r/<code>`; the app also answers
`opson://recipe/<code>`. The share sheet shows the link, Copy link, Share link
(the system sheet) and the QR code, which saves or shares as a PNG. The QR is
drawn in the app from the link by a small encoder in `:libraries:ui` (byte
mode, level M); no new dependency.

**Finding one.** A link or QR code opens the recipe in the app (when the app
is installed and the link verifies) or the web page. In the app: More ways in
are the Foods tab's Recipes card (Scan a recipe code, which turns on QR for
that scanner only) and search, where public recipes come under the catalog
results labelled "Public recipe".

**Web fallback.** `website/src/pages/recipe.astro` is a static page;
`_redirects` rewrites `/r/*` to it. It reads the code from the path, fetches
`GET /v1/recipes/public/{code}` (no auth, CORS open for that GET only), and
shows the recipe, the unverified line, "Open in Opson" (`opson://recipe/…`)
and links to both stores. Without JavaScript it says to open the link on a
phone with Opson.

## What the owner has to do

See "Universal links and App Links" in `website/README.md`. In short: the AASA
file is served from the site already; iOS needs the Associated Domains
capability on the App ID before the release entitlements can carry it (only
the debug entitlements do in v1, so a release build never fails to sign);
Android needs the Play app signing SHA-256 in a repo variable for
`assetlinks.json`. Until then links open the web page, whose Open in Opson
button opens the app.
