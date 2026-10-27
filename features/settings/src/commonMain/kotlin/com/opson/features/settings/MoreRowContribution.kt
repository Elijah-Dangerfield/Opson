package com.opson.features.settings

import com.opson.libraries.navigation.Route
import com.opson.libraries.ui.components.icon.IconResource
import org.jetbrains.compose.resources.StringResource

/**
 * A row another module puts on More, below the Account card, under a label of its own. Rows that
 * share a [section] share a card. Contribute one with
 * `@ContributesBinding(AppScope::class, multibinding = true)` and register [route] in the same
 * module's `FeatureEntryPoint`.
 *
 * This is how a module that is not in every build adds to More: More draws whatever the set holds,
 * so leaving the module out of a build (`:features:qa` on the store channel) leaves the row out
 * with it. [visible] is for a module that is in the build but should still hide at run time.
 */
interface MoreRowContribution {
    /** The label above the card, as More's own "About" and "Account". */
    val section: StringResource
    val title: StringResource
    val subtitle: StringResource?
    val icon: IconResource

    fun route(): Route

    /** Asked whenever More builds its list, and again on a tap. */
    fun visible(): Boolean = true
}
