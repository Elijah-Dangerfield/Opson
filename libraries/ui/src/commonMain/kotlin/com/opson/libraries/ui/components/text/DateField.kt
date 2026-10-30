package com.opson.libraries.ui.components.text

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import com.opson.system.background
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
 * A date typed on the number pad, shown in three boxes in the order the phone's region writes
 * dates ([DateOrder]).
 *
 * It is one text field holding the digits as typed ([DateText]); the boxes are drawn from it
 * ([DateEntry.boxes]). Typing fills the boxes in turn, a month or day whose first digit can only
 * stand alone moves on by itself, delete takes the last digit typed, and a whole date pasted or
 * filled in by the phone lands in all three. Tapping any box puts the keyboard up, and the box the
 * next digit goes into is outlined.
 *
 * With [birthday], the field tells the phone it holds a birthday, so iOS and Android can offer the
 * one on the person's own contact card. Quicker than a picker that opens on today and needs thirty
 * years of scrolling.
 *
 * [imeAction] is the return key, and [onDone] runs when it, or the Done bar over an iOS number
 * pad, is pressed. With [focusOnOpen] the keyboard comes up once the screen has come in
 * ([FocusOnOpen]), for a step that asks only this.
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
    val text = remember(order) { DateText(order, parts) }
    text.onChange = onChange
    SideEffect { text.heard(parts) }

    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default
    val ime = rememberFieldIme(keyboardActions, KeyboardType.Number, singleLine = true)
    val content = if (birthday) FieldContent.BirthDate else null
    val shown = text.parts
    val spoken = spokenDate(text, order)

    BasicTextField(
        state = text.state,
        modifier = modifier
            .focusRequester(focusRequester)
            .then(ime.modifier)
            .onFocusChanged { focused = it.isFocused }
            .fieldContent(content),
        enabled = enabled,
        inputTransformation = text.input,
        // The digits themselves are never drawn: the boxes are. Kept out of sight rather than
        // left out, since the text input, the keyboard and the screen reader all work on them.
        textStyle = TextStyle(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ).withContent(content),
        onKeyboardAction = { ime.actions.perform(imeAction) },
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(Color.Transparent),
        outputTransformation = spoken,
        decorator = { innerTextField ->
            Box {
                DateBoxes(parts = shown, current = text.current.takeIf { focused && enabled }, order = order)
                // A sliver the size of nothing: the field's own text and cursor, out of sight,
                // and too small for a cursor handle to show on.
                Box(modifier = Modifier.size(1.dp).clipToBounds()) { innerTextField() }
            }
        },
    )

    if (focusOnOpen) FocusOnOpen(focusRequester, enabled = enabled)
}

/**
 * The three boxes, [current] outlined. Silent to a screen reader, which reads the field's
 * [spokenDate] instead: once, as Month, Day and Year.
 */
@Composable
private fun DateBoxes(parts: DateParts, current: DatePart?, order: DateOrder) {
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        order.parts.forEach { part ->
            Column(
                modifier = Modifier.weight(if (part == DatePart.Year) YEAR_WEIGHT else 1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = stringResource(part.label), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
                DateBox(value = parts[part], placeholder = stringResource(part.hint), active = part == current)
            }
        }
    }
}

/** One box, drawn like [InputFrame]: the value centred, its hint while empty, an accent outline while it is next. */
@Composable
private fun DateBox(value: String, placeholder: String, active: Boolean) {
    val shape = AppTheme.shapes.input
    val border = if (active) AppTheme.colors.accent else AppTheme.colors.line
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FieldMinHeight)
            .clip(shape)
            .background(AppTheme.colors.bg)
            .border(if (active) 2.dp else 1.dp, border.color, shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            typography = AppTheme.type.lead,
            color = if (value.isEmpty()) AppTheme.colors.muted else AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * What the field reads as, to a screen reader: each box with something in it, by name and in
 * order ("Month 05, Day 11, Year 1998"). Nothing while it is empty.
 *
 * The names go in around the typed digits rather than replacing them, so every digit keeps its
 * place: a hardware delete works on this text and takes the digit before the cursor, where a
 * replaced text maps back to all of it and would clear the field. A box the digits don't reach
 * yet but the screen filled (a year alone) goes on the end.
 *
 * One transformation for the field's life, reading the date as it runs. A new one per keystroke
 * restarts the text input session, which on iOS takes the keyboard's text view down and drops
 * the keys typed while it comes back.
 */
@Composable
private fun spokenDate(text: DateText, order: DateOrder): OutputTransformation {
    val names = order.parts.map { stringResource(it.label) }
    return remember(text, order, names) {
        OutputTransformation {
            val shown = text.parts
            val boxes = DateEntry.boxes(asCharSequence().toString(), order)
            var at = 0
            order.parts.forEachIndexed { index, part ->
                val value = boxes[part].ifEmpty { return@forEachIndexed }
                val name = (if (at == 0) "" else SPOKEN_SEPARATOR) + names[index] + " "
                insert(at, name)
                at += name.length + value.length
            }
            order.parts.forEachIndexed { index, part ->
                if (boxes[part].isEmpty() && shown[part].isNotEmpty()) {
                    append((if (length == 0) "" else SPOKEN_SEPARATOR) + names[index] + " " + shown[part])
                }
            }
        }
    }
}

private const val SPOKEN_SEPARATOR = ", "

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
