package com.opson.features.logger.impl

import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.ExerciseEnergy
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The exercise sheet (docs/research/energy-expenditure.md): pick an activity, say how long and
 * when, and see what it counts in Burned. Every amount is energy out, resolved on
 * [Surface.Logger]; the sheet is only offered where energy out is not hidden.
 */
data class ExerciseSheetState(
    val loading: Boolean = true,
    /** Editing an entry: Delete shows and the title says so. */
    val editing: Boolean = false,
    val pane: ExercisePane = ExercisePane.Form,
    /** An `ExerciseActivities` key, `other`, or null before one is picked. */
    val activity: String? = null,
    /** Other's name, as typed. Null for a listed activity. */
    val name: String? = null,
    /** Minutes as typed, digits only. */
    val minutes: String = "",
    val startedAt: LocalTime = LocalTime(0, 0),
    /** "About 133 kcal": the estimate, Hidden without one or when energy out does not show. */
    val estimate: Display = Display.Hidden,
    /** No weigh-in yet, so no estimate: the sheet says why and asks for the number. */
    val noWeight: Boolean = false,
    /** The number is needed (Other, or no estimate), so the field is labelled as the answer rather than an extra. */
    val numberNeeded: Boolean = false,
    /** The person's own number, as typed. Only while energy out shows: the field is an amount. */
    val typed: String = "",
    val canNumber: Boolean = false,
    /**
     * Other in the activity list. Other has no estimate, so it is only offered where the number
     * can be typed; on Later or a softened energy out it would leave Save off for good.
     */
    val offersOther: Boolean = false,
    /** "My watch didn't record this", only with a health app connected. */
    val notOnWatch: Boolean? = null,
    val canSave: Boolean = false,
    /** The activity search, on [ExercisePane.Pick]. */
    val query: String = "",
)

enum class ExercisePane { Form, Pick }

sealed interface ExerciseSheetAction {
    data class InputsArrived(val inputs: ExerciseInputs) : ExerciseSheetAction
    data class PolicyArrived(val policy: VisibilityPolicy) : ExerciseSheetAction
    data object ActivityTapped : ExerciseSheetAction
    data class QueryTyped(val text: String) : ExerciseSheetAction
    data class ActivityPicked(val key: String) : ExerciseSheetAction
    data object PickBackTapped : ExerciseSheetAction
    data class NameTyped(val text: String) : ExerciseSheetAction
    data class MinutesTyped(val text: String) : ExerciseSheetAction
    data class MinutesPicked(val minutes: Int) : ExerciseSheetAction
    data class TimePicked(val time: LocalTime) : ExerciseSheetAction
    data class NumberTyped(val text: String) : ExerciseSheetAction
    data class NotOnWatchToggled(val on: Boolean) : ExerciseSheetAction
    data object SaveTapped : ExerciseSheetAction
    data object DeleteTapped : ExerciseSheetAction
    data object CloseTapped : ExerciseSheetAction
}

sealed interface ExerciseSheetEvent {
    data class ShowToast(val toast: Toast) : ExerciseSheetEvent
    data object Dismiss : ExerciseSheetEvent
}

/**
 * What the sheet reads. [entry] is the one being edited, null for a new one; [weightKg] the latest
 * weigh-in; [healthConnected] a health app connected with `release.health` on.
 */
data class ExerciseInputs(
    val day: LocalDate,
    val now: LocalDateTime,
    val timeZone: TimeZone,
    val weightKg: Double?,
    val healthConnected: Boolean,
    val entry: ExerciseEntry? = null,
)

/** What the person has done on the sheet. Starts from the entry being edited, or empty. */
data class ExerciseForm(
    val pane: ExercisePane = ExercisePane.Form,
    val query: String = "",
    val activity: String? = null,
    val name: String = "",
    val minutes: String = "",
    val startedAt: LocalTime? = null,
    val typed: String = "",
    val notOnWatch: Boolean = false,
) {
    companion object {
        fun from(entry: ExerciseEntry, timeZone: TimeZone): ExerciseForm = ExerciseForm(
            activity = entry.activity,
            name = entry.name.orEmpty(),
            minutes = entry.minutes.toString(),
            startedAt = entry.startedAt.toLocalDateTime(timeZone).time,
            typed = if (entry.kcalTyped) entry.kcal.toString() else "",
            notOnWatch = entry.notOnWatch,
        )
    }
}

