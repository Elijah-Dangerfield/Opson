# Findings: domain-diary-nutrition (round two)

`libraries/diary/` (api, impl, ui) and `libraries/nutrition/` (api, ui). Each
finding names the place, the rule from [PRACTICES.md](../PRACTICES.md), what
breaks, and its fate. Findings 1 to 5 were routed here by the cross-cutting
audits; 6 onward are this review's own. The unslop-code scanner found only long
identifiers (`clientUpdatedAtEpochMs` and test names), which match the wire
names on purpose. Round one's fixes still hold: `isKnownFor` before auth
answers, `Targets.today(goal = …)`, copies ending the fast before they write,
and the dead code it removed has not come back.

Fates: 10 fixed, 3 not a problem, none deferred within the slice. The fixes
need changes in other slices, listed at the end; one of them is required for
`:apps:integration` to compile.

## 1. A phone that updates to accept exercise never pulls the exercise logged before: fixed

- **Where:** `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/DiarySyncer.kt:237`
  (audit: contract). `sync_seq` is one sequence across every table, and the
  server moves the cursor past rows the request does not accept.
- **Rule:** §9 contract across versions; §10 two devices editing the same day.
- **Failure:** a phone on 0.1, or a 0.2 build from before exercise, syncs while
  another phone logs exercise. Its cursor passes those rows. After the update
  `SYNC_ACCEPTS` includes `exercise` but the stored cursor does not go back, so
  Burned and history on that phone stay short until someone edits the entries.
  The same happened to `fast`, and would to every entity added later.
- **Fix:** the syncer keeps the `accepts` its cursor was pulled with
  (`PulledWith`, a persistent cache, `DiarySyncer.kt:93`) and starts from 0 when
  this build reads an entity that cursor skipped (`startCursor`, `:303`). The
  set is written only after the cursor commits, so a crash in between costs one
  re-pull and can never skip rows. I used a cache rather than the audit's Room
  column because a column needs `AppDatabase` version 11, its schema JSON and an
  auto-migration in `libraries/storage/impl`, another slice, and a split change
  there would crash existing installs on an identity-hash mismatch. A missing
  cache entry (every phone updating to this build) means one full re-pull,
  which the merge rule makes safe. Test:
  `DiarySyncerTest.aCursorFromABuildThatDidNotReadExercise_pullsAgainFromTheStart`.
  Integration test written and run green locally, handed over below.
  Commit: `fix(diary): a cursor pulled without exercise pulls again from the start`.

## 2. An entity the server does not know yet was deleted from the phone: fixed

- **Where:** `DiarySyncer.kt:300` (audit: contract). `apply()` reverted every
  rejected row, whatever its code.
- **Rule:** §3 offline first, never a lost write; §9 server and client contract.
- **Failure:** prod runs a server from before V24 (exercises). If
  `release.exercise` is flipped before that deploy lands, every exercise logged
  shows, then disappears with "Couldn't save that". `unknown_entity` means the
  server is older than the app, not that the row is bad.
- **Fix:** `unknown_entity` rows are left in the outbox and on the phone, no
  rejection is emitted, and that entity sits out the rest of the drain
  (`OutboxDao.pageExcept`, `DiaryDaos.kt:225`), so neither a long pull nor the
  rest of the outbox stalls behind it. It goes up on the first sync after the
  server knows it, and a warning names the entity held. Only `invalid_row`
  rolls back. Tests: `DiarySyncerTest.anEntityTheServerDoesNotKnowYet_keepsTheWriteAndSendsItLater`
  and `…_doesNotHoldUpThePull`. Integration test written and run green locally,
  handed over below. Commit: `fix(diary): a server that does not know an entity yet keeps the write`.

## 3. A first pull longer than one sync left the settings unknown for hours: fixed

- **Where:** `DiarySyncer.kt:261` (audit: journeys).
- **Rule:** §10 sync to a second device; visibility's safe default.
- **Failure:** a long-time user signs in on a new phone. The pull needs more
  than ten pages, the sync ends `BudgetSpent` with the pull incomplete, and
  `markPulled` never runs. Today shows No numbers to a Full numbers user and
  settings edits are held, until the next foreground or reconnect.
- **Fix:** a sync that spent its budget mid-pull, and moved the cursor, asks
  for the next one (`DiarySyncer.kt:298`). The cursor check stops a server that
  says `hasMore` forever from looping the phone. Test:
  `DiarySyncerDebounceTest.aPullLongerThanOneSyncCarriesOnWithoutANewTrigger`.
  Commit: `fix(diary): a first pull longer than one sync carries on by itself`.

## 4. Meal card food rows kept their swipe state by position: fixed

