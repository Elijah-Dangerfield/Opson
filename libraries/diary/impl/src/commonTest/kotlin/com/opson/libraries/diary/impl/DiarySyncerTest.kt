package com.opson.libraries.diary.impl

import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.impl.wire.CustomFoodRowDto
import com.opson.libraries.diary.impl.wire.FoodUnitDto
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsJson
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.SyncRejection
import com.opson.libraries.diary.db.SyncStateEntity
import com.opson.libraries.diary.impl.wire.ExerciseRowDto
import com.opson.libraries.diary.impl.wire.RecipeRowDto
import com.opson.libraries.diary.impl.wire.FastRowDto
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.SettingsRowDto
import com.opson.libraries.diary.impl.wire.SyncChangesDto
import com.opson.libraries.diary.impl.wire.SyncRejectionDto
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DiarySyncer] against a fake `/v1/sync`: cursor persistence and `hasMore`
 * paging, outbox paging and the drain discipline (stall and budget warnings),
 * the merge rule, rejection rollback, one sync at a time and never a stale
 * one, the stop on a user change, held-back future days, and pulled settings.
 *
 * Not covered: the wire itself (`:apps:integration` owns client and server
 * contract drift) and the debounce, which needs virtual time
 * ([DiarySyncerDebounceTest]).
 */
class DiarySyncerTest : CoroutineTest() {
    private val db = FakeDiaryDb()
    private val clock = FakeClock()
    private val user = FakeActiveUser(ActiveUser.SignedIn("user-a"))
    private val appScope by lazy { AppCoroutineScope(dispatchers) }
    private val api = FakeSyncApi { ackEverything(it) }
    private val settingsStore by lazy { SettingsStore(FakeCacheFactory(), db.outboxDao, db.transactor, clock, user, appScope, { null }) }
    private val syncer by lazy { DiarySyncer(api, db.tables, settingsStore, user, appScope, clock, FakeCacheFactory()) }
    private val repo = DiaryRepositoryImpl(
        db.tables,
        DiaryWriter(db.tables, clock, FakeScheduler()),
        FakeSettingsRepository(),
        FakeCalendar(),
        FakeFastEnder(),
    )
    private val logs = RecordingTree()
    private val today = LocalDate(2026, 10, 25)

    @BeforeTest
    fun plantLogs() = KLog.plant(logs)

    @AfterTest
    fun tearDown() {
        KLog.uproot(logs)
        appScope.cancel()
    }

    @Test
    fun anEmptyOutboxStillPostsOnceAndTheCursorPersists() = runUnitTest {
        assertTrue(syncer.sync().isSuccess)
        assertTrue(syncer.sync().isSuccess)

        assertEquals(listOf(0L, 1L), api.requests.map { it.cursor })
        assertTrue(api.requests.all { it.mutations.isEmpty() })
        assertEquals(2L, db.syncStateDao.get()?.cursor)
    }

    @Test
    fun followsHasMoreUntilThePullCatchesUp() = runUnitTest {
        api.respond = { request -> ackEverything(request).copy(hasMore = request.cursor < 2) }

        syncer.sync()

        assertEquals(listOf(0L, 1L, 2L), api.requests.map { it.cursor })
        assertEquals(3L, db.syncStateDao.get()?.cursor)
    }

    @Test
    fun acknowledgedMutationsLeaveTheOutbox() = runUnitTest {
        val items = repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice"), draft("Beans")), LogMethod.Search).written()

        syncer.sync()

        val sent = api.requests.single().mutations
        assertEquals(items.map { it.id }.toSet(), sent.map { it.row.getValue("id").toString().trim('"') }.toSet())
        assertTrue(sent.all { it.entity == "log_entry" })
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun theOutboxDrainsTwoHundredRowsAtATime() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, (1..250).map { draft("Item $it") }, LogMethod.Search).written()

        syncer.sync()

