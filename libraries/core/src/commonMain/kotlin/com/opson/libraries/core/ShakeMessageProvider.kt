package com.opson.libraries.core

/**
 * What the shake dialog says. The app's is `ResourceShakeMessageProvider` in `:apps:compose`,
 * where the string resources are; this module has none.
 */
interface ShakeMessageProvider {
    suspend fun getMessage(context: ShakeMessageContext): ShakeMessage
}

data class ShakeMessageContext(
    val shakeCount: Int = 0,
    val intensity: ShakeIntensity = ShakeIntensity.NORMAL,
    val userName: String? = null,
)

data class ShakeMessage(
    val headline: String,
    val subtext: String? = null,
)
