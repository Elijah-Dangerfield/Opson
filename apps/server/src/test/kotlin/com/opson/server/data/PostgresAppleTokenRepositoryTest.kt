package com.opson.server.data

import com.opson.server.db.AppleRefreshTokensTable
import com.opson.server.db.DatabaseTest
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PostgresAppleTokenRepositoryTest : DatabaseTest() {

    @After
    fun cleanTables() {
        database.blockingTransaction { AppleRefreshTokensTable.deleteAll() }
    }

    private val repo get() = PostgresAppleTokenRepository(database, Clock.System)

    @Test
    fun aSavedTokenIsFoundForItsAccountOnly() = runTest {
        val ada = seedAuthUser()
        val grace = seedAuthUser()

        repo.save(ada, "r-ada")

        assertEquals("r-ada", repo.find(ada))
        assertNull(repo.find(grace))
    }

    @Test
    fun aNewerSignInReplacesTheToken() = runTest {
        val ada = seedAuthUser()

        repo.save(ada, "r-old")
        repo.save(ada, "r-new")

        assertEquals("r-new", repo.find(ada))
    }

    @Test
    fun theTokenGoesWithTheAccount() = runTest {
        val ada = seedAuthUser()
        repo.save(ada, "r-ada")

        database.blockingTransaction {
            TransactionManager.current().exec("DELETE FROM auth.users WHERE id = '${ada.value}'")
        }

        assertNull(repo.find(ada))
    }
}
