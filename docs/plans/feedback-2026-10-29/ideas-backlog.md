# Ideas backlog (not v1)

Ideas the owner raised or the notes deferred. Each is short on purpose: enough
to decide on later, not a spec. Sources: the owner's 2026-10-29 feedback,
`docs/review/product-ideas-2026-10-27.md` ("Later" rows),
`docs/review/product-notes.md`, `docs/plans/onboarding-v2.md`, and the other
files in this folder.

## Coach (sharing with a professional, live)

A coach, dietitian or therapist sees a client's log and check-ins, hears when
the client logs, and can leave a note on a meal or on the whole day to
encourage or celebrate.

- **Roles.** Client (an ordinary Opson account) and Professional (an account
  with a verified "professional" flag, free). A professional has many clients;
  a client has at most a few professionals.
- **Joining.** The client invites from More › Sharing with a code or link; the
  professional accepts. Either side can end it at any time, and ending it
  removes access at once.
- **What the client shares, chosen per professional:** meals and notes,
  check-ins (hunger, energy, sleep, moods), weight, water, fasting, GLP-1 doses
  and side effects, targets. Everything is off until ticked.
- **Modes.** The client's Mode does not limit what the professional can see,
  because the professional may need the numbers the client chose not to see.
  But nothing the professional writes may contain numbers the client's Mode
  hides: the comment box warns the professional, and the client's app renders
  a comment with numbers redacted if their Mode hides them. Needs clinician
  input.
- **Notifications.** The professional chooses: each meal as it's logged
  ("Sam logged lunch"), a daily digest, or none. Never the food or amounts in
  the notification text. The client is notified of a new comment, and can turn
  that off.
- **Comments.** On a meal or a day, text only, editable for a short window,
  deletable by either side. A small fixed set of reactions (for example a heart
  and a thumbs up) for a quick "seen". No public anything.
- **Privacy.** This is health data shared by consent. Log every view by the
  professional for the client to see ("Seen by Dr Lee, today 3:10 PM").
  Export includes comments. Deleting either account ends the share.
- **Relation to Pro.** The client's Pro unlocks live sharing; the professional
  side is free (`pricing-and-pro.md`). The one-off provider summary PDF
  (`release.providerSummary`) stays free and is the first step: the earlier
  review recommended no live clinician dashboard by default, so this is opt-in
  and client-initiated only.

## Sharing with friends

A lighter cousin of the coach: share chosen things (meals, check-in mood,
"logged today" without detail) with a friend or partner, with the same
per-item consent and the same Mode redaction. No feeds, no leaderboards, no
comparisons. Pro.

## Nutrient score

A daily or weekly "how nourishing was this" score, **only once the maths is
validated and disclosed**. The earlier review said no food grades or scores,
so this needs a careful version:

- About the day or week, never a grade on a single food.
- Based on a published method (for example a nutrient-density index such as
  Drewnowski's Nutrient Rich Foods, or plain "share of your vitamin and mineral
  references met"), reviewed by the dietitian.
- Says what it counts and what it ignores, and when data is partial ("9 of 12
  foods list vitamin D"), as Nutrients already does.
- All numbers only; never for someone who said numbers have been hard; off by
  default.

## Daily report visualisations

A richer Close the day and a weekly report: energy in and out across the day,
when meals happened against hunger, macros as a share, the week's deficit
against the goal (`design-brief-energy-and-goals.md`), nutrients met. Every
chart follows the Mode and has a spoken summary for VoiceOver and TalkBack.

## Energy and goals, later parts

- **Adaptive burn** from logged intake and the weight trend (MacroFactor's
  approach), as a fourth source on Energy. Needs about two weeks of data. Pro.
- **Body fat %** in Profile, unlocking Katch-McArdle for resting energy.
- **Goal projections** as a band, with the slowing that real weight loss shows.

## GLP-1, after v1

From `glp1-research.md`: estimated medication level (if ever, as a rough
"shot phase" rather than a curve), titration step-ups recorded, supply and
refill reminders, two or more medications in the UI, a widget, "food noise" as
its own check-in question.

## Package scanning and OPDB, later parts

From `package-scanning.md`: ingredients and allergens, opt-in sharing back to
Open Food Facts (after counsel), multiple serving columns, offline structuring
with Apple's on-device model on iOS 27.

## Logging and Today

- Recipes with servings and a cooked weight; import from a URL (Pro) (#9).
- Copy a meal from any day; offer "Your usual breakfast" when foods repeat (#18).
- Meal groups people can rename, hide or add to, up to three snacks (#13).
- Change day by swiping the date header, long-press for the calendar, "Back to
  today" (#12).
- Merge "untracked" and "left out of the math" days into one state (#10).
- Photo-only meals for No numbers, a photo journal with no estimate (#15; ask
  first, it overlaps the retired "Ate, no details").
- Long-press Water's + to add a glass without the sheet.
- Fasting window on the "When you eat" chart (#22).

## Reminders and coming back

- Reminders that back off after days of no response, a week's pause, a daily
  cap (#16).
- After a gap: a neutral greeting and one tap to leave the missed days out,
  never counted (#17).

## Platform and polish

- Lock screen and interactive widgets (add a glass, log my usual), app icon
  quick actions, Siri and App Intents (#14).
- Regroup More by what people are trying to do (#11).
- A store review prompt at a good moment, or delete the unused one.
- Feedback to a server endpoint with a higher character cap.
- A custom auth domain (`auth.opsontracker.com`) so Google's chooser stops
  showing the Supabase host.

## Onboarding follow-ups

- A "setup done" setting so a new Apple account that quits mid-setup returns
  to setup, not Today.
- A way to change the "numbers have been hard" answer later.
- Use or cut the three "why" answers (curious, medical need, provider). The
  GLP-1 offer in `glp1-research.md` is one use.
- Default weight and water units from the phone's region for everyone.
