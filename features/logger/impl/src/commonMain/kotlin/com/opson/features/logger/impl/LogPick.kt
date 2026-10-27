package com.opson.features.logger.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Where in the logger a food was picked. Telemetry counts these; the draft's [LogMethod] follows from it. */
@Serializable
enum class PickSource {
    Search,
    Favorite,
    Usual,
    Recent,
    Saved,

    /** The Foods tab's + opened the logger with this food already picked. */
    Foods,

    /** A line Describe it read out of free text. */
    Describe,

    /** "Save the name only": a name-only custom food made on the spot. */
    Name,

    /** A scanned barcode the catalog knew. */
    Barcode,

    /** A line Photo estimate read from a meal photo, as the person corrected it. */
    Photo,
    ;

    /**
     * The method the item is logged with. A custom food is always `Custom`,
     * however it was found; the server enum has no "usual", so Usual counts
     * as Favorite.
     */
    fun methodFor(ref: FoodRef?): LogMethod = when {
        ref is FoodRef.Custom || this == Name -> LogMethod.Custom
        this == Search -> LogMethod.Search
        this == Favorite || this == Usual -> LogMethod.Favorite
        this == Saved -> LogMethod.Saved
        // A photo line is an estimate read for the person, like Describe; the server has no photo method.
        this == Describe || this == Photo -> LogMethod.Describe
        this == Barcode -> LogMethod.Barcode
        else -> LogMethod.Recent
    }
}

/**
 * One food ready to log: [draft] carries the amount, precision, nutrients and
 * method. [key] is [ref]'s key, or a line's own key for an item with no food
 * behind it. [food] is the catalog food when the logger has it, so logging can
 * mark it used without a lookup.
 */
data class LogPick(
    val key: String,
    val ref: FoodRef?,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
    /** When it was eaten, if the food detail's Time changed it; null logs it as now. */
    val loggedAt: Instant? = null,
)
