package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_closed_not_now
import opson.libraries.resources.generated.resources.diary_closed_other_day
import opson.libraries.resources.generated.resources.diary_closed_reopen
import opson.libraries.resources.generated.resources.diary_closed_today
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * What a day closed with Close the day says wherever someone tries to add to it: "Today is
 * closed. Reopen it to add more." with Reopen, which the caller turns into
 * `DiaryRepository.reopenDay`. [onNotNow] adds a second button for a place that has nothing else
 * to offer, such as the sheet a refused write opens.
 */
@Composable
fun DayClosedNotice(
    isToday: Boolean,
    onReopen: () -> Unit,
    modifier: Modifier = Modifier,
    onNotNow: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon = AppIcons.CheckCircle, size = 24.dp, color = AppTheme.colors.accent)
            Text(
                text = stringResource(if (isToday) Res.string.diary_closed_today else Res.string.diary_closed_other_day),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatButton(
                text = stringResource(Res.string.diary_closed_reopen),
                onClick = onReopen,
                tone = FlatButtonTone.Accent,
                centered = true,
                modifier = Modifier.weight(1f),
            )
            if (onNotNow != null) {
                FlatButton(
                    text = stringResource(Res.string.diary_closed_not_now),
                    onClick = onNotNow,
                    tone = FlatButtonTone.Soft,
                    centered = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun DayClosedNoticePreview() {
    PreviewContent {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DayClosedNotice(isToday = true, onReopen = {})
            DayClosedNotice(isToday = false, onReopen = {}, onNotNow = {})
        }
    }
}

@Preview
@Composable
private fun DayClosedNoticeDarkPreview() {
    PreviewContent(darkTheme = true) {
        DayClosedNotice(isToday = true, onReopen = {}, onNotNow = {}, modifier = Modifier.padding(16.dp))
    }
}
