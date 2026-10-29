package com.opson.libraries.ui.time

import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_date_range
import opson.libraries.resources.generated.resources.ds_date_range_same_month
import opson.libraries.resources.generated.resources.ds_date_today
import opson.libraries.resources.generated.resources.ds_date_tomorrow
import opson.libraries.resources.generated.resources.ds_date_yesterday
import org.jetbrains.compose.resources.StringResource

/**
 * `strings_ui.xml`'s date words as [DateWords], each read raw through [get] with its placeholders
 * left in: `getString` in the app, the XML file itself in `DateWordsDriftTest`.
 */
suspend fun loadDateWords(get: suspend (StringResource) -> String): DateWords = DateWords(
    today = get(Res.string.ds_date_today),
    yesterday = get(Res.string.ds_date_yesterday),
    tomorrow = get(Res.string.ds_date_tomorrow),
    rangeSameMonth = get(Res.string.ds_date_range_same_month),
    range = get(Res.string.ds_date_range),
)
