package com.opson.features.today.impl.burned

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.Settings
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
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/** Today's Burned section for previews and the leak test, built through [TodayBurnedStateBuilder]. */
object TodayBurnedFixtures {
    val day: LocalDate = LocalDate(2026, 10, 25)
    private val resting = RestingEnergy(1610, RestingFrom.Calculated)

    val entries: List<ExerciseEntry> = listOf(
        ExerciseEntry("run", day, Instant.parse("2026-10-25T07:10:00Z"), "running_10", minutes = 35, kcal = 340),
        ExerciseEntry("polo", day, Instant.parse("2026-10-25T17:30:00Z"), ExerciseActivities.OTHER, name = "Canoe polo", minutes = 60, kcal = 420, kcalTyped = true, notOnWatch = true),
    )

    /** No health app: resting, everyday activity and both entries. */
    fun manual(policy: VisibilityPolicy, entries: List<ExerciseEntry> = this.entries): TodayBurnedCard? = card(
        policy,
        burnedFor(resting, health = null, EverydayActivity.MostlySitting, ExerciseTotals(entries.sumOf { it.kcal }, 0), activityCounted = true),
        entries,
    )

    /** A watch counts the run; Canoe polo was marked as not recorded, so it still adds. */
    fun withHealth(policy: VisibilityPolicy): TodayBurnedCard? = card(
        policy,
        burnedFor(resting, HealthActivity(movementKcal = 380, workoutKcal = 330), EverydayActivity.MostlySitting, ExerciseTotals(760, 420), activityCounted = true),
        entries,
    )

    /** Resting energy not known yet: no total, and the way to fix that. */
    fun unknown(policy: VisibilityPolicy): TodayBurnedCard? = card(policy, burned = null, entries = emptyList())

    private fun card(policy: VisibilityPolicy, burned: Burned?, entries: List<ExerciseEntry>): TodayBurnedCard? =
        TodayBurnedStateBuilder.build(
            TodayBurnedInputs(day, Settings(), burned, entries, policy, released = true, timeZone = TimeZone.UTC),
        ).card
}

private val full = previewVisibilityPolicy(PresetId.Full)

@Composable
private fun Section(card: TodayBurnedCard?, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    card?.let { TodayBurnedSection(it, onAction = {}, modifier = Modifier.padding(12.dp)) }
}

@Preview(widthDp = 390)
@Composable
private fun TodayBurnedPreview() = Section(TodayBurnedFixtures.manual(full))

@Preview(widthDp = 390)
@Composable
private fun TodayBurnedDarkPreview() = Section(TodayBurnedFixtures.manual(full), dark = true)

@Preview(widthDp = 390)
@Composable
private fun TodayBurnedHealthPreview() = Section(TodayBurnedFixtures.withHealth(full))

@Preview(widthDp = 390)
@Composable
private fun TodayBurnedEmptyPreview() = Section(TodayBurnedFixtures.manual(full, entries = emptyList()))

@Preview(widthDp = 390)
@Composable
private fun TodayBurnedUnknownPreview() = Section(TodayBurnedFixtures.unknown(full))
