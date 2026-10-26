package com.opson.server.domain

import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A validated sign-up from the website form: [email] lower-cased, [source] null unless it matched the pattern. */
data class WaitlistSignup(val email: String, val source: String?) {
    companion object {
        const val MAX_EMAIL = 254
        private const val MAX_LOCAL = 64
        private val SOURCE = Regex("^[a-z0-9-]{1,32}$")

        // Plausible, not RFC 5322: one @, a local part of the usual characters
        // starting with a letter or digit (so no export cell starts like a
        // spreadsheet formula), and a dotted domain ending in letters.
        private val LOCAL = Regex("^[a-z0-9][a-z0-9.!#$%&'*+/=?^_`{|}~-]*$")
        private val DOMAIN = Regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

        /** The address as stored (trimmed, lower-cased), or null when it is not a plausible email. */
        fun normalizeEmail(raw: String?): String? {
            val email = raw?.trim()?.lowercase() ?: return null
            if (email.isEmpty() || email.length > MAX_EMAIL) return null
            val at = email.lastIndexOf('@')
            if (at <= 0 || email.indexOf('@') != at) return null
            val local = email.substring(0, at)
            val domain = email.substring(at + 1)
            val plausible = local.length <= MAX_LOCAL &&
                LOCAL.matches(local) &&
                !local.endsWith('.') &&
                ".." !in local &&
                DOMAIN.matches(domain)
            return email.takeIf { plausible }
        }

        /** [raw] when it is a page or campaign name, else null. Never a reason to refuse a sign-up. */
        fun normalizeSource(raw: String?): String? = raw?.takeIf { SOURCE.matches(it) }
    }
}

@OptIn(ExperimentalTime::class)
data class WaitlistEntry(
    val id: UUID,
    val email: String,
    val source: String?,
    val createdAt: Instant,
)

/** One page of the list, newest first, and how many addresses are on it in all. */
data class WaitlistPage(val total: Long, val entries: List<WaitlistEntry>)

/**
 * The `waitlist` table (V16). Every address is unique ignoring case. Rows
 * marked unsubscribed are left out of [page], [export] and the total.
 */
interface WaitlistRepository {
    /** Adds [signup]. True when it is new, false when the address was already there (nothing changes). */
    suspend fun add(signup: WaitlistSignup): Boolean

    suspend fun page(limit: Int, offset: Long): WaitlistPage

    /** Every address, oldest first, one row at a time. */
    suspend fun export(sink: suspend (WaitlistEntry) -> Unit)

    /** Deletes [email] (any case). False when it was not on the list. */
    suspend fun remove(email: String): Boolean
}
