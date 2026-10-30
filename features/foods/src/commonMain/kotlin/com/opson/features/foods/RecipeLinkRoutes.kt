package com.opson.features.foods

import com.opson.libraries.navigation.Route

/**
 * A recipe link (`https://www.opsontracker.com/r/<code>`, `opson://recipe/<code>`) as the page it
 * opens, or null for any other link or with `release.recipes` off. App.kt asks before handing a
 * link to the nav graph, and routes it only while the app is someone's.
 */
fun interface RecipeLinkRoutes {
    fun routeFor(url: String): Route?
}
