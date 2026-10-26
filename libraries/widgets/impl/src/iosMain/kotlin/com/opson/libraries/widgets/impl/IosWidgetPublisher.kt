package com.opson.libraries.widgets.impl

import com.opson.libraries.widgets.WidgetBridge
import com.opson.libraries.widgets.WidgetJson
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * iOS widgets through the Swift [WidgetBridge]: it writes the JSON to the App Group the
 * `OpsonWidgets` extension reads and reloads WidgetKit's timelines. On the main thread, as UIKit
 * and WidgetKit calls should be; the caller's coroutine can resume on a worker.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WidgetPublisher::class)
@Inject
class IosWidgetPublisher(private val bridge: WidgetBridge) : WidgetPublisher {

    override suspend fun publish(snapshot: WidgetSnapshot) {
        val json = WidgetJson.encode(snapshot)
        withContext(Dispatchers.Main) { bridge.write(json) }
    }

    override suspend fun clear() {
        withContext(Dispatchers.Main) { bridge.write(null) }
    }
}
