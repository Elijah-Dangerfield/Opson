package com.opson.libraries.widgets

import androidx.compose.runtime.Composable
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.widgets_log
import opson.libraries.resources.generated.resources.widgets_meal_done
import opson.libraries.resources.generated.resources.widgets_meal_not_yet
import opson.libraries.resources.generated.resources.widgets_note_log
import opson.libraries.resources.generated.resources.widgets_note_untracked
import opson.libraries.resources.generated.resources.widgets_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The widget's words, resolved. A snapshot carries finished text because a widget can't read the
 * app's string resources, so [WidgetSnapshotBuilder] takes them already resolved: from
 * [WidgetStrings] off the main thread, or [rememberWidgetCopy] in a composable.
 *
 * None of these takes a number. The one number a widget shows comes from the policy.
 */
data class WidgetCopy(
    val today: String,
    val log: String,
    val noteLog: String,
    val noteUntracked: String,
    val meals: Map<MealSlot, MealCopy>,
) {
    /** A meal's name and what a screen reader says for its circle either way. */
    data class MealCopy(val label: String, val done: String, val notYet: String)

    fun meal(slot: MealSlot): MealCopy = meals[slot] ?: MealCopy(slot.wire, slot.wire, slot.wire)

    companion object {
        suspend fun resolve(strings: WidgetStrings): WidgetCopy = WidgetCopy(
            today = strings.get(Res.string.widgets_today),
            log = strings.get(Res.string.widgets_log),
            noteLog = strings.get(Res.string.widgets_note_log),
            noteUntracked = strings.get(Res.string.widgets_note_untracked),
            meals = MealSlot.meals.associateWith { slot ->
                val label = strings.get(slot.label)
                MealCopy(
                    label = label,
                    done = strings.get(Res.string.widgets_meal_done, label),
                    notYet = strings.get(Res.string.widgets_meal_not_yet, label),
                )
            },
        )
    }
}

/** Resolves the copy. The app's is `getString`; tests return the resource's key. */
interface WidgetStrings {
    suspend fun get(resource: StringResource, vararg args: Any): String
}

/** [WidgetCopy] in a composable, for the Widgets page preview, previews and the leak test. */
@Composable
fun rememberWidgetCopy(): WidgetCopy = WidgetCopy(
    today = stringResource(Res.string.widgets_today),
    log = stringResource(Res.string.widgets_log),
    noteLog = stringResource(Res.string.widgets_note_log),
    noteUntracked = stringResource(Res.string.widgets_note_untracked),
    meals = MealSlot.meals.associateWith { slot ->
        val label = stringResource(slot.label)
        WidgetCopy.MealCopy(
            label = label,
            done = stringResource(Res.string.widgets_meal_done, label),
            notYet = stringResource(Res.string.widgets_meal_not_yet, label),
        )
    },
)
