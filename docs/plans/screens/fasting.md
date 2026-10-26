# Fasting plan (0.2)

The Fasting page under More, its More row, a Today card, a row in Edit
Today's cards, and the fasts themselves: stored on the device, synced through
`/v1/sync`. Behind `release.fasting` (default off). Read `shared.md` and
`settings.md` first.

## Source
- Design v3: `fasting.png`, `more.png`, `today-day-menu.png`, and the manifest
  notes for each. `CHANGES.md`: "Fasting is shown only in Full numbers and Sprint."
- Handoff (older, v3 wins): `README.md` §2 (safety) and §More, `Phone.dc.html`
  `is.fasting` (782 to 808) and `fs.todayCard` (229), `phone-logic.js` `fs`,
  `FAST_OK`, `FAST_PLANS`, `addItems` (`fastActive:false`).

## Where v3 and the brief disagree, and what this builds

| Brief or handoff | v3 / rules | Built |
|---|---|---|
| Add menu "Start a fast / End fast" | v3 has no Add menu (no header +, `blind-leak-test.md` row 28) | No Add menu entry. Nothing to add it to. |
| Day menu "Fasting" | v3 moved it into **Edit Today's cards** | A "Fasting" switch in Edit Today's cards (the Today card on or off). |
| "Start a fast" button | v3: "The Start fast button is gone, because a fast starts whenever the window closes" | No Start button. A fast starts at the plan's window close. **End fast** stays: ending is always allowed. |
| Prototype footnote "No streaks, no longest-fast records, nothing past 16 hours" | Not in v3 | Not drawn. The rules it states are enforced in code (below). |
| v3 times "7:00 pm", "9:00 am – 7:00 pm" | App convention is `DateCopy.time` ("7:00 PM") and "to" for ranges | "7:00 PM", "9:00 AM to 7:00 PM". |

v3 shows no streak, record or target, so nothing conflicts with the safety
rules. The goal length is the plan's fasting hours, never more than 16.

## Safety rules (each one tested)

1. **Who sees it.** `FastingAccess.of(settings, released)`:
   - `Hidden` when `release.fasting` is off, or `settings.onboarding.hard` (the
     person agreed "Numbers or tracking have been hard for me before"), in
     **every** mode, Full and Sprint included.
   - `NotInThisMode` when the preset is not Full or Sprint (`Fasting.allowed`).
   - `Offered` otherwise.
   Hidden and NotInThisMode render nothing: no More row, no Today card, no
   Edit cards row. The page, reached anyway (a stale back stack), shows
   "Fasting isn't part of this mode" for NotInThisMode and closes itself for
   Hidden. `TODO(clinician)` stays on the allowed modes and the cap
   (`:libraries:nutrition` `Fasting`), and goes on `FastingAccess`.
2. **Off by default.** `Settings.fasting = false`. Nothing starts until the
   person turns the timer on.
3. **Capped at 16 hours.** Plans are 12:12, 14:10 and 16:8 only. A fast ends
   at `startedAt + goalHours` (at most 16 h) even if nobody ends it, the timer
   never reads past that, and the server refuses a fast longer than 16 h or a
   goal over 16.
4. **Logging food ends a fast.** `DiaryRepository.addItems`, `copySlot`,
   `copyDay` and `setAteNoDetails(ate = true)` on today or earlier end a
   running fast at the moment of logging. No fast starts for a window that
   already has food logged after it closed.
5. **No streaks, no records.** Nothing counts or compares fasts. Only the
   latest fast is ever read.

## Data

### Domain (`:libraries:diary`, `Fasts.kt`)

