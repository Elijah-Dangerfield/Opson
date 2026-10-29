---
app: Opson
title: Privacy Policy
updated: 2026-10-29
contact: contact@nightjarlabs.llc
---

Opson is a food log made by Nightjar Labs LLC ("we", "us"). What you eat, how
meals felt and what you weigh are personal, and for some people they are
sensitive. This policy says what the app collects, why, who else handles it,
how long we keep it, and how to get a copy or delete it.

If you live in Washington, Nevada or Connecticut, our
[Consumer Health Data Privacy Policy](https://nightjarlabs.llc/opson/health-data)
also applies to you.

## In short

- No ads, no ad networks, no tracking across other apps or websites, and we
  never sell your data.
- Your log is kept on your phone and copied to our server so it survives a lost
  phone.
- You can export everything or delete everything from inside the app.
- What the app reads from Apple Health or Health Connect stays on your phone,
  unless you choose to copy weigh-ins or your height, birth year or sex into
  your Opson log, which is then saved like anything else you enter.

## What you put in

- **Your food log**: foods, amounts, their nutrition values, how you added each
  one, and meal times.
- **Your days**: water, notes, how meals and days felt (hunger, fullness,
  mood) and check-ins.
- **Weigh-ins**, if you add them, and **fasts**, if you use fasting.
- **Your settings**: which numbers you want to see, your targets, and your
  answers to the setup questions, including whether numbers or tracking have
  been hard for you before.
- **Foods you create**: custom foods and saved meals.

This is health information. We use it only to run the app for you: to show your
log and totals, apply your settings, and keep your phones in sync.

## Your account

Opson creates an account the first time you open it. It has a random ID and a
random display name (such as "QuietFox72"), and no email address or real name.

If you sign in with Apple, we receive the email address Apple shares with us,
which may be a private relay address, and use it only to sign you in and move
your log to that account. We don't use the name Apple offers.

## Where your data is stored

Your log is saved on your phone first, so the app works without a connection.
It is then copied to our server so it survives a lost phone or a reinstall. The
server runs on [Fly.io](https://fly.io/legal/privacy-policy) and the database is
hosted by [Supabase](https://supabase.com/privacy), in the United States.

The copy of your log kept on the phone, and the app's settings, are left out of
your phone's own backups (iCloud Backup, a computer backup, Google backup and
phone-to-phone transfers). A new phone gets your log back from our server when
you sign in. A backup made with an older version of the app may still hold a
copy. Those backups belong to your Apple or Google account, and we cannot see
them.

## What else the app sends

- **Food searches and typed meals.** What you search for, and what you type
  into "Describe it", is sent to our server to find matching foods. Search
  words are recorded without your account attached, so we can find foods
  missing from the database. Typed meals are not stored.
- **Crash reports** go to [Sentry](https://sentry.io/privacy/): what went
  wrong, your device model, OS version and app version, recent technical events
  in the app, and your account's random ID. If a network request failed, a
  report can include the address the app was calling, which for a food search
  contains the words searched. Reports are built to leave out your food log,
  weights and notes.
- **Feedback and bug reports** you send from the app also go to Sentry, with
  the app's recent technical log, your device model and the app version, so we
  can find the problem you describe. A report can also carry screenshots: one
  you pick from your photos, or, if you shake your phone to report a bug, a
  picture of the screen you were on. A screenshot shows whatever was on screen,
  which can include your log. The report page shows everything it will send
  first, and you can remove the screenshots and the technical log before you
  send it.
- **App usage events** go to our monitoring service,
  [Grafana Cloud](https://grafana.com/legal/privacy-policy/): which screens
  open, which features get used (for example, which way you added a food),
  errors and timings, with a random install ID and some facts about the device
  (OS version, the kind of device, whether it runs in an emulator or on a
  modified system, which store installed the app). These events never include
  your account ID, foods, amounts, weights or notes.
- **Your IP address** reaches our server whenever the app connects. We use it
  to answer the request and to limit how often some features can be used. We
  don't store it in our database, though our hosting and monitoring providers
  may record it in short-lived logs.

## Features that may not be in your version yet

Some features are switched on over time. When they are, this is how they work.

- **Apple Health and Health Connect.** If you connect them, Opson reads
  active energy, resting energy and workouts, including the name of the app
  that recorded each workout (Apple Health), or calories burned, active
  calories, basal metabolic rate and exercise (Health Connect). It reads them
  on your phone when it needs to show them, keeps the latest day's reading on
  your phone so the app opens quickly, and never sends them to our server or
  anyone else or writes anything back. Two things are copied into your log only
  if you ask: weigh-ins, when you turn on importing them, and your height,
  birth year and sex (height only on Health Connect), when you tap Fill in on
  your profile. Once copied they are part of your log and are saved and synced
  like a weigh-in or profile detail you typed. Your choices about connecting
  and showing them are saved with your settings.
- **Barcode scanning.** The barcode is sent to our server. If our database has
  no match, our server asks [Open Food Facts](https://world.openfoodfacts.org/privacy)
  and, where we have switched it on, Nutritionix, sending only the barcode. We
  record which barcodes were looked up, without your account attached. On
  Android, the camera image is read on the phone by Google's ML Kit, which may
  send Google usage and performance metrics but not the image.
- **Photo estimates.** When you use one, the photo and your note are sent
  through our server to [Anthropic](https://www.anthropic.com/legal/privacy),
  which reads them and returns a list of foods. We don't save the photo or keep
  a copy on our server, and it is not linked to your account. Anthropic does
  not use it to train its models.
- **Adding a food from its pack.** When a barcode or a search finds nothing,
  you can photograph the front of the pack and its nutrition label. Both
  photos go through our server to [Anthropic](https://www.anthropic.com/legal/privacy),
  which reads the label; Anthropic does not use them to train its models. You
  check what it read and submit. The food is saved to your foods and your log
  straight away, and a copy goes to us for review: the food's details, the
  barcode if there is one, and the two photos, linked to your account. The
  photos are seen only by you and the person at Opson who reviews the food,
  and are never at a public address. We delete them 30 days after the review,
  and straight away if you delete your account. If we approve the food, its
  name, brand, barcode, serving and nutrition values (not the photos, and not
  who sent it) join the food database that everyone searches, and stay there
  after you delete your account, as any other food in the database would. We
  keep a private record of which account sent it (see "Keeping the food
  database honest" below). If you only read the label and don't submit,
  nothing from it is kept.
- **Opson Builders.** If you join, we keep that you joined, when, which
  version of the Builder rules you accepted and your photo choice below, and
  we count the foods and checks we accept from you to keep Builder Pro on.
  - *Adding a food as a Builder.* Your phone reads the label photo itself
    (Apple's Vision on iPhone, Google's ML Kit on Android, which may send
    Google usage metrics but not the image). With the food we receive the text
    your phone read from the label, the numbers it filled in, a few
    measurements of how clearly it read them, whether the photo came from the
    camera or your library, and a photo of the ingredients if you add one.
    The label photo also goes through our server to
    [Anthropic](https://www.anthropic.com/legal/privacy) for a second reading,
    which Anthropic does not use to train its models. We use all of this to
    check the numbers and to decide whether a person needs to look before the
    food is added for everyone. It is kept with the review record and deleted
    with your account. The photos follow the rules above.
  - *Check against the pack.* Only the text your phone read from the label is
    sent, not the photo. We keep which food you checked, when, and whether the
    numbers matched, not the text. If they didn't and you send a correction, it
    goes as a report, with the photo, like any report.
  - *Also read the label* in the scanner reads nothing from the camera while it
    scans. After the barcode, you take the label photo yourself.
  - *Keeping label photos longer (optional).* If you choose it when you join or
    later on More › Builders, we keep the photos of foods you send for up to
    two years after review, to test how well Opson reads labels. Turn it off,
    or leave Builders, and the usual 30 days applies again.
- **Keeping the food database honest.** When you send a food or report a
  problem with one, and only then, the app also sends a random install code
  it keeps for this (on iPhone in the Keychain, on Android the app's install
  ID), and, where we have switched it on, a token from Apple's DeviceCheck or
  Google's Play Integrity. We keep a scrambled copy of the install code, never
  the code itself, and use the token once to ask Apple or Google whether we
  have marked that phone, then discard it. Apple and Google keep up to two or
  three on/off marks per phone for us, which can last through deleting the
  app. We use this only to stop someone we have removed for sending fake or
  copied data from sending more, or from getting Pro for free through a
  program, from the same phone. It never stops anyone logging their own food.
  Phones change hands, so if a phone you bought is blocked, write to us and we
  will clear it. We also keep a record of which account sent each food or
  correction we accepted, so we can undo them if we have to.
- **Barcode suggestions.** If a barcode finds nothing and, within ten minutes,
  you log a food you found by searching, we record that pairing (the barcode,
  the food and when) so we can attach the barcode to the right food. It is not
  linked to your account and holds nothing you typed.
- **Voice logging.** Your phone's own speech recognizer turns speech into words.
  It runs on the phone where it can. Where it can't, Apple (on iPhone) or your
  phone's speech service (on Android) processes the audio. Opson never receives
  or keeps the audio. The words are handled like a typed meal.
- **Progress photos** stay on your phone, behind Face ID or your passcode. They
  are left out of phone backups, never uploaded, and deleted when you delete
  everything.
- **Medication.** If you track a medication, what you take, your doses,
  injection sites, notes and side effects are kept on your phone only. They are
  not copied to our server, so they are not in Export everything and don't come
  back on a new phone, and signing out or deleting everything removes them. Like
  your log, they are left out of phone backups. They go into the provider
  summary only while Medication is switched on there (it starts on while you
  track one); the summary is made on your phone and goes only where you send
  it. A dose reminder says "Dose day", never the medicine or the dose. Usage
  events can say that a dose was logged or that tracking was turned on, never
  which medicine, how much, where, or how you felt, and none of it is written
  to the app's technical log or crash reports.

## What we look at

We look at totals across everyone: how many people logged today, which ways of
adding food get used, which searches find nothing. Our dashboards are built so
they cannot show one person's log. We don't read individual logs, except when
you ask us to help with your account or the law requires it.

## What the app never does

- No advertising, no ad networks and no advertising identifiers. We don't track
  you across other companies' apps or websites.
- We don't sell personal data, and we don't share it with anyone for
  advertising or marketing. That includes health data.
- Data from Apple Health or Health Connect is never used for advertising,
  marketing or data mining, never sold or disclosed to anyone, and never stored
  in iCloud.
- No location. We don't collect it and we don't use geofencing.
- No camera, photos, microphone or Face ID unless you choose a feature that
  needs it, and then only for that feature.

## Who handles data for us

| Provider | What for |
|---|---|
| Fly.io | Runs our server |
| Supabase | Stores your synced log and your account |
| Sentry | Crash reports and feedback |
| Grafana Labs | App usage events and server monitoring |
| Anthropic | Photo estimates and reading pack labels, including a second reading of a Builder's label photo, only when you use them |
| Apple | Sign in with Apple, speech recognition when you use voice logging, and DeviceCheck when you send a food or a report |
| Google | Speech recognition and barcode reading on Android when you use them, and Play Integrity when you send a food or a report |

They handle data on our behalf, only to provide their service to us. Open Food
Facts and Nutritionix receive only barcodes from our server, never anything
about you. We may also disclose information if the law requires it, or to a
company that takes over Opson, which would stay bound by this policy.

## How long we keep it

- **Your log, settings and account**: until you delete them. When you delete a
  single entry, it disappears everywhere in the app, but our server keeps the
  row marked as deleted so your other phones learn about it. It is gone for good
  when you delete your account.
- **After you delete your account**: everything is removed from our database
  straight away, with one exception. If you sent us foods or reports, or we
  acted on your account for sending fake data, we keep those records with
  your account ID replaced by a scrambled code that can't be turned back into
  it, so we can still undo what was sent. Copies in our database provider's
  backups expire within 30 days.
- **Crash reports, feedback and server logs**: up to 90 days.
- **App usage events**: up to 90 days.
- **Search words and barcodes** recorded without your account: as long as they
  help us improve the food database. They can't be traced back to you.
- **Photos of a pack you submit**: until 30 days after we review the food, or
  until you delete your account if that comes first. As a Builder who chose to
  let us keep them, up to two years after the review, for as long as you keep
  that choice and stay a Builder.
- **Your Builder membership and checks against the pack**: until you delete
  your account. Leaving Builders ends Builder Pro but keeps the record that
  you were one. The review record (the
  food's details and our decision) stays with your account until you delete it.
- **Foods we approve from submissions**: in the food database for everyone,
  without the photos and without your account.
- **Records of who sent which food or correction, and of action we took on an
  account**: as long as the food database holds what they describe, under the
  scrambled code once the account is deleted.
- **The scrambled install code** from a phone we marked: until we clear the
  mark. Otherwise it goes with your account.

## Getting a copy, or deleting it

**More → Support and sharing → Export everything** gives you a zip of
spreadsheets with everything we hold for your account: your food log, notes,
hunger and feelings, check-ins, fasts, water, weigh-ins, exercise, the foods
and meals you saved, your settings and a summary of each day. A README in the
zip says what each file holds. Older versions of the app export a CSV of the
food log, weigh-ins and water only; update the app for the rest, or email us.

**More → Support and sharing → Delete everything** deletes your account and
every row we hold about you, on the phone and on our server. It cannot be
undone. You can also email us and we will delete it for you.

## Your rights

Wherever you live, you can ask us to tell you what we hold about you, give you a
copy, correct it, delete it, or stop using it for something you agreed to. Email
the address at the end of this page. We answer within 45 days, or tell you
within that time if we need up to 45 more. It's free. Because most Opson
accounts have no name or email attached, we may not be able to find or confirm
yours from an email alone; the export and delete buttons in the app always
work.

If we turn down a request, you can appeal by replying to our answer. We decide
appeals within 45 days and explain why. If you still disagree, you can contact
your state attorney general or your data protection authority.

**California.** In the last 12 months we collected identifiers (a random account
ID, and an email if you signed in with Apple), health information you entered,
and app activity and device information, for the purposes above. We do not sell
or share personal information as California law defines those words, and we use
sensitive personal information only to provide the app. You have the rights
above, and we won't treat you differently for using them.

**European Economic Area, United Kingdom and Switzerland.** Nightjar Labs LLC is
the controller. We process your log and account to provide the app you asked
for (contract). Health information you enter is processed with your explicit
consent, which you can withdraw at any time by deleting it or your account.
Crash reports, usage events and security measures rest on our legitimate
interest in keeping the app working and safe, and you can object to them.
The install code, DeviceCheck and Play Integrity checks, and the records of
who sent which food rest on our legitimate interest in preventing fraud. Your
data is processed in the United States; where needed we rely on our providers'
standard contractual clauses or their certification under the Data Privacy
Framework. You can complain to your local data protection authority.

## Children

Opson is not meant for children under 13, and we don't knowingly collect their
data. If we learn that we have, we delete it. In some countries the age at which
you can use an app like this without a parent's permission is higher, up to 16.

## Security

Data travels between the app and our server encrypted, and our database
provider encrypts it at rest. Sign-in tokens are kept in the phone's secure
storage (the Keychain on iPhone, encrypted storage on Android). No system is
perfectly secure, but we limit who can reach your data and what they can see.

If a breach of security affects your health information, we will tell you, and
the Federal Trade Commission and other authorities where the law requires,
without unreasonable delay and within the time the law sets.

## Changes to this policy

If this policy changes, the new version is posted here and the date at the top
changes. If a change affects how we use or share health information, we will
tell you in the app and ask for your consent where the law requires it before
the change applies to you.

## Contact

Nightjar Labs LLC, 418 Broadway, Albany, NY 12207, [contact@nightjarlabs.llc](mailto:contact@nightjarlabs.llc).
