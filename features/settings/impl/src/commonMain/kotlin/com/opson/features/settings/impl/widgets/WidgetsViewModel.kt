package com.opson.features.settings.impl.widgets

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** The mode the widget follows, named over it (design sync 2: "Full numbers"). */
    val preset: PresetId? = null,
)

object WidgetsStateBuilder {
    fun build(snapshot: WidgetSnapshot, preset: PresetId? = null): WidgetsState =
        WidgetsState(snapshot = snapshot, today = snapshot.days.firstOrNull()?.day?.let(LocalDate::parse), preset = preset)
}

@Inject
class WidgetsViewModel(
    source: WidgetSnapshotSource,
    settingsRepository: SettingsRepository,
) : SEAViewModel<WidgetsState, WidgetsEvent, WidgetsAction>(initialStateArg = WidgetsState()) {

    init {
        viewModelScope.launch {
            val presets = settingsRepository.observe().map { it.numbers.preset }.distinctUntilChanged()
            combine(source.observe(), presets, WidgetsAction::SnapshotArrived).collect { takeAction(it) }
        }
    }

    override suspend fun handleAction(action: WidgetsAction) {
        when (action) {
            is WidgetsAction.SnapshotArrived -> action.updateState { WidgetsStateBuilder.build(action.snapshot, action.preset) }
            WidgetsAction.BackTapped -> sendEvent(WidgetsEvent.Close)
        }
    }
}

sealed interface WidgetsEvent {
    data object Close : WidgetsEvent
}

sealed interface WidgetsAction {
    data class SnapshotArrived(val snapshot: WidgetSnapshot, val preset: PresetId? = null) : WidgetsAction
    data object BackTapped : WidgetsAction
}
