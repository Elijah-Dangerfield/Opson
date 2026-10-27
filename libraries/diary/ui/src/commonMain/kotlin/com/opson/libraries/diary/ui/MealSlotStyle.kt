package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.MealTone
import com.opson.system.color.ToneColors
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The design system's mirror of this slot, which picks its tile colours and icon. */
val MealSlot.tone: MealTone
    get() = when (this) {
        MealSlot.Breakfast -> MealTone.Breakfast
        MealSlot.Lunch -> MealTone.Lunch
        MealSlot.Snack -> MealTone.Snack
        MealSlot.Dinner -> MealTone.Dinner
        MealSlot.Uncategorized -> MealTone.Uncategorized
    }

/** `bakery_dining`, `lunch_dining`, `nutrition`, `dinner_dining`, `format_list_bulleted`. */
val MealSlot.icon: IconResource get() = AppIcons.meal(tone)

/**
 * The slot's tile colours: Breakfast carbs, Lunch fat, Snack fiber, Dinner
 * protein, Uncategorized muted on soft. They only tell meals apart.
 */
@Composable
fun MealSlot.tileColors(): ToneColors = AppTheme.colors.meal(tone)

@Preview
@Composable
private fun MealSlotStylePreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MealSlot.entries.forEach { slot ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ToneIcon(icon = slot.icon, tone = slot.tileColors())
                    Text(text = stringResource(slot.label), typography = AppTheme.type.caption, color = AppTheme.colors.muted)
                }
            }
        }
    }
}
