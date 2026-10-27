package com.opson.libraries.foods

/**
 * Photo estimate (0.2, behind `release.photo`): a photo of a meal in, the
 * foods it probably holds out, in the same shape "Describe it" returns.
 * Online only. The photo goes to the server, which hands it to a vision
 * model and keeps nothing; see docs/plans/screens/photo.md.
 */
interface MealPhotoEstimator {
    /**
     * [jpeg] is the photo as the device re-encoded it, small enough to send.
     * [note] is what the person typed about what a photo cannot show (oil,
     * butter, how it was cooked), or null.
     */
    suspend fun estimate(jpeg: ByteArray, note: String?): PhotoEstimate
}

sealed interface PhotoEstimate {
    /** What the photo seems to hold. Empty when the model saw no food. */
    data class Items(val items: List<ParsedItem>) : PhotoEstimate

    /** The server has no way to read photos right now (no key configured). */
    data object Unavailable : PhotoEstimate

    /** This person has asked for a lot of estimates in a short time. */
    data object TooMany : PhotoEstimate

    /** It did not work this time. [offline] when the phone had no network. */
    data class Failed(val offline: Boolean) : PhotoEstimate
}
