package com.opson.libraries.entitlements.impl

import com.opson.libraries.entitlements.Entitlement
import com.opson.libraries.entitlements.Permission
import com.opson.libraries.entitlements.Role
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.networking.InternalNetworkingApi
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(InternalNetworkingApi::class)
class AccessStoreTest : CoroutineTest() {

    private class MutableClock(var epochMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(epochMs)
    }

    private var status = HttpStatusCode.OK
    private var body = MEMBER
    private val paths = mutableListOf<String>()
    private val clock = MutableClock(NOW)

    private fun store() = AccessStore(
        cacheFactory = MemoryCacheFactory(),
        networkClient = FakeNetworkClient({ status }, { body }, paths, testDispatcher),
        clock = clock,
        appScope = AppCoroutineScope(dispatchers),
    )

    @Test
    fun beforeAnyAnswerTheFreeFeaturesAreThere() = runUnitTest {
        val store = store()

        assertTrue(store.has(Entitlement.PhotoEstimate))
        assertEquals(Entitlement.DEFAULTS, store.entitlements.value)
        assertEquals(Role.Member, store.role.value)
        assertEquals(emptySet(), store.permissions.value)
    }

    @Test
    fun theServersAnswerIsWhatCounts() = runUnitTest {
        val store = store()
        body = """
            {"role":"owner","permissions":["food.contribute","food.edit.direct","food.review","food.teleport"],
             "entitlements":[
               {"key":"photo_estimate","sources":[{"type":"everyone"},{"type":"role","bundle":"pro","role":"owner"}]},
               {"key":"share_friends","sources":[{"type":"role","bundle":"pro","role":"owner"}]},
               {"key":"time_travel","sources":[{"type":"everyone"}]}
             ]}
        """.trimIndent()

        assertTrue(store.sync().isSuccess)

        assertEquals(AccessStore.PATH, paths.single())
        assertEquals(setOf(Entitlement.PhotoEstimate, Entitlement.ShareFriends), store.entitlements.value)
        assertEquals(Role.Owner, store.role.value)
        assertTrue(store.can(Permission.FoodEditDirect))
        assertEquals(3, store.permissions.value.size, "an unknown permission is ignored")
    }

    @Test
    fun anAnswerWithoutTheFreeFeatureTakesItAway() = runUnitTest {
        val store = store()
        body = """{"role":"member","permissions":[],"entitlements":[]}"""

        assertTrue(store.sync().isSuccess)

        assertFalse(store.has(Entitlement.PhotoEstimate))
    }

    @Test
    fun aServerFromBeforeEntitlementsKeepsTheDefaults() = runUnitTest {
        val store = store()
        status = HttpStatusCode.NotFound
        body = """{"error":{"code":"not_found","message":"x"}}"""

        assertTrue(store.sync().isSuccess)

        assertTrue(store.has(Entitlement.PhotoEstimate))
    }

    @Test
    fun aFailedSyncIsRetriedAndKeepsTheLastAnswer() = runUnitTest {
        val store = store()
        body = """{"role":"builder","permissions":["food.contribute"],"entitlements":[{"key":"recipe_import","sources":[{"type":"role","bundle":"pro","role":"builder"}]}]}"""
        store.sync()
        status = HttpStatusCode.InternalServerError

        assertTrue(store.sync().isFailure, "the coordinator retries a failure")

        assertEquals(setOf(Entitlement.RecipeImport), store.entitlements.value)
        assertEquals(Role.Builder, store.role.value)
    }

    @Test
    fun aGrantStopsCountingWhenItEnds_unlessSomethingElseHoldsIt() = runUnitTest {
        val store = store()
        body = """
            {"role":"member","permissions":[],"entitlements":[
              {"key":"share_friends","sources":[{"type":"grant","bundle":"pro","expiresAtEpochMs":${NOW + 1_000}}]},
              {"key":"photo_estimate","sources":[{"type":"everyone"},{"type":"grant","bundle":"pro","expiresAtEpochMs":${NOW + 1_000}}]}
            ]}
        """.trimIndent()
        store.sync()
        assertTrue(store.has(Entitlement.ShareFriends))

        clock.epochMs = NOW + 1_000

        assertFalse(store.has(Entitlement.ShareFriends))
        assertTrue(store.has(Entitlement.PhotoEstimate))
    }

    @Test
    fun aUserChangeForgetsTheDepartingAccountsAnswer() = runUnitTest {
        val store = store()
        body = """{"role":"admin","permissions":["food.review"],"entitlements":[]}"""
        store.sync()

        store.clear("previous-user")

        assertEquals(Role.Member, store.role.value)
        assertEquals(Entitlement.DEFAULTS, store.entitlements.value)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MEMBER = """{"role":"member","permissions":[],"entitlements":[{"key":"photo_estimate","sources":[{"type":"everyone"}]}]}"""
    }
}
