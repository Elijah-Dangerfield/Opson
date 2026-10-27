package com.opson.libraries.diary

import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where an item sits in the day. [wire] is the value the server stores and the
 * `@SerialName`, so a route arg, a saved meal snapshot and a sync row all spell
 * it the same way.
 */
@Serializable
enum class MealSlot(val wire: String) {
    @SerialName("breakfast")
    Breakfast("breakfast"),

    @SerialName("lunch")
    Lunch("lunch"),

    @SerialName("snack")
    Snack("snack"),

    @SerialName("dinner")
    Dinner("dinner"),

    @SerialName("uncategorized")
    Uncategorized("uncategorized"),
    ;

    val isMeal: Boolean get() = this != Uncategorized

    companion object {
        /** The four meals, in the order Today shows them. */
        val meals: List<MealSlot> = listOf(Breakfast, Lunch, Snack, Dinner)

        fun fromWire(value: String): MealSlot? = entries.firstOrNull { it.wire == value }

        /**
         * The slot a new item lands in when the user did not pick one. With meal
         * groups off everything goes to [Uncategorized], which Today titles
         * "All food".
         */
        fun defaultFor(time: LocalTime, mealGroups: Boolean = true): MealSlot = when {
            !mealGroups -> Uncategorized
            time < LocalTime(10, 30) -> Breakfast
            time < LocalTime(14, 30) -> Lunch
            time < LocalTime(17, 0) -> Snack
            else -> Dinner
        }
    }
}

/**
 * How sure the user is about an amount. Shown on item rows as Weighed / Estimate / Named / Label.
 * A build from before [Label] reads a logged item's "label" as Estimate.
 */
@Serializable
enum class Precision(val wire: String) {
    @SerialName("weighed")
    Weighed("weighed"),

    @SerialName("estimate")
    Estimate("estimate"),

    /** Just the name: no amount, usually no nutrients. */
    @SerialName("named")
    Named("named"),

    /** A serving as the package's label gives it ("From label", design sync 4). */
    @SerialName("label")
    Label("label"),
    ;

    companion object {
        fun fromWire(value: String): Precision? = entries.firstOrNull { it.wire == value }
    }
}

/** How an entry was logged. Only ever counted in aggregate on the server. */
@Serializable
enum class LogMethod(val wire: String) {
    @SerialName("search")
    Search("search"),

    @SerialName("barcode")
    Barcode("barcode"),

    @SerialName("describe")
    Describe("describe"),

    @SerialName("saved")
    Saved("saved"),

    @SerialName("copy")
    Copy("copy"),

    @SerialName("ate_no_details")
    AteNoDetails("ate_no_details"),

    @SerialName("quick_number")
    QuickNumber("quick_number"),

    @SerialName("recent")
    Recent("recent"),

    @SerialName("favorite")
    Favorite("favorite"),

    @SerialName("custom")
    Custom("custom"),
    ;

    companion object {
        fun fromWire(value: String): LogMethod? = entries.firstOrNull { it.wire == value }
    }
}
