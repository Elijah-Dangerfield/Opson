package com.opson.server.domain

/** The image types a meal photo may arrive as. Read from the bytes, never from what the client says. */
enum class ImageType(val mediaType: String) {
    Jpeg("image/jpeg"),
    Png("image/png"),
    WebP("image/webp"),
    ;

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** The type the magic bytes name, or null for anything else (HEIC, GIF, a PDF, garbage). */
        fun sniff(bytes: ByteArray): ImageType? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> Jpeg
            bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] } -> Png
            bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 12) == "WEBP" -> WebP
            else -> null
        }

        private fun ByteArray.ascii(from: Int, to: Int): String = String(copyOfRange(from, to), Charsets.US_ASCII)
    }
}

/**
 * One food a model saw in a photo. [unit] is a household measure or a size
 * ("cup", "tbsp", "slice", "medium") or null; [grams] is the model's weight
 * estimate for the whole amount, used when the catalog has no matching
 * portion.
 */
data class EstimatedFood(
    val name: String,
    val quantity: Double,
    val unit: String?,
    val grams: Double?,
)

sealed interface EstimateResult {
    data class Estimated(val foods: List<EstimatedFood>) : EstimateResult

    /** No provider key is set: photo estimates are off on this server. */
    data object NotConfigured : EstimateResult

    /**
     * The provider could not answer: a timeout, an error, a refusal, output
     * that did not parse. [reason] is a short code for the log line, never
     * anything from the photo or the note.
     */
    data class Failed(val reason: String) : EstimateResult
}

/**
 * Reads a meal photo into a list of foods with amounts. The image and the
 * note are the person's meal: an implementation keeps neither and logs
 * neither, only counts and timings.
 */
interface EstimateProvider {
    suspend fun estimate(image: ByteArray, type: ImageType, note: String?): EstimateResult
}

/** What `POST /v1/describe/photo` answers with, after catalog matching. */
sealed interface PhotoDescribeResult {
    data class Described(val items: List<DescribedItem>) : PhotoDescribeResult
    data object NotConfigured : PhotoDescribeResult
    data class Failed(val reason: String) : PhotoDescribeResult
}

/** Limits for `POST /v1/describe/photo`. */
object PhotoLimits {
    /** Checked against `Content-Length` before the body is read, and while it is read. */
    const val MAX_BODY_BYTES = 6L * 1024 * 1024

    /** The decoded image. The app sends a JPEG of at most 1568 px, far under this. */
    const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

    const val MAX_NOTE = 300
}
