@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIResponder
import platform.UIKit.UITextInputProtocol
import platform.UIKit.UIToolbar
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.reloadInputViews
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.objc.class_addMethod
import platform.objc.object_getClass
import platform.objc.sel_registerName

@Composable
internal actual fun KeyboardDoneBar(active: Boolean, onDone: () -> Unit) {
    if (!active) return
    val currentOnDone by rememberUpdatedState(onDone)
    DisposableEffect(Unit) {
        val owner = DoneBarOwner { currentOnDone() }
        IosDoneBar.attach(owner)
        onDispose { IosDoneBar.detach(owner) }
    }
}

private class DoneBarOwner(val onDone: () -> Unit)

/**
 * The one Done bar, lent to whichever field is focused.
 *
 * Compose Multiplatform has no API for an input accessory: the keyboard belongs to its own
 * text input view, a UIKit class it creates per input session and does not let us subclass. So the
 * `inputAccessoryView` getter is added to that class at runtime, answering with the bar while a
 * field holds it and nil otherwise, which leaves every other field's keyboard exactly as it was.
 * `class_addMethod` only adds: it cannot replace an accessory a class defines itself.
 *
 * UIKit reads the getter when the view becomes first responder, and again on `reloadInputViews`,
 * which is what [refresh] asks for once a field takes or gives up the bar.
 */
private object IosDoneBar {
    private var owner: DoneBarOwner? = null
    private var refreshScheduled = false

    private val toolbar: UIToolbar by lazy {
        UIToolbar(frame = CGRectMake(0.0, 0.0, 0.0, BAR_HEIGHT)).apply {
            autoresizingMask = UIViewAutoresizingFlexibleWidth
            setItems(
                listOf(
                    UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace, menu = null),
                    UIBarButtonItem(
                        barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemDone,
                        primaryAction = UIAction.actionWithHandler { _ -> owner?.onDone?.invoke() },
                    ),
                ),
                animated = false,
            )
            sizeToFit()
        }
    }

    fun attach(next: DoneBarOwner) {
        owner = next
        scheduleRefresh()
    }

    fun detach(leaving: DoneBarOwner) {
        // Focus moving field to field attaches the next owner before the last one detaches.
        if (owner !== leaving) return
        owner = null
        scheduleRefresh()
    }

    /** What the added getter answers: the bar while a field holds it. */
    fun accessoryView(): COpaquePointer? = if (owner == null) null else interpretCPointer<COpaque>(toolbar.objcPtr())

    // One refresh per run loop turn, after focus has settled: hopping between two number fields
    // detaches and attaches in the same frame, and the bar should not blink between them.
    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            refreshScheduled = false
            refresh()
        }
    }

    private fun refresh() {
        val responder = firstResponder() ?: return
        if (responder !is UITextInputProtocol) return
        class_addMethod(object_getClass(responder), sel_registerName("inputAccessoryView"), accessoryViewImp.reinterpret(), "@@:")
        responder.reloadInputViews()
    }

    private fun firstResponder(): UIResponder? {
        UIApplication.sharedApplication.connectedScenes.forEach { scene ->
            (scene as? UIWindowScene)?.windows?.forEach { window ->
                (window as UIWindow).findFirstResponder()?.let { return it }
            }
        }
        return null
    }

    private fun UIView.findFirstResponder(): UIView? {
        if (isFirstResponder) return this
        subviews.forEach { child -> (child as UIView).findFirstResponder()?.let { return it } }
        return null
    }

    private const val BAR_HEIGHT = 44.0
}

/** `- (UIView *)inputAccessoryView`, as a C function the Objective-C runtime can call. */
private val accessoryViewImp = staticCFunction { _: COpaquePointer?, _: COpaquePointer? ->
    IosDoneBar.accessoryView()
}
