package com.opson.features.onboarding.impl

import kotlin.random.Random

/**
 * Display names like "QuietFox72", "BoldOtter41". Opson has no social surface,
 * so onboarding never asks for a name: the guest account is created with one
 * of these and nobody sees it.
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
