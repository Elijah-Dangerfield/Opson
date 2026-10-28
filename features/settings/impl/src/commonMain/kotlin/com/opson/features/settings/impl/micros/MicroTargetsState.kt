package com.opson.features.settings.impl.micros

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.MicroReferences
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.ReferenceBasis
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.microReference
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * Vitamins and minerals: every daily reference and where it comes from, and which four Today's
 * nutrients page lists. References are amounts to compare against, not amounts eaten, so they
 * show in any mode that shows vitamins and minerals at all; where the mode hides them ([off])
 * the page says so and links to Modes and numbers, the one place that shows more.
 */
data class MicroTargetsState(
    val loaded: Boolean = false,
    val backToTodayScreen: Boolean = false,
    val off: Boolean = false,
    val basis: ReferenceBasis = ReferenceBasis.DailyValue,
    /** Resting energy will take the birth year and sex these could come from. */
    val canAddDetails: Boolean = false,
    val groups: List<MicroTargetGroup> = emptyList(),
)

data class MicroTargetGroup(val group: MicroGroup, val rows: List<MicroTargetRow>)

/** "Iron", `18 mg` or `Your own, 12 mg`, and "On Today" for the four. */
data class MicroTargetRow(val micro: Micronutrient, val reference: String, val own: Boolean, val onToday: Boolean)

/**
 * One vitamin or mineral's sheet. [ownText] is what the field shows: what is being typed, else
 * their own number, else empty with the suggestion as its placeholder. [invalid]: what was typed
 * is not a sensible daily amount, so nothing was saved.
 */
data class MicroTargetSheetState(
    val loaded: Boolean = false,
    val micro: Micronutrient = Micronutrient.VitaminA,
    val off: Boolean = false,
    val basis: ReferenceBasis = ReferenceBasis.DailyValue,
    /** `700 µg`, or `2,300 mg limit`. */
    val suggested: String = "",
    /** The suggestion as a bare number, for the field's placeholder: `700`. */
    val suggestedNumber: String = "",
    val ownText: String = "",
    val own: Boolean = false,
    val invalid: Boolean = false,
    val onToday: Boolean = false,
    /** Today already lists four and not this one. */
    val todayFull: Boolean = false,
    /** This is the only one on Today, so it cannot come off. */
    val todayLast: Boolean = false,
)

object MicroTargetsStateBuilder {

    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        today: LocalDate,
        backToTodayScreen: Boolean = false,
        goalsAndEnergy: Boolean = false,
    ): MicroTargetsState {
        if (off(settings)) return MicroTargetsState(loaded = true, backToTodayScreen = backToTodayScreen, off = true)
        val references = settings.microReferences(today)
        val onToday = settings.todayMicronutrients
        return MicroTargetsState(
            loaded = true,
            backToTodayScreen = backToTodayScreen,
            basis = references.basis,
            // The fields are on Resting energy only while it calculates, and only with the flag.
            canAddDetails = goalsAndEnergy && settings.restingSource == RestingSource.Calculate && references.basis != ReferenceBasis.AgeAndSex,
            groups = MicroGroup.entries.map { group ->
                MicroTargetGroup(
                    group = group,
                    rows = Micronutrient.inGroup(group).map { micro ->
                        MicroTargetRow(micro, policy.microReference(micro, references), references.isOwn(micro), micro in onToday)
                    },
                )
            },
        )
    }

    fun sheet(
        settings: Settings,
        policy: VisibilityPolicy,
        today: LocalDate,
        micro: Micronutrient,
        typed: String? = null,
    ): MicroTargetSheetState {
        if (off(settings)) return MicroTargetSheetState(loaded = true, micro = micro, off = true)
        val references = settings.microReferences(today)
        val onToday = settings.todayMicronutrients
        val own = references.own[micro]
        return MicroTargetSheetState(
            loaded = true,
            micro = micro,
            basis = references.basis,
            suggested = policy.microReference(micro, references.suggested(micro)),
            suggestedNumber = plain(references.suggested(micro)),
            ownText = typed ?: own?.let(::plain).orEmpty(),
            own = own != null,
            invalid = typed != null && typed.isNotEmpty() && parseOwn(micro, typed) == null,
            onToday = micro in onToday,
            todayFull = micro !in onToday && onToday.size >= Settings.TODAY_NUTRIENTS_MAX,
            todayLast = micro in onToday && onToday.size == 1,
        )
    }

    /** A typed daily amount, or null when it is not one ([MicroReferences.isValidOwn]). */
    fun parseOwn(micro: Micronutrient, text: String): Double? =
        text.toDoubleOrNull()?.takeIf { MicroReferences.isValidOwn(micro, it) }

    /** A number for the field: no grouping, which the field would read as a decimal point, and up to two places. */
    private fun plain(amount: Double): String {
        val hundredths = (amount * HUNDREDTHS).roundToLong()
        val fraction = (hundredths % HUNDREDTHS.toLong()).toString().padStart(2, '0').trimEnd('0')
        val whole = hundredths / HUNDREDTHS.toLong()
        return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
    }

    private const val HUNDREDTHS = 100.0

    private fun off(settings: Settings): Boolean = settings.numbers.level(Metric.Micros) == Visibility.Hide
}
