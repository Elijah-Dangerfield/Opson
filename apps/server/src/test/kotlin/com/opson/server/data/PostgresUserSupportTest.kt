package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.InstallSighting
import com.opson.server.domain.LogMethod
import com.opson.server.domain.UserId
import com.opson.server.domain.UserSettings
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.DiaryFixtures.T0
import com.opson.server.testing.DiaryFixtures.entry
import com.opson.server.testing.DiaryFixtures.mutation
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * The install history and the user lookup over the `support` views (V22). The load-bearing
 * checks are the privacy ones: the lookup can show an account's email and counts, and nothing
 * anyone wrote or measured, however the settings or the log are filled in.
 */
@OptIn(ExperimentalTime::class)
class PostgresUserSupportTest : DatabaseTest() {

    @After
    fun clean() {
        DiaryFixtures.cleanAll(database)
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM user_installs") }
    }

    private val installs get() = PostgresUserInstallRepository(database)
    private val lookup get() = PostgresUserLookupRepository(database)
    private val sync get() = PostgresSyncRepository(database)

    @Test
    fun aBuildIsOneRow_andANewBuildStartsTheNextLineOfHistory() = runTest {
        val user = seedAuthUser()
        installs.record(sighting(user, build = 40, version = "0.1.0"), T0)
        installs.record(sighting(user, build = 40, version = "0.1.0"), T0 + 2.hours)
        installs.record(sighting(user, build = 52, version = "0.2.0"), T0 + 30.hours)

        val history = assertNotNull(lookup.detail(user)).installs
        assertEquals(listOf("0.2.0" to 52, "0.1.0" to 40), history.map { it.appVersion to it.buildNumber })
        val old = history.last()
        assertEquals(T0, old.firstSeenAt)
        assertEquals(T0 + 2.hours, old.lastSeenAt, "a later sighting of the same build moves last seen")
    }

    @Test
    fun aLateSightingNeverMovesLastSeenBackwards() = runTest {
        val user = seedAuthUser()
        installs.record(sighting(user), T0 + 5.hours)
        installs.record(sighting(user), T0)
        assertEquals(T0 + 5.hours, assertNotNull(lookup.detail(user)).installs.single().lastSeenAt)
    }

    @Test
    fun searchFindsAnAccountByIdEmailOrDisplayName() = runTest {
        val user = seedAuthUser()
        setEmail(user, "Sam.Lee@example.com")
        seedProfile(user, "QuietOtter12")
        seedAuthUser()

        assertEquals(listOf(user), lookup.search(user.toString(), 25).map { it.userId })
        assertEquals(listOf(user), lookup.search("sam.lee", 25).map { it.userId }, "email, any case")
        assertEquals(listOf(user), lookup.search("otter", 25).map { it.userId })
        assertEquals(emptyList(), lookup.search("%", 25).map { it.userId }, "a LIKE wildcard is a literal")
    }

    @Test
    fun blankSearchListsTheMostRecentlySeenFirst() = runTest {
        val older = seedAuthUser()
        val newer = seedAuthUser()
        installs.record(sighting(older), T0)
        installs.record(sighting(newer), T0 + 1.hours)

        val listed = lookup.search("", 25).map { it.userId }
        assertTrue(listed.indexOf(newer) < listed.indexOf(older))
    }

    @Test
    fun detailCountsLoggingByMethod_andShowsNoFoodNameOrAmount() = runTest {
        val user = seedAuthUser()
        sync.sync(
            user,
            0,
            listOf(
                mutation(entry(name = "Grandma's secret soup", kcal = 812.0)),
                mutation(entry(name = "Second bowl", kcal = 812.0).copy(method = LogMethod.Barcode)),
            ),
        )

        val detail = assertNotNull(lookup.detail(user))
        assertEquals(1, detail.methods["search"])
        assertEquals(1, detail.methods["barcode"])
        assertEquals(2, detail.summary.entries)
        assertEquals(1, detail.summary.daysLogged)
        val shown = detail.toString()
        assertFalse("soup" in shown || "bowl" in shown || "812" in shown, shown)
    }

    @Test
    fun settingsShowTheChoicesAndNeverTheTargetsOrTheOnboardingAnswers() = runTest {
        val user = seedAuthUser()
        val settings = """
            {"preset":"blind","numbers":{"preset":"blind","overrides":{"calories":"hide"},"pauseBeforeMore":true},
             "theme":"dark","tone":"plain","fasting":true,
             "targets":{"energyKcal":1437,"proteinG":97},
             "onboarding":{"why":["control"],"hard":true},
             "favoriteFoods":["Grandma's secret soup"]}
        """.trimIndent()
        sync.sync(user, 0, listOf(mutation(UserSettings(settings, changedAt = T0, clientUpdatedAt = T0, deleted = false))))

        val shown = assertNotNull(lookup.detail(user)).settings.toMap()
        assertEquals("blind", shown["preset"])
        assertEquals("true", shown["adjusted"])
        assertEquals("""{"calories": "hide"}""", shown["visibility_overrides"])
        assertEquals("dark", shown["theme"])
        assertEquals("true", shown["fasting"])
        val everything = shown.toString()
        assertFalse("1437" in everything || "97" in everything, "a target leaked: $everything")
        assertFalse("hard" in everything || "control" in everything, "an onboarding answer leaked: $everything")
        assertFalse("soup" in everything, "a favourite food leaked: $everything")
    }

    @Test
    fun noSupportViewHasAColumnThatCouldHoldWhatSomeoneWroteOrMeasured() = runTest {
        val columns = database.blockingTransaction {
            val found = mutableListOf<String>()
            TransactionManager.current().exec(
                "SELECT table_name || '.' || column_name FROM information_schema.columns WHERE table_schema = ?",
                listOf(TextColumnType() to "support"),
            ) { rs -> while (rs.next()) found += rs.getString(1) }
            found
        }
        assertTrue(columns.size > 20, "the scan read the wrong schema: $columns")
        val forbidden = setOf("name", "kcal", "protein", "carbs", "fat", "fiber", "grams", "kg", "water", "note",
            "notes", "micros", "amount", "hunger", "fullness", "moods", "targets", "onboarding", "goal", "quantity")
        // Names reviewed and allowed: the account's chosen name, and a reminder switch.
        val allowed = setOf("display_name", "water_reminders")
        val offending = columns.filter { column ->
            val name = column.substringAfter('.')
            name !in allowed && name.split('_').any { it in forbidden }
        }
        assertEquals(emptyList(), offending)
    }

    @Test
    fun anUnknownAccountHasNoDetail() = runTest {
        assertNull(lookup.detail(UserId(java.util.UUID.randomUUID())))
    }

    private fun sighting(user: UserId, build: Int = 1, version: String = "0.2.0") = InstallSighting(
        userId = user,
        installId = "install-${user.value}",
        platform = "ios",
        appVersion = version,
        buildNumber = build,
    )

    private fun setEmail(user: UserId, email: String) = database.blockingTransaction {
        TransactionManager.current().exec(
            "UPDATE auth.users SET email = ? WHERE id = ?",
            listOf(TextColumnType() to email, UUIDColumnType() to user.value),
        )
    }

    private fun seedProfile(user: UserId, displayName: String) = database.blockingTransaction {
        TransactionManager.current().exec(
            "INSERT INTO profiles (user_id, display_name) VALUES (?, ?)",
            listOf(UUIDColumnType() to user.value, TextColumnType() to displayName),
        )
    }
}
