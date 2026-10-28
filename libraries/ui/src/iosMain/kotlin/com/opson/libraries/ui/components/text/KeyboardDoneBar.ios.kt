@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.opson.libraries.ui.components.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.invoke
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIResponder
import platform.UIKit.UIScreen
import platform.UIKit.UITextInputProtocol
import platform.UIKit.UIToolbar
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.reloadInputViews
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.objc.class_getMethodImplementation
import platform.objc.class_replaceMethod
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
 * Compose Multiplatform has no API for an input accessory: the keyboard belongs to its own text
 * input view, a UIKit class it creates per input session and does not let us subclass. So that
 * class's `inputAccessoryView` getter is swapped at runtime for one that answers with the bar
 * while a field holds it and otherwise asks the getter it replaced, which leaves every other
 * field's keyboard exactly as it was. Replaced rather than added: the class Kotlin/Native generates
 * for Compose's view already has a getter of its own, so `class_addMethod` would refuse.
 *
 * UIKit reads the getter when the view becomes first responder, and again on `reloadInputViews`,
 * which is what [refresh] asks for once a field takes or gives up the bar.
 */
private object IosDoneBar {
    private var owner: DoneBarOwner? = null
    private var refreshScheduled = false

    /** The text input view last given the bar, so [refresh] can tell it from the next one. */
    private var holder: UIResponder? = null

    /** The getters [install] replaced, by the class they belong to. */
    private val replaced = mutableMapOf<Long, CPointer<AccessoryGetter>>()

    private val toolbar: UIToolbar by lazy {
        val width = UIScreen.mainScreen.bounds.useContents { size.width }
        UIToolbar(frame = CGRectMake(0.0, 0.0, width, BAR_HEIGHT)).apply {
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

    /** What the swapped-in getter answers: the bar while a field holds it, else what UIKit would. */
    fun accessoryView(self: COpaquePointer?, selector: COpaquePointer?): COpaquePointer? {
        if (owner != null) return interpretCPointer(toolbar.objcPtr())
        val view = self?.let { interpretObjCPointerOrNull<UIResponder>(it.rawValue) } ?: return null
        val original = replaced[object_getClass(view).objcPtr().toLong()] ?: return null
        return original.invoke(self, selector)
    }

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
        val outgoing = holder
        holder = null
        val responder = firstResponder() ?: return
        if (responder !is UITextInputProtocol) return
        // Each input session gets a new view, so the view that held the bar being first responder
        // with no owner means its field let go of focus and the view is on its way out. Reloading
        // it would swap the closing number pad for its emptied, default traits: a letter keyboard.
        if (owner == null && responder === outgoing) return
        install(responder)
        if (owner != null) holder = responder
        responder.reloadInputViews()
    }

    private fun install(responder: UIResponder) {
        val type = object_getClass(responder) ?: return
        val key = type.objcPtr().toLong()
        if (key in replaced) return
        val selector = sel_registerName("inputAccessoryView")
        // Taken first, so the fallback is whichever getter the class answered with until now,
        // its own or the one it inherits.
        val original = class_getMethodImplementation(type, selector) ?: return
        replaced[key] = original.reinterpret()
        class_replaceMethod(type, selector, accessoryViewImp.reinterpret(), "@@:")
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

private typealias AccessoryGetter = CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>

/** `- (UIView *)inputAccessoryView`, as a C function the Objective-C runtime can call. */
private val accessoryViewImp = staticCFunction { self: COpaquePointer?, selector: COpaquePointer? ->
    IosDoneBar.accessoryView(self, selector)
}
