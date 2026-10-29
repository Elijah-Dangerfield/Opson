package com.opson.libraries.core

/**
 * The phone in plain words, for the line a bug report shows under what it sends: the model and
 * the system version, as in "Google Pixel 8, Android 15" or "iPhone15,4, iOS 18.1". Crash
 * reporting records the same facts on every event, so the line tells the person what goes out
 * rather than adding to it.
 */
interface DeviceDescription {
    fun summary(): String
}
