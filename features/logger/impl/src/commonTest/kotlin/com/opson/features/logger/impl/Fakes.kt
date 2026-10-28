package com.opson.features.logger.impl

import com.opson.libraries.core.AppState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.foods.BarcodeMissReporter
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechToText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import com.opson.libraries.scanner.PhotoPickResult
import com.opson.libraries.scanner.MealPhotoPicker
import com.opson.libraries.foods.PhotoEstimate
import com.opson.libraries.foods.MealPhotoEstimator
import androidx.compose.runtime.Composable

/** Noon on the fixtures' today, before the 9 pm reveal. */
val NOON: Instant = Instant.parse("2026-10-25T12:00:00Z")

fun policy(preset: PresetId, at: Instant = NOON): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(at), TimeZone.UTC)

/**
 * An in-memory diary that remembers every write. Only what the logger reads
 * and writes does anything; the rest is inert.
 */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository {
    private val daysState = MutableStateFlow(days.associateBy { it.date })
    val favorites = MutableStateFlow<List<LogItem>>(emptyList())
    private var nextId = 0
    val usual = MutableStateFlow<Map<MealSlot, List<LogItem>>>(emptyMap())
    val recent = MutableStateFlow<List<LogItem>>(emptyList())

    data class Added(val day: LocalDate, val slot: MealSlot, val items: List<LogDraft>, val method: LogMethod)

    val added = mutableListOf<Added>()
    val water = mutableListOf<Pair<LocalDate, Int>>()
    val copied = mutableListOf<Triple<LocalDate, LocalDate, MealSlot>>()
    val ateNoDetails = mutableListOf<Pair<LocalDate, MealSlot>>()
    val feelings = mutableListOf<Triple<LocalDate, MealSlot, MealFeeling?>>()

    override fun observeDay(date: LocalDate): Flow<Day> = daysState.map { it[date] ?: Day.empty(date) }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> =
        daysState.map { days -> days.values.filter { it.date in from..to }.sortedBy { it.date } }

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = flowOf(emptySet())

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> {
        added += Added(day, slot, items, method)
        val written = items.map { draft ->
            LogItem(
                id = "new-${nextId++}",
                day = day,
                slot = slot,
                loggedAt = NOON,
                name = draft.name,
                precision = draft.precision,
                method = draft.method ?: method,
                foodId = draft.foodId,
                customFoodId = draft.customFoodId,
                amountText = draft.amountText,
                quantity = draft.quantity,
                unit = draft.unit,
                kcal = draft.kcal,
            )
        }
        // Written to the day too, so a screen reading it sees what it just logged.
        daysState.value = daysState.value + (day to (daysState.value[day] ?: Day.empty(day)).let { d ->
            d.copy(itemsBySlot = d.itemsBySlot + (slot to d.items(slot) + written))
        })
        return written
    }

    val updated = mutableListOf<LogItem>()

    override suspend fun updateItem(item: LogItem) {
        updated += item
    }

    val removed = mutableListOf<String>()

    override suspend fun removeItem(id: String) {
        removed += id
        daysState.value = daysState.value.mapValues { (_, d) -> d.copy(itemsBySlot = d.itemsBySlot.mapValues { (_, items) -> items.filterNot { it.id == id } }) }
    }

    override suspend fun moveItem(id: String, slot: MealSlot) = Unit

    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> = emptyList()

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        copied += Triple(fromDay, toDay, slot)
        return daysState.value[fromDay]?.items(slot).orEmpty()
    }

    override suspend fun clearDay(day: LocalDate) = Unit

    override suspend fun addWater(day: LocalDate, ml: Int) {
        water += day to ml
    }

    override suspend fun removeWater(day: LocalDate, ml: Int) = Unit

    override suspend fun setWater(day: LocalDate, ml: Int) = Unit

    override suspend fun closeDay(day: LocalDate) = Unit

    override suspend fun reopenDay(day: LocalDate) = Unit

    override suspend fun setDayNote(day: LocalDate, note: String?) = Unit

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = Unit

    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) = Unit

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) {
        if (ate) ateNoDetails += day to slot
    }

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) {
        feelings += Triple(day, slot, feeling)
    }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = recent

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = usual.map { it[slot].orEmpty() }

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = favorites
}

