package com.opson.libraries.reminders.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/** A lock screen answer always calls iOS's completion handler, once, whatever the work did. */
class BackgroundAnswerTest : CoroutineTest() {

    private var finished = 0

    @Test
    fun afterTheWork() = runUnitTest {
        val worked = CompletableDeferred<Unit>()
        answerInBackground(work = { worked.complete(Unit) }, done = { finished++ }).join()

        assertEquals(true, worked.isCompleted)
        assertEquals(1, finished)
    }

    @Test
    fun whenTheWorkFails() = runUnitTest {
        answerInBackground(work = { error("the database is locked") }, done = { finished++ }).join()

        assertEquals(1, finished)
    }

    @Test
    fun whenTheWorkIsCancelled() = runUnitTest {
        val job = answerInBackground(work = { awaitCancellation() }, done = { finished++ })
        runCurrent()

        job.cancelAndJoin()

        assertEquals(1, finished)
    }
}
