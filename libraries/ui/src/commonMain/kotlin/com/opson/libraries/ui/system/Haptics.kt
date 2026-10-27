package com.opson.libraries.ui.system

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/*
 * The app's few haptics, named for the moment rather than the motor, so every screen that means
 * "a food was logged" feels the same. Read the feedback from `LocalHapticFeedback.current` and
 * call one of these from an event or a tap, never from composition or a loop.
 *
 * Compose maps each type per platform. On iOS: confirm is the success notification, press and
 * toggles and the swipe threshold are a light impact, a long press is a medium impact, a tick is
 * the selection click. Android plays its own constants, several of which (confirm from API 30,
 * toggles, ticks and the swipe threshold from API 34) are silent on older versions.
 *
 * Long presses are not here on purpose: `combinedClickable` already plays one when its
 * `onLongClick` fires, so a second would double it.
 */

/** Something the person asked for was saved: a food logged, a paused change applied. */
fun HapticFeedback.confirm() = performHapticFeedback(HapticFeedbackType.Confirm)

/** A light press on a button that opens something, like the bar's + opening the logger. */
fun HapticFeedback.press() = performHapticFeedback(HapticFeedbackType.VirtualKey)

/** A choice moved to another option, as in a segmented control. */
fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.SegmentTick)

/** A switch, or an add-to-basket toggle, turned [on] or off. */
fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** A swipe went past the point where letting go commits it, as swipe-to-delete does. */
fun HapticFeedback.swipeCommitted() = performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
