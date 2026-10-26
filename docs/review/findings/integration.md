# Findings: end-to-end integration suite

Bugs and gaps the `:apps:integration` journeys turned up. Each names the place,
the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. A phone clock a day or more fast lost every write — fixed

- **Where:** `apps/server/src/main/kotlin/com/opson/server/domain/DiaryRules.kt:59`
  (before the fix: `notFuture` on `clientUpdatedAtEpochMs`, and at :81, :108,
  :121 on `loggedAt`, `startedAt`, `measuredAt`), meeting the client's rollback
  in `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/DiarySyncer.kt:340`.
- **Rule:** §3 "Offline first", §6 "Idempotent writes and retries … an explicit
  last-write-wins rule", §7 error surfaces "never … a lost write".
- **Failure:** the server refused any row stamped more than 24 hours past its
  own clock as `invalid_row`. A refused row is rolled back on the phone, and a
  new row rolls back to nothing. So on a phone whose clock was two days fast,
  every food logged appeared, synced, and vanished, with only a "Couldn't save
  that" to show for it. `ErrorSurfacesTest.sync_aPhoneClockDaysAhead_doesNotLoseTheWrite`
  reproduces it.
- **Fix:** `DiaryRules.clampClock` (`DiaryRules.kt:66`), called by
  `SyncRoutes.kt:65` before validation, stores any time more than the 24 hour
  allowance ahead as the server's now instead of refusing the row. The write is
  kept, and because its stamp is now the server's, it cannot outrank a later
  edit from a phone with a good clock (the same test checks that). The
  `notFuture` checks it replaces are gone; `DiaryRulesTest` covers the clamp.
- **Left as is:** a planned day more than eight days ahead *by the server's
  date* is still refused, so the same fast phone could lose an entry planned
  nine or ten days out. Rare enough (a wrong clock and planning that far) not to
  widen this change.

## Harness notes (not app bugs)

- `apps/integration` needs `unitTests.isReturnDefaultValues`: OkHttp detects
  android.jar, decides it is on Android and calls `android.util.Log`, which the
  stub jar throws from. The first test that built the server's Anthropic client
  before the app's own HTTP client tripped it.
- `android.database.SQLException` is shadowed in the test sources with a real
  exception. The stub drops its message, and with the stub `DiarySyncer.apply`
  failed with a bare `SQLException`; with a real exception class the same code
  passes. Not traced further: something under Room's transaction depends on the
  exception behaving like the framework's.
