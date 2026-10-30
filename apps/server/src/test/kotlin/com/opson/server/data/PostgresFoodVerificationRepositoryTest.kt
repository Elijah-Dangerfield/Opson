package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.ImageType
import com.opson.server.domain.Nutrients
import com.opson.server.domain.PackSide
import com.opson.server.domain.ReportPhoto
import com.opson.server.domain.UserId
import com.opson.server.domain.VerificationDecision
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerificationStatus
import com.opson.server.domain.VerifyOutcome
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestImages
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * `food_verifications` against Postgres: when two Builders' matches agree, and the photo purge.
 * What the route answers and what the catalog then serves are in FullStackBuilderTest.
 */
@OptIn(ExperimentalTime::class)
class PostgresFoodVerificationRepositoryTest : DatabaseTest() {

    @After
    fun clean() {
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM food_verifications") }
        DiaryFixtures.cleanAll(database)
    }

    private var now = Instant.parse("2026-10-30T09:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repo by lazy { PostgresFoodVerificationRepository(database, clock) }

    private val seen = FoodSnapshot("Maple granola", "Hillside", "usda_branded", Nutrients(kcal = 418.2, protein = 5.45, carbs = 67.3, fat = 14.5), null)

    private fun match(user: UserId, food: UUID, snapshot: FoodSnapshot = seen) = FoodVerification(
        id = UUID.randomUUID(),
        foodId = food,
        userId = user,
        createdAt = now,
        outcome = VerificationOutcome.Confirmed,
        grounded = 4,
        groundable = 4,
        engine = "vision",
        status = VerificationStatus.Open,
        snapshot = snapshot,
    )

    private val photos = mapOf(PackSide.Label to ReportPhoto(TestImages.jpeg, ImageType.Jpeg))

    @Test
    fun aSecondBuildersMatchAcceptsBoth_butNotOneMadeAgainstOtherNumbers() = runTest {
        val food = UUID.randomUUID()
        val (first, second, third) = List(3) { seedAuthUser() }

        val waiting = assertIs<VerifyOutcome.Recorded>(repo.record(match(first, food), photos)).verification
        assertEquals(true, waiting.awaitsAgreement)
        assertEquals(setOf(PackSide.Label), waiting.photos)

        val corrected = seen.copy(per100g = seen.per100g.copy(kcal = 450.0))
        val elsewhere = assertIs<VerifyOutcome.Recorded>(repo.record(match(second, food, corrected), photos)).verification
        assertEquals(true, elsewhere.awaitsAgreement, "matched other numbers, so it doesn't agree")

        val agreeing = assertIs<VerifyOutcome.Recorded>(repo.record(match(third, food), photos)).verification
        assertEquals(VerificationStatus.Accepted, agreeing.status)
        assertEquals(FoodVerification.AGREED_BY, agreeing.resolvedBy)
        assertEquals(VerificationStatus.Accepted, repo.get(waiting.id)?.status)
        assertEquals(VerificationStatus.Open, repo.get(elsewhere.id)?.status)

        val again = assertIs<VerifyOutcome.Recorded>(repo.record(match(first, food), photos)).verification
        assertEquals(VerificationOutcome.Unconfirmed, again.outcome)
        assertNull(again.status, "the same person again waits for nothing")
        assertEquals(emptySet(), again.photos)
    }

    @Test
    fun aReviewerAcceptingAWaitingMatchKeepsItConfirmed() = runTest {
        val user = seedAuthUser()
        val waiting = assertIs<VerifyOutcome.Recorded>(repo.record(match(user, UUID.randomUUID()), photos)).verification
        val decided = assertIs<VerificationDecision.Accepted>(repo.accept(waiting.id, null, null, "elijah", null)).verification
        assertEquals(VerificationOutcome.Confirmed, decided.outcome)
        assertEquals(VerificationStatus.Accepted, decided.status)
    }

    /** More decided checks than Postgres takes parameters in one statement (65,535): one ids list would fail. */
    @Test
    fun decidedPhotosGo_howeverManyChecksHaveBeenDecided() = runTest {
        val user = seedAuthUser()
        val waiting = assertIs<VerifyOutcome.Recorded>(repo.record(match(user, UUID.randomUUID()), photos)).verification
        assertIs<VerificationDecision.Rejected>(repo.reject(waiting.id, "Blurry", "elijah"))
        database.blockingTransaction {
            TransactionManager.current().exec(
                "INSERT INTO food_verifications (id, food_id, user_id, outcome, grounded, groundable, status, resolved_at) " +
                    "SELECT gen_random_uuid(), gen_random_uuid(), '${user.value}', 'unconfirmed', 0, 4, 'rejected', now() - interval '400 days' " +
                    "FROM generate_series(1, 70000)",
            )
        }
        assertEquals(0, repo.purgeDecidedPhotos(now - 30.days))
        now += 31.days
        assertEquals(1, repo.purgeDecidedPhotos(now - 30.days))
        assertNull(repo.photo(waiting.id, PackSide.Label))
    }
}
