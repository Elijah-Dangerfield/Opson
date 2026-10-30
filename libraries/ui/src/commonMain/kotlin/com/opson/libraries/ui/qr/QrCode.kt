package com.opson.libraries.ui.qr

import kotlin.math.abs
import kotlin.math.max

/**
 * A QR code: a square of [size] modules, dark where [isDark] says. Made by [encode] from text,
 * in byte mode at error correction level M (about 15% of the code can be lost and still read),
 * versions 1 to 10, which holds 213 bytes: a share link is about 40.
 *
 * Written for the app's own links rather than taken as a dependency. The construction follows
 * ISO/IEC 18004 as Project Nayuki's reference implementation lays it out (MIT): function
 * patterns, Reed-Solomon over GF(256), interleaving, the zigzag fill and the eight masks scored
 * by the four penalty rules. `QrCodeTest` checks the parts against the standard's numbers.
 */
class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {

    val size: Int get() = modules.size

    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 10

        /** [text] as UTF-8 in the smallest version that holds it. Throws when it is longer than version 10 holds. */
        fun encode(text: String): QrCode {
            val data = text.encodeToByteArray()
            val version = (MIN_VERSION..MAX_VERSION).firstOrNull { dataBits(it, data.size) <= dataCodewords(it) * 8 }
                ?: throw IllegalArgumentException("Too long for a QR code this encoder makes (${data.size} bytes)")
            return Builder(version).build(dataCodewordsFor(version, data))
        }

        // ---- capacity ----

        private fun countBits(version: Int): Int = if (version <= 9) 8 else 16

        private fun dataBits(version: Int, length: Int): Int =
            if (length >= (1 shl countBits(version))) Int.MAX_VALUE else 4 + countBits(version) + 8 * length

