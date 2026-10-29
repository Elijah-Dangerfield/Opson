package com.opson.libraries.diary

/**
 * A figure on Today's summary card that can be read another way, and the key the person's choice
 * is kept under in [Settings.todayReadings]. The card's main number is not one of these: it is
 * remembered per goal style in [Settings.todayMainNumberByStyle], as it was before figures flipped.
 */
enum class FlipFigure(val key: String) {
    /** The macro columns under the eaten bar, read together so the row never mixes readings. */
    Macros("macros"),

    /** The older card's "Deficit so far" stat. */
    DeficitSoFar("deficit_so_far"),

    /** The older card's water stat. */
    Water("water"),
}

/**
 * One way to read a [FlipFigure]. [wire] is what [Settings.todayReadings] stores, so renaming an
 * entry never loses a choice; a value no build knows reads as nothing chosen.
 */
enum class Reading(val wire: String) {
    /** What went in: grams eaten, glasses drunk. */
    Eaten("eaten"),

    /** What is still to go to the target: grams left, glasses to go. */
    Left("left"),

    /** Eaten as a percent of the target. */
    Percent("percent"),

    /** Burned so far less eaten. */
    Deficit("deficit"),

    /** Burned so far, alone. */
    Burned("burned"),
    ;

    companion object {
        fun fromWire(value: String): Reading? = entries.firstOrNull { it.wire == value }
    }
}

/** The reading picked for [figure] on the card, if one was. */
fun Settings.reading(figure: FlipFigure): Reading? = todayReadings[figure.key]?.let(Reading::fromWire)

/** [reading] remembered for [figure], across days, restarts and the person's other phones. */
fun Settings.withReading(figure: FlipFigure, reading: Reading): Settings =
    copy(todayReadings = todayReadings + (figure.key to reading.wire))

/**
 * Which of a figure's readings shows, and what a tap moves to. The readings a figure may show
 * come from the levels (Today decides them); this only walks them, so a choice the mode no longer
 * allows falls back to the first one rather than showing.
 */
object Flips {
    /** [wanted] when it is one of [options], else the first; null when there are none. */
    fun <T> pick(options: List<T>, wanted: T?): T? = wanted?.takeIf { it in options } ?: options.firstOrNull()

    /** The reading after [current], round again at the end; null when there is nothing to flip to. */
    fun <T> next(options: List<T>, current: T): T? {
        if (options.size < 2) return null
        val index = options.indexOf(current)
        return options[(index + 1) % options.size]
    }
}