- **Where:** `libraries/diary/ui/src/commonMain/kotlin/com/opson/libraries/diary/ui/TodayCards.kt:281`
  (audit: compose-perf).
- **Rule:** §4 stable keys for positional state in a list that changes.
- **Failure:** remove food A (Undo showing), swipe food B open, tap Undo. A
  comes back above B and inherits B's slot, drawn open beside Delete while B
  animates open one row down. A pull landing above an open row does the same.
- **Fix:** `key(item.key) { itemRow(item) }`. The module had no composition
  tests, so this adds the Robolectric tier as `libraries/ui` has it
  (`libraries/diary/ui/build.gradle.kts`). Test:
  `TodayMealCardTest.aFoodPutBackAboveAnotherDoesNotTakeItsRowState`, which
  fails without the key. Commit: `fix(diary-ui): a meal card keys its food rows by entry`.

## 5. Five composables nothing draws: fixed

- **Where:** `FavoriteFoodTile.kt`, `DayTotals.kt` (`NutrientTotalColumn`,
  `MealCheckChip`), `NutrientTile.kt`, `libraries/nutrition/ui/.../NutrientRingLabel.kt`
  (audit: truth).
- **Rule:** §1 no dead code.
- **Failure:** referenced only by their own previews since the logger rebuild,
  so the design sync kept sending Claude Design components the app never draws.
- **Fix:** deleted with their previews. With `NutrientRingLabel` gone, the
  Balance ring (`balanceRingValue`, the formatter's `ringBalance`) and the
  summary ring modes (`SummaryRingMode`, `availableRingModes`, `nextIn`,
  `orTargetIn`) had no reader left and went with their tests. `ringValue` stays:
  the home screen widget draws its ring from it (my first pass removed it too
  and broke `:libraries:widgets`; the follow-up commit restores it). The two
  strings only the meal chip read are gone from `strings_diary.xml`. The design
  sync manifest is generated from the previews, so the next sync picks this up
  with no file to change. Commits: `refactor(diary-ui): drop composables nothing draws`,
  `refactor(nutrition): keep the ring label the widget reads`,
  `refactor(diary-ui): tidy what the deleted composables left behind`.
  The docs that still list them are another slice's, below.

## 6. Undo dropped a basket one food at a time: fixed

- **Where:** `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/RecentLogEventsImpl.kt:30`.
- **Rule:** §4 no needless recomposition; §3 writes that belong together land together.
- **Failure:** Undo after adding three foods ran three transactions, so Today
  re-read the day three times and the foods vanished one by one; a process
  death in between left the undo half done.
- **Fix:** one `DiaryItemEditor.removeItems` call. Test:
  `RecentLogEventsTest.undoRemovesTheWholeBasketInOneWrite`. Commit:
  `perf(diary): undoing an add removes the basket in one write`.

## 7. The fake database did not roll exercises back: fixed

- **Where:** `libraries/diary/impl/src/commonTest/kotlin/com/opson/libraries/diary/impl/Fakes.kt:114`
  (`snapshot()` listed every table but `exercises`).
- **Rule:** §7 tests fail when the behaviour breaks.
- **Failure:** `ExerciseRepositoryImpl` writing its row outside the outbox's
  transaction would have passed every test.
- **Fix:** the snapshot includes exercises.
  `ExerciseRepositoryImplTest.add_thatCannotQueueLeavesNoEntryBehind` now
  proves the row and its change land together. Commit:
  `test(diary): the fake database rolls exercises back with the rest`.

## 8. The outbox KDoc gave the wrong limits: fixed

- **Where:** `libraries/diary/src/commonMain/kotlin/com/opson/libraries/diary/db/DiaryEntities.kt:161`.
- **Rule:** §1 stale comments are bugs.
- **Failure:** it said the server refuses days more than two ahead and Today
  plans six ahead. The server allows eight (`DiaryRules.MAX_DAYS_AHEAD`) and
  Today plans one.
- **Fix:** rewritten against `SERVER_MAX_DAYS_AHEAD`. Commit:
  `docs(diary): the outbox and ringMode say what is true now`.

## 9. `Settings.ringMode` described a ring that never read it: fixed (comment)

- **Where:** `libraries/diary/src/commonMain/kotlin/com/opson/libraries/diary/Settings.kt:64`, `:251`.
- **Rule:** §1 stale comments; no dead code.
- **Failure:** "The third ring on Today's summary card". No build has ever read
  it (`git log -S ringMode -- features` is empty).
- **Fix:** the KDoc says it is never read and kept so old JSON round-trips, as
  0.1's `exercise` key does. Removing the key is safe on the wire (the server
  merges shallowly), but it is a settings-shape change with nothing to gain
  now. Same commit as 8.

