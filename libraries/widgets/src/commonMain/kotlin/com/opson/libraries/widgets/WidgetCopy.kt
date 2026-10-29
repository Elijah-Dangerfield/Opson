package com.opson.libraries.widgets

import androidx.compose.runtime.Composable
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_macro_carbs
import opson.libraries.resources.generated.resources.today_macro_fat
import opson.libraries.resources.generated.resources.today_macro_fiber
import opson.libraries.resources.generated.resources.today_macro_protein
import opson.libraries.resources.generated.resources.widgets_add_glass
import opson.libraries.resources.generated.resources.widgets_add_glass_cd
import opson.libraries.resources.generated.resources.widgets_all_food
import opson.libraries.resources.generated.resources.widgets_amount_unit
import opson.libraries.resources.generated.resources.widgets_calories
import opson.libraries.resources.generated.resources.widgets_fasting
import opson.libraries.resources.generated.resources.widgets_fasting_done
import opson.libraries.resources.generated.resources.widgets_fasting_ends_at
import opson.libraries.resources.generated.resources.widgets_fasting_next_at
import opson.libraries.resources.generated.resources.widgets_fasting_now
import opson.libraries.resources.generated.resources.widgets_fasting_off
import opson.libraries.resources.generated.resources.widgets_fasting_until_at
import opson.libraries.resources.generated.resources.widgets_fasting_window
import opson.libraries.resources.generated.resources.widgets_log
import opson.libraries.resources.generated.resources.widgets_macros
import opson.libraries.resources.generated.resources.widgets_macro_cd
import opson.libraries.resources.generated.resources.widgets_macros_hidden
import opson.libraries.resources.generated.resources.widgets_meal_done
import opson.libraries.resources.generated.resources.widgets_meal_not_yet
import opson.libraries.resources.generated.resources.widgets_meals
import opson.libraries.resources.generated.resources.widgets_meals_done
import opson.libraries.resources.generated.resources.widgets_note_log
import opson.libraries.resources.generated.resources.widgets_note_untracked
import opson.libraries.resources.generated.resources.widgets_nothing_yet
import opson.libraries.resources.generated.resources.widgets_to_goal
import opson.libraries.resources.generated.resources.widgets_to_range
import opson.libraries.resources.generated.resources.widgets_today
import opson.libraries.resources.generated.resources.widgets_water
import opson.libraries.resources.generated.resources.widgets_water_glasses
import opson.libraries.resources.generated.resources.widgets_water_hidden
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The widget's words, resolved. A snapshot carries finished text because a widget can't read the
 * app's string resources, so [WidgetSnapshotBuilder] takes them already resolved: from
 * [WidgetStrings] off the main thread, or [rememberWidgetCopy] in a composable.
 *
 * None of these takes a nutrition number; those come from the policy. [mealsDone] and
 * [waterGlasses] take counts of meals and glasses.
 *
 * Everything after [meals] has the English copy as its default, so a preview or test that only
 * cares about the Today widget needs to name nothing more; the app's two paths resolve them all.
 */
