package com.opson.features.settings

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every [NumbersChange] survives the trip through [PauseBeforeMoreRoute]'s string arguments, and
 * a route nothing could have built reads back as null rather than as some other change.
 *
 * Not covered: that the route registers and navigates (the settings entry point, run in the app).
 */
class PauseBeforeMoreRouteTest {

    @Test
    fun everyChangeRoundTrips() {
        val changes = buildList {
            PresetId.entries.forEach { add(NumbersChange.PickPreset(it)) }
            Metric.entries.forEach { metric ->
                Visibility.entries.forEach { add(NumbersChange.Override(metric, it)) }
                add(NumbersChange.ClearOverride(metric))
            }
            add(NumbersChange.TurnOffPause)
        }

        changes.forEach { assertEquals(it, it.toRoute().change()) }
    }

    @Test
    fun anUnknownRouteHoldsNoChange() {
        assertNull(PauseBeforeMoreRoute(kind = "lift").change())
        assertNull(PauseBeforeMoreRoute(kind = "preset", presetKey = "everything").change())
        assertNull(PauseBeforeMoreRoute(kind = "override", metricKey = "calories").change())
        assertNull(PauseBeforeMoreRoute(kind = "override", metricKey = "calories", levelKey = "loud").change())
    }
}
