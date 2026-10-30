package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * A weekly or monthly recap (`release.recaps`), reached from Today's "Your week is ready", from
 * Trends, and from Wrap up the day on a week's last day. A `screen<>`; Trends registers it.
 *
 * @param kind `RecapKind.wire`: `week` or `month`.
 * @param startIso the period's first day, `LocalDate.toString()`. Empty opens the last one that
 *   has ended. Both are strings, so the route needs no type map.
 */
@Serializable
data class RecapRoute(val kind: String = KIND_WEEK, val startIso: String = "") : Route() {
    companion object {
        const val KIND_WEEK = "week"
        const val KIND_MONTH = "month"
    }
}
