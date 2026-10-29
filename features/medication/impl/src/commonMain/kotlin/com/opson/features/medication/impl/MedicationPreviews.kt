package com.opson.features.medication.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.medication.impl.dose.LogDoseSheetContent
import com.opson.features.medication.impl.dose.LogDoseState
import com.opson.features.medication.impl.today.MedicationCard
import com.opson.features.medication.impl.today.MedicationCardBuilder
import com.opson.features.medication.impl.today.MedicationTodayCard
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.system.AppTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.ui.tooling.preview.Preview

/** First visit: what this is, and Set up. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MedicationIntroPreview() = Page(MedicationFixtures.state(MedicationFixtures.inputs(plan = MedicationPlan())))

/** Set up tapped: which medication. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MedicationPickPreview() =
    Page(MedicationFixtures.state(MedicationFixtures.inputs(plan = MedicationPlan()), MedicationUi(settingUp = true)))

/** A weekly injection, tracked, with two side effects today. Tall, to show the whole page. */
@Preview(widthDp = 390, heightDp = 2400)
@Composable
private fun MedicationTrackingPreview() = Page(
    MedicationFixtures.state(MedicationFixtures.inputs(symptoms = mapOf(Symptom.Nausea to Severity.Mild, Symptom.Fatigue to Severity.Moderate))),
)

/** Severe vomiting: the line about getting care, under the side effects. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun MedicationCareLinePreview() = Page(
    MedicationFixtures.state(MedicationFixtures.inputs(symptoms = mapOf(Symptom.Vomiting to Severity.Severe))),
)

/** A daily pill: no sites card, and nothing logged yet. */
@Preview(widthDp = 390, heightDp = 1800)
@Composable
private fun MedicationPillPreview() = Page(
    MedicationFixtures.state(MedicationFixtures.inputs(plan = MedicationFixtures.plan(MedicationFixtures.rybelsus), doses = emptyList())),
)

/** Tracking stopped: the history stays, with the way back. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MedicationOffPreview() = Page(MedicationFixtures.state(MedicationFixtures.inputs(plan = MedicationFixtures.plan(on = false))))

@Preview(widthDp = 390, heightDp = 2400)
@Composable
private fun MedicationTrackingDarkPreview() = Page(MedicationFixtures.state(), dark = true)

@Composable
private fun Page(state: MedicationState, dark: Boolean = false) = MedicationPreview(dark) { MedicationScreen(state, onAction = {}) }

/** The dose sheet for a new dose: the current dose, now, and the suggested site. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun LogDoseNewPreview() = Sheet(MedicationFixtures.newDose())

/** Editing last week's dose: its own day, its site and its note, and Delete. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun LogDoseEditPreview() = Sheet(MedicationFixtures.editDose())

/** After saving a new amount: is it the current dose now? */
@Preview(widthDp = 390, heightDp = 300)
@Composable
private fun LogDoseAskCurrentPreview() = Sheet(MedicationFixtures.askCurrent())

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun LogDoseNewDarkPreview() = Sheet(MedicationFixtures.newDose(), dark = true)

@Composable
private fun Sheet(state: LogDoseState, dark: Boolean = false) = MedicationPreview(dark) { LogDoseSheetContent(state, onAction = {}) }

/** Today's card in each of its states, through the real builder. */
@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun MedicationTodayCardPreview() = Cards(dark = false)

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun MedicationTodayCardDarkPreview() = Cards(dark = true)

@Composable
private fun Cards(dark: Boolean) = MedicationPreview(dark) {
    val today = MedicationFixtures.today
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        cards(today).forEach { MedicationTodayCard(it, onAction = {}) }
    }
}

private fun cards(today: LocalDate): List<MedicationCard> {
    val plan = MedicationFixtures.plan()
    val pills = MedicationFixtures.plan(MedicationFixtures.rybelsus)
    fun card(plan: MedicationPlan, doses: List<MedicationDose>, day: LocalDate) =
        checkNotNull(MedicationCardBuilder.build(released = true, plan, doses, day, MedicationFixtures.zone))
    val loggedToday = MedicationFixtures.dose(0, 1.0, InjectionSite.ThighRight)
    return listOf(
        // Dose day, nothing logged.
        card(plan, MedicationFixtures.history, today),
        // Logged this evening.
        card(plan, listOf(loggedToday) + MedicationFixtures.history, today),
        // Between doses, the Monday after.
        card(plan, listOf(loggedToday) + MedicationFixtures.history, today.plus(DatePeriod(days = 4))),
        // Two days after a dose day with nothing logged.
        card(plan, MedicationFixtures.history, today.plus(DatePeriod(days = 2))),
        // A daily pill: today's dose, then logged.
        card(pills, emptyList(), today),
        card(pills, listOf(MedicationFixtures.dose(0, 7.0, null, hour = 8, minute = 2, medication = MedicationFixtures.rybelsus)), today),
        // When needed, last taken a while ago.
        card(
            MedicationFixtures.plan(MedicationFixtures.wegovy.copy(schedule = DoseSchedule.AsNeeded)),
            listOf(MedicationFixtures.dose(12, 1.0, InjectionSite.ThighLeft)),
            today,
        ),
    )
}