object ExerciseSheetStateBuilder {
    /** The lengths offered as one tap. */
    val QUICK_MINUTES = listOf(15, 30, 45, 60)

    private const val MAX_TYPED_DIGITS = 4
    private const val NOON = 12

    fun build(form: ExerciseForm, inputs: ExerciseInputs, policy: VisibilityPolicy): ExerciseSheetState {
        val estimate = estimateKcal(form, inputs)
        val other = form.activity == ExerciseActivities.OTHER
        val canNumber = canNumber(inputs, policy)
        return ExerciseSheetState(
            loading = false,
            editing = inputs.entry != null,
            pane = form.pane,
            activity = form.activity,
            name = form.name.takeIf { other },
            minutes = form.minutes,
            startedAt = startTime(form, inputs),
            estimate = estimate
                ?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Logger, inputs.context()) }
                ?: Display.Hidden,
            noWeight = form.activity != null && !other && inputs.weightKg == null,
            numberNeeded = form.activity != null && estimate == null,
            typed = form.typed.takeIf { canNumber }.orEmpty(),
            canNumber = canNumber,
            offersOther = canNumber,
            notOnWatch = form.notOnWatch.takeIf { inputs.healthConnected },
            canSave = draft(form, inputs) != null,
            query = form.query,
        )
    }

    /** Typed, the number is an amount like any other, so only where energy out shows right now. */
    fun canNumber(inputs: ExerciseInputs, policy: VisibilityPolicy): Boolean =
        policy.effectiveLevel(Metric.EnergyOut, Surface.Logger, inputs.context()) == Visibility.Show

    /**
     * What Save writes, or null while something is missing. A typed number wins; otherwise the
     * estimate, except that an edit that left the activity and length alone keeps the number it
     * was saved with, so a newer weigh-in does not quietly rewrite a past day.
     */
    fun draft(form: ExerciseForm, inputs: ExerciseInputs): ExerciseDraft? {
        val activity = form.activity ?: return null
        val minutes = form.minutes.toIntOrNull()?.takeIf { it in 1..ExerciseEnergy.MAX_MINUTES } ?: return null
        val typed = typedKcal(form)
        val entry = inputs.entry
        val kept = entry?.takeIf { typed == null && !it.kcalTyped && it.activity == activity && it.minutes == minutes }?.kcal
        val kcal = typed ?: kept ?: estimateKcal(form, inputs) ?: return null
        val name = form.name.trim().takeIf { activity == ExerciseActivities.OTHER }
        val startedAt = LocalDateTime(inputs.day, startTime(form, inputs)).toInstant(inputs.timeZone)
        return ExerciseDraft(
            day = inputs.day,
            startedAt = startedAt,
            activity = activity,
            name = name,
            minutes = minutes,
            kcal = kcal,
            kcalTyped = typed != null,
            notOnWatch = form.notOnWatch && inputs.healthConnected,
        ).takeIf { it.problem() == null }
    }

    /** Digits only, and no more than the most an entry may count has. */
    fun sanitizeNumber(text: String): String = NumberInput.sanitize(text, allowDecimal = false).take(MAX_TYPED_DIGITS)

    private fun typedKcal(form: ExerciseForm): Int? = form.typed.toIntOrNull()?.takeIf { it in 0..ExerciseEnergy.MAX_KCAL }

    private fun estimateKcal(form: ExerciseForm, inputs: ExerciseInputs): Int? {
        val met = form.activity?.let { ExerciseActivities[it] }?.met ?: return null
        val minutes = form.minutes.toIntOrNull() ?: return null
        return ExerciseEnergy.estimateKcal(met, inputs.weightKg, minutes)
    }

    /** Now on today, noon on another day, until the person picks a time. */
    private fun startTime(form: ExerciseForm, inputs: ExerciseInputs): LocalTime =
        form.startedAt ?: if (inputs.day == inputs.now.date) LocalTime(inputs.now.hour, inputs.now.minute) else LocalTime(NOON, 0)

    private fun ExerciseInputs.context() = FormatContext(day = day)
}