```kotlin
data class Fast(
    val day: LocalDate,          // the local date the fast began; one fast per evening, the key everywhere
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val goalHours: Int? = null,  // the plan's fasting hours when it started, at most 16
) { val endsBy: Instant; fun isRunningAt(now): Boolean; fun endedAt(now): Instant? }

enum class FastingAccess { Hidden, NotInThisMode, Offered; companion object { fun of(settings, released) } }

sealed interface FastingStatus {
    data class Running(val since: LocalTime, val elapsed: Duration) : FastingStatus
    data class WindowOpen(val until: LocalTime) : FastingStatus
    data class Ended(val at: LocalTime, val nextStart: LocalTime) : FastingStatus   // after the window, the evening's fast is over
    data class Resting(val nextStart: LocalTime) : FastingStatus                     // after the window, no fast this evening
}

object FastingSchedule {   // pure
    fun isWindowOpen(plan, now, zone): Boolean
    fun lastClose(plan, now, zone): Pair<LocalDate, Instant>
    fun due(plan, now, zone, latest: Fast?, lastLoggedAt: Instant?): Fast?
    fun status(plan, now, zone, latest: Fast?): FastingStatus
}

interface FastingRepository {
    fun observeLatest(): Flow<Fast?>
    suspend fun startDue()   // starts FastingSchedule.due when access is Offered and the timer is on; idempotent
    suspend fun end()        // "End fast"; nothing when none is running
    suspend fun endForTimerOff()
}

fun interface FastingRelease { fun isOn(): Boolean }   // bound to the release.fasting flag
fun minuteTicks(clock: Clock): Flow<Instant>          // the live timer: now, then on each wall-clock minute
```

A fast is keyed by its `day`, so two devices that both start the evening's
fast write the same row. A started fast is stamped `clientUpdatedAt =
startedAt`, so any real end (stamped when it happens) wins last-write-wins.
Reaching the goal is not written: `endsBy` is derived, so it never races a
real end.

### Room (`:libraries:diary`, schema 8, auto-migration 7 to 8)

`fasts(day TEXT pk, startedAtEpochMs, endedAtEpochMs?, goalHours?, clientUpdatedAtEpochMs, deleted)`.
`FastDao`: `upsert`, `get(day)`, `observeLatest()`, `latest()`, `delete(day)`,
`deleteAll` (a `ClearableDao`, so a user change wipes it). Writes go through
`DiaryWriter` with an outbox row (`entity = "fast"`, `entityId = day`).

### Sync

- Client: `SyncEntity.Fast("fast")`, `FastRowDto(day, startedAtEpochMs,
  endedAtEpochMs?, goalHours?, clientUpdatedAtEpochMs, deleted)`, merged and
  rolled back like every other row. Requests send `accepts: ["fast"]`.
- Server, additive: `V13__fasts.sql` (`IF NOT EXISTS` everywhere, so it is
  idempotent), `fasts (user_id, day, pk (user_id, day), started_at, ended_at,
  goal_hours smallint check 1..16, client_updated_at, server_seq, deleted)`,
  `user_id` cascades from `auth.users`, RLS on, the `bump_server_seq` trigger.
  `SyncRequest.accepts` (default empty). `changes.fasts` is only in the
  response when the request accepts `fast`, so a 0.1 client gets the exact
  0.1 payload. A 0.1 client never sends `fast`. `DiaryRules.validateFast`:
  day in range, `startedAt` not in the future, `endedAt >= startedAt`, at most
  16 h, goal 1 to 16.

## Route
`FastingRoute()` in `:features:settings`, a `screen<>` in
`SettingsFeatureEntryPoint`. Opened from More's Fasting row and the Today card.

## State

```kotlin
data class FastingState(val loading: Boolean = true, val page: FastingPage? = null)

sealed interface FastingPage {
    data class Offered(
        val timerOn: Boolean,
        val showOnToday: Boolean,
        val plan: FastingPlan,
        val plans: List<FastingPlan>,     // FastingPlan.entries: 12:12, 14:10, 16:8
        val opens: LocalTime, val closes: LocalTime,
        val status: FastingStatus?,       // null while the timer is off
    ) : FastingPage
    data object NotInThisMode : FastingPage
}
```
Built by `FastingStateBuilder.build(FastingInputs(settings, released, latest, now, zone))`.

## Actions
- `InputsArrived(inputs)`: build; when Hidden, send `Close`. When the timer is on, ask the repository to start a due fast.
- `TimerToggled`: flip `settings.fasting`. Turning it off ends a running fast.
- `ShowOnTodayToggled`: flip `settings.fastToday` (only while the timer is on).
- `PlanPicked(plan)`: `settings.fastPlan` (only while the timer is on). A running fast keeps its goal.
- `EndFastTapped`: `FastingRepository.end()`, toast "Fast ended."
- `BackTapped`: `Close`.

