@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.foods

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Swift half of [LabelTextModel] on iOS: Apple's Foundation Models (iOS 26) asked for a
 * nutrition panel's numbers from its OCR text. Implemented by `IOSLabelModelBridge` in the iOS
 * app and passed into the app's component from `iOSApp.swift`, like the text recognition bridge.
 *
 * Text in, JSON out, so no Kotlin collection crosses: the answer is
 * `{"servingSize":55,"servingUnit":"g","servingsPerContainer":8,"perServing":{"energy_kcal":230},"per100":{}}`
 * with nutrients keyed by their wire names.
 */
@ObjCName("LabelModelBridge", exact = true)
interface LabelModelBridge {
    /**
     * Reads [labelText] (the label's OCR lines, one per line). Calls [callback] exactly once, on any
     * thread. Must not block the caller.
     */
    fun extract(labelText: String, callback: LabelModelCallback)
}

/** Where [LabelModelBridge] answers. Kotlin implements it; Swift only calls it. */
@ObjCName("LabelModelCallback", exact = true)
interface LabelModelCallback {
    /**
     * [json] as described on [LabelModelBridge] when [error] is null. [error] is "unavailable" when
     * this phone has no model to ask (older iOS, Apple Intelligence off or not downloaded yet), and
     * anything else for a failure. [model] names what read it.
     */
    fun finished(json: String?, model: String?, error: String?)
}
