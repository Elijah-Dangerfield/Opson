package com.opson.libraries.ui.qr

/**
 * A [QrCode] as a black-on-white PNG, for saving or sending. One bit a pixel, [scale] pixels a
 * module and the standard's four-module quiet zone all round, so a printer or another phone reads
 * it as is. Written by hand (an uncompressed deflate stream inside zlib) so it needs no platform
 * image code: a share-link code at the default scale is about 30 KB.
 */
object QrPng {
    const val DEFAULT_SCALE = 16
    const val QUIET_ZONE = 4

    fun encode(code: QrCode, scale: Int = DEFAULT_SCALE): ByteArray {
        require(scale in 1..MAX_SCALE) { "scale must be 1 to $MAX_SCALE" }
        val modules = code.size + QUIET_ZONE * 2
        val side = modules * scale
        val rowBytes = (side + 7) / 8
        val raw = ByteArray((rowBytes + 1) * side)
        for (py in 0 until side) {
            val offset = py * (rowBytes + 1)
            // Filter type 0 (none) leads each row; the zeros after it are black until set white.
            val my = py / scale - QUIET_ZONE
            for (px in 0 until side) {
                val mx = px / scale - QUIET_ZONE
                if (!code.isDark(mx, my)) {
                    val index = offset + 1 + px / 8
                    raw[index] = (raw[index].toInt() or (0x80 ushr (px % 8))).toByte()
                }
            }
            // Pad bits past the edge are white, like the quiet zone they continue.
            for (px in side until rowBytes * 8) {
                val index = offset + 1 + px / 8
                raw[index] = (raw[index].toInt() or (0x80 ushr (px % 8))).toByte()
            }
        }
        val out = Bytes()
        out.write(SIGNATURE)
        out.chunk("IHDR", Bytes().apply { int(side); int(side); byte(1); byte(0); byte(0); byte(0); byte(0) }.toByteArray())
        out.chunk("IDAT", zlibStored(raw))
        out.chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** zlib around deflate's stored blocks: valid, uncompressed, and a few lines long. */
    private fun zlibStored(data: ByteArray): ByteArray {
        val out = Bytes()
        out.byte(ZLIB_CMF)
        out.byte(ZLIB_FLG)
        var at = 0
        do {
            val length = minOf(MAX_STORED, data.size - at)
            val last = at + length >= data.size
            out.byte(if (last) 1 else 0)
            out.byte(length and 0xFF)
            out.byte(length ushr 8)
            out.byte(length.inv() and 0xFF)
            out.byte((length.inv() ushr 8) and 0xFF)
            out.write(data, at, length)
            at += length
        } while (!last)
        out.int(adler32(data))
        return out.toByteArray()
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1L
        var b = 0L
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % ADLER_MOD
            b = (b + a) % ADLER_MOD
        }
        return ((b shl 16) or a).toInt()
    }

    private fun crc32(type: ByteArray, data: ByteArray): Int {
        var crc = -1
        fun feed(byte: Int) {
            crc = CRC_TABLE[(crc xor byte) and 0xFF] xor (crc ushr 8)
        }
        type.forEach { feed(it.toInt()) }
        data.forEach { feed(it.toInt()) }
        return crc.inv()
    }

    private fun Bytes.chunk(type: String, data: ByteArray) {
        val name = type.encodeToByteArray()
        int(data.size)
        write(name)
        write(data)
        int(crc32(name, data))
    }

    private class Bytes {
        private var buffer = ByteArray(INITIAL)
        private var size = 0

        fun byte(value: Int) {
            grow(1)
            buffer[size++] = value.toByte()
        }

        fun int(value: Int) {
            byte(value ushr 24)
            byte(value ushr 16)
            byte(value ushr 8)
            byte(value)
        }

        fun write(bytes: ByteArray, from: Int = 0, length: Int = bytes.size) {
            grow(length)
            bytes.copyInto(buffer, size, from, from + length)
            size += length
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun grow(by: Int) {
            if (size + by > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + by))
        }

        private companion object {
            const val INITIAL = 1024
        }
    }

    private const val MAX_SCALE = 64
    private const val MAX_STORED = 65_535
    private const val ADLER_MOD = 65_521L
    private const val ZLIB_CMF = 0x78
    private const val ZLIB_FLG = 0x01
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val CRC_POLYNOMIAL = 0xEDB88320.toInt()

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) CRC_POLYNOMIAL xor (c ushr 1) else c ushr 1 }
        c
    }
}