        /** Modules left for data and error correction once the function patterns are drawn. */
        internal fun rawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64
            if (version >= 2) {
                val align = version / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (version >= 7) result -= 36
            }
            return result
        }

        internal fun dataCodewords(version: Int): Int =
            rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version]

        /** The byte-mode segment, the terminator and the pad bytes, as data codewords. */
        private fun dataCodewordsFor(version: Int, data: ByteArray): ByteArray {
            val bits = BitBuffer()
            bits.append(MODE_BYTE, 4)
            bits.append(data.size, countBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            val capacity = dataCodewords(version) * 8
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = PAD_A
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = if (pad == PAD_A) PAD_B else PAD_A
            }
            return bits.toBytes()
        }

        // ---- Reed-Solomon over GF(2^8), modulus x^8 + x^4 + x^3 + x^2 + 1 ----

        internal fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * GF_MODULUS)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }

        internal fun rsDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = gfMultiply(root, 2)
            }
            return result
        }

        internal fun rsRemainder(data: ByteArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
            }
            return result
        }

        // ---- tables for level M, indexed by version (0 unused) ----

        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        /** Level M's two format bits. */
        private const val ECC_FORMAT_BITS = 0
        private const val MODE_BYTE = 0x4
        private const val PAD_A = 0xEC
        private const val PAD_B = 0x11
        private const val GF_MODULUS = 0x11D
        private const val FORMAT_GENERATOR = 0x537
        private const val FORMAT_XOR = 0x5412
        private const val VERSION_GENERATOR = 0x1F25
        private const val N1 = 3
        private const val N2 = 3
        private const val N3 = 40
        private const val N4 = 10
    }

    /** Draws one version: the function patterns, the codewords, then the best mask. */
    private class Builder(private val version: Int) {
        private val size = version * 4 + 17
        private val modules = Array(size) { BooleanArray(size) }
        private val isFunction = Array(size) { BooleanArray(size) }

        fun build(data: ByteArray): QrCode {
            drawFunctionPatterns()
            drawCodewords(addEccAndInterleave(data))
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask)
                drawFormatBits(mask)
                val penalty = penalty()
                if (penalty < bestPenalty) {
                    best = mask
                    bestPenalty = penalty
                }
                applyMask(mask)
            }
            applyMask(best)
            drawFormatBits(best)
            return QrCode(version, best, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val positions = alignmentPositions()
            val last = positions.size - 1
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val onFinder = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)
                    if (!onFinder) drawAlignment(positions[i], positions[j])
                }
            }
            // A placeholder for the format bits, so the codewords leave room for them.
            drawFormatBits(0)
            drawVersion()
        }

        private fun drawFinder(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val distance = max(abs(dx), abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) set(xx, yy, distance != 2 && distance != 4)
                }
            }
        }

        private fun drawAlignment(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) set(x + dx, y + dy, max(abs(dx), abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val count = version / 7 + 2
            val step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2
            val result = IntArray(count)
            result[0] = 6
            var position = size - 7
            for (i in count - 1 downTo 1) {
                result[i] = position
                position -= step
            }
            return result
        }

        private fun drawFormatBits(mask: Int) {
            val data = ECC_FORMAT_BITS shl 3 or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * FORMAT_GENERATOR) }
            val bits = (data shl 10 or rem) xor FORMAT_XOR
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * VERSION_GENERATOR) }
            val bits = version shl 12 or rem
            for (i in 0 until 18) {
                val bit = (bits ushr i) and 1 != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, bit)
                set(b, a, bit)
            }
        }

        private fun addEccAndInterleave(data: ByteArray): ByteArray {
            val blocks = BLOCKS[version]
            val eccLength = ECC_PER_BLOCK[version]
            val raw = rawDataModules(version) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLength = raw / blocks
            val divisor = rsDivisor(eccLength)
            var k = 0
            val all = List(blocks) { i ->
                val length = shortLength - eccLength + if (i < shortBlocks) 0 else 1
                val chunk = data.copyOfRange(k, k + length)
                k += length
                val block = chunk.copyOf(shortLength + 1)
                val ecc = rsRemainder(chunk, divisor)
                for (e in ecc.indices) block[block.size - eccLength + e] = ecc[e].toByte()
                block
            }
            val result = ByteArray(raw)
            var n = 0
            for (i in all[0].indices) {
                for (j in all.indices) {
                    // The short blocks have no byte at the padding position.
                    if (i != shortLength - eccLength || j >= shortBlocks) result[n++] = all[j][i]
                }
            }
            return result
        }

        private fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = (right + 1) and 2 == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < data.size * 8) {
                            modules[y][x] = (data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
                }
            }
        }

        /** The standard's four penalty rules; the mask with the lowest score is used. */
        private fun penalty(): Int {
            var result = 0
            for (line in 0 until size) {
                result += runPenalty { modules[line][it] }
                result += runPenalty { modules[it][line] }
            }
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = modules[y][x]
                    if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += N2
                }
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
            return result + k * N4
        }

        /** Rules 1 and 3 along one row or column: long runs, and the finder's 1:1:3:1:1 with four light either side. */
        private fun runPenalty(at: (Int) -> Boolean): Int {
            var result = 0
            var run = 1
            for (i in 1 until size) {
                if (at(i) == at(i - 1)) {
                    run++
                } else {
                    if (run >= 5) result += N1 + run - 5
                    run = 1
                }
            }
            if (run >= 5) result += N1 + run - 5
            for (i in 0..size - FINDER_LIKE.size) {
                if (FINDER_LIKE.indices.all { at(i + it) == FINDER_LIKE[it] }) {
                    val before = (i - 4 until i).all { it < 0 || !at(it) }
                    val after = (i + FINDER_LIKE.size until i + FINDER_LIKE.size + 4).all { it >= size || !at(it) }
                    if (before || after) result += N3
                }
            }
            return result
        }

        private companion object {
            val FINDER_LIKE = booleanArrayOf(true, false, true, true, true, false, true)
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += (value ushr i) and 1 != 0
        }

        fun toBytes(): ByteArray = ByteArray(bits.size / 8) { index ->
            var b = 0
            for (i in 0 until 8) if (bits[index * 8 + i]) b = b or (0x80 ushr i)
            b.toByte()
        }
    }
}
