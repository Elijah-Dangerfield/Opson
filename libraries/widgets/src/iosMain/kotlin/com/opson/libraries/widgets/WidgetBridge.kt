@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.widgets

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Swift side of the iOS widgets: `IOSWidgetBridge` in the app target writes the snapshot JSON
 * to the App Group's UserDefaults, where the `OpsonWidgets` extension reads it, and asks WidgetKit
 * to reload. WidgetKit has no Objective-C API, so Kotlin can't call it directly. Passed into the
 * graph through `IosAppComponent`.
 *
 * Called on the main thread. Never called while `release.widgets` is off. Exported to Swift as
 * plain `WidgetBridge` rather than the framework's prefixed name.
 */
@ObjCName("WidgetBridge", exact = true)
interface WidgetBridge {
    /** Stores [json] for the widgets, or removes what is stored when null, then reloads them. */
    fun write(json: String?)
}