## 10. `DiaryItemRowDefaults.CardPadding` and a public `foodTone`: fixed

- **Where:** `DiaryItemRow.kt:96`, `FoodIcon.kt:75`, `libraries/nutrition/build.gradle.kts:13`.
- **Rule:** §1 no dead code; stale comments.
- **Failure:** `CardPadding` had no caller and the KDoc claimed Today's card
  passed it. `foodTone` was public only for the deleted favorites tile. The
  build file gave `SummaryRingMode` as a reason for its API dependency.
- **Fix:** removed, made private, and reworded. Commit:
  `refactor(diary-ui): tidy what the deleted composables left behind`.

## 11. `setAteNoDetails` tells the fast ender after the write: not a problem

`DiaryRepositoryImpl.kt:218` runs `foodLoggedOn` after the write, unlike the
copies round one moved. The due fast reads only `log_entries`
(`FastingRepositoryImpl.kt:98`), and "ate, no details" lives on the day record,
so the order cannot hide the fast.

## 12. The Week surface shows a daily Later number before the reveal time: not a problem (product note)

`VisibilityPolicy.isRevealed` (`VisibilityPolicy.kt:48`) returns true on
`Surface.Week` for daily Later, today included, and
`VisibilityPolicyRevealTest.delayed_ignoresTheWeekEndOnTheWeekView` pins it.
Deliberate; whether it should be is in the product notes.

## 13. Sync, merge and visibility rules from round one: not a problem

Re-read with the new code in place: an outbox row held for `unknown_entity`
still keeps its pulled twin from overwriting it (the merge skips pending ids),
a re-pull from 0 cannot overwrite a waiting local change, `Catching` still
rethrows cancellation, and every number in the diary and nutrition UI still
renders through a tagged `NutrientText`, `NutrientBar`, `NutrientFigure`,
`NutrientPhrase` or `MacroLine`.

## Deferred (cross-slice)

1. **`apps/integration/src/androidUnitTest/kotlin/com/opson/integration/helpers/TestClient.kt:226`**
   (test-apps): `DiarySyncer(syncApi, tables, settingsStore, activeUser, appScope, clock)`
   becomes `DiarySyncer(syncApi, tables, settingsStore, activeUser, appScope, clock, cacheFactory)`.
   Required: `DiarySyncer` takes a `CacheFactory` now (last parameter, for
   finding 1). DI supplies it in the app; `TestClient` is the one hand-built
   instance, and `:apps:integration` does not compile on this branch until
   this lands.
2. **`apps/integration/.../exercise/ExerciseSyncTest.kt`** and
   **`apps/integration/.../errors/ErrorSurfacesTest.kt`** (test-apps): add
   `aPhoneThatUpdatesToAcceptExercise_pullsExerciseLoggedBeforeTheUpdate` and
   `sync_anEntityTheServerDoesNotKnowYet_keepsTheWrite`. Both ran green here
   against the real server (`ExerciseSyncTest` 4/4, `ErrorSurfacesTest` 8/8,
   `FastingSyncTest` 1/1). The patch is in the appendix.
