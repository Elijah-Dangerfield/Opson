package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_why_body
import opson.libraries.resources.generated.resources.today_dayclose_why_ok
import opson.libraries.resources.generated.resources.today_dayclose_why_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Why not cut back?" (design sync 2, 4.2): why tomorrow's range stays where it is after a big
 * day. Words only, no numbers, and nothing to save.
 *
 * TODO(clinician): approve the wording and pick the citation; no study is named until then.
 */
@Composable
fun WhyNotCutBackSheetContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.today_dayclose_why_title), onClose = onClose)
        Text(text = stringResource(Res.string.today_dayclose_why_body), typography = AppTheme.type.lead, color = AppTheme.colors.ink)
        FlatButton(
            text = stringResource(Res.string.today_dayclose_why_ok),
            onClick = onClose,
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
