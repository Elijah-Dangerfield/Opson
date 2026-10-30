package com.opson.integration.sync

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.nutrition.ActivityFrom
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.RestingFrom
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Goals across phones and versions** (docs/plans/goals-v2.md, "Storage and migration"). `goals`
 * is a new top-level key: it reaches a second phone whole, a save from a build that predates it
 * leaves it on the server, and that build's own edit to `weightGoal` is folded back into the goal.
 */
class GoalsSyncTest : IntegrationTest() {

    /** A build from before goals: `goals` neither goes up nor comes down. */
    private class PreGoalsWire(private val delegate: DiarySyncApi) : DiarySyncApi {
        override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> {
            val sent = request.copy(
                mutations = request.mutations.map { m ->
                    if (m.entity != "settings") m else m.copy(row = JsonObject(m.row + ("settings" to JsonObject(m.row.getValue("settings").jsonObject - GOALS))))
                },
            )
            return delegate.sync(sent).map { response ->
                val pulled = response.changes.settings ?: return@map response
                response.copy(changes = response.changes.copy(settings = pulled.copy(settings = JsonObject(pulled.settings - GOALS))))
            }
        }
    }

    private val inputs = BaselineInputs(
        restingFrom = RestingFrom.Health,
        restingKcal = 1800,
        activityFrom = ActivityFrom.Health,
        everyday = EverydayActivity.SomeWalking,
        activityKcal = 650,
        digesting = false,
        weightKg = 90.0,
        heightCm = 180.0,
        ageYears = 30,
        sex = BodySex.Male,
    )

    private val lose = Goal(
        id = Goal.WEIGHT_ID,
        type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 0.5, startKg = 90.0),
        startDate = LocalDate(2026, 10, 30),
        averaging = GoalAveraging.Weekly,
        toleranceKcal = 150,
        baseline = GoalBaselines.of(inputs),
    )
    private val track = Goal("track", GoalType.TrackEveryDay, LocalDate(2026, 10, 30))

    @Test
    fun goals_reachASecondPhone_whole() = integration {
        val user = randomUserId()
        val first = client(user)
        first.syncNow().getOrThrow()
        first.settings.isKnown.awaitState { it }
        first.settings.update { GoalBook.withGoal(GoalBook.applyTarget(it, lose), track) }
        first.syncNow().getOrThrow()
        val written = GoalBook.goals(first.settings.settings.value)

        val second = client(user)
        second.syncNow().getOrThrow()
        val settings = second.settings.settings.awaitState { it.goals != null }
        assertEquals(written, GoalBook.goals(settings))
        assertEquals(first.settings.settings.value.targets, settings.targets)
        assertEquals(GoalDirection.Lose, settings.weightGoal.direction, "older builds still read the weight goal")
    }

    @Test
    fun aSaveFromBeforeGoals_keepsThem_andItsWeightGoalEditIsFoldedIn() = integration {
        val user = randomUserId()
        val newPhone = client(user)
        newPhone.syncNow().getOrThrow()
        newPhone.settings.isKnown.awaitState { it }
        newPhone.settings.update { GoalBook.withGoal(GoalBook.applyTarget(it, lose), track) }
        newPhone.syncNow().getOrThrow()

        val oldPhone = client(user, wrapSync = ::PreGoalsWire)
        oldPhone.syncNow().getOrThrow()
        oldPhone.settings.isKnown.awaitState { it }
        // The old build changes the pace through its own goal flow, which writes weightGoal and the target.
        oldPhone.settings.update { it.copy(weightGoal = it.weightGoal.copy(kgPerWeek = 0.25, targetsKcal = 2300), targets = it.targets.withBand(2300, 2100, 2500)) }
        oldPhone.syncNow().getOrThrow()

        val stored = server.query(
            "SELECT jsonb_array_length(settings->'goals') AS goals FROM user_settings WHERE user_id = ?::uuid",
            user,
        ).single()
        assertEquals(2, stored["goals"], "the merge kept goals")

        newPhone.syncNow().getOrThrow()
        val settings = newPhone.settings.settings.awaitState { it.weightGoal.kgPerWeek == 0.25 }
        val goals = GoalBook.goals(settings)
        val weight = goals.first()
        assertEquals(0.25, (weight.type as GoalType.Weight).kgPerWeek)
        assertTrue(weight.drivesTarget, "the old build's target is the goal's")
        assertEquals(GoalAveraging.Weekly, weight.averaging, "what it didn't know is kept")
        assertEquals("track", goals[1].id)
    }

    private companion object {
        const val GOALS = "goals"
    }
}
