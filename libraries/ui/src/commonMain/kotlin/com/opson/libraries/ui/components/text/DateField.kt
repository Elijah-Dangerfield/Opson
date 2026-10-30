package com.opson.libraries.ui.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.core.Catching
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
 * next, and digits that land in a finished box go on into the next ones. A whole date pasted, or
 * filled in by the phone, into any box is split across all three. Delete in an empty box goes back
 * to the one before and takes its last digit. The rules are [DateEntry]'s; [DateBoxes] applies them
 * inside each keystroke, so typing faster than the screen redraws loses nothing.
 *
 * With [birthday], each box tells the phone it holds that part of a birthday, so iOS and
 * Android can offer the one on the person's own contact card. Quicker than a picker that opens
 * on today and needs thirty years of scrolling.
 *
 * [imeAction] is the last box's return key, and [onDone] runs when it, or the Done bar over an iOS
 * number pad in that box, is pressed. With [focusOnOpen] the cursor starts in the first empty box
 * once the screen has come in ([FocusOnOpen]), for a step that asks only this.
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
    onDone: (() -> Unit)? = null,
    focusOnOpen: Boolean = false,
) {
    val focus = remember { DatePart.entries.associateWith { FocusRequester() } }
    val boxes = remember(order) { DateBoxes(order, parts) }
    boxes.onChange = onChange
    // The cursor moves on the next frame, never inside the edit that asks for it: a box losing
    // focus mid-edit records its undo against text it has not committed yet, and throws. Digits
    // typed before the move lands still reach the right box (DateEntry carries them on).
    var moveTo by remember { mutableStateOf<FocusMove?>(null) }
    boxes.moveTo = { part -> moveTo = FocusMove(part) }
    LaunchedEffect(moveTo) { moveTo?.let { Catching { focus.getValue(it.part).requestFocus() } } }
    SideEffect { boxes.heard(parts) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        order.parts.forEach { part ->
            val last = part == order.parts.last()
            DateBox(
                boxes = boxes,
                part = part,
                enabled = enabled,
                imeAction = if (last) imeAction else ImeAction.Next,
                keyboardActions = if (last && onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
                content = if (birthday) part.birthdayContent else null,
                modifier = Modifier.weight(if (part == DatePart.Year) YEAR_WEIGHT else 1f).focusRequester(focus.getValue(part)),
            )
        }
    }

    if (focusOnOpen) {
        val first = remember { DateEntry.firstEmpty(parts, order) }
        if (first != null) FocusOnOpen(focus.getValue(first), enabled = enabled)
    }
}

/** One box: [NumberField]'s look and keyboard, on the box's own [DateBoxes] state. */
@Composable
private fun DateBox(
    boxes: DateBoxes,
    part: DatePart,
    enabled: Boolean,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    content: FieldContent?,
    modifier: Modifier,
) {
    val ime = rememberFieldIme(keyboardActions, KeyboardType.Number, singleLine = true)
    val typography = AppTheme.type.lead
    val placeholder = stringResource(part.hint)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(part.label), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        BasicTextField(
            state = boxes.state(part),
            modifier = Modifier
                .then(ime.modifier)
                .fieldContent(content)
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.Backspace && boxes.deleteAtStart(part)
                },
            enabled = enabled,
            inputTransformation = boxes.input(part),
            textStyle = typography.style(AppTheme.colors.ink.color).copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ).withContent(content),
            onKeyboardAction = { ime.actions.perform(imeAction) },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(AppTheme.colors.accent.color),
            outputTransformation = boxes.output(part),
            decorator = { innerTextField ->
                InputFrame(
                    empty = boxes.isEmpty(part),
                    placeholder = placeholder,
                    suffix = null,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    typography = typography,
                    fill = AppTheme.colors.bg,
                    shape = AppTheme.shapes.input,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    minHeight = FieldMinHeight,
                    borderColor = AppTheme.colors.line,
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

/** A request to move the cursor to [part]. Not a data class: asking for the same box twice moves it twice. */
private class FocusMove(val part: DatePart)

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
