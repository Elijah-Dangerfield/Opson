package com.opson.libraries.navigation

import com.opson.libraries.core.Catching

fun interface WebLinkLauncher {
    fun open(url: String): Catching<Unit>
}
