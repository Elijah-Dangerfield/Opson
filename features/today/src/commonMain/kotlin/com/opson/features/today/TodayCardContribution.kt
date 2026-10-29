@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.today

import androidx.compose.runtime.Composable
import com.opson.libraries.navigation.Router
import kotlinx.datetime.LocalDate
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A card another module puts in Today's list, after Fasting. Contribute one with
 * `@ContributesBinding(AppScope::class, multibinding = true)`; Today draws whatever the set holds,
 * so the module owning the card owns its state, its copy and where it navigates.
 *
 * Hidden from the iOS header, as Swift never uses it (see `MoreRowContribution`).
 */
@HiddenFromObjC
interface TodayCardContribution {
    /** Stable and unique in Today's list. */
    val key: String

    /**
     * The card for [day], the day Today is showing, or null when there is none. Called in the
     * Today screen's composition, outside the list, so it can hold a view model of its own; the
     * returned content is drawn as a list item.
     */
    @Composable
    fun card(day: LocalDate, router: Router): (@Composable () -> Unit)?
}