class FakeCustomFoodRepository(foods: List<CustomFood> = emptyList()) : CustomFoodRepository {
    val foods = MutableStateFlow(foods)

    override fun observeAll(): Flow<List<CustomFood>> = foods

    override suspend fun get(id: String): CustomFood? = foods.value.firstOrNull { it.id == id }

    override suspend fun create(draft: CustomFoodDraft): CustomFood {
        val food = CustomFood(
            id = "made-${foods.value.size}",
            name = draft.name,
            servingAmount = draft.servingAmount,
            servingUnit = draft.servingUnit,
            brand = draft.brand,
            kcal = draft.kcal,
            protein = draft.protein,
            carbs = draft.carbs,
            fat = draft.fat,
            fiber = draft.fiber,
        )
        foods.value = foods.value + food
        return food
    }

    override suspend fun update(food: CustomFood) = Unit

    override suspend fun delete(id: String) = Unit
}

class FakeSavedMealRepository(meals: List<SavedMeal> = emptyList()) : SavedMealRepository {
    val meals = MutableStateFlow(meals)

    override fun observeAll(): Flow<List<SavedMeal>> = meals

    override suspend fun get(id: String): SavedMeal? = meals.value.firstOrNull { it.id == id }

    override suspend fun create(name: String, usualSlot: MealSlot?, items: List<LogDraft>): SavedMeal =
        error("The logger never saves a meal")

    override suspend fun update(meal: SavedMeal) = Unit

    override suspend fun delete(id: String) = Unit
}

