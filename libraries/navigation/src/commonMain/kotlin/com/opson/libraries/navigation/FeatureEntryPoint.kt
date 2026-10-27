package com.opson.libraries.navigation

import androidx.navigation.NavGraphBuilder

/**
 * A feature's slice of the nav graph. Contribute an implementation with
 * `@ContributesBinding(AppScope::class, multibinding = true)`; the app builds every
 * one into its NavHost.
 */
interface FeatureEntryPoint {
    fun NavGraphBuilder.buildNavGraph(router: Router)
}