        assertEquals(listOf(200, 50), api.requests.map { it.mutations.size })
        assertTrue(db.outbox.rows.isEmpty())
        assertTrue(logs.entries.isEmpty())
    }

    @Test
    fun aPulledRowReplacesTheLocalRowWhenNothingIsWaiting() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search).written().single()
        syncer.sync()
        val fromOtherDevice = db.entries.state.value.getValue(item.id).toRow()
            .copy(name = "Brown rice", clientUpdatedAtEpochMs = clock.now.toEpochMilliseconds() + 60_000)
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(logEntries = listOf(fromOtherDevice))) }

        syncer.sync()

        assertEquals("Brown rice", repo.observeDay(today).first().allItems.single().name)
    }

    @Test
    fun aLocalChangeStillWaitingWinsOverAPulledRow() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search).written().single()
        api.respond = { request ->
            // The user moves the item while the request is in flight; the
            // response then echoes the version that went up.
            val echoed = LogEntryRowDto.serializer().let { s ->
                com.opson.libraries.diary.impl.wire.WireJson.decodeFromJsonElement(s, request.mutations.single().row)
            }
            repo.moveItem(item.id, MealSlot.Dinner)
            ackEverything(request).copy(changes = SyncChangesDto(logEntries = listOf(echoed)))
        }

        syncer.sync()

        assertEquals(MealSlot.Dinner, repo.observeDay(today).first().allItems.single().slot)
        assertEquals(1, db.outbox.rows.size, "the move still has to go up")
    }

    @Test
    fun aRefusedNewRowIsRemovedAndReported() = runUnitTest {
        val rejections = mutableListOf<SyncRejection>()
        backgroundScope.launch { syncer.rejections.toList(rejections) }
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search).written().single()
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertTrue(repo.observeDay(today).first().allItems.isEmpty())
        assertTrue(db.outbox.rows.isEmpty())
        assertEquals(listOf(SyncRejection(SyncEntity.LogEntry, item.id, "invalid_row")), rejections)
    }

    @Test
    fun aRefusedEditRollsBackToTheSyncedRow() = runUnitTest {
        val item = repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search).written().single()
        syncer.sync()
        repo.updateItem(item.copy(name = "x".repeat(10)))
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertEquals("Rice", repo.observeDay(today).first().allItems.single().name)
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun everyRequestSaysItAcceptsFastsExerciseAndRecipes() = runUnitTest {
        syncer.sync()

        assertEquals(listOf("fast", "exercise", "recipe"), api.requests.single().accepts)
    }

    @Test
    fun aPulledRecipeIsStored_andOneWithAnEditWaitingStillTakesTheServersSharing() = runUnitTest {
        val pulled = RecipeRowDto(
            id = "00000000-0000-4000-8000-0000000000a1",
            name = "Chili",
            servings = 6.0,
            clientUpdatedAtEpochMs = 1_000,
        )
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(recipes = listOf(pulled))) }
        syncer.sync()
        assertEquals(pulled.toEntity(), db.recipes.state.value[pulled.id])

        val local = pulled.copy(name = "Chili for six", clientUpdatedAtEpochMs = 5_000).toEntity()
        db.recipes.put(local.id, local)
        db.outboxDao.enqueue(SyncEntity.Recipe, local.id, "{}", base = null, nowEpochMs = 5_000)
        val shared = pulled.copy(publicCode = "abcd2345", publicState = "live", publicShowName = true)
        api.respond = { ackEverything(it).copy(applied = emptyList(), changes = SyncChangesDto(recipes = listOf(shared))) }
        syncer.sync()
        val stored = db.recipes.state.value.getValue(pulled.id)
        assertEquals("Chili for six", stored.name, "the waiting local edit wins")
        assertEquals("abcd2345", stored.publicCode, "the sharing is the server's")
        assertEquals("live", stored.publicState)
    }

    @Test
    fun aRefusedRecipeEditRollsBack_andKeepsItsSharing() = runUnitTest {
        val json = com.opson.libraries.diary.impl.wire.WireJson
        val shared = RecipeRowDto(
            id = "00000000-0000-4000-8000-0000000000a2",
            name = "Chili",
            servings = 6.0,
            clientUpdatedAtEpochMs = 1_000,
            publicCode = "abcd2345",
            publicState = "live",
            publicShowName = true,
        ).toEntity()
        val edited = shared.copy(name = "x".repeat(300), clientUpdatedAtEpochMs = 2_000)
        db.recipes.put(edited.id, edited)
        db.outboxDao.enqueue(
            SyncEntity.Recipe,
            edited.id,
            json.encodeToString(RecipeRowDto.serializer(), edited.toRow()),
            base = json.encodeToString(RecipeRowDto.serializer(), shared.toRow()),
            nowEpochMs = 2_000,
        )
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertEquals(shared, db.recipes.state.value[shared.id], "the edit is gone and the recipe is still shared")
    }

    @Test
    fun aPulledExerciseIsStoredUnlessALocalChangeIsWaiting() = runUnitTest {
        val pulled = ExerciseRowDto(
            id = "00000000-0000-4000-8000-000000000001",
            day = "2026-10-24",
            startedAtEpochMs = 1_000,
            activity = "walking",
            minutes = 30,
            kcal = 120,
            clientUpdatedAtEpochMs = 1_000,
        )
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(exercises = listOf(pulled))) }
        syncer.sync()
        assertEquals(pulled.toEntity(), db.exercises.state.value[pulled.id])

        val local = pulled.copy(minutes = 45, kcal = 180, clientUpdatedAtEpochMs = 5_000).toEntity()
        db.exercises.put(local.id, local)
        db.outboxDao.enqueue(SyncEntity.Exercise, local.id, "{}", base = null, nowEpochMs = 5_000)
        api.respond = { ackEverything(it).copy(applied = emptyList(), changes = SyncChangesDto(exercises = listOf(pulled))) }
        syncer.sync()
        assertEquals(local, db.exercises.state.value[pulled.id], "the waiting local edit wins")
    }

    @Test
    fun aPulledCustomFoodKeepsItsWeightAndSizes_unlessTheServerPredatesThem() = runUnitTest {
        val pulled = CustomFoodRowDto(
            id = "00000000-0000-4000-8000-0000000000c1",
            name = "Creamer",
            servingAmount = 1.0,
            servingUnit = "tbsp",
            kcal = 35.0,
            clientUpdatedAtEpochMs = 1_000,
            servingGrams = 15.0,
            sizes = listOf(FoodUnitDto("cup", 240.0)),
            micros = mapOf("sugars_g" to 5.0),
        )
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(customFoods = listOf(pulled))) }
        syncer.sync()
        val stored = db.customFoods.state.value.getValue(pulled.id).toDomain()
        assertEquals(15.0, stored.servingGrams)
        assertEquals(listOf(FoodUnit("cup", 240.0)), stored.sizes)
        assertEquals(mapOf("sugars_g" to 5.0), stored.micros)

        // A server from before 0.2 echoes the row back without them: what is here stays.
        val old = pulled.copy(name = "Oat creamer", clientUpdatedAtEpochMs = 2_000, servingGrams = null, sizes = null, micros = null)
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(customFoods = listOf(old))) }
        syncer.sync()
        val kept = db.customFoods.state.value.getValue(pulled.id).toDomain()
        assertEquals("Oat creamer", kept.name)
        assertEquals(stored.copy(name = "Oat creamer"), kept)
    }

    @Test
    fun aCustomFoodGoesUpWithItsSizesEvenWhenItHasNone() = runUnitTest {
        CustomFoodRepositoryImpl(db.tables, DiaryWriter(db.tables, clock, FakeScheduler()))
            .create(CustomFoodDraft(name = "Bar", servingAmount = 1.0, servingUnit = "bar"))
        val sent = db.outbox.rows.single().payload
        val row = com.opson.libraries.diary.impl.wire.WireJson.decodeFromString(CustomFoodRowDto.serializer(), sent)
        assertEquals(emptyList(), row.sizes, "an empty list, never absent: absent is how the server tells a 0.1 row")
    }

    @Test
    fun aRefusedExerciseEditRollsBack_andARefusedNewOneIsRemoved() = runUnitTest {
        val json = com.opson.libraries.diary.impl.wire.WireJson
        val synced = ExerciseRowDto(
            id = "00000000-0000-4000-8000-000000000002",
            day = "2026-10-25",
            startedAtEpochMs = 1_000,
            activity = "yoga",
            minutes = 60,
            kcal = 90,
            clientUpdatedAtEpochMs = 1_000,
        )
        val edited = synced.copy(minutes = 90, kcal = 135, clientUpdatedAtEpochMs = 2_000)
        val fresh = synced.copy(id = "00000000-0000-4000-8000-000000000003")
        db.exercises.put(edited.id, edited.toEntity())
        db.exercises.put(fresh.id, fresh.toEntity())
        db.outboxDao.enqueue(
            SyncEntity.Exercise,
            edited.id,
            json.encodeToString(ExerciseRowDto.serializer(), edited),
            base = json.encodeToString(ExerciseRowDto.serializer(), synced),
            nowEpochMs = 2_000,
            day = edited.day,
        )
        db.outboxDao.enqueue(SyncEntity.Exercise, fresh.id, json.encodeToString(ExerciseRowDto.serializer(), fresh), base = null, nowEpochMs = 2_000)
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertEquals(synced.toEntity(), db.exercises.state.value[synced.id])
        assertEquals(null, db.exercises.state.value[fresh.id])
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun aCursorFromABuildThatDidNotReadExercise_pullsAgainFromTheStart() = runUnitTest {
        // An older build pulled to 50 without `exercise`; the server moved its
        // cursor past the exercise another phone logged at 20.
        db.syncState.put(SyncStateEntity.SINGLETON_ID, SyncStateEntity(cursor = 50, lastSuccessAtEpochMs = 1))
        val walk = ExerciseRowDto(
            id = "00000000-0000-4000-8000-000000000009",
            day = "2026-10-24",
            startedAtEpochMs = 1_000,
            activity = "walking",
            minutes = 30,
            kcal = 120,
            clientUpdatedAtEpochMs = 1_000,
        )
        api.respond = { request ->
            val changes = if (request.cursor < 20) SyncChangesDto(exercises = listOf(walk)) else SyncChangesDto()
            ackEverything(request).copy(changes = changes, cursor = maxOf(request.cursor, 50) + 1)
        }

        syncer.sync()
        syncer.sync()

        assertEquals(listOf(0L, 51L), api.requests.map { it.cursor }, "once from the start, then on from where it got to")
        assertEquals(walk.toEntity(), db.exercises.state.value[walk.id])
    }

    @Test
    fun anEntityTheServerDoesNotKnowYet_keepsTheWriteAndSendsItLater() = runUnitTest {
        val rejections = mutableListOf<SyncRejection>()
        backgroundScope.launch { syncer.rejections.toList(rejections) }
        val json = com.opson.libraries.diary.impl.wire.WireJson
        val run = ExerciseRowDto(
            id = "00000000-0000-4000-8000-000000000004",
            day = "2026-10-25",
            startedAtEpochMs = 1_000,
            activity = "running",
            minutes = 20,
            kcal = 200,
            clientUpdatedAtEpochMs = 1_000,
        )
        db.exercises.put(run.id, run.toEntity())
        db.outboxDao.enqueue(SyncEntity.Exercise, run.id, json.encodeToString(ExerciseRowDto.serializer(), run), base = null, nowEpochMs = 1_000)
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search)
        // A server from before exercise: it applies the food and has no table for the run.
        api.respond = { request ->
            val (unknown, known) = request.mutations.partition { it.entity == "exercise" }
            ackEverything(request).copy(
                applied = known.map { it.id },
                rejected = unknown.map { SyncRejectionDto(it.id, "unknown_entity", "Unknown entity 'exercise'") },
            )
        }

        syncer.sync()

        assertEquals(run.toEntity(), db.exercises.state.value[run.id], "the run stays on the phone")
        assertEquals(listOf("exercise"), db.outbox.rows.map { it.entity }, "and its change stays queued")
        assertTrue(rejections.isEmpty(), "nothing to tell the person: nothing was lost")

        api.respond = { ackEverything(it) }
        syncer.sync()

        assertEquals(run.id, api.requests.last().mutations.single().row.getValue("id").toString().trim('"'))
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun anEntityTheServerDoesNotKnowYet_doesNotHoldUpThePull() = runUnitTest {
        val json = com.opson.libraries.diary.impl.wire.WireJson
        val swim = ExerciseRowDto(
            id = "00000000-0000-4000-8000-000000000005",
            day = "2026-10-25",
            startedAtEpochMs = 1_000,
            activity = "swimming",
            minutes = 40,
            kcal = 300,
            clientUpdatedAtEpochMs = 1_000,
        )
        db.exercises.put(swim.id, swim.toEntity())
        db.outboxDao.enqueue(SyncEntity.Exercise, swim.id, json.encodeToString(ExerciseRowDto.serializer(), swim), base = null, nowEpochMs = 1_000)
        api.respond = { request ->
            ackEverything(request).copy(
                applied = emptyList(),
                rejected = request.mutations.map { SyncRejectionDto(it.id, "unknown_entity", "Unknown entity 'exercise'") },
                hasMore = request.cursor < 3,
            )
        }

        syncer.sync()

        assertEquals(listOf(0L, 1L, 2L, 3L), api.requests.map { it.cursor }, "the pull goes on past the held row")
        assertEquals(1, db.outbox.rows.size)
    }

    @Test
    fun aPulledFastIsStoredUnlessALocalChangeIsWaiting() = runUnitTest {
        val pulled = FastRowDto(day = "2026-10-24", startedAtEpochMs = 1_000, goalHours = 14, clientUpdatedAtEpochMs = 1_000)
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(fasts = listOf(pulled))) }
        syncer.sync()
        assertEquals(pulled.toEntity(), db.fasts.state.value["2026-10-24"])

        val local = pulled.copy(endedAtEpochMs = 5_000, clientUpdatedAtEpochMs = 5_000).toEntity()
        db.fasts.put(local.day, local)
        db.outboxDao.enqueue(SyncEntity.Fast, local.day, "{}", base = null, nowEpochMs = 5_000)
        api.respond = { ackEverything(it).copy(applied = emptyList(), changes = SyncChangesDto(fasts = listOf(pulled))) }
        syncer.sync()
        assertEquals(local, db.fasts.state.value["2026-10-24"], "the waiting local end wins")
    }

    @Test
    fun aRefusedNewFastIsRemoved() = runUnitTest {
        val row = FastRowDto(day = "2026-10-25", startedAtEpochMs = 1_000, goalHours = 14, clientUpdatedAtEpochMs = 1_000)
        db.fasts.put(row.day, row.toEntity())
        db.outboxDao.enqueue(
            SyncEntity.Fast,
            row.day,
            com.opson.libraries.diary.impl.wire.WireJson.encodeToString(FastRowDto.serializer(), row),
            base = null,
            nowEpochMs = 1_000,
            day = row.day,
        )
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertTrue(db.fasts.rows.isEmpty())
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun aRefusedEndRollsTheFastBack() = runUnitTest {
        val synced = FastRowDto(day = "2026-10-25", startedAtEpochMs = 1_000, goalHours = 14, clientUpdatedAtEpochMs = 1_000)
        val ended = synced.copy(endedAtEpochMs = 2_000, clientUpdatedAtEpochMs = 2_000)
        db.fasts.put(ended.day, ended.toEntity())
        val json = com.opson.libraries.diary.impl.wire.WireJson
        db.outboxDao.enqueue(
            SyncEntity.Fast,
            ended.day,
            json.encodeToString(FastRowDto.serializer(), ended),
            base = json.encodeToString(FastRowDto.serializer(), synced),
            nowEpochMs = 2_000,
            day = ended.day,
        )
        api.respond = { request -> refuseEverything(request) }

        syncer.sync()

        assertEquals(synced.toEntity(), db.fasts.state.value["2026-10-25"])
    }

    @Test
    fun syncsAskedForDuringASyncShareOneMore() = runUnitTest {
        val gate = CompletableDeferred<Unit>()
        api.respond = { request ->
            gate.await()
            ackEverything(request)
        }

        val running = async { syncer.sync() }
        val asked = List(3) { async { syncer.sync() } }
        gate.complete(Unit)
        val results = awaitAll(running, *asked.toTypedArray())

        assertEquals(2, api.requests.size, "the sync already running, then one for everyone who asked during it")
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun aSyncAskedForWhileOneIsInFlight_pullsWhatChangedAfterTheAsk() = runUnitTest {
        // A background sync has its answer, made before another phone saved,
        // and is still finishing when pull-to-refresh asks for a sync.
        fun glasses(count: Int) = SettingsRowDto(
            settings = SettingsJson.encodeToJsonElement(Settings.serializer(), Settings(waterGlasses = count)).jsonObject,
            clientUpdatedAtEpochMs = 1,
        )
        var onServer = glasses(8)
        val answered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        api.respond = { request ->
            val response = ackEverything(request).copy(changes = SyncChangesDto(settings = onServer))
            if (api.requests.size == 1) {
                answered.complete(Unit)
                finish.await()
            }
            response
        }
        val background = async { syncer.sync() }
        answered.await()

        onServer = glasses(10)
        val refresh = async { syncer.syncNow() }
        finish.complete(Unit)
        awaitAll(background, refresh)

        assertEquals(10, settingsStore.settings.value.waterGlasses, "the refresh must pull after it was asked for")
    }

    @Test
    fun aPageTheServerNeverResolvesStallsTheDrainWithAWarning() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, listOf(draft("Rice")), LogMethod.Search).written()
        api.respond = { request -> ackEverything(request).copy(applied = emptyList()) }

        syncer.sync()

        assertEquals(1, api.requests.size, "a page that changed nothing is not sent again")
        assertTrue(logs.entries.any { it.message.orEmpty().contains("stalled") })
    }

    @Test
    fun aSyncThatSpendsItsBudgetWarnsWithTheRowsLeft() = runUnitTest {
        repo.addItems(today, MealSlot.Lunch, (1..2_100).map { draft("Item $it") }, LogMethod.Search).written()

        syncer.sync()

        assertEquals(MAX_REQUESTS_PER_SYNC, api.requests.size)
        assertEquals(100, db.outbox.rows.size)
        assertTrue(logs.entries.any { it.message.orEmpty().contains("100 change(s) still queued") })
    }

    @Test
    fun aChangeToADayTooFarAheadWaitsUntilTheServerWillTakeIt() = runUnitTest {
        // One day past what the server takes, counted from the fake clock's 2026-10-25.
        val planned = LocalDate(2026, 10, 25).plus(SERVER_MAX_DAYS_AHEAD + 1, DateTimeUnit.DAY)
        repo.addItems(planned, MealSlot.Lunch, listOf(draft("Planned")), LogMethod.Search).written()

        syncer.sync()

        assertTrue(api.requests.single().mutations.isEmpty())
        assertEquals(1, db.outbox.rows.size)
        assertTrue(logs.entries.isEmpty(), "holding a planned day is expected, not a warning")

        clock.now = kotlin.time.Instant.parse("2026-10-26T12:00:00Z")
        syncer.sync()

        assertEquals(1, api.requests.last().mutations.size)
        assertTrue(db.outbox.rows.isEmpty())
    }

    @Test
    fun noSyncStartsForAUserWhoseWorkWasStopped() = runUnitTest {
        val gate = CompletableDeferred<Unit>()
        api.respond = { request ->
            gate.await()
            ackEverything(request)
        }
        val running = async { syncer.sync() }

        syncer.stopWorkFor("user-a")

        assertTrue(running.await().isFailure, "the stopped sync must not report success")
        syncer.sync()
        assertEquals(1, api.requests.size, "user-a's outbox must not go up under the next session")

        api.respond = { ackEverything(it) }
        user.user.value = ActiveUser.SignedIn("user-b")
        syncer.sync()
        assertEquals(2, api.requests.size)
    }

    @Test
    fun pulledSettingsReplaceTheDefaultsAndMakeThemKnown() = runUnitTest {
        assertFalse(settingsStore.isKnown.value, "signed in, but this user's settings have not arrived")
        val blind = Settings(numbers = NumbersSettings(preset = PresetId.NoNumbers))
        val row = SettingsRowDto(
            settings = SettingsJson.encodeToJsonElement(Settings.serializer(), blind).jsonObject,
            clientUpdatedAtEpochMs = 1,
        )
        api.respond = { ackEverything(it).copy(changes = SyncChangesDto(settings = row)) }

        syncer.sync()

        assertEquals(PresetId.NoNumbers, settingsStore.settings.value.numbers.preset)
        assertTrue(settingsStore.isKnown.value)
    }

    @Test
    fun aSyncStartedBeforeTheSignedInUserReachedTheSyncer_stillMakesTheirSettingsKnown() = runUnitTest {
        // The sync coordinator and the active-user source each follow auth on
        // their own, so the first sync after sign-in can start while the
        // source still says Unresolved. By the time the pull lands it has
        // caught up, and the pull is that user's.
        user.user.value = ActiveUser.Unresolved
        val blind = Settings(numbers = NumbersSettings(preset = PresetId.NoNumbers))
        val row = SettingsRowDto(
            settings = SettingsJson.encodeToJsonElement(Settings.serializer(), blind).jsonObject,
            clientUpdatedAtEpochMs = 1,
        )
        api.respond = {
            user.user.value = ActiveUser.SignedIn("user-a")
            ackEverything(it).copy(changes = SyncChangesDto(settings = row))
        }

        syncer.sync()

        assertEquals(PresetId.NoNumbers, settingsStore.settings.value.numbers.preset)
        assertTrue(settingsStore.isKnown.value, "known without waiting for another sync")
    }

    private fun ackEverything(request: SyncRequestDto) = SyncResponseDto(
        applied = request.mutations.map { it.id },
        rejected = emptyList(),
        changes = SyncChangesDto(),
        cursor = request.cursor + 1,
        hasMore = false,
    )

    private fun refuseEverything(request: SyncRequestDto) = ackEverything(request).copy(
        applied = emptyList(),
        rejected = request.mutations.map { SyncRejectionDto(it.id, "invalid_row", "name is longer than 200 characters") },
    )

    private fun draft(name: String) = LogDraft(name = name, precision = Precision.Estimate, kcal = 100.0)
}
