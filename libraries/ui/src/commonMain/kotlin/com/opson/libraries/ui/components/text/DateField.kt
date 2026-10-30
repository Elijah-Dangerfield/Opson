package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_date_day
import opson.libraries.resources.generated.resources.ds_date_day_hint
import opson.libraries.resources.generated.resources.ds_date_month
import opson.libraries.resources.generated.resources.ds_date_month_hint
import opson.libraries.resources.generated.resources.ds_date_year
import opson.libraries.resources.generated.resources.ds_date_year_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A date typed on the number pad, in three boxes in the order the phone's region writes dates
 * ([DateOrder]). A box that is full, or whose first digit can only stand alone, moves on to the
 * next. A whole date pasted, or filled in by the phone, into any box is split across all three
 * ([DateEntry.type]).
 *
 * With [birthday], each box tells the phone it holds that part of a birthday, so iOS and
 * Android can offer the one on the person's own contact card. Quicker than a picker that opens
 * on today and needs thirty years of scrolling.
 *
 * It draws [parts] as given; the caller turns them into a date ([DateParts.dateOrNull]) and says
 * what is wrong under the field.
 */
@Composable
fun DateField(
    parts: DateParts,
    onChange: (DateParts) -> Unit,
    modifier: Modifier = Modifier,
    order: DateOrder = DateOrder.forRegion(Locale.current.region),
    birthday: Boolean = true,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    val focus = remember { DatePart.entries.associateWith { FocusRequester() } }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        order.parts.forEachIndexed { index, part ->
            val next = order.parts.getOrNull(index + 1)
            NumberField(
                value = parts[part],
                onValueChange = { raw ->
                    val typed = DateEntry.type(parts, part, raw, order)
                    onChange(typed)
                    val whole = typed != parts.with(part, typed[part])
                    when {
                        whole -> focus.getValue(order.parts.last()).requestFocus()
                        next != null && DateEntry.isDone(part, typed[part]) && typed[part] != parts[part] ->
                            focus.getValue(next).requestFocus()
                    }
                },
                label = stringResource(part.label),
                placeholder = stringResource(part.hint),
                allowDecimal = false,
                textAlign = TextAlign.Center,
                enabled = enabled,
                imeAction = if (next == null) imeAction else ImeAction.Next,
                content = if (birthday) part.birthdayContent else null,
                modifier = Modifier.weight(if (part == DatePart.Year) YEAR_WEIGHT else 1f).focusRequester(focus.getValue(part)),
            )
        }
    }
}

/** The year box is wider: four digits to the others' two. */
private const val YEAR_WEIGHT = 1.4f

private val DatePart.label: StringResource
    get() = when (this) {
        DatePart.Day -> Res.string.ds_date_day
        DatePart.Month -> Res.string.ds_date_month
        DatePart.Year -> Res.string.ds_date_year
    }

private val DatePart.hint: StringResource
    get() = when (this) {
        DatePart.Day -> Res.string.ds_date_day_hint
        DatePart.Month -> Res.string.ds_date_month_hint
        DatePart.Year -> Res.string.ds_date_year_hint
    }

private val DatePart.birthdayContent: FieldContent
    get() = when (this) {
        DatePart.Day -> FieldContent.BirthDay
        DatePart.Month -> FieldContent.BirthMonth
        DatePart.Year -> FieldContent.BirthYear
    }

@Preview(widthDp = 390)
@Composable
private fun DateFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DateField(parts = DateParts(), onChange = {}, order = DateOrder.MonthDayYear)
            DateField(parts = DateParts.of(LocalDate(1992, 4, 18)), onChange = {}, order = DateOrder.DayMonthYear)
            DateField(parts = DateParts(year = "1998", month = "5"), onChange = {}, order = DateOrder.YearMonthDay)
        }
    }
}
