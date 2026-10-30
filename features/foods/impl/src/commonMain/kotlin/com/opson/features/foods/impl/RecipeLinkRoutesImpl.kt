package com.opson.features.foods.impl

import com.opson.features.foods.PublicRecipeRoute
import com.opson.features.foods.RecipeLinkRoutes
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipesRelease
import com.opson.libraries.navigation.Route
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Only links: a bare code someone types is the scanner's business, not a URL's. */
@ContributesBinding(AppScope::class)
@Inject
class RecipeLinkRoutesImpl(private val release: RecipesRelease) : RecipeLinkRoutes {

    override fun routeFor(url: String): Route? {
        if (!url.contains("://")) return null
        val code = RecipeLinks.codeFrom(url) ?: return null
        return if (release.isOn()) PublicRecipeRoute(code) else null
    }
}
