@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.today.impl.notes

import com.opson.features.today.NotesOrigin
import com.opson.features.today.NotesRoute
import com.opson.features.trends.NotesListLink
import com.opson.libraries.navigation.Route
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Trends' Notes card opens this list, with Trends as the back label. Hidden from ObjC, as its interface is. */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class)
class TodayNotesListLink : NotesListLink {
    override fun route(): Route = NotesRoute(from = NotesOrigin.Trends)
}
