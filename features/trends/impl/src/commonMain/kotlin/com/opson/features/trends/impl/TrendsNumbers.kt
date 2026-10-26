package com.opson.features.trends.impl

import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.nutrition.NumbersChange
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/** What asking for a numbers change came to. */
sealed interface NumbersRequest {
    data object Applied : NumbersRequest

    /** "Pause before showing more" is on and this shows more: nothing was written. */
    data object NeedsPause : NumbersRequest
}

/**
 * How Nutrients detail and Weight change what numbers the user sees.
 *
 * TODO(NumbersChanger): replace with `NumbersChanger.request(change)` from
 * `:libraries:diary` when it lands, which also emits `mode.changed`. This is the
 * same rule in the meantime, and the only place in this feature that writes
 * visibility: a raise with the pause on writes nothing and asks for the pause.
 */
@Inject
class TrendsNumbers(
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun request(change: NumbersChange): NumbersRequest {
        if (change.needsPause(settings.settings.value.numbers)) return NumbersRequest.NeedsPause
        var paused = false
        settings.update { current ->
            // Checked again inside the atomic update: the pause may have been
            // turned on between the read above and this write.
            if (change.needsPause(current.numbers)) {
                paused = true
                current
            } else {
                current.withNumbers(change.applyTo(current.numbers, clock.now()))
            }
        }
        return if (paused) NumbersRequest.NeedsPause else NumbersRequest.Applied
    }
}