## Events
`Close`, `ShowToast(Toast)`.

## Today card
`TodayState.fasting: FastingStatus?`, built by `TodayFastingCard.build(...)`:
present only when access is Offered, the timer and `fastToday` are on, and the
day shown is today. Collected and ticked separately from Today's inputs
(`FastingArrived(status)`), so the card is one field and one row in the list,
placed before the water card. Actions `FastingCardTapped` (opens
`FastingRoute`) and `EndFastTapped`.

## Edit Today's cards
A "Fasting" row with a switch for `settings.fastToday`, only when access is
Offered and the timer is on.

## More
`MoreRow.Fasting(on, plan)` in the extras card after Water, only when access
is Offered. Trailing `Off` or `On, 14:10`.

## Components
`HeroCard`, `HeroLabel`, `HeroBody`, `HeroWashButton` (`:libraries:ui`) for the
status card; `TodayCard`, `IconTile`, `PillAction` for the Today card;
`SettingsCard`, `AppSwitch`, `SegmentedControl`. New in `:libraries:diary:ui`:
`FastingStatusCard`, `FastingTodayCard` and `FastingCopy` (the status lines,
`13h 40m`). New icon `AppIcons.HourglassTop`.

## Visibility surfaces
None. Fasting shows clock times and a duration, no nutrition number. It only
exists in Full and Sprint, and the page renders nothing that the policy
resolves. `BlindModeLeakTest` gets a `fasting` surface (no calories in Full).

## Telemetry
`fast.started` (`plan`: `12:12` / `14:10` / `16:8`) when this device starts a
fast; `fast.ended` (`by`: `user` / `food` / `timer_off`). No durations, start or
end times: how long someone goes without eating is health data, and a
duration in Loki is one query away from a longest-fast record.

## Copy (`strings_fasting.xml`, `fasting_` prefix)
`Fasting`; `Eating window open` / `Until %1$s` / `Logging food ends a fast.`;
`Fasting` / `%1$s` / `Since %1$s` / `End fast`; `Fast ended` / `At %1$s` /
`The timer starts again at %1$s.`; `Not fasting` / `Until %1$s`; `Fasting
timer` / `Off unless you turn it on`; `Card on Today`; `Eating window` /
`%1$s to %2$s`; `Fasting isn't part of this mode` / `Long gaps between meals
tend to work against the goals of this mode. If regular meals are the aim,
meal reminders help with that instead.`; More `Off` / `On, %1$s`; Edit cards
`Fasting` / `The timer, while it is on`; toast `Fast ended.`

## Edge cases
- App restarts: the timer reads the stored start, so nothing is lost.
- Timer turned on after the window closed: the evening's fast starts from the
  close, unless food was logged since.
- Food logged after the close with the app closed: logging starts the due fast
  and ends it in the same breath, so the record reads 7:00 PM to 8:10 PM.
- Mode changed to one without fasting while a fast runs: nothing is written;
  the card and row disappear, and `endsBy` closes the fast on time.
- Older server without `fast`: it answers `unknown_entity`, the device rolls
  the fast back. The flag stays off until the server ships.
- Time zone change: the window follows the device's zone at the time.

## Tests
- `:libraries:diary`: `FastingSchedule` (window, due, status, cap), `FastingAccess` (every preset × the statement × the flag).
- `:libraries:diary:impl`: `FastingRepositoryImpl` (start due once, not after food, stamps, end, food ends it, timer off, outbox rows), the syncer merging and rolling back fasts, `DiaryRepositoryImpl` ending a fast on add.
- `:features:settings:impl`: `FastingStateBuilder`, `FastingViewModel` per action, More row on/off per mode, statement and flag.
- `:features:today:impl`: `TodayFastingCard` per rule, Today VM card actions, Edit Today's cards row.
- Server: `DiaryRulesTest` for fasts, `PostgresSyncRepositoryTest` (push, pull only when accepted, last write wins), `SyncRoutesTest` (0.1 request gets no `fasts` key; `accepts` gets them), `DatabaseSchemaTest` (table, RLS, cascade).
- Previews: no fast (timer off), running, finished, not in this mode, and dark; Today card running and window open; More with the Fasting row.
