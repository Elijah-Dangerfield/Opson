package com.opson.libraries.diary.impl

import com.opson.libraries.diary.DiaryItemEditor
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.RecentLogEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RecentLogEventsImpl(private val editor: DiaryItemEditor) : RecentLogEvents {
    private val state = MutableStateFlow<LoggedToMeal?>(null)

    override val latest: StateFlow<LoggedToMeal?> = state.asStateFlow()

    override fun logged(event: LoggedToMeal) {
        state.value = event
    }

    override fun consumed(event: LoggedToMeal) {
        state.compareAndSet(event, null)
    }

    override suspend fun undo(event: LoggedToMeal) {
        // One write for the whole basket, so Today drops it at once rather than a food at a time.
        editor.removeItems(event.itemIds)
        consumed(event)
    }
}
