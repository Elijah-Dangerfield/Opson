package com.opson.integration.exercise

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.rawSync
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.nutrition.ExerciseActivities
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Exercise sync (`release.exercise`).** Logged on one phone, an entry reaches the user's other
 * phone; an edit and a delete follow it. Exercises are an entity 0.1 and the first 0.2 builds do
 * not know: a request has to `accepts` `exercise` to get the `exercises` key, so their strict
 * decoders never meet one.
 */
class ExerciseSyncTest : IntegrationTest() {

    @Test
    fun anEntryLoggedOnOnePhone_isEditedAndDeletedOnTheOther() = integration {
        val user = randomUserId()
        val phone = client(user)
        val tablet = client(user)
        val day = phone.today()
        val zone = TimeZone.currentSystemDefault()
        val morning = LocalDateTime(day, LocalTime(7, 30)).toInstant(zone)

        val entry = phone.exercise.add(ExerciseDraft(day, morning, "running_10", minutes = 30, kcal = 291))
        phone.syncNow().getOrThrow()
        assertEquals(0, phone.pendingChanges(), "the server took it")
        assertEquals(1L, server.countFor("exercises", user))

        tablet.syncNow().getOrThrow()
        assertEquals(listOf(entry), tablet.exercise.observeDay(day).first())

        tablet.exercise.update(entry.id, ExerciseDraft(day, morning, "running_10", minutes = 45, kcal = 437, notOnWatch = true))
        tablet.syncNow().getOrThrow()
        phone.syncNow().getOrThrow()
        val edited = phone.exercise.observeDay(day).first().single()
        assertEquals(45, edited.minutes)
        assertEquals(437, edited.kcal)
        assertTrue(edited.notOnWatch)

        phone.exercise.delete(entry.id)
        phone.syncNow().getOrThrow()
        tablet.syncNow().getOrThrow()
        assertTrue(tablet.exercise.observeDay(day).first().isEmpty(), "the delete reached the tablet")
        assertEquals(listOf(true), server.query("SELECT deleted FROM exercises WHERE user_id = ?::uuid", user).map { it["deleted"] })
    }

    @Test
    fun otherWithItsNameAndTypedNumber_survivesTheRoundTrip() = integration {
        val user = randomUserId()
        val phone = client(user)
        val day = phone.today()
        val at = LocalDateTime(day, LocalTime(18, 0)).toInstant(TimeZone.currentSystemDefault())
        val entry = phone.exercise.add(ExerciseDraft(day, at, ExerciseActivities.OTHER, name = "Canoe polo", minutes = 60, kcal = 420, kcalTyped = true))
        phone.syncNow().getOrThrow()

        val fresh = client(user)
        fresh.syncNow().getOrThrow()

        assertEquals(listOf(entry), fresh.exercise.observeDay(day).first())
    }

    @Test
    fun onlyARequestThatAcceptsExercise_seesTheKey() = integration {
        val user = randomUserId()
        val phone = client(user)
        val day = phone.today()
        val at = LocalDateTime(day, LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault())
        phone.exercise.add(ExerciseDraft(day, at, "yoga", minutes = 60, kcal = 90))
        phone.syncNow().getOrThrow()

        val v01 = Json.parseToJsonElement(rawSync(user, """{"cursor":0,"mutations":[]}""").bodyAsText()).jsonObject
        assertFalse("exercises" in v01.getValue("changes").jsonObject, "a 0.1 request gets no exercises key")

        val fastsOnly = Json.parseToJsonElement(rawSync(user, """{"cursor":0,"mutations":[],"accepts":["fast"]}""").bodyAsText()).jsonObject
        assertFalse("exercises" in fastsOnly.getValue("changes").jsonObject, "an earlier 0.2 build asked for fasts only")

        val now = Json.parseToJsonElement(rawSync(user, """{"cursor":0,"mutations":[],"accepts":["fast","exercise"]}""").bodyAsText()).jsonObject
        val exercises = now.getValue("changes").jsonObject.getValue("exercises").jsonArray
        assertEquals(listOf("yoga"), exercises.map { it.jsonObject.getValue("activity").jsonPrimitive.content })
    }

    /**
     * A phone on a build that did not accept `exercise` pulls past the exercise rows: the server
     * leaves them out and the cursor moves on. When the phone updates, the cursor it kept is
     * already past them, so the update has to pull them some other way.
     */
    @Ignore // TODO(review): the client never re-pulls an entity it starts to accept; deferred to libraries/diary/impl, see docs/review/findings/test-apps.md
    @Test
    fun aPhoneThatUpdatesToAcceptExercise_pullsExerciseLoggedBeforeTheUpdate() = integration {
        val user = randomUserId()
        val watch = client(user)
        val day = watch.today()
        val at = LocalDateTime(day, LocalTime(7, 0)).toInstant(TimeZone.currentSystemDefault())
        val run = watch.exercise.add(ExerciseDraft(day, at, "running_10", minutes = 30, kcal = 291))
        watch.syncNow().getOrThrow()
        // Anything synced after the run, so the earlier build's cursor moves past it.
        watch.log("Porridge", kcal = 300.0)
        watch.syncNow().getOrThrow()

        val build = EarlierBuild()
        val phone = client(user, wrapSync = build::wrap)
        phone.syncNow().getOrThrow()
        assertTrue(phone.exercise.observeDay(day).first().isEmpty(), "the earlier build never asked for exercise")

        build.acceptsExercise = true // the update
        phone.syncNow().getOrThrow()

        assertEquals(listOf(run), phone.exercise.observeDay(day).first())
    }

    /** The first 0.2 builds: `accepts` named fasts only, until the update this test makes. */
    private class EarlierBuild {
        @Volatile
        var acceptsExercise = false

        fun wrap(delegate: DiarySyncApi): DiarySyncApi = object : DiarySyncApi {
            override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> =
                delegate.sync(if (acceptsExercise) request else request.copy(accepts = request.accepts - "exercise"))
        }
    }
}
