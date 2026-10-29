package com.opson.server.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class EntitlementPolicyTest {

    private val user = UserId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun tree(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun grant(
        key: String,
        kind: GrantKind = GrantKind.Entitlement,
        effect: GrantEffect = GrantEffect.Grant,
        expiresAt: Instant? = null,
    ) = EntitlementGrant(UUID.randomUUID(), user, kind, key, effect, "manual", null, "test", now, expiresAt)

    @Test
    fun theDefaultMatchesTodayEveryoneEstimatesPhotosAndNothingElse() {
        val resolved = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Member, emptyList())

        assertEquals(setOf(Entitlement.PhotoEstimate), resolved.granted.keys)
        assertEquals(listOf<EntitlementSource>(EntitlementSource.Everyone), resolved.granted.getValue(Entitlement.PhotoEstimate))
    }

    @Test
    fun aMissingOrEmptyConfigTreeReadsAsTheDefault() {
        assertEquals(EntitlementPolicy.DEFAULT, EntitlementPolicy.from(tree("{}")))
        assertEquals(EntitlementPolicy.DEFAULT, EntitlementPolicy.from(tree("""{"entitlements":{}}""")))
    }

    @Test
    fun theSeededConfigReadsAsTheDefault() {
        // What V28 inserts, as AppConfigTree assembles it.
        val seeded = tree(
            """{"entitlements":{
                "everyone":["photo_estimate"],
                "bundles":{"pro":["photo_estimate","share_professional","share_friends","adaptive_burn","long_range_reports","recipe_import"]},
                "roles":{"owner":["pro"],"admin":["pro"],"builder":["pro"]}
            }}""",
        )
        assertEquals(EntitlementPolicy.DEFAULT, EntitlementPolicy.from(seeded))
    }

    @Test
    fun anEmptyEveryoneListTakesTheFreeFeatureAway_aMalformedOneDoesNot() {
        assertEquals(emptySet(), EntitlementPolicy.from(tree("""{"entitlements":{"everyone":[]}}""")).everyone)
        assertEquals(
            EntitlementPolicy.DEFAULT.everyone,
            EntitlementPolicy.from(tree("""{"entitlements":{"everyone":"photo_estimate"}}""")).everyone,
        )
    }

    @Test
    fun unknownKeysAndRolesInConfigAreIgnored() {
        val policy = EntitlementPolicy.from(
            tree("""{"entitlements":{"everyone":["photo_estimate","teleport"],"roles":{"wizard":["pro"],"moderator":["pro"]}}}"""),
        )
        assertEquals(setOf(Entitlement.PhotoEstimate), policy.everyone)
        assertEquals(mapOf(Role.Moderator to setOf("pro")), policy.roleBundles)
    }

    @Test
    fun aBundleGrantBringsEveryEntitlementInTheBundle_andSaysSo() {
        val pro = grant("pro", kind = GrantKind.Bundle)
        val resolved = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Member, listOf(pro))

        assertEquals(Entitlement.entries.toSet(), resolved.granted.keys)
        assertEquals(
            listOf(EntitlementSource.Everyone, EntitlementSource.FromGrant(pro, "pro")),
            resolved.granted.getValue(Entitlement.PhotoEstimate),
        )
        assertEquals(listOf<EntitlementSource>(EntitlementSource.FromGrant(pro, "pro")), resolved.granted.getValue(Entitlement.ShareFriends))
    }

    @Test
    fun aBuilderGetsProThroughTheirRole() {
        val resolved = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Builder, emptyList())

        assertEquals(Entitlement.entries.toSet(), resolved.granted.keys)
        assertTrue(EntitlementSource.FromRole(Role.Builder, "pro") in resolved.granted.getValue(Entitlement.RecipeImport))
    }

    @Test
    fun aRevocationBeatsEveryone_aGrant_andARole() {
        val revoke = grant("photo_estimate", effect = GrantEffect.Revoke)
        val resolved = ResolvedEntitlements.resolve(
            EntitlementPolicy.DEFAULT,
            Role.Admin,
            listOf(grant("photo_estimate"), revoke),
        )

        assertFalse(resolved.has(Entitlement.PhotoEstimate))
        assertTrue(resolved.has(Entitlement.ShareFriends))
        assertEquals(listOf(revoke), resolved.revoked.getValue(Entitlement.PhotoEstimate))
    }

    @Test
    fun aGrantOfABundleTheConfigNoLongerHasGivesNothing() {
        val resolved = ResolvedEntitlements.resolve(EntitlementPolicy.DEFAULT, Role.Member, listOf(grant("plus", kind = GrantKind.Bundle)))
        assertEquals(setOf(Entitlement.PhotoEstimate), resolved.granted.keys)
    }

    @Test
    fun aGrantIsActiveUntilItExpiresOrIsRemoved() {
        assertTrue(grant("share_friends").isActive(now))
        assertTrue(grant("share_friends", expiresAt = now + 1.days).isActive(now))
        assertFalse(grant("share_friends", expiresAt = now).isActive(now))
        assertFalse(grant("share_friends").copy(removedAt = now).isActive(now))
    }

    @Test
    fun theAdminApiRefusesAPolicyThatWouldQuietlyDropAFeature() {
        val json = Json
        assertNull(EntitlementPolicy.validate("entitlements.everyone", json.parseToJsonElement("""["photo_estimate"]""")))
        assertNull(EntitlementPolicy.validate("entitlements.everyone", json.parseToJsonElement("[]")))
        assertNotNull(EntitlementPolicy.validate("entitlements.everyone", json.parseToJsonElement("""["photo_estimates"]""")))
        assertNotNull(EntitlementPolicy.validate("entitlements.everyone", json.parseToJsonElement(""""photo_estimate"""")))
        assertNull(EntitlementPolicy.validate("entitlements.bundles", json.parseToJsonElement("""{"pro":["share_friends"],"coach":[]}""")))
        assertNotNull(EntitlementPolicy.validate("entitlements.bundles", json.parseToJsonElement("""{"Pro!":["share_friends"]}""")))
        assertNotNull(EntitlementPolicy.validate("entitlements.bundles", json.parseToJsonElement("""["pro"]""")))
        assertNull(EntitlementPolicy.validate("entitlements.roles", json.parseToJsonElement("""{"builder":["pro"]}""")))
        assertNotNull(EntitlementPolicy.validate("entitlements.roles", json.parseToJsonElement("""{"wizard":["pro"]}""")))
        assertNotNull(EntitlementPolicy.validate("entitlements.pro", json.parseToJsonElement("true")))
        assertNull(EntitlementPolicy.validate("release.photo", json.parseToJsonElement("true")))
    }

    @Test
    fun rolesMapToPermissions() {
        assertEquals(emptySet(), Role.Member.permissions)
        assertEquals(setOf(Permission.FoodContribute), Role.Builder.permissions)
        assertEquals(setOf(Permission.FoodContribute, Permission.FoodReview), Role.Moderator.permissions)
        assertTrue(Permission.FoodEditDirect in Role.Owner.permissions)
        assertTrue(Permission.FoodEditDirect in Role.Admin.permissions)
        assertFalse(Permission.FoodEditDirect in Role.Moderator.permissions)
    }
}
