package com.opson.server.testing

import com.opson.server.domain.EstimateProvider
import com.opson.server.domain.EstimateResult
import com.opson.server.domain.ImageType

/** Answers with [result] and records what it was asked, so a test can see the image and note arrive. */
class FakeEstimateProvider(var result: EstimateResult = EstimateResult.Estimated(emptyList())) : EstimateProvider {
    val calls = mutableListOf<Triple<Int, ImageType, String?>>()

    override suspend fun estimate(image: ByteArray, type: ImageType, note: String?): EstimateResult {
        calls += Triple(image.size, type, note)
        return result
    }
}

/** The smallest byte strings [ImageType.sniff] accepts, padded so they look like a body. */
object TestImages {
    val jpeg: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(64)
    val png: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
    val webp: ByteArray = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(64)
    val gif: ByteArray = "GIF89a".toByteArray() + ByteArray(64)
}
