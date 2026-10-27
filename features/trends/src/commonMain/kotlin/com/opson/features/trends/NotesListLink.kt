@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.trends

import com.opson.libraries.navigation.Route
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Where the Notes card's "All notes" link goes: the list of every meal note, newest first. The
 * list belongs to another feature, so that feature binds this with
 * `@ContributesBinding(AppScope::class)` and Trends never names its route. With nothing bound,
 * Trends leaves the link off rather than drawing one that goes nowhere. Today binds it
 * (`TodayNotesListLink`), as the Notes list lives with Today.
 *
 * Hidden from the iOS header, as Swift never uses it (see `MoreRowContribution`).
 */
@HiddenFromObjC
fun interface NotesListLink {
    fun route(): Route
}
