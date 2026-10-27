---
app: Opson
title: Privacy Policy
updated: 2026-10-27
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
- You can export your log or delete everything from inside the app.
- Anything the app reads from Apple Health or Health Connect stays on your
  phone.

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

Your phone's own backups (iCloud Backup or Google backup) may include the copy
of your log kept on the phone. Those backups belong to your Apple or Google
account, and we cannot see them.

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
- **Feedback** you send from the app also goes to Sentry, with the app's recent
  technical log, so we can find the problem you describe.
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
  active energy, resting energy and workouts (Apple Health), or calories
  burned, active calories, basal metabolic rate and exercise (Health Connect).
  It reads them on your phone when it needs to show them. They are never sent
  to our server or anyone else, never stored by Opson, and never written back.
  Only your choices about connecting and showing them are saved with your
  settings.
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
- **Voice logging.** Your phone's own speech recognizer turns speech into words.
  It runs on the phone where it can. Where it can't, Apple (on iPhone) or your
  phone's speech service (on Android) processes the audio. Opson never receives
  or keeps the audio. The words are handled like a typed meal.
- **Progress photos** stay on your phone, behind Face ID or your passcode. They
  are left out of phone backups, never uploaded, and deleted when you delete
  everything.

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
| Anthropic | Photo estimates, only when you use one |
| Apple | Sign in with Apple, and speech recognition when you use voice logging |
| Google | Speech recognition and barcode reading on Android, when you use them |

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
  straight away. Copies in our database provider's backups expire within 30
  days.
- **Crash reports, feedback and server logs**: up to 90 days.
- **App usage events**: up to 90 days.
- **Search words and barcodes** recorded without your account: as long as they
  help us improve the food database. They can't be traced back to you.

## Getting a copy, or deleting it

**More → Support and sharing → Export everything** gives you a CSV of your food
log, weigh-ins and water. For a copy of everything else we hold (notes,
feelings, settings, custom foods, saved meals and fasts), email us.

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
interest in keeping the app working and safe, and you can object to them. Your
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
