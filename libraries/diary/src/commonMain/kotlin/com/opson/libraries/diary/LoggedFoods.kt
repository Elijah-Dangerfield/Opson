package com.opson.libraries.diary

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * A food this person has logged: its [latest] entry, which carries the last amount and that
 * amount's nutrients, and how many [times] it was logged.
 */
data class LoggedFood(val latest: LogItem, val times: Int) {
    val lastLoggedAt: Instant get() = latest.loggedAt
}

/**
 * The long tail of foods someone logs, newest first: search ranks them and finds them offline,
 * and a + logs one again at its last amount through the diary, outbox and all. Read from the
 * diary itself, so it follows sync and a sign-out wipes it with the diary. Only entries logged
 * from a food count, and a food taken off Recent stays off here too until it is logged again.
 * Its own interface so the many `DiaryRepository` fakes stay as they are.
 */
interface LoggedFoods {
    /** Up to [limit] distinct foods, the most recently logged first. */
    fun observe(limit: Int = LONG_TAIL): Flow<List<LoggedFood>>

    companion object {
        /** How many distinct foods search keeps at hand. */
        const val LONG_TAIL = 500
    }
}
