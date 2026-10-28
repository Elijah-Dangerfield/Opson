package com.opson.libraries.reminders.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [work] for a button answered from the lock screen ("Already ate", "In 30 min"), then
 * [done], whether the work finished, failed or was cancelled. iOS keeps a backgrounded app running
 * only until its completion handler is called, and an app that never calls it is one the system
 * stops giving that time to.
 */
internal fun CoroutineScope.answerInBackground(work: suspend () -> Unit, done: () -> Unit): Job = launch {
    try {
        Catching { work() }.logOnFailure { "Answering a reminder failed" }
    } finally {
        done()
    }
}
