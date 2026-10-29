package com.opson.admin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessViewTest {

    private fun grant(
        effect: String = "grant",
        kind: String = "bundle",
        key: String = "pro",
        active: Boolean = true,
        removedAt: Long? = null,
    ) = GrantDto(
        id = "g1", kind = kind, key = key, effect = effect, source = "founding", reason = "early",
        grantedBy = "elijah", grantedAtEpochMs = 0, removedAtEpochMs = removedAt, removedBy = removedAt?.let { "elijah" }, active = active,
    )

    @Test
    fun sourcesReadAsPlainClauses() {
        assertEquals("everyone has it", sourceSentence(EntitlementSourceDto(type = "everyone")))
        assertEquals("role builder brings pro", sourceSentence(EntitlementSourceDto(type = "role", role = "builder", bundle = "pro")))
        assertEquals(
            "the pro bundle (founding, early)",
            sourceSentence(EntitlementSourceDto(type = "grant", bundle = "pro", grant = grant())),
        )
        assertEquals("a direct grant", sourceSentence(EntitlementSourceDto(type = "grant")))
    }

    @Test
    fun aGrantSaysWhetherItIsInForce() {
        assertEquals("in force", grantStatus(grant()))
        assertEquals("ended", grantStatus(grant(active = false)))
        assertEquals("removed by elijah", grantStatus(grant(active = false, removedAt = 5)))
        assertEquals("revoke entitlement photo_estimate", grantLabel(grant(effect = "revoke", kind = "entitlement", key = "photo_estimate")))
    }

    @Test
    fun theDaysFieldIsBlankOrAWholeNumber() {
        assertEquals(Expiry.Never, expiryFromDays("  ", 1_000))
        assertEquals(Expiry.At(1_000 + 2 * 86_400_000L), expiryFromDays("2", 1_000))
        assertEquals(Expiry.Invalid, expiryFromDays("0", 1_000))
        assertEquals(Expiry.Invalid, expiryFromDays("a week", 1_000))
    }

    @Test
    fun roleLabelNamesWhoSetIt() {
        assertEquals("member", roleLabel("member", null))
        assertEquals("owner, set by elijah (Opson owner)", roleLabel("owner", RoleAssignmentDto("owner", "elijah", 0, "Opson owner")))
    }

    @Test
    fun whoHasReadsThePolicy() {
        val policy = EntitlementPolicyDto(
            everyone = listOf("photo_estimate"),
            bundles = mapOf("pro" to listOf("photo_estimate", "share_friends")),
            roles = mapOf("builder" to listOf("pro")),
        )
        assertEquals("everyone", whoHas("photo_estimate", policy))
        assertEquals("bundles pro; roles builder, and grants", whoHas("share_friends", policy))
        assertEquals("only direct grants", whoHas("recipe_import", policy))
    }

    @Test
    fun takingSomethingFromEveryoneAsksTwice() {
        val before = JsonArray(listOf(JsonPrimitive("photo_estimate")))
        assertNotNull(policyWarning("entitlements.everyone", before, JsonArray(emptyList())))
        assertNull(policyWarning("entitlements.everyone", JsonArray(emptyList()), before))
        assertNull(policyWarning("entitlements.bundles", before, JsonArray(emptyList())))
        assertTrue(roleWarning("owner")!!.contains("verified"))
        assertNull(roleWarning("builder"))
    }
}
