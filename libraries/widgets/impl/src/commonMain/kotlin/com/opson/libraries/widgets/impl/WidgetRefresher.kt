package com.opson.libraries.widgets.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshotSource
import com.opson.libraries.widgets.WidgetsEnabled
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Keeps the platform's widgets showing today's snapshot: publishes each new one from
 * [WidgetSnapshotSource] while `release.widgets` is on.
 *
 * [AutoInit] because a widget should be current whenever the app has run, not only once someone
 * opens a screen. At construction it only watches the flag. With the flag off nothing else runs:
 * no repository read, no platform write, no widget reload. A flip from on to off within a run
 * clears what it published, so a widget falls back to "Open Opson" instead of freezing on a
 * day that is gone.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class WidgetRefresher(
    private val config: AppConfigFlow,
    private val source: WidgetSnapshotSource,
    private val publisher: WidgetPublisher,
    private val appScope: AppCoroutineScope,
) : AutoInit {

    private var published = false

    private val started = appScope.launch {
        config.map(WidgetsEnabled::isOn).distinctUntilChanged().collectLatest { on ->
            if (on) {
                source.observe().collect { snapshot ->
                    Catching { publisher.publish(snapshot) }
                        .onSuccess { published = true }
                        .logOnFailure { "Publishing the widget snapshot failed" }
                }
            } else if (published) {
                Catching { publisher.clear() }
                    .onSuccess { published = false }
                    .logOnFailure { "Clearing the widget snapshot failed" }
            }
        }
    }
}