/** A catalog that answers from [foods], or fails when [online] is false, and records every search. */
class FakeFoodCatalog(
    var foods: List<Food> = emptyList(),
    var online: Boolean = true,
    var describeResult: Catching<List<ParsedItem>> = Catching.success(emptyList()),
) : FoodCatalog {
    val searches = mutableListOf<String>()
    val marked = mutableListOf<Food>()

    /** Every text sent to be read, typed or spoken. */
    val described = mutableListOf<String>()

    /** The filter each search was asked with, in order. */
    val filters = mutableListOf<FoodSearchFilter>()

    /** What [searchOptions] answers. */
    var options: Catching<SearchOptions> = Catching.success(SearchOptions(openFoodFacts = false))
    var optionsAsked = 0

    /** What [observeRecent] holds: catalog foods the device knows. */
    val recentFoods = MutableStateFlow<List<Food>>(emptyList())

    override suspend fun search(query: String, limit: Int, filter: FoodSearchFilter): Catching<FoodSearchResult> {
        searches += query
        filters += filter
        val hits = foods.filter { it.name.contains(query, ignoreCase = true) }
        return Catching.success(FoodSearchResult(query = query, foods = hits, fromCache = !online))
    }

    /** What [recentSearch] answers, by query: searches the catalog remembers. */
    val remembered = mutableMapOf<String, FoodSearchResult>()

    override fun recentSearch(query: String, limit: Int, filter: FoodSearchFilter): FoodSearchResult? = remembered[query]

    override suspend fun searchOptions(): Catching<SearchOptions> {
        optionsAsked++
        return options
    }

    override suspend fun detail(id: String): Catching<Food> =
        foods.firstOrNull { it.id == id }?.let { Catching.success(it) } ?: Catching.failure(IllegalStateException("offline"))

    /** What [barcode] answers; null is a miss. */
    var barcodeResult: Catching<Food?> = Catching.success(null)

    /** How long [barcode] takes, in virtual time: a first lookup at an outside source is slow. */
    var barcodeDelay: Duration = Duration.ZERO
    val barcodes = mutableListOf<String>()

    override suspend fun barcode(code: String): Catching<Food?> {
        barcodes += code
        delay(barcodeDelay)
        return barcodeResult
    }

    override suspend fun describe(text: String): Catching<List<ParsedItem>> {
        described += text
        return describeResult
    }

    override fun observeFavorites(): Flow<List<Food>> = flowOf(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Food>> = recentFoods

    override suspend fun markUsed(food: Food) {
        marked += food
    }

    override suspend fun setFavorite(food: Food, favorite: Boolean) = Unit
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

class FakePolicySource(preset: PresetId = PresetId.Full) : VisibilityPolicySource {
    override val policy = MutableStateFlow(policy(preset))
}

class FakeCalendar(private val today: LocalDate = LoggerFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

class FakeSelectedDay(day: LocalDate = LoggerFixtures.today) : SelectedDay {
    override val date = MutableStateFlow(day)

    override fun select(day: LocalDate) {
        date.value = day
    }
}

class FakeRecentLogEvents : RecentLogEvents {
    override val latest = MutableStateFlow<LoggedToMeal?>(null)
    val logged = mutableListOf<LoggedToMeal>()

    override fun logged(event: LoggedToMeal) {
        logged += event
        latest.value = event
    }

    override fun consumed(event: LoggedToMeal) {
        if (latest.value == event) latest.value = null
    }

    val undone = mutableListOf<LoggedToMeal>()

    override suspend fun undo(event: LoggedToMeal) {
        undone += event
        consumed(event)
    }
}

class FakeMealPicks : MealPicks {
    override val latest = MutableStateFlow<PickedForMeal?>(null)

    override fun picked(picks: PickedForMeal) {
        latest.value = picks
    }

    override fun consumed(picks: PickedForMeal) {
        if (latest.value == picks) latest.value = null
    }
}

class RecordingTelemetry : LoggerTelemetry {
    val events = mutableListOf<Pair<LogVia, Int>>()

    override fun methodPicked(via: LogVia, items: Int) {
        events += via to items
    }
}

/** `release.barcode` as the remote config would set it. */
/** `release.searchOptions` as the remote config would set it. */
fun searchOptionsFlag(on: Boolean): SearchOptionsEnabled = SearchOptionsEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("searchOptions" to on))
    },
)

class RecordingSearchOptionsTelemetry : SearchOptionsTelemetry {
    val events = mutableListOf<String>()

    override fun sortPicked(sort: SearchSort) {
        events += "sort:${sort.wire}"
    }

    override fun databasePicked(database: SearchDatabase) {
        events += "database:${database.wire}"
    }

    override fun tabsToggled(on: Boolean) {
        events += "tabs:$on"
    }

    override fun tabPicked(tab: SearchTab) {
        events += "tab:${tab.wire}"
    }
}

fun scanFlag(on: Boolean): BarcodeScanEnabled = BarcodeScanEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("barcode" to on))
    },
)

class FakeMissReporter : BarcodeMissReporter {
    val reported = mutableListOf<String>()

    override suspend fun report(code: String) {
        reported += code
    }
}

class FakeCameraPermissions(var permission: CameraPermission = CameraPermission.Granted) : CameraPermissions {
    var settingsOpened = 0

    override fun current(): CameraPermission = permission

    override fun openSettings() {
        settingsOpened++
    }
}

class RecordingBarcodeTelemetry : BarcodeTelemetry {
    val events = mutableListOf<String>()

    override fun scanStarted(asked: Boolean) {
        events += "scan_started asked=$asked"
    }

    override fun hit(source: FoodSource, slow: Boolean, typed: Boolean) {
        events += "hit ${source.wire} slow=$slow" + if (typed) " typed" else ""
    }

    override fun miss(typed: Boolean) {
        events += "miss" + if (typed) " typed" else ""
    }

    override fun cameraUnavailable() {
        events += "camera_unavailable"
    }

    override fun permissionDenied(canAskAgain: Boolean) {
        events += "permission_denied can_ask_again=$canAskAgain"
    }
}

