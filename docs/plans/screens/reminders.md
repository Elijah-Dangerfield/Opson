# Reminders plan

Reminders and celebrations (a More page), the meal and water reminder
notifications on both platforms, and the Rhythm card that links to them. 0.2,
behind `release.reminders` (default false). Read `shared.md` and `settings.md`
first; this page follows the settings page conventions.

## Source
- Design v3: `reminders.png`, `reminders--full.png` (7.1),
  `reminder-notification.png` (7.2), `today-rhythm.png` (the "Dinner reminder"
  card), `modes.png` (Rhythm: "A meal checklist with reminders"), `more.png`
  (the row and its "Quiet mode on" subtitle). Manifest notes: "Meal times come
  from the Rhythm checklist and can be edited here." and "Already ate checks
  the meal off right from the lock screen ... The same copy works in every
  mode, because it never mentions food amounts."
- Handoff (older): `Phone.dc.html` `is.reminders` (569 to 600),
  `phone-logic.js` `events`, `mealRem`, `toneSample`; `README.md` safety
  rule "No evening calories left notification, and no setting for one".

## The flag

`RemindersEnabled : FlagConfigValue`, path `release.reminders`, default
`false`, in `:libraries:reminders` (QA menu through the `QaConfigValue`
multibinding, listed in `apps/admin/config-manifest-registry.json` and the
drift test). Off means: no More row, no Rhythm card, no Water reminders row,
tone and hunger questions stay on Look and feel, and **nothing is scheduled**
(the coordinator cancels everything it scheduled and dismisses what it
showed). It is read live from `AppConfigFlow`, so a remote flip takes effect
on the next config emission.

## Decisions

