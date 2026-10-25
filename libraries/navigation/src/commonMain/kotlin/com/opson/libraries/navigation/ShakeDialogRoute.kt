package com.opson.libraries.navigation

import kotlinx.serialization.Serializable

@Serializable
data class ShakeDialogRoute(
    val headline: String,
    val subtext: String? = null,
) : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)