class FakeAppState(offline: Boolean = false) : AppState {
    override val isOffline = MutableStateFlow(offline)
}

/** `release.photo` as the remote config would set it. */
fun photoFlag(on: Boolean): PhotoEstimateEnabled = PhotoEstimateEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("photo" to on))
    },
)

/** `release.submitFood` as the remote config would set it. */
fun submitFoodFlag(on: Boolean): SubmitFoodEnabled = SubmitFoodEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("submitFood" to on))
    },
)

/** Answers with [answer] and remembers what it was sent. */
class FakeMealPhotoEstimator(var answer: PhotoEstimate = PhotoEstimate.Items(PhotoFixtures.items)) : MealPhotoEstimator {
    val sent = mutableListOf<Pair<ByteArray, String?>>()

    override suspend fun estimate(jpeg: ByteArray, note: String?): PhotoEstimate {
        sent += jpeg to note
        return answer
    }
}

class FakeMealPhotoPicker(override val canTakePhoto: Boolean = true) : MealPhotoPicker {
    @Composable
    override fun rememberCamera(onResult: (PhotoPickResult) -> Unit): () -> Unit = {}

    @Composable
    override fun rememberLibrary(onResult: (PhotoPickResult) -> Unit): () -> Unit = {}
}

class RecordingPhotoTelemetry : PhotoTelemetry {
    val events = mutableListOf<String>()

    override fun started(fromCamera: Boolean) {
        events += "started ${if (fromCamera) "camera" else "library"}"
    }

    override fun estimated(items: Int, matched: Int) {
        events += "estimated items=$items matched=$matched"
    }

    override fun added(items: Int) {
        events += "added items=$items"
    }

    override fun failed(reason: PhotoFailure) {
        events += "failed ${reason.wire}"
    }
}

/** `release.voice` as the remote config would set it. */
fun voiceFlag(on: Boolean): VoiceLogEnabled = VoiceLogEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("voice" to on))
    },
)

/**
 * A recognizer the test speaks into: [say] delivers what it heard to the
 * running session. [listening] is true while a session is open and nobody has
 * cancelled it.
 */
class FakeSpeechToText(
    var availability: SpeechAvailability = SpeechAvailability.Ready(onDevice = true),
    var permission: MicPermission = MicPermission.Granted,
) : SpeechToText {
    private var session: Channel<Heard>? = null
    var sessions = 0
    var finished = 0
    var settingsOpened = 0
    val listening: Boolean get() = session != null

    /** Thrown from the session as it opens, as a platform failure no [Heard.Failed] describes. */
    var failure: Throwable? = null

    override fun availability(): SpeechAvailability = availability

    override fun permission(): MicPermission = permission

    override fun listen(): Flow<Heard> = flow {
        val channel = Channel<Heard>(Channel.UNLIMITED)
        session = channel
        sessions++
        failure?.let { throw it }
        try {
            for (heard in channel) {
                emit(heard)
                if (heard !is Heard.Partial) break
            }
        } finally {
            session = null
        }
    }

    fun say(heard: Heard) {
        checkNotNull(session) { "nothing is listening" }.trySend(heard)
    }

    override fun finishListening() {
        finished++
    }

    override fun openSettings() {
        settingsOpened++
    }
}

class RecordingVoiceTelemetry : VoiceTelemetry {
    val events = mutableListOf<String>()

    override fun started(onDevice: Boolean, asked: Boolean) {
        events += "started on_device=$onDevice asked=$asked"
    }

    override fun transcribed(words: WordBucket) {
        events += "transcribed words=${words.wire}"
    }

    override fun permissionDenied(canAskAgain: Boolean) {
        events += "permission_denied can_ask_again=$canAskAgain"
    }

    override fun fallback(reason: VoiceFallback) {
        events += "fallback ${reason.wire}"
    }
}

fun fastingFlag(on: Boolean): FastingRelease = FastingRelease { on }