| Question | Decision |
|---|---|
| Where meal times live | They did not: Today hardcoded them (`TodayStateBuilder.usualTimes`). New `Settings.mealTimes: MealTimes` (8:00, 12:30, 15:30, 19:00, Today's old constants, so nothing on Today moves), serialized `"HH:MM"`, unreadable falls back per meal. Today's "Usually around" and Rhythm's "planned around" read it; Reminders edits it. |
| Reminder time vs meal time | One time per meal. The design's 15 to 30 minute gaps between Rhythm's "planned around" and the reminder are not consistent between meals (8:00/8:30, 12:30/1:00, 7:00/7:15), so the nudge fires at the meal time itself, which is what "A nudge if a meal isn't logged by its time" says. |
| Opt-in | 0.1 synced `remindersEvent = subtle` and three meals on for everyone (prototype defaults, `encodeDefaults`). Acting on that when the flag flips would schedule for users who never chose it, and on Android 12 and below would post without ever asking. New `Settings.remindersChosen = false`: until the person picks a level or turns a meal on, the effective level (`Settings.mealReminderLevel`) is Off. Water reminders already default off. |
| Skip when logged | Yes (v3: "7:15 pm, only if dinner isn't logged"). Today's reminder for a meal is dropped once that meal has items or "Ate, no details"; with meal groups off it is dropped once anything was logged since the previous meal's time. A delivered nudge is dismissed when its meal gets logged. Water: the rest of today's water nudges drop once the day reaches the water goal. |
| What the rows do | Shown: Quiet mode, After a big day, Meal reminders, the four meal times, How we talk to you, Hunger and fullness questions, the "no evening messages" line. **Not shown: New food tried and Weekly recap.** Nothing in the app acts on them yet (no new-food note, no recap notification), and 0.1's rule is to offer only what works. They come back with the features. |
| Subtle vs Full | Subtle: silent, no heads-up (Android channel `IMPORTANCE_LOW`, iOS `passive`, no sound). Full: the system's default sound and a heads-up where the platform allows (`IMPORTANCE_DEFAULT`, iOS `active` with the default sound). Quiet mode on makes Full silent too (its own channel without sound or vibration). Water nudges always use Subtle. |
| Exact vs inexact (Android) | Inexact: `AlarmManager.setAndAllowWhileIdle`. A meal nudge a few minutes late is fine; exact alarms need `SCHEDULE_EXACT_ALARM` (denied by default for new installs on 14) or `USE_EXACT_ALARM`, which Play reserves for alarm and calendar apps. No exact-alarm permission is requested. |
| Horizon | Seven days ahead (4 meals and 2 waters a day stay under iOS's 64 pending). Any replan extends it: opening the app, a notification action, an Android alarm (it starts the process). On iOS a week without opening the app or answering a nudge lets them run out, which suits an app that should not nag someone who stepped away. |
| Permission | Asked only at the moment someone turns a reminder on (a level other than Off, a meal switch on, or water reminders on), never at launch. Denied: the choice is still saved and the page shows a note with Open settings. Android 13+ uses `POST_NOTIFICATIONS` through the activity result registry; below 13 it is "granted" unless the app's notifications are off. |
| Tap | The notification and its Log it button open the Logger for that meal and day, through `Router` (`LoggerRoute(slot, dayIso)`), not a URL. A typed `ReminderOpens` channel carries the request to `App.kt`, which navigates; `DelegatingRouter` holds it until the NavHost is bound, so a cold start from a tap works. An `opson://` link was the other option: `LoggerRoute.slot` is a JSON enum argument, so the URL would need `%22dinner%22`, and a link handled before the boot gate sets the graph is dropped. |
| Already ate, In 30 min | v3 shows them. Already ate marks the meal "Ate, no details" without opening the app (Android broadcast receiver, iOS background action). In 30 min replaces the nudge with one 30 minutes later; snoozes survive replans because the platform's pending list is an input to the plan. |
| Evening "calories left" | Never. No notification carries an amount, and there is no setting for one (safety rule). |

## Modules

| Module | Holds |
|---|---|
| `:libraries:diary` | `Settings.mealTimes`, `Settings.remindersChosen`, `Settings.mealReminderLevel`, `MealReminders.isOn(slot)` / `with(slot, on)`, `MealTimes.at(slot)` / `with(slot, time)`. |
| `:libraries:reminders` (new) | `RemindersEnabled`; `Reminder`, `ReminderKey`, `ReminderPlanner` (pure); `ReminderCopy` (the notification's `StringResource`s, by key and tone); `NotificationPermissions`, `LocalNotifications`, `ReminderNotification`; `ReminderActions`, `ReminderOpens`; `ReminderEvents` (telemetry names). |
| `:libraries:reminders:impl` (new) | `ReminderCoordinator` (`AutoInit`): observes the flag, settings, today and foregrounds, plans and hands the platform the plan; handles actions. Android: `AndroidLocalNotifications`, the alarm, action and boot receivers, the manifest entries, `ic_stat_reminder`. iOS: `IosLocalNotifications` (`UNUserNotificationCenter` and its delegate, set when the coordinator is built in `iOSApp.init`). |

## Route

`RemindersRoute()` in `:features:settings`, a `screen<>` (system design
section 10). Opened from More and from the Rhythm card on Today.

## State

```kotlin
data class RemindersState(
    val quiet: Boolean,
    val bigDay: EventLevel,
    val mealLevel: EventLevel,                 // Settings.mealReminderLevel (Off until chosen)
    val meals: List<MealReminderRow>,          // Breakfast, Lunch, Snack, Dinner
    val tone: Tone,
    val askTags: Boolean,
    val permission: NotificationPermission,    // Granted | Denied | NotAsked
    val pickingTime: MealSlot?,                // the time dialog, open on that meal
) { val showPermissionNote: Boolean }          // something is on and permission is Denied
data class MealReminderRow(val slot: MealSlot, val on: Boolean, val time: LocalTime, val timeLabel: String)
```

`on` is what will actually happen: the meal's switch and a level other than
Off. `timeLabel` is `DateCopy.time` ("8:30 AM"); the row shows it when on and
"Off" when off, as v3.

## Actions

| Action | Does |
|---|---|
| `InputsArrived(settings)` | `RemindersStateBuilder.build`. |
| `PermissionChecked(permission)` | From init and `ScreenResumed` (back from system settings). |
| `QuietToggled` | `quiet`. |
| `BigDayPicked(level)` | `bigDayEvent`. |
| `MealLevelPicked(level)` | `remindersEvent = level`, `remindersChosen = true`; not Off asks for permission if not asked yet. `reminder.set`. |
| `MealToggled(slot)` | Flips the meal. Turning one on while the level is Off sets the level to Subtle (turning a reminder on turns reminders on) and asks for permission. `reminder.set`. |
| `MealTimeTapped(slot)` / `MealTimeDismissed` | Opens and closes the time dialog (`:libraries:ui`'s `TimePickerDialog`). |
| `MealTimePicked(time)` | `mealTimes.with(slot, time)`; the same time writes nothing. |
| `TonePicked(tone)` / `HungerQuestionsToggled` | As Look and feel. |
| `OpenSettingsTapped` | `NotificationPermissions.openSettings()`. |
| `ScreenResumed` | Re-checks permission. |
| `BackTapped` | `Close`. |

Events: `Close`. Writes are `SettingsRepository.update`, local first; none of
them touch visibility, so nothing goes through `NumbersChanger`.

Elsewhere in settings, all only with the flag on:
- **More**: `MoreRow.Reminders(quiet)` third in the first card ("Quiet mode
  on" / "Quiet mode off"), opening `RemindersRoute`.
- **Look and feel**: tone and hunger questions move to Reminders (settings.md
  0.2 table), so the page drops them.
- **Water settings**: a Reminders row, `One mid-morning, one mid-afternoon.`
  (10:30 and 15:00), `RemindersToggled`; on asks for permission.

## Today (Rhythm)

`TodayInputs.remindersOn` (the flag) and `RhythmCard.reminder:
RhythmReminder?`: the next meal still to log today, with its reminder on,
`Next(slot, time)` ("Dinner reminder" / "7:00 PM, only if dinner isn't
logged" / Change times), or `Off` when meal reminders are off ("Meal
reminders" / "Off" / Set up). None when the flag is off, every meal is done,
or the next meal's switch is off. `ReminderTapped` opens `RemindersRoute`.
`usualTimes` becomes `settings.mealTimes`.

## Notification

`ReminderCopy` picks resources by key and tone; the coordinator resolves them
(`getString`) into a `ReminderNotification(reminder, title, body, actions,
style)` for the platform.

| Key | Plain | Warm |
|---|---|---|
| Meal title | `Dinner isn't logged yet` (one per meal) | `Dinner, whenever you're ready` |
| Meal body | `Add it whenever you like, or let us know you already ate.` | `No rush. Add it when it suits you, or tell us you already ate.` |
| Meal actions | `Already ate`, `Log it`, `In 30 min` | same |
| Water title | `Time for some water` | `A glass of water?` |
| Water body | `Log a glass whenever you like.` | `Only if you feel like one. Log it whenever.` |

**Visibility.** A notification is a surface, and it has no `Display` and no
format argument at all: every string is a fixed resource, so no number the
policy hides can reach it in any mode. Adding a number later means resolving
it through `VisibilityPolicy` and keeping this test green. `CaloriesHiddenLeakTest`
lists `reminder-notification` (`showsCaloriesInFull = false`): a lookalike of
the system notification (`ReminderNotificationCard`, also the
`reminder-notification` design-sync preview) rendering every key in both tones
from `ReminderCopy`, scanned in every preset. `reminders` (the page) is listed
too.

## Data and platform

- `ReminderCoordinator` combines `AppConfigFlow` (the flag), settings (only
  once `isKnown`), today's `Day` (the date re-read on every foreground and at
  midnight) and a refresh signal, then `LocalNotifications.replaceAll(plan)`
  and `dismiss(keys)` for today's meals that are now done.
  `ReminderPlanner.plan(PlanInputs)` is pure.
- Android: alarms are `setAndAllowWhileIdle(RTC_WAKEUP)` to
  `ReminderAlarmReceiver`, whose intent carries the resolved notification, so
  firing needs no DI. What is scheduled is kept in `SharedPreferences` (alarms
  cannot be listed), for cancelling and for `pending()`. `ReminderBootReceiver`
  (boot, package replaced, time and time zone changed) asks the coordinator to
  replan, through `ReminderActionsHost` (the `Application`).
  `ReminderActionReceiver` handles Already ate and In 30 min. Taps go to the
  launcher activity with the reminder in extras; `MainActivity` hands them to
  `ReminderActions.opened`.
- iOS: `UNCalendarNotificationTrigger` per reminder (non-repeating),
  `UNNotificationCategory` "meal" with the three actions (Log it opens the
  app, the other two run in the background), the delegate presents in the
  foreground (banner for Full, list only for Subtle).
- No Info.plist or entitlement change: local notifications need neither.
  AndroidManifest (`:libraries:reminders:impl`): `POST_NOTIFICATIONS`,
  `RECEIVE_BOOT_COMPLETED`, three receivers, all `exported="false"`.

## Telemetry

Added to `app-events.md`. No meal names, no times, no free text.

| Event | Attributes | Fires |
|---|---|---|
| `reminder.set` | `kind` (meal / water), `level` (off / subtle / full), `meals_on` (0 to 4), `permission` (granted / denied / not_asked) | A Reminders or Water change to what gets scheduled: a level, a meal switch, a time, water on or off. |
| `reminder.permission` | `granted` | After the system prompt answers. |
| `reminder.delivered` | `kind`, `snoozed` | Android only, when the alarm posts. iOS gives no delivery callback for a backgrounded app, so there is no iOS number to compare. |
| `reminder.tapped` | `kind`, `action` (open / log_it / already_ate / snooze) | A notification or one of its buttons. |

## Copy

`strings_reminders.xml`, prefix `reminders_`: `Reminders and celebrations`;
`Quiet mode` / `No confetti, sounds or vibration.`; `After a big day` / `A calm
card at day close. Full adds the two buttons.`; `Meal reminders` / `A nudge if
a meal isn't logged by its time.`; `Off` `Subtle` `Full`; `Meal reminder
times`; `How we talk to you`, `Plain` / `Warm`, `For example: %s` with
`"Dinner isn't logged yet."` / `"Dinner's still open. Add it whenever."`;
`Hunger and fullness questions` / `Asked when you add food. Always skippable.`;
`We don't send evening messages about what's left to eat. That isn't a
setting, we just don't.`; permission note `Notifications are off for Opson, so
reminders can't reach you.` / `Open settings`; time dialog `%s time`, `OK`,
`Cancel`; More `Quiet mode on` / `Quiet mode off`; Water `Reminders` / `One
mid-morning, one mid-afternoon.`; Rhythm `%s reminder`, `%1$s, only if %2$s
isn't logged`, `Change times`, `Meal reminders`, `Off`, `Set up`; Android
channel names `Meal reminders`, `Meal reminders, subtle`, `Meal reminders,
quiet`; plus the notification table above.

## Edge cases

Flag off (nothing scheduled, nothing rendered); permission denied (saved,
note shown, nothing delivered); meal groups off; a meal logged after its nudge
was shown (dismissed); time zone change (Android replans from the boot
receiver, iOS on next foreground); sign out or delete (settings reset, so the
plan is empty and everything is cancelled); large text (rows wrap).

## Tests

- `:libraries:diary`: `SettingsTest` defaults and old JSON for the new fields,
  `MealTimes` round trip and unreadable values.
- `:libraries:reminders`: `ReminderPlannerTest` (flag, opt-in, level Off,
  per-meal switches, skip when logged, meal groups off, horizon, past times,
  snoozes kept and dropped, water and the goal), `ReminderCopyTest` (tone,
  keys, actions).
- `:libraries:reminders:impl`: `ReminderCoordinatorTest` with a fake
  `LocalNotifications`: flag off schedules nothing, turning on schedules,
  logging dinner drops and dismisses it, Already ate writes, snooze survives
  a replan, opened sends the Logger request, unknown settings wait.
- `:features:settings:impl`: `RemindersViewModelTest` per action (permission
  asked only on turn-on, denied note, telemetry fired), `RemindersStateBuilderTest`,
  More with the flag on and off, Look and feel with the flag on and off, Water
  reminders row.
- `:features:today:impl`: the Rhythm reminder card per state and flag; times
  from settings.
- Previews: `RemindersScreen.kt` configured, empty (never set), permission
  denied, Blind, dark, the time dialog; `ReminderNotification.kt` (dinner as
  v3, and every reminder warm); More and Water settings with the flag on;
  `TodayRhythmRemindersPreview`.
- `SURFACES`: `reminders`, `reminder-notification` (settings), `today-reminders` (Today
  with the Rhythm line).
