package com.opson.server.domain

/**
 * Authoritative rules for an acceptable display name. Length is measured on
 * the trimmed name; emoji are rejected outright.
 *
 * The client keeps a copy of these rules (`:libraries:identity`'s
 * DisplayNameRules) for immediate feedback so an obviously-bad name doesn't
 * cost a round-trip — this object is the backstop that actually gates the
 * write. Keep the two in sync when the rules change (the server is a
 * standalone JVM module, so the code can't be shared directly).
 */
object DisplayNameRules {
    /** Minimum trimmed length — rules out 1–2 char throwaways. */
    const val MIN_LENGTH: Int = 3

    /** Server-side cap. The client may clamp lower for UX. */
    const val MAX_LENGTH: Int = 32

    /**
     * True when [raw] is an acceptable display name: within length bounds
     * (after trimming) and free of emoji.
     */
    fun isValid(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.length in MIN_LENGTH..MAX_LENGTH && !containsEmoji(trimmed)
    }

    /**
     * True if [text] contains any emoji code point. Tests each Unicode code
     * point against the common emoji blocks.
     */
    fun containsEmoji(text: String): Boolean =
        text.codePoints().anyMatch { cp -> isEmojiCodePoint(cp) }

    private fun isEmojiCodePoint(cp: Int): Boolean = when {
        cp in 0x1F000..0x1FAFF -> true       // emoticons, pictographs, transport, flags, supplemental
        cp in 0x2600..0x27BF -> true         // misc symbols + dingbats (☀ ✂ ✅ …)
        cp in 0x2300..0x23FF -> true         // misc technical (⌚ ⏰ ⏳ …)
        cp in 0x2B00..0x2BFF -> true         // misc symbols & arrows (⭐ ⬅ …)
        cp in 0x2190..0x21FF -> true         // arrows (↔ ↩ …) — commonly emoji-presented
        cp in 0xFE00..0xFE0F -> true         // variation selectors (emoji vs text presentation)
        cp == 0x200D -> true                 // zero-width joiner (emoji sequences)
        cp == 0x20E3 -> true                 // combining enclosing keycap (1️⃣ …)
        cp == 0x2122 || cp == 0x2139 -> true // ™ ℹ
        cp == 0x24C2 -> true                 // Ⓜ
        cp == 0x3030 || cp == 0x303D -> true // 〰 〽
        cp == 0x3297 || cp == 0x3299 -> true // ㊗ ㊙
        else -> false
    }
}