data class WidgetCopy(
    val today: String,
    val log: String,
    val noteLog: String,
    val noteUntracked: String,
    val meals: Map<MealSlot, MealCopy>,
    val labels: WidgetLabels = WidgetLabels(),
    val toRange: String = "to range",
    val toGoal: String = "to goal",
    val allFood: String = "All food",
    val mealsDone: (done: Int, total: Int) -> String = { done, total -> "$done of $total meals" },
    val waterGlasses: (filled: Int, total: Int) -> String = { filled, total -> "$filled of $total glasses" },
    val fasting: FastingCopy = FastingCopy(),
    /** By [com.opson.libraries.nutrition.Metric.key]: Today's own macro names. */
    val macroLabels: Map<String, String> = mapOf("protein" to "Protein", "carbs" to "Carbs", "fat" to "Fat", "fiber" to "Fiber"),
    /** A figure's amount and unit on one line, "655 to range", for a Lock Screen widget. */
    val amountUnit: String = "%1\$s %2\$s",
    /** What a screen reader says for a macro: its name, then its amount against the target. */
    val macroDescription: String = "%1\$s, %2\$s",
    /** What a screen reader says for the add button: "Add a glass", then the glass. */
    val addGlassDescription: String = "%1\$s, %2\$s",
) {
    /** A meal's name and what a screen reader says for its circle either way. */
    data class MealCopy(val label: String, val done: String, val notYet: String)

    /**
     * The fasting widget's phase titles, and the line under each with `%1$s` where the widget
     * prints the time in the device's clock style.
     */
    data class FastingCopy(
        val now: String = "Fasting",
        val endsAt: String = "Ends at %1\$s",
        val window: String = "Eating window",
        val untilAt: String = "Until %1\$s",
        val done: String = "Fast done",
        val nextAt: String = "Next fast at %1\$s",
    )

    fun meal(slot: MealSlot): MealCopy = meals[slot] ?: MealCopy(slot.wire, slot.wire, slot.wire)

    companion object {
        suspend fun resolve(strings: WidgetStrings): WidgetCopy {
            // The counts are whole numbers in a sentence; resolved once per count a day can have.
            val mealsDone = (0..MealSlot.meals.size).associateWith { strings.get(Res.string.widgets_meals_done, it, MealSlot.meals.size) }
            val glassesTemplate = strings.get(Res.string.widgets_water_glasses, FILLED, TOTAL)
            return WidgetCopy(
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
                labels = WidgetLabels(
                    calories = strings.get(Res.string.widgets_calories),
                    macros = strings.get(Res.string.widgets_macros),
                    water = strings.get(Res.string.widgets_water),
                    meals = strings.get(Res.string.widgets_meals),
                    fasting = strings.get(Res.string.widgets_fasting),
                    addGlass = strings.get(Res.string.widgets_add_glass),
                    macrosHidden = strings.get(Res.string.widgets_macros_hidden),
                    waterHidden = strings.get(Res.string.widgets_water_hidden),
                    fastingOff = strings.get(Res.string.widgets_fasting_off),
                    nothingYet = strings.get(Res.string.widgets_nothing_yet),
                ),
                toRange = strings.get(Res.string.widgets_to_range),
                toGoal = strings.get(Res.string.widgets_to_goal),
                allFood = strings.get(Res.string.widgets_all_food),
                mealsDone = { done, total -> mealsDone[done] ?: "$done / $total" },
                waterGlasses = { filled, total -> glassesTemplate.replace(FILLED, "$filled").replace(TOTAL, "$total") },
                fasting = FastingCopy(
                    now = strings.get(Res.string.widgets_fasting_now),
                    endsAt = strings.get(Res.string.widgets_fasting_ends_at),
                    window = strings.get(Res.string.widgets_fasting_window),
                    untilAt = strings.get(Res.string.widgets_fasting_until_at),
                    done = strings.get(Res.string.widgets_fasting_done),
                    nextAt = strings.get(Res.string.widgets_fasting_next_at),
                ),
                macroLabels = mapOf(
                    "protein" to strings.get(Res.string.today_macro_protein),
                    "carbs" to strings.get(Res.string.today_macro_carbs),
                    "fat" to strings.get(Res.string.today_macro_fat),
                    "fiber" to strings.get(Res.string.today_macro_fiber),
                ),
                amountUnit = strings.get(Res.string.widgets_amount_unit),
                macroDescription = strings.get(Res.string.widgets_macro_cd),
                addGlassDescription = strings.get(Res.string.widgets_add_glass_cd),
            )
        }

        // Placeholders for a template resolved once and filled per snapshot.
        private const val FILLED = "\u0000filled\u0000"
        private const val TOTAL = "\u0000total\u0000"
    }
}

/** Resolves the copy. The app's is `getString`; tests return the resource's key. */
interface WidgetStrings {
    suspend fun get(resource: StringResource, vararg args: Any): String
}

/** [WidgetCopy] in a composable, for the Widgets page preview, previews and the leak test. */
@Composable
fun rememberWidgetCopy(): WidgetCopy {
    val mealsDone = (0..MealSlot.meals.size).associateWith { stringResource(Res.string.widgets_meals_done, it, MealSlot.meals.size) }
    val glasses = stringResource(Res.string.widgets_water_glasses, "\u0000f", "\u0000t")
    return WidgetCopy(
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
        labels = WidgetLabels(
            calories = stringResource(Res.string.widgets_calories),
            macros = stringResource(Res.string.widgets_macros),
            water = stringResource(Res.string.widgets_water),
            meals = stringResource(Res.string.widgets_meals),
            fasting = stringResource(Res.string.widgets_fasting),
            addGlass = stringResource(Res.string.widgets_add_glass),
            macrosHidden = stringResource(Res.string.widgets_macros_hidden),
            waterHidden = stringResource(Res.string.widgets_water_hidden),
            fastingOff = stringResource(Res.string.widgets_fasting_off),
            nothingYet = stringResource(Res.string.widgets_nothing_yet),
        ),
        toRange = stringResource(Res.string.widgets_to_range),
        toGoal = stringResource(Res.string.widgets_to_goal),
        allFood = stringResource(Res.string.widgets_all_food),
        mealsDone = { done, total -> mealsDone[done] ?: "$done / $total" },
        waterGlasses = { filled, total -> glasses.replace("\u0000f", "$filled").replace("\u0000t", "$total") },
        fasting = WidgetCopy.FastingCopy(
            now = stringResource(Res.string.widgets_fasting_now),
            endsAt = stringResource(Res.string.widgets_fasting_ends_at),
            window = stringResource(Res.string.widgets_fasting_window),
            untilAt = stringResource(Res.string.widgets_fasting_until_at),
            done = stringResource(Res.string.widgets_fasting_done),
            nextAt = stringResource(Res.string.widgets_fasting_next_at),
        ),
        macroLabels = mapOf(
            "protein" to stringResource(Res.string.today_macro_protein),
            "carbs" to stringResource(Res.string.today_macro_carbs),
            "fat" to stringResource(Res.string.today_macro_fat),
            "fiber" to stringResource(Res.string.today_macro_fiber),
        ),
        amountUnit = stringResource(Res.string.widgets_amount_unit),
        macroDescription = stringResource(Res.string.widgets_macro_cd),
        addGlassDescription = stringResource(Res.string.widgets_add_glass_cd),
    )
}