3. **`docs/plans/0.2/release-checklist.md:250` and `:256`** (scripts-ops-ci or
   whoever owns the plans): the second item ("A phone that synced on a build
   without exercise…") is fixed by finding 1, so delete it. The first stays
   (deploy V24 before the flag), but its reason changes: "A server without it
   cannot store exercise, so entries stay queued on the phone until it can."
4. **Docs that list the deleted composables** (finding 5): remove
   `FavoriteFoodTile`, `NutrientTotalColumn`, `MealCheckChip` and `NutrientTile`
   from `docs/system-design.md:89` and `docs/plans/screens/shared.md:124-126`;
   remove `NutrientRingLabel` from `docs/system-design.md:86`,
   `docs/plans/screens/shared.md:111`, `docs/plans/screens/BUILDER-BRIEF.md:9`,
   `docs/practices/review-checklist.md:52` and
   `docs/plans/screens/blind-leak-test.md:94`.

## Product notes

- **Later shows today's total on the week view.** Someone on daily Later who
  opens Trends at noon sees the running total that Today keeps shut until 9 pm
  (finding 12). It is tested as intended, but it undoes the point of Later for
  anyone who checks Trends. Fairly sure it will surprise people.
- **"Not fasting" reads oddly.** After food logged past the window close, the
  status card says "Not fasting / Until 7:00 PM / Logging food ends a fast."
  The time is tomorrow's start and the body describes a fast that is not
  running. "The timer starts again at 7:00 PM." (already a string) fits better.
  Fairly sure.
- **Logging yesterday's food still ends today's fast.** Round one's note stands:
  back-filling last night's snack at 8 am ends the morning's fast. Sure it is
  still the behaviour; how much it matters depends on how often fasters back-fill.
- **Unread settings keys.** `ringMode`, `healthOpen` and `newFoodEvent` are
  synced and never read. Worth deciding whether they are coming or should go
  before more phones carry them. Sure about the facts, unsure it matters.

## Appendix: the integration tests (deferred 1 and 2)

```diff
--- a/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/helpers/TestClient.kt
+++ b/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/helpers/TestClient.kt
@@ -223,7 +223,7 @@ class TestClient(
-    val sync = DiarySyncer(syncApi, tables, settingsStore, activeUser, appScope, clock)
+    val sync = DiarySyncer(syncApi, tables, settingsStore, activeUser, appScope, clock, cacheFactory)
--- a/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/exercise/ExerciseSyncTest.kt
+++ b/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/exercise/ExerciseSyncTest.kt
+import com.opson.libraries.diary.db.SyncStateEntity
+import kotlinx.serialization.json.long
@@ class ExerciseSyncTest : IntegrationTest() {
+    @Test
+    fun aPhoneThatUpdatesToAcceptExercise_pullsExerciseLoggedBeforeTheUpdate() = integration {
+        val user = randomUserId()
+        val tablet = client(user)
+        val day = tablet.today()
+        val at = LocalDateTime(day, LocalTime(7, 0)).toInstant(TimeZone.currentSystemDefault())
+        val entry = tablet.exercise.add(ExerciseDraft(day, at, "yoga", minutes = 60, kcal = 90))
+        tablet.syncNow().getOrThrow()
+
+        // The phone was on a build that did not accept exercise: its pull moved the cursor past the entry.
+        val old = Json.parseToJsonElement(rawSync(user, """{"cursor":0,"mutations":[],"accepts":["fast"]}""").bodyAsText()).jsonObject
+        val phone = client(user)
+        phone.tables.syncState.upsert(SyncStateEntity(cursor = old.getValue("cursor").jsonPrimitive.long, lastSuccessAtEpochMs = 1))
+
+        phone.syncNow().getOrThrow()
+
+        assertEquals(listOf(entry), phone.exercise.observeDay(day).first(), "the update pulls what the old cursor skipped")
+    }
--- a/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/errors/ErrorSurfacesTest.kt
+++ b/apps/integration/src/androidUnitTest/kotlin/com/opson/integration/errors/ErrorSurfacesTest.kt
+import com.opson.libraries.core.Catching
+import com.opson.libraries.diary.ExerciseDraft
+import com.opson.libraries.diary.impl.DiarySyncApi
+import com.opson.libraries.diary.impl.wire.SyncRequestDto
+import com.opson.libraries.diary.impl.wire.SyncResponseDto
+import kotlinx.coroutines.withTimeoutOrNull
@@ class ErrorSurfacesTest : IntegrationTest() {
+    /** A server deployed before an entity: the wire carries a name it has no table for. */
+    private class OlderServer(private val api: DiarySyncApi) : DiarySyncApi {
+        var knowsExercise = false
+
+        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> = api.sync(
+            if (knowsExercise) request
+            else request.copy(mutations = request.mutations.map { if (it.entity == "exercise") it.copy(entity = "exercise_v0") else it }),
+        )
+    }
+
+    @Test
+    fun sync_anEntityTheServerDoesNotKnowYet_keepsTheWrite() = integration {
+        val user = randomUserId()
+        var server: OlderServer? = null
+        val phone = client(user, wrapSync = { api -> OlderServer(api).also { server = it } })
+        val day = phone.today()
+        val run = phone.exercise.add(ExerciseDraft(day, kotlin.time.Clock.System.now(), "running_10", minutes = 20, kcal = 200))
+        val bread = phone.log("Bread", kcal = 80.0)
+
+        coroutineScope {
+            val rejection = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2_000) { phone.sync.rejections.first() } }
+            phone.syncNow().getOrThrow()
+            assertEquals(null, rejection.await(), "nothing to report: nothing was lost")
+        }
+        assertEquals(listOf(run), phone.exercise.observeDay(day).first(), "the run stays on the phone")
+        assertEquals(listOf(bread.id), phone.diary.observeDay(day).first().allItems.map { it.id }, "the food went up beside it")
+        assertEquals(1, phone.pendingChanges(), "the run is still queued")
+
+        checkNotNull(server).knowsExercise = true
+        phone.syncNow().getOrThrow()
+        assertEquals(0, phone.pendingChanges())
+        assertEquals(1L, this.server.countFor("exercises", user))
+    }
```
