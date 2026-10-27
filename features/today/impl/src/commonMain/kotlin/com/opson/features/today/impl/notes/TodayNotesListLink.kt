package com.opson.features.today.impl.notes

import com.opson.features.today.NotesRoute
import com.opson.features.trends.NotesListLink
import com.opson.libraries.navigation.Route
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Trends' Notes card opens this list, with Trends as the back label. */
@Inject
@ContributesBinding(AppScope::class)
class TodayNotesListLink : NotesListLink {
    override fun route(): Route = NotesRoute(fromTrends = true)
}
