package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.DateField
import com.opson.libraries.ui.components.text.DateOrder
import com.opson.libraries.ui.components.text.DateParts
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.plan_birthday_hint_date
import opson.libraries.resources.generated.resources.plan_birthday_hint_old
import opson.libraries.resources.generated.resources.plan_birthday_hint_young
import opson.libraries.resources.generated.resources.plan_birthday_label
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Why a typed birthday can't be used. */
enum class BirthdayProblem {
    /** No such day: the 31st of April, a 13th month. */
    NotADate,

    /** Under [Birthdays.AGES]: Opson is for people 13 and over. */
    TooYoung,

    /** Over [Birthdays.AGES]: most likely a typo in the year. */
    TooOld,
}

/** Birthdays as onboarding and Profile take them. */
object Birthdays {
    /** Who a birthday can belong to: Opson is for people 13 and over, and the resting energy equation stops at 120. */
    val AGES: IntRange = 13..120

    /** Age in whole years on [today]: the birthday has to have come this year. */
    fun ageOn(born: LocalDate, today: LocalDate): Int {
        val had = today.month > born.month || (today.month == born.month && today.day >= born.day)
        return today.year - born.year - if (had) 0 else 1
    }

    /** What is wrong with [parts], once every box is typed; null while unfinished or when it's fine. */
    fun problem(parts: DateParts, today: LocalDate): BirthdayProblem? {
        if (!parts.isComplete) return null
        val born = parts.dateOrNull() ?: return BirthdayProblem.NotADate
        val age = ageOn(born, today)
        return when {
            age < AGES.first -> BirthdayProblem.TooYoung
            age > AGES.last -> BirthdayProblem.TooOld
            else -> null
        }
    }

    /** The birthday [parts] make, when it is one Opson takes. */
    fun dateOrNull(parts: DateParts, today: LocalDate): LocalDate? =
        parts.dateOrNull()?.takeIf { problem(parts, today) == null }
}

/**
 * The birthday, typed in three boxes ([DateField]) the phone can fill from the person's own
 * contact card, with what is wrong said under it once every box is typed. [note] is the line on
 * why it is asked.
 */
@Composable
fun BirthdayField(
    parts: DateParts,
    onChange: (DateParts) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    order: DateOrder? = null,
    label: String? = stringResource(Res.string.plan_birthday_label),
    note: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    val problem = Birthdays.problem(parts, today)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(text = label, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        if (order != null) {
            DateField(parts = parts, onChange = onChange, order = order, enabled = enabled, imeAction = imeAction, modifier = Modifier.fillMaxWidth())
        } else {
            DateField(parts = parts, onChange = onChange, enabled = enabled, imeAction = imeAction, modifier = Modifier.fillMaxWidth())
        }
        problem?.let {
            Text(
                text = stringResource(it.text),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (note != null) Text(text = note, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

private val BirthdayProblem.text
    get() = when (this) {
        BirthdayProblem.NotADate -> Res.string.plan_birthday_hint_date
        BirthdayProblem.TooYoung -> Res.string.plan_birthday_hint_young
        BirthdayProblem.TooOld -> Res.string.plan_birthday_hint_old
    }

@Preview(widthDp = 390)
@Composable
private fun BirthdayFieldPreview() {
    val today = LocalDate(2026, 10, 25)
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BirthdayField(parts = DateParts(), onChange = {}, today = today, order = DateOrder.MonthDayYear)
            BirthdayField(parts = DateParts.of(LocalDate(1992, 4, 18)), onChange = {}, today = today, order = DateOrder.DayMonthYear)
            BirthdayField(parts = DateParts(day = "31", month = "04", year = "1990"), onChange = {}, today = today, order = DateOrder.DayMonthYear)
            BirthdayField(parts = DateParts.of(LocalDate(2016, 1, 2)), onChange = {}, today = today, order = DateOrder.DayMonthYear)
        }
    }
}
