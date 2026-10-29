package com.opson.features.today.impl.activity

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.ExerciseTotals
import com.opson.libraries.nutrition.HealthActivity
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.burnedFor
import com.opson.libraries.nutrition.thermicEffectKcal
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/** Today's Activity card for previews and the leak test, built through [TodayActivityStateBuilder]. */
object TodayActivityFixtures {
    val day: LocalDate = LocalDate(2026, 10, 25)
    private val resting = RestingEnergy(1610, RestingFrom.Calculated)

    /** 1,245 kcal eaten: digesting it is 125. */
    private val food = thermicEffectKcal(1245.0)

    val entries: List<ExerciseEntry> = listOf(
        ExerciseEntry("run", day, Instant.parse("2026-10-25T07:10:00Z"), "running_10", minutes = 35, kcal = 340),
        ExerciseEntry("polo", day, Instant.parse("2026-10-25T17:30:00Z"), ExerciseActivities.OTHER, name = "Canoe polo", minutes = 60, kcal = 420, kcalTyped = true, notOnWatch = true),
    )

    /** No health app: resting, everyday activity and both entries. [withFood] counts the day's food being digested. */
    fun manual(policy: VisibilityPolicy, entries: List<ExerciseEntry> = this.entries, withFood: Boolean = false): TodayActivityCard? = card(
        policy,
        burnedFor(
            resting,
            health = null,
            EverydayActivity.MostlySitting,
            ExerciseTotals(entries.sumOf { it.kcal }, 0),
            activityCounted = true,
            foodKcal = food.takeIf { withFood },
        ),
        entries,
    )

    /** Apple Health counts the day's activity and the run; Canoe polo was marked as not recorded, so it still adds. */
    fun withHealth(policy: VisibilityPolicy, entries: List<ExerciseEntry> = this.entries, withFood: Boolean = false): TodayActivityCard? = card(
        policy,
        burnedFor(
            resting,
            HealthActivity(movementKcal = 380, workoutKcal = 330),
            EverydayActivity.MostlySitting,
            ExerciseTotals(entries.sumOf { it.kcal }, entries.filter { it.notOnWatch }.sumOf { it.kcal }),
            activityCounted = true,
            foodKcal = food.takeIf { withFood },
        ),
        entries,
        HealthSource.AppleHealth,
    )

    /** Resting energy not known yet: no total, and the way to fix that. */
    fun unknown(policy: VisibilityPolicy): TodayActivityCard? = card(policy, burned = null, entries = emptyList())

    private fun card(policy: VisibilityPolicy, burned: Burned?, entries: List<ExerciseEntry>, health: HealthSource? = null): TodayActivityCard? =
        TodayActivityStateBuilder.build(
            TodayActivityInputs(day, Settings(), burned, entries, policy, released = true, timeZone = TimeZone.UTC, healthSource = health),
        ).card
}

private val full = previewVisibilityPolicy(PresetId.Full)

@Composable
private fun Section(card: TodayActivityCard?, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    card?.let { TodayActivitySection(it, onAction = {}, modifier = Modifier.padding(12.dp)) }
}

/** No health app: resting, everyday activity and logged exercise add up to the total; the + logs more. */
@Preview(widthDp = 390)
@Composable
private fun TodayActivityPreview() = Section(TodayActivityFixtures.manual(full))

@Preview(widthDp = 390)
@Composable
private fun TodayActivityDarkPreview() = Section(TodayActivityFixtures.manual(full), dark = true)

/** From Apple Health: its active energy with the workouts in it; adding is for what the watch missed. */
@Preview(widthDp = 390)
@Composable
private fun TodayActivityHealthPreview() = Section(TodayActivityFixtures.withHealth(full))

/** From Apple Health with nothing logged by hand: the ordinary day with a watch. */
@Preview(widthDp = 390)
@Composable
private fun TodayActivityHealthNothingLoggedPreview() = Section(TodayActivityFixtures.withHealth(full, entries = emptyList()))

/** "Count digesting food" on: its own line in the sum. */
@Preview(widthDp = 390)
@Composable
private fun TodayActivityWithFoodPreview() = Section(TodayActivityFixtures.manual(full, withFood = true))

@Preview(widthDp = 390)
@Composable
private fun TodayActivityHealthWithFoodDarkPreview() = Section(TodayActivityFixtures.withHealth(full, withFood = true), dark = true)

@Preview(widthDp = 390)
@Composable
private fun TodayActivityEmptyPreview() = Section(TodayActivityFixtures.manual(full, entries = emptyList()))

@Preview(widthDp = 390)
@Composable
private fun TodayActivityUnknownPreview() = Section(TodayActivityFixtures.unknown(full))
