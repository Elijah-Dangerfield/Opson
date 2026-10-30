package com.opson.features.foods

import com.opson.libraries.navigation.Route

/**
 * A recipe link (`https://www.opsontracker.com/r/<code>`, `opson://recipe/<code>`) as the page it
 * opens. App.kt asks before handing a link to the nav graph.
 */
interface RecipeLinkRoutes {
    /** The app's page for a recipe link, or null for any other link or with `release.recipes` off. */
    fun routeFor(url: String): Route?

    /**
     * The website's page for a recipe link, whether or not the app can show it, at an address the
     * app does not claim. Null for any other link.
     */
    fun pageFor(url: String): String?
}
