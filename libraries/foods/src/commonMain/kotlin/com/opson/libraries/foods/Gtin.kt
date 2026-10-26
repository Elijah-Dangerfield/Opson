package com.opson.libraries.foods

/**
 * Product codes as GS1 GTINs. The rule matches the importers
 * (`normalize_barcode` in scripts/import_off_foods.py) and the server's
 * `Barcodes.gtin14`: leading zeros never change a GTIN, so a UPC-A, its
 * EAN-13 and its GTIN-14 are one product, and an 8-digit code is read as a
 * UPC-E (the compressed UPC-A on small US packs) before it is read as an
 * EAN-8. Pure, so the scanner and the food cache agree with the server on
 * what "the same barcode" means.
 */
object Gtin {
    const val LENGTH = 14

    /**
     * The 14-digit GTIN for [code], or null when it is not one: wrong length,
     * not digits, a failed check digit (a misread, from a camera), or a
     * placeholder that is mostly zeros.
     */
    fun normalize(code: String): String? {
        if (code.length !in MIN_LENGTH..LENGTH || !code.isDigits()) return null
        val gtin = upcEToUpcA(code)?.padStart(LENGTH, '0')
            ?: code.takeIf { it.length in FORMS && hasValidCheckDigit(it) }?.padStart(LENGTH, '0')
        return gtin?.takeIf { it.trimStart('0').length >= MIN_SIGNIFICANT_DIGITS }
    }

    /** GS1 check digit for the digits before it. Leading zeros do not change it. */
    fun checkDigit(body: String): Int {
        val total = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - total % 10) % 10
    }

    /** The 12-digit UPC-A an 8-digit UPC-E (number system 0 or 1) stands for, or null. */
    fun upcEToUpcA(code: String): String? {
        if (code.length != UPC_E_LENGTH || !code.isDigits() || code[0] !in "01") return null
        val upcA = expandUpcE(code.take(UPC_E_LENGTH - 1)) + code.last()
        return upcA.takeIf(::hasValidCheckDigit)
    }

    /**
     * The 8-digit UPC-E for the six digits some decoders report on their own
     * (number system 0, check digit computed from the expansion), or [code]
     * unchanged when it is not six digits.
     */
    fun completeUpcE(code: String): String {
        if (code.length != UPC_E_DIGITS || !code.isDigits()) return code
        val head = "0$code"
        return head + checkDigit(expandUpcE(head))
    }

    /** The UPC-A, without its check digit, for a UPC-E's number system and six digits. */
    private fun expandUpcE(head: String): String {
        val d = head.substring(1, 7)
        val body = when (d[5]) {
            '0', '1', '2' -> d.substring(0, 2) + d[5] + "0000" + d.substring(2, 5)
            '3' -> d.substring(0, 3) + "00000" + d.substring(3, 5)
            '4' -> d.substring(0, 4) + "00000" + d[4]
            else -> d.substring(0, 5) + "0000" + d[5]
        }
        return head[0] + body
    }

    private fun hasValidCheckDigit(code: String): Boolean = checkDigit(code.dropLast(1)) == code.last() - '0'

    private fun String.isDigits(): Boolean = all { it in '0'..'9' }

    private const val MIN_LENGTH = 8
    private const val UPC_E_LENGTH = 8
    private const val UPC_E_DIGITS = 6
    private val FORMS = setOf(8, 12, 13, 14)

    /** No assigned GTIN is mostly zeros; "00000000000000" and friends are placeholders. */
    private const val MIN_SIGNIFICANT_DIGITS = 7
}
