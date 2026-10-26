package com.opson.features.settings.impl.widgets

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject

/**
 * The Widgets page: the person's own widget as it looks today, from the same snapshot the app
 * writes for the real one. No `Display` here: the snapshot is already the policy's output.
 *
 * @property snapshot null until the first one arrives.
 * @property today the snapshot's own first day, so the preview draws today's entry.
 */
data class WidgetsState(
    val snapshot: WidgetSnapshot? = null,
    val today: LocalDate? = null,
)

object WidgetsStateBuilder {
    fun build(snapshot: WidgetSnapshot): WidgetsState =
        WidgetsState(snapshot = snapshot, today = snapshot.days.firstOrNull()?.day?.let(LocalDate::parse))
}

@Inject
class WidgetsViewModel(
    source: WidgetSnapshotSource,
) : SEAViewModel<WidgetsState, WidgetsEvent, WidgetsAction>(initialStateArg = WidgetsState()) {

    init {
        viewModelScope.launch {
            source.observe().collect { takeAction(WidgetsAction.SnapshotArrived(it)) }
        }
    }

    override suspend fun handleAction(action: WidgetsAction) {
        when (action) {
            is WidgetsAction.SnapshotArrived -> action.updateState { WidgetsStateBuilder.build(action.snapshot) }
            WidgetsAction.BackTapped -> sendEvent(WidgetsEvent.Close)
        }
    }
}

sealed interface WidgetsEvent {
    data object Close : WidgetsEvent
}

sealed interface WidgetsAction {
    data class SnapshotArrived(val snapshot: WidgetSnapshot) : WidgetsAction
    data object BackTapped : WidgetsAction
}
