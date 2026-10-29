package com.opson.features.medication.impl.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.medication.impl.clock
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.medication.inSentence
import com.opson.libraries.ui.components.PillAction
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RowIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_card_between
import opson.libraries.resources.generated.resources.medication_card_daily_due
import opson.libraries.resources.generated.resources.medication_card_due
import opson.libraries.resources.generated.resources.medication_card_due_site
import opson.libraries.resources.generated.resources.medication_card_last
import opson.libraries.resources.generated.resources.medication_card_log
import opson.libraries.resources.generated.resources.medication_card_logged
import opson.libraries.resources.generated.resources.medication_card_logged_site
import opson.libraries.resources.generated.resources.medication_card_next
import opson.libraries.resources.generated.resources.medication_card_none
import opson.libraries.resources.generated.resources.medication_card_not_logged
import opson.libraries.resources.generated.resources.medication_card_skipped
import opson.libraries.resources.generated.resources.medication_card_title
import org.jetbrains.compose.resources.stringResource

/**
 * Today's Medication card, in the shape of the Fasting card: an icon, "Medication" and one line,
 * with Log dose on the right when a dose is due. Logged, the card opens the dose; otherwise the
 * Medication page, where Log a dose comes first.
 */
@Composable
fun MedicationTodayCard(card: MedicationCard, onAction: (MedicationCardAction) -> Unit, modifier: Modifier = Modifier) {
    TodayCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        onClick = { onAction(MedicationCardAction.CardTapped) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            RowIcon(icon = AppIcons.Medication, color = AppTheme.colors.nutrients.fiber.content)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(Res.string.medication_card_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(text = card.line(), typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
            }
            // Between doses, Log dose is one tap further, on the Medication page the card opens.
            if (card is MedicationCard.Due || card == MedicationCard.DailyDue || card is MedicationCard.NotLogged) {
                PillAction(label = stringResource(Res.string.medication_card_log), onClick = { onAction(MedicationCardAction.LogTapped) })
            }
        }
        if (card is MedicationCard.NotLogged) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.align(Alignment.End)) {
                PillAction(label = stringResource(Res.string.medication_card_skipped), onClick = { onAction(MedicationCardAction.SkippedTapped) })
            }
        }
    }
}

@Composable
private fun MedicationCard.line(): String = when (this) {
    is MedicationCard.Due -> site?.let { stringResource(Res.string.medication_card_due_site, stringResource(it.inSentence)) }
        ?: stringResource(Res.string.medication_card_due)
    MedicationCard.DailyDue -> stringResource(Res.string.medication_card_daily_due)
    is MedicationCard.Logged -> site?.let { stringResource(Res.string.medication_card_logged_site, time.clock(), stringResource(it.inSentence)) }
        ?: stringResource(Res.string.medication_card_logged, time.clock())
    is MedicationCard.NotLogged -> stringResource(Res.string.medication_card_not_logged, DateCopy.longWeekday(due.dayOfWeek))
    is MedicationCard.Between -> when {
        last != null && next != null -> stringResource(Res.string.medication_card_between, day(last, today), day(next, today))
        next != null -> stringResource(Res.string.medication_card_next, day(next, today))
        last != null -> stringResource(Res.string.medication_card_last, day(last, today))
        else -> stringResource(Res.string.medication_card_none)
    }
}

/** "Thursday" within the week, "Oct 3" beyond it. */
private fun day(day: LocalDate, today: LocalDate): String =
    if (MedicationCardBuilder.saysWeekday(day, today)) DateCopy.longWeekday(day.dayOfWeek) else DateCopy.monthDay(day)
