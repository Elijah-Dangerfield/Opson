package com.opson.libraries.ui.qr

import java.io.File
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Writes share-link codes as PNGs under `build/qr-check/`, so a person (or the macOS check in
 * docs/plans/recipes.md) can point a real reader at them, and inflates one with the JDK to
 * prove the zlib stream inside is valid.
 */
class QrPngFileTest {

    private val links = listOf(
        "https://www.opsontracker.com/r/abcd2345",
        "https://www.opsontracker.com/r/zzzz9999",
        "opson://recipe/k7m2p9qx",
        "Opson " + "recipe ".repeat(25),
    )

    @Test
    fun writesReadableImages() {
        val dir = File("build/qr-check").apply { mkdirs() }
        links.forEachIndexed { i, text ->
            val png = QrPng.encode(QrCode.encode(text))
            File(dir, "code-$i.png").writeBytes(png)
            File(dir, "code-$i.txt").writeText(text)
        }
    }

    @Test
    fun theImageDataInflates() {
        val code = QrCode.encode(links.first())
        val png = QrPng.encode(code, scale = 2)
        val idat = chunk(png, "IDAT")
        val side = (code.size + 2 * QrPng.QUIET_ZONE) * 2
        val expected = ((side + 7) / 8 + 1) * side
        val inflater = Inflater().apply { setInput(idat) }
        val out = ByteArray(expected + 16)
        val read = inflater.inflate(out)
        assertEquals(expected, read)
        assertEquals(true, inflater.finished(), "the adler and the final block are right")
    }

    private fun chunk(png: ByteArray, type: String): ByteArray {
        var at = 8
        while (at < png.size) {
            val length = (0..3).fold(0) { acc, i -> (acc shl 8) or (png[at + i].toInt() and 0xFF) }
            val name = png.copyOfRange(at + 4, at + 8).decodeToString()
            if (name == type) return png.copyOfRange(at + 8, at + 8 + length)
            at += 12 + length
        }
        error("no $type chunk")
    }
}
