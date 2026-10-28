package com.opson.libraries.reminders.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [work] for a button answered from the lock screen ("Already ate", "In 30 min"), then
 * [done], whether the work finished, failed, was cancelled or ran past [ANSWER_BUDGET]. iOS keeps a
 * backgrounded app running only for a short while and only until its completion handler is called,
 * and an app that never calls it is one the system stops giving that time to. The budget is the
 * one Android's receivers get.
 */
internal fun CoroutineScope.answerInBackground(work: suspend () -> Unit, done: () -> Unit): Job = launch {
    try {
        Catching { withTimeoutOrNull(ANSWER_BUDGET) { work() } ?: error("Answering a reminder ran out of time") }
            .logOnFailure { "Answering a reminder failed" }
    } finally {
        done()
    }
}

internal val ANSWER_BUDGET = 8.seconds
