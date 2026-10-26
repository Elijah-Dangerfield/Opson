package com.opson.libraries.navigation

import kotlinx.coroutines.flow.StateFlow

/**
 * Lets a tab screen put the bottom bar away for a while, as Today does while foods are being
 * selected (design sync 2, 2.12): the selection panel takes the bar's place. The app shell reads
 * [hidden]; a screen asks with [hide] under its own [owner] key and must give it back, since the
 * bar stays away while any owner still holds it.
 */
interface TabBar {
    val hidden: StateFlow<Boolean>

    fun hide(owner: String, hidden: Boolean)
}
