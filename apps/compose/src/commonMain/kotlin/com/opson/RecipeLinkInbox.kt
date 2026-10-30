package com.opson

import com.opson.features.foods.RecipeLinkRoutes
import com.opson.libraries.navigation.Route
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where a recipe link goes, so one the app claimed is never dropped. While the app is ready (it is
 * someone's and they finished setup) the link opens the recipe's page in the app, or the website's
 * page when the app can't show it (`release.recipes` off). Before that (signed out, or still in
 * setup) it opens the website's page at once, and the app keeps the link to open its own page
 * once they are in ([resume]), when it can show it by then. Held in memory: a link from a
 * previous run of the app is not news.
 */
@SingleIn(AppScope::class)
@Inject
class RecipeLinkInbox(private val routes: RecipeLinkRoutes) {

    sealed interface Step {
        data class Open(val route: Route) : Step

        data class Web(val url: String) : Step
    }

    private var pending: String? = null

    /** Null when [url] is not a recipe link, for the caller to route some other way. */
    fun receive(url: String, ready: Boolean): Step? {
        val page = routes.pageFor(url) ?: return null
        if (ready) return routes.routeFor(url)?.let { Step.Open(it) } ?: Step.Web(page)
        pending = url
        return Step.Web(page)
    }

    /** The page of the link that came in before the app was ready, once; null when there is none or the app can't show it. */
    fun resume(): Route? {
        val url = pending ?: return null
        pending = null
        return routes.routeFor(url)
    }
}
