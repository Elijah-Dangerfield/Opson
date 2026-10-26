package com.opson.libraries.nutrition

/**
 * Every nutrition number the app can show. [key] is the prototype's name for
 * the metric and the suffix of the `metric:<key>` test tag, which
 * `BlindModeLeakTest` greps for, so it must never change.
 */
enum class Metric(val key: String) {
    Calories("calories"),
    Remaining("remaining"),
    EnergyOut("energyOut"),
    Protein("protein"),
    Carbs("carbs"),
    Fat("fat"),
    Fiber("fiber"),
    Micros("micros"),
    Weight("weight"),
    Water("water"),
    ;

    val isMacro: Boolean get() = this == Protein || this == Carbs || this == Fat || this == Fiber
}

/**
 * How much of a metric the user sees. [score] is what "Pause before showing
 * more" compares: a change that raises the summed score is a raise.
 */
enum class Visibility(val score: Int) {
    Show(3),
    Soften(2),
    Delay(1),
    Hide(0),
}

/** Where a number is rendered. Delayed numbers reveal on some surfaces and not others. */
enum class Surface {
    Today,
    Logger,
    DayClose,
    Week,
    Nutrients,
    Trends,
    Foods,
    Settings,
    Weight,
}
