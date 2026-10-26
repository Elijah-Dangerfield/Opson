package com.opson.libraries.diary

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The food diary. Reads are flows straight off the device database, so they
 * work offline and update the moment a write lands. Writes are local-first:
 * each one commits the row and its outbox mutation together and returns, and
 * sync carries it to the server later. None of them needs a connection.
 *
 * Deletes are tombstones, so they sync like any other change.
 */
interface DiaryRepository {
    /** One date, with every slot present. A date with nothing logged is [Day.empty]. */
    fun observeDay(date: LocalDate): Flow<Day>

    /** Every date from [from] to [to] inclusive, oldest first, empty days included. For Trends. */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>>

    /** Dates in [year]/[month] with anything logged, for the calendar dots. */
    fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>>

    /**
     * Logs a basket. Each draft becomes a [LogItem] in [slot] on [day], or in
     * Uncategorized when meal groups are off for that day. Returns what was
     * written, in order.
     */
    suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem>

    /** Saves an edited item. The id, day and logged time are the caller's to keep. */
    suspend fun updateItem(item: LogItem)

    suspend fun removeItem(id: String)

    suspend fun moveItem(id: String, slot: MealSlot)

    /**
     * Copies every item on [from] into [to], appended to what [to] already has
     * ("Copy previous day", "Copy this day to tomorrow"). New ids, method
     * [LogMethod.Copy]. Water, notes and feelings stay where they are.
     */
    suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem>

    /** Copies one slot's items into the same slot on [toDay] ("Copy yesterday's dinner"). */
    suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem>

    /**
     * Removes every item on [day] and its "ate, no details" marks, as the
     * prototype's "Clear this day". Water, the note, feelings and whether the
     * day is closed are left alone.
     */
    suspend fun clearDay(day: LocalDate)

    /** Adds [ml] of water to [day]. */
    suspend fun addWater(day: LocalDate, ml: Int)

    /** Takes [ml] off [day], never below zero. */
    suspend fun removeWater(day: LocalDate, ml: Int)

    suspend fun setWater(day: LocalDate, ml: Int)

    suspend fun closeDay(day: LocalDate)

    suspend fun reopenDay(day: LocalDate)

    /** A blank note clears it. */
    suspend fun setDayNote(day: LocalDate, note: String?)

    /** Marks [day] untracked (a holiday) or tracked again. Trends shows an untracked day as a gap. */
    suspend fun setUntracked(day: LocalDate, untracked: Boolean)

    /** Day close's "How did today feel?"; null clears it. */
    suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?)

    suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean)

    /** Null, or an empty feeling, clears it. */
    suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?)

    /** This day's meal groups; null goes back to following [Settings.mealGroups]. */
    suspend fun setMealGroups(day: LocalDate, on: Boolean?)

    /** The most recently logged distinct foods, newest first, for the logger's Recent row. */
    fun recentFoods(limit: Int = 6): Flow<List<LogItem>>

    /**
     * The foods most often logged in [slot] over the last [days] days, most
     * frequent first, each as its latest entry. The logger's "Usual at {meal}".
     */
    fun usualAt(slot: MealSlot, limit: Int = 4, days: Int = 28): Flow<List<LogItem>>

    /**
     * The foods most often logged in any slot over the last [days] days, most
     * frequent first, each as its latest entry. The logger's Favorites: the
     * handoff has no way to star a food, so the foods someone keeps coming
     * back to stand in for it.
     */
    fun frequentFoods(limit: Int = 5, days: Int = 30): Flow<List<LogItem>>
}

interface WeightRepository {
    /** Weigh-ins oldest first, from [since] when given. */
    fun observeWeighIns(since: Instant? = null): Flow<List<WeighIn>>

    /** [measuredAt] defaults to now. */
    suspend fun addWeighIn(kg: Double, measuredAt: Instant? = null): WeighIn

    suspend fun removeWeighIn(id: String)
}

interface CustomFoodRepository {
    /** The user's foods, by name. */
    fun observeAll(): Flow<List<CustomFood>>

    suspend fun get(id: String): CustomFood?

    suspend fun create(draft: CustomFoodDraft): CustomFood

    suspend fun update(food: CustomFood)

    suspend fun delete(id: String)
}

interface SavedMealRepository {
    /** Saved meals, by name. */
    fun observeAll(): Flow<List<SavedMeal>>

    suspend fun get(id: String): SavedMeal?

    suspend fun create(name: String, usualSlot: MealSlot?, items: List<LogDraft>): SavedMeal

    suspend fun update(meal: SavedMeal)

    suspend fun delete(id: String)
}

interface SettingsRepository {
    /**
     * The current settings. Holds the defaults until the stored copy has been
     * read; check [isKnown] before trusting the numbers settings.
     */
    val settings: StateFlow<Settings>

    /**
     * False from a user change until that user's first pull lands, and before
     * the stored copy has been read at all. While false, the numbers settings
     * are the defaults, not the user's, so anything that shows a number should
     * treat the user as Blind. A fresh install that has never signed in is
     * known as soon as its stored copy is read.
     */
    val isKnown: StateFlow<Boolean>

    fun observe(): Flow<Settings> = settings

    /**
     * Read-modify-write, atomic. Returns the new settings.
     *
     * `numbers.changedAt` is stamped here, and only when the preset, an
     * override or the pause setting actually changed; nothing else moves it.
     * Onboarding passes [stampNumbersChange] false so its first preset keeps
     * `changedAt` null and Modes can say "Set by you during setup."
     */
    suspend fun update(stampNumbersChange: Boolean = true, transform: (Settings) -> Settings): Settings
}

/**
 * The sync engine as screens see it. Sync itself runs on its own triggers
 * (sign-in, foreground, reconnect, and two seconds after a write).
 */
interface DiarySync {
    /**
     * Changes the server refused and the device rolled back. Rare: the client
     * validates first. A screen can say "Couldn't save that" and nothing more.
     */
    val rejections: Flow<SyncRejection>

    /** Push and pull now, for pull-to-refresh. Shares a sync already in flight. */
    suspend fun syncNow(): Result<Unit>
}

data class SyncRejection(
    val entity: SyncEntity,
    val entityId: String,
    /** `invalid_row` or `unknown_entity`. */
    val code: String,
)

/** What a mutation is about. [wire] is the `entity` string on `/v1/sync`. */
enum class SyncEntity(val wire: String) {
    LogEntry("log_entry"),
    DayRecord("day_record"),
    MealFeeling("meal_feeling"),
    WeighIn("weigh_in"),
    CustomFood("custom_food"),
    SavedMeal("saved_meal"),
    Settings("settings"),

    /** 0.2. A server that predates it refuses it as `unknown_entity`. */
    Fast("fast"),
    ;

    companion object {
        fun fromWire(value: String): SyncEntity? = entries.firstOrNull { it.wire == value }
    }
}
