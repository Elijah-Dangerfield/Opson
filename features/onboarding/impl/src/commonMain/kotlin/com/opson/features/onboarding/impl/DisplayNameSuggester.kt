package com.opson.features.onboarding.impl

import kotlin.random.Random

/**
 * Fallback-only display name generator. Yields names like "QuietFox72",
 * "BoldOtter41". Used on the PickIdentity step to prefill the name field so
 * the user never lands on a blank input.
 */
internal object DisplayNameSuggester {
    private val adjectives = listOf(
        "Quiet", "Bold", "Sharp", "Lucky", "Steady", "Calm", "Wild",
        "Quick", "Stoic", "Bright", "Cool", "Slick", "Brave", "Sly",
    )

    private val nouns = listOf(
        "Fox", "Otter", "Falcon", "Panda", "Tiger", "Wolf", "Heron", "Lynx",
    )

    fun next(random: Random = Random): String {
        val adj = adjectives.random(random)
        val noun = nouns.random(random)
        val n = random.nextInt(10, 100)
        return "$adj$noun$n"
    }
}
