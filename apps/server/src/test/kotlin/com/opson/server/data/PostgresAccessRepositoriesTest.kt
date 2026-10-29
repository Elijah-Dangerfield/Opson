package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.AddGrantOutcome
import com.opson.server.domain.GrantEffect
import com.opson.server.domain.GrantKind
import com.opson.server.domain.NewGrant
import com.opson.server.domain.Permission
import com.opson.server.domain.Permissions
import com.opson.server.domain.Role
import com.opson.server.domain.SetRoleOutcome
import com.opson.server.domain.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PostgresAccessRepositoriesTest : DatabaseTest() {

    private class MutableClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val clock = MutableClock(Instant.fromEpochMilliseconds(1_800_000_000_000))
    private val roles by lazy { PostgresUserRoleRepository(database, clock) }
    private val grants by lazy { PostgresEntitlementGrantRepository(database, clock) }
    private val audit by lazy { PostgresAccessAuditRepository(database) }

    @After
    fun clean() {
        database.blockingTransaction {
            TransactionManager.current().exec("DELETE FROM access_audit")
            TransactionManager.current().exec("DELETE FROM entitlement_grants")
            TransactionManager.current().exec("DELETE FROM user_roles")
        }
    }

    @Test
    fun anAccountWithNoRowIsAMember() = runTest {
        val user = seedAuthUser()
        assertEquals(Role.Member, roles.roleOf(user))
        assertFalse(Permissions(roles).can(user, Permission.FoodReview))
    }

    @Test
    fun settingARoleStoresWhoAndWhy_andTheAuditHasBeforeAndAfter() = runTest {
        val user = seedAuthUser()

        val set = roles.setRole(user, Role.Moderator, actor = "elijah", reason = "reviews foods")
        assertIs<SetRoleOutcome.Set>(set)
        assertEquals(Role.Moderator, roles.roleOf(user))
        assertTrue(Permissions(roles).can(user, Permission.FoodReview))
        assertEquals("elijah", roles.assignment(user)!!.grantedBy)

        clock.instant += 1.hours
        roles.setRole(user, Role.Owner, actor = "elijah", reason = null)
        assertTrue(Permissions(roles).can(user, Permission.FoodEditDirect))

        val trail = audit.list(user, 10)
        assertEquals(listOf("set_role", "set_role"), trail.map { it.action })
        assertEquals("owner", trail.first().after!!.jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("moderator", trail.first().before!!.jsonObject.getValue("role").jsonPrimitive.content)
    }

    @Test
    fun memberClearsTheRow() = runTest {
        val user = seedAuthUser()
        roles.setRole(user, Role.Builder, "elijah", null)
        clock.instant += 1.hours

        val cleared = roles.setRole(user, Role.Member, "elijah", "left the program")

        assertEquals(SetRoleOutcome.Set(null), cleared)
        assertNull(roles.assignment(user))
        assertEquals("clear_role", audit.list(user, 1).single().action)
    }

    @Test
    fun anUnknownAccountIsSaidPlainly() = runTest {
        val nobody = UserId(UUID.randomUUID())
        assertEquals(SetRoleOutcome.NoSuchUser, roles.setRole(nobody, Role.Admin, "elijah", null))
        assertEquals(AddGrantOutcome.NoSuchUser, grants.add(newGrant(nobody, "share_friends"), "elijah"))
    }

    @Test
    fun aGrantIsActiveUntilItExpires() = runTest {
        val user = seedAuthUser()
        val added = grants.add(newGrant(user, "share_friends", expiresAt = clock.instant + 1.days), "elijah")
        assertIs<AddGrantOutcome.Added>(added)

        assertEquals(listOf("share_friends"), grants.active(user, clock.now()).map { it.key })

        clock.instant += 25.hours
        assertTrue(grants.active(user, clock.now()).isEmpty())
        assertEquals(1, grants.history(user).size, "an expired grant stays in the history")
    }

    @Test
    fun removingAGrantKeepsTheRowAndAuditsIt() = runTest {
        val user = seedAuthUser()
        val pro = (grants.add(newGrant(user, "pro", kind = GrantKind.Bundle), "elijah") as AddGrantOutcome.Added).grant

        clock.instant += 1.hours
        assertTrue(grants.remove(user, pro.id, "elijah"))
        assertFalse(grants.remove(user, pro.id, "elijah"), "already removed")

        assertTrue(grants.active(user, clock.now()).isEmpty())
        val history = grants.history(user).single()
        assertEquals("elijah", history.removedBy)
        assertEquals(listOf("remove_grant", "grant"), audit.list(user, 10).map { it.action })
    }

    @Test
    fun aGrantCanOnlyBeRemovedFromItsOwnAccount() = runTest {
        val user = seedAuthUser()
        val other = seedAuthUser()
        val grant = (grants.add(newGrant(user, "share_friends"), "elijah") as AddGrantOutcome.Added).grant

        assertFalse(grants.remove(other, grant.id, "elijah"))
        assertEquals(1, grants.active(user, clock.now()).size)
    }

    @Test
    fun aRevocationIsStoredAsOne() = runTest {
        val user = seedAuthUser()
        grants.add(newGrant(user, "photo_estimate", effect = GrantEffect.Revoke), "elijah")

        assertEquals(GrantEffect.Revoke, grants.active(user, clock.now()).single().effect)
        assertEquals("revoke", audit.list(user, 1).single().action)
    }

    private fun newGrant(
        user: UserId,
        key: String,
        kind: GrantKind = GrantKind.Entitlement,
        effect: GrantEffect = GrantEffect.Grant,
        expiresAt: Instant? = null,
    ) = NewGrant(user, kind, key, effect, source = "manual", reason = "test", expiresAt = expiresAt)
}
