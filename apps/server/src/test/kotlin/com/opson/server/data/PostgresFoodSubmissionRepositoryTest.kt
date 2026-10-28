package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelReading
import com.opson.server.domain.NewFoodSubmission
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
import com.opson.server.domain.PhotoKind
import com.opson.server.domain.ReviewOutcome
import com.opson.server.domain.ServingUnit
import com.opson.server.domain.SubmissionOrigin
import com.opson.server.domain.SubmissionStatus
import com.opson.server.domain.SubmitOutcome
import com.opson.server.domain.UserId
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestImages
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.After
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PostgresFoodSubmissionRepositoryTest : DatabaseTest() {

    @After
    fun clean() = DiaryFixtures.cleanAll(database)

    private var now = Instant.parse("2026-10-27T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repo get() = PostgresFoodSubmissionRepository(database, clock)
    private val catalog get() = PostgresFoodCatalogRepository(database, clock)

    private val bar = LabelFacts(
        name = "Oats & honey granola bar",
        brand = "Hillside",
        servingSize = 40.0,
        servingUnit = ServingUnit.Gram,
        servingLabel = "1 bar",
        basis = CarbBasis.Us,
        perServing = mapOf(
            NutrientKey.Energy to 190.0, NutrientKey.Protein to 4.0, NutrientKey.Carbs to 29.0, NutrientKey.Fat to 7.0,
            NutrientKey.Sodium to 150.0, NutrientKey.Sugars to 12.0,
        ),
    )

    private fun submission(
        user: UserId,
        barcode: String? = "012345678905",
        facts: LabelFacts = bar,
        id: UUID = UUID.randomUUID(),
        customFoodId: UUID? = null,
    ) = NewFoodSubmission(
        id = id,
        userId = user,
        barcode = barcode,
        origin = SubmissionOrigin.Barcode,
        facts = facts.copy(barcode = barcode),
        checks = LabelChecks.check(facts),
        acknowledged = listOf(LabelCheckCode.EnergyMacrosMismatch),
        parsed = LabelReading(facts, mapOf("name" to 0.9), readable = true),
        model = "claude-fable-5-1",
        photos = PackPhotos(TestImages.jpeg, ImageType.Jpeg, TestImages.png, ImageType.Png),
        customFoodId = customFoodId,
    )

    @Test
    fun aSubmissionIsAPendingFoodOnlyItsSubmitterSees() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()

        val created = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission

        assertEquals(SubmissionStatus.Pending, created.status)
        assertEquals("00012345678905", created.gtin)
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), created.acknowledged)
        assertEquals("claude-fable-5-1", created.model)
        assertEquals(0.9, created.parsed?.confidence?.get("name"))
        assertTrue(created.hasPhotos)
        val food = assertNotNull(catalog.findById(created.foodId))
        assertEquals(CatalogSource.UserSubmitted, food.source)
        assertNull(food.barcode, "the barcode waits on the submission")
        assertEquals(475.0, food.per100g.kcal, "190 kcal in a 40 g serving")
        assertEquals(375.0, food.micros["sodium_mg"])
        assertEquals("1 bar (40 g)", food.portions.single().label)
        assertEquals(40.0, food.portions.single().grams)
        assertFalse(food.verified)
        assertEquals(SubmissionStatus.Pending, food.reviewStatus, "the submitter's own detail says it is in review")

        assertTrue(repo.isVisible(created.foodId, alice))
        assertFalse(repo.isVisible(created.foodId, bob))
        assertEquals(created.foodId, repo.pendingFoodFor(alice, "0012345678905"), "any form of the code")
        assertNull(repo.pendingFoodFor(bob, "012345678905"))
        assertNull(catalog.findByBarcode("012345678905"))
        assertTrue(catalog.search("granola", 10).none { it.food.id == created.foodId }, "search never returns a pending food")
    }

    @Test
    fun theSameIdAgainIsTheFirstSubmissionAndAnotherPersonsIdIsAConflict() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val id = UUID.randomUUID()
        val first = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, id = id))).submission

        val again = assertIs<SubmitOutcome.Existing>(repo.submit(submission(alice, id = id))).submission
        assertEquals(first.foodId, again.foodId)
        assertEquals(SubmitOutcome.Conflict, repo.submit(submission(bob, id = id)))
        assertEquals(1L, repo.count(SubmissionStatus.Pending))
    }

    @Test
    fun approvingPutsTheFoodInSearchAndBarcodeLookupForEveryone() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val created = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission

        val approved = assertIs<ReviewOutcome.Done>(repo.approve(created.id, null, "reviewer")).submission

        assertEquals(SubmissionStatus.Approved, approved.status)
        assertEquals("reviewer", approved.reviewedBy)
        assertNotNull(approved.reviewedAt)
        assertTrue(repo.isVisible(created.foodId, bob))
        val found = assertNotNull(catalog.findByBarcode("012345678905"))
        assertEquals(created.foodId, found.id)
        assertTrue(found.verified, "a reviewer checked it against the photos")
        assertEquals(SubmissionStatus.Approved, found.reviewStatus)
        assertTrue(catalog.search("granola", 10).any { it.food.id == created.foodId })
        assertEquals(ReviewOutcome.AlreadyDecided, repo.reject(created.id, "late", "other"))
    }

    @Test
    fun aReviewersCorrectionIsWhatGetsApprovedAndWhatWasSentStaysAsSent() = runTest {
        val alice = seedAuthUser()
        val created = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, facts = bar.copy(perServing = bar.perServing + (NutrientKey.Energy to 270.0))))).submission
        assertTrue(created.checks.any { it.code == LabelCheckCode.EnergyMacrosMismatch })

        val edited = assertIs<ReviewOutcome.Done>(repo.edit(created.id, bar.copy(name = "Granola bar, oats & honey"), "reviewer")).submission

        assertEquals(270.0, edited.submitted.perServing[NutrientKey.Energy])
        assertEquals(190.0, edited.reviewed?.perServing?.get(NutrientKey.Energy))
        assertTrue(edited.checks.isEmpty())
        assertEquals("Granola bar, oats & honey", catalog.findById(created.foodId)?.name)
        assertEquals(SubmissionStatus.Pending, edited.status)
        repo.approve(created.id, null, "reviewer")
        assertEquals(475.0, catalog.findById(created.foodId)?.per100g?.kcal)
    }

    @Test
    fun approvalNeedsTheRequiredFieldsAndAFreeBarcode() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val incomplete = assertIs<SubmitOutcome.Created>(
            repo.submit(submission(alice, barcode = null, facts = bar.copy(perServing = mapOf(NutrientKey.Protein to 4.0)))),
        ).submission
        val missing = assertIs<ReviewOutcome.Incomplete>(repo.approve(incomplete.id, null, "reviewer"))
        assertEquals(listOf("energy_kcal", "carbs_g", "fat_g"), missing.fields)

        val first = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission
        val second = assertIs<SubmitOutcome.Created>(repo.submit(submission(bob))).submission
        repo.approve(first.id, null, "reviewer")
        assertEquals(ReviewOutcome.BarcodeTaken(first.foodId), repo.approve(second.id, null, "reviewer"))
    }

    @Test
    fun rejectingKeepsTheFoodFromEveryoneButItsSubmitter() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val created = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission

        val rejected = assertIs<ReviewOutcome.Done>(repo.reject(created.id, "Photo too blurry", "reviewer")).submission

        assertEquals("Photo too blurry", rejected.rejectReason)
        assertFalse(repo.isVisible(created.foodId, bob))
        assertTrue(repo.isVisible(created.foodId, alice))
        assertNull(repo.pendingFoodFor(alice, "012345678905"), "a rejected food no longer answers a scan")
        assertEquals(listOf(created.id), repo.list(SubmissionStatus.Rejected, 10).map { it.id })
        assertEquals(ReviewOutcome.NotFound, repo.approve(UUID.randomUUID(), null, "reviewer"))
    }

    @Test
    fun photosAreKeptPrivateAndDeletedThirtyDaysAfterTheDecision() = runTest {
        val alice = seedAuthUser()
        val kept = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission
        val decided = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, barcode = "4006381333931"))).submission
        assertContentEquals(TestImages.jpeg, repo.photo(decided.id, PhotoKind.Front)?.bytes)
        assertEquals("image/png", repo.photo(decided.id, PhotoKind.Label)?.mediaType)
        repo.reject(decided.id, "duplicate", "reviewer")

        now += 31.days
        assertEquals(2, repo.purgeDecidedPhotos(now - 30.days))

        assertNull(repo.photo(decided.id, PhotoKind.Front))
        assertFalse(assertNotNull(repo.get(decided.id)).hasPhotos)
        assertNotNull(repo.photo(kept.id, PhotoKind.Front), "a pending submission keeps its photos")
        assertEquals(0, repo.purgeDecidedPhotos(now - 30.days))
    }

    @Test
    fun ownSubmissionsAreTheSubmittersNewestFirstWithTheirOwnCopy() = runTest {
        val alice = seedAuthUser()
        val bob = seedAuthUser()
        val customFoodId = UUID.randomUUID()
        val older = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, customFoodId = customFoodId))).submission
        now += 1.days
        val newer = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, barcode = null))).submission
        repo.submit(submission(bob, barcode = null))
        repo.approve(older.id, null, "reviewer")

        val mine = repo.mine(alice, 10)
        assertEquals(listOf(newer.id, older.id), mine.map { it.id })
        assertEquals(listOf(SubmissionStatus.Pending, SubmissionStatus.Approved), mine.map { it.status })
        assertEquals(listOf(null, customFoodId), mine.map { it.customFoodId })
        assertEquals(listOf(newer.id), repo.mine(alice, 1).map { it.id })
    }

    @Test
    fun thePendingQueueIsOldestFirst() = runTest {
        val alice = seedAuthUser()
        val older = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission
        now += 1.days
        val newer = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice, barcode = null))).submission

        assertEquals(listOf(older.id, newer.id), repo.list(SubmissionStatus.Pending, 10).map { it.id })
        assertEquals(listOf(older.id), repo.list(SubmissionStatus.Pending, 1).map { it.id })
    }

    @Test
    fun deletingTheAccountRemovesItsPendingFoodsAndPhotos() = runTest {
        val alice = seedAuthUser()
        val created = assertIs<SubmitOutcome.Created>(repo.submit(submission(alice))).submission

        database.transaction { TransactionManager.current().exec("DELETE FROM auth.users WHERE id = '${alice.value}'") }

        assertNull(repo.get(created.id))
        assertNull(catalog.findById(created.foodId))
    }
}
