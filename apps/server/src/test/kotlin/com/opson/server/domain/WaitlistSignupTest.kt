package com.opson.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaitlistSignupTest {

    @Test
    fun plausibleAddressesAreTrimmedAndLowerCased() {
        assertEquals("ada@example.com", WaitlistSignup.normalizeEmail("  Ada@Example.COM "))
        assertEquals("first.last+tag@mail.co.uk", WaitlistSignup.normalizeEmail("first.last+tag@mail.co.uk"))
        assertEquals("o'neil@ex-ample.io", WaitlistSignup.normalizeEmail("o'neil@ex-ample.io"))
    }

    @Test
    fun implausibleAddressesAreRefused() {
        listOf(
            null, "", "   ", "plain", "a@b", "@b.co", "a@", "a@@b.co", "a@b@c.co", "a b@c.co", "a@b .co",
            ".a@b.co", "a.@b.co", "a..b@c.co", "a@-b.co", "a@b-.co", "a@b.c", "a@b.co1", "=sum@b.co", "+a@b.co",
            "${"a".repeat(65)}@b.co", "a@${"b".repeat(250)}.co",
        ).forEach { assertNull(WaitlistSignup.normalizeEmail(it), "accepted: $it") }
    }

    @Test
    fun theLengthCapIs254() {
        val domain = List(5) { "d".repeat(47) }.joinToString(".") + ".co" // 242 characters
        val atCap = "a".repeat(254 - domain.length - 1) + "@" + domain
        assertEquals(254, atCap.length)
        assertEquals(atCap, WaitlistSignup.normalizeEmail(atCap))
        assertNull(WaitlistSignup.normalizeEmail("a$atCap"))
    }

    @Test
    fun sourceIsKeptOnlyWhenItMatches() {
        assertEquals("home", WaitlistSignup.normalizeSource("home"))
        assertEquals("spring-2026", WaitlistSignup.normalizeSource("spring-2026"))
        listOf(null, "", "Home", "a b", "x".repeat(33), "home/page").forEach {
            assertNull(WaitlistSignup.normalizeSource(it), "kept: $it")
        }
    }
}
