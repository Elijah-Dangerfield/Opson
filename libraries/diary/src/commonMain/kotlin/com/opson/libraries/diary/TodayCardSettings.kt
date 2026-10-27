package com.opson.libraries.diary

import com.opson.libraries.nutrition.TargetStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How Today's summary card draws energy in All numbers (design sync 4, 13.1 and More › Today
 * screen, 13.6). [Rings] needs a single target; with a range the card draws [InOut] instead.
 */
@Serializable
enum class SummaryCardPreset {
    @SerialName("in_out")
    InOut,

    @SerialName("range_bar")
    RangeBar,

    @SerialName("rings")
    Rings,

    @SerialName("equation")
    Equation,
}

/**
 * What the card's main number is (13.6b). [Follow] takes the goal style's own: the gap to the
 * goal ([Goal]: "To your range", "Left to target", "To your surplus"), or [Balance] ("Deficit",
 * "Surplus") for a set deficit. The card's label toggles between them; this is what it resets to.
 */
@Serializable
enum class MainNumberChoice {
    @SerialName("follow")
    Follow,

    @SerialName("goal")
    Goal,

    @SerialName("balance")
    Balance,

    @SerialName("eaten")
    Eaten,
    ;

    companion object {
        fun fromWire(value: String): MainNumberChoice? = entries.firstOrNull { it.wire == value }
    }

    val wire: String get() = name.lowercase()
}

/** The key a goal style's main number is remembered under in [Settings.todayMainNumberByStyle]. */
val TargetStyle.mainNumberKey: String
    get() = when (this) {
        TargetStyle.Range -> "range"
        TargetStyle.Exact -> "exact"
        is TargetStyle.Deficit -> "deficit"
        is TargetStyle.Surplus -> "surplus"
        is TargetStyle.ByDay -> "by_day"
    }

/** The main number picked on the card for the current goal style, if one was. */
val Settings.rememberedMainNumber: MainNumberChoice?
    get() = todayMainNumberByStyle[targets.style.mainNumberKey]?.let(MainNumberChoice::fromWire)

/** [choice] remembered for the current goal style, across days and restarts. */
fun Settings.rememberMainNumber(choice: MainNumberChoice): Settings =
    copy(todayMainNumberByStyle = todayMainNumberByStyle + (targets.style.mainNumberKey to choice.wire))

/** The current goal style back on its default main number: picking a style resets it (13.1). */
fun Settings.forgetMainNumber(): Settings = copy(todayMainNumberByStyle = todayMainNumberByStyle - targets.style.mainNumberKey)
