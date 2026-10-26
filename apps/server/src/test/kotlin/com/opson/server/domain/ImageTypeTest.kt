package com.opson.server.domain

import com.opson.server.testing.TestImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageTypeTest {
    @Test
    fun readsTheTypeFromTheBytes() {
        assertEquals(ImageType.Jpeg, ImageType.sniff(TestImages.jpeg))
        assertEquals(ImageType.Png, ImageType.sniff(TestImages.png))
        assertEquals(ImageType.WebP, ImageType.sniff(TestImages.webp))
    }

    @Test
    fun anythingElseIsRejected() {
        assertNull(ImageType.sniff(TestImages.gif))
        assertNull(ImageType.sniff(ByteArray(0)))
        assertNull(ImageType.sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(ImageType.sniff("RIFF0000WAVE".toByteArray()))
    }
}
