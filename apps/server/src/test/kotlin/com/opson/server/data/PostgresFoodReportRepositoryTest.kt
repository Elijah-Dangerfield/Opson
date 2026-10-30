package com.opson.server.data

import com.opson.server.db.DatabaseTest
import com.opson.server.db.FoodsTable
import com.opson.server.domain.CorrectionOutcome
import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.EnergyFlag
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.ImageType
import com.opson.server.domain.NewFoodReport
import com.opson.server.domain.PackSide
import com.opson.server.domain.ReportOutcome
import com.opson.server.domain.ReportPhoto
import com.opson.server.domain.ReportReason
import com.opson.server.domain.ReportStatus
import com.opson.server.domain.RevertOutcome
import com.opson.server.domain.SuggestedValues
import com.opson.server.domain.UserId
import com.opson.server.testing.DiaryFixtures
import com.opson.server.testing.TestImages
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
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
class PostgresFoodReportRepositoryTest : DatabaseTest() {

    @After
    fun clean() {
        database.blockingTransaction {
            TransactionManager.current().exec("DELETE FROM food_reports")
            TransactionManager.current().exec("DELETE FROM food_corrections")
        }
        DiaryFixtures.cleanAll(database)
    }

    private var now = Instant.parse("2026-10-29T09:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val repo by lazy { PostgresFoodReportRepository(database, clock) }
    private val catalog get() = PostgresFoodCatalogRepository(database, clock)

    /** The Monster row as USDA Branded imports it. */
    private fun seedMonster(): UUID {
        val id = UUID.randomUUID()
        database.blockingTransaction {
            FoodsTable.insert {
                it[FoodsTable.id] = id
                it[sourceKind] = "usda_branded"
                it[sourceId] = "2680587"
                it[name] = "Energy Drink"
                it[brand] = "Monster"
                it[barcode] = "00070847012474"
                it[kcal100g] = 0f
                it[protein100g] = 0f
                it[carbs100g] = 0.83f
                it[fat100g] = 0f
                it[portions] = """[{"label": "8.0 fl oz (240 ml)", "grams": null}]"""
            }
        }
        return id
    }

    private suspend fun newReport(
        food: UUID,
        user: UserId?,
        id: UUID = UUID.randomUUID(),
        suggested: SuggestedValues? = SuggestedValues(basisLabel = "1 can", basisGrams = 473.0, kcal = 10.0),
        photo: ReportPhoto? = null,
    ) = NewFoodReport(
        id = id,
        userId = user,
        foodId = food,
        reason = ReportReason.WrongCalories,
        suggested = suggested,
        note = "The can says 10",
        snapshot = FoodSnapshot.of(checkNotNull(catalog.findById(food))),
        photo = photo,
    )

    @Test
    fun aReportIsStoredOnceAndNeverChangesTheFood() = runTest {
        val food = seedMonster()
        val user = seedAuthUser()
        val report = newReport(food, user, photo = ReportPhoto(TestImages.jpeg, ImageType.Jpeg))

        val created = assertIs<ReportOutcome.Created>(repo.report(report)).report
        assertEquals(ReportStatus.Open, created.status)
        assertTrue(created.hasPhoto)
        assertEquals(10.0, created.suggested?.kcal)
        assertEquals("Energy Drink", created.snapshot.name)
        assertIs<ReportOutcome.Existing>(repo.report(report))
        assertIs<ReportOutcome.Conflict>(repo.report(report.copy(userId = seedAuthUser())))
        assertContentEquals(TestImages.jpeg, repo.photo(report.id)?.bytes)

        // Served as the guard has it, not as the report says.
        assertEquals(2.1, catalog.findById(food)?.per100g?.kcal)
        assertEquals(1L, repo.countOpen())
    }

    @Test
    fun aReportKeepsEveryReasonAndBothSidesOfThePack_andACorrectionMarksTheFoodChecked() = runTest {
        val food = seedMonster()
        val user = seedAuthUser()
        val label = ReportPhoto(TestImages.jpeg, ImageType.Jpeg)
        val front = ReportPhoto(TestImages.png, ImageType.Png)
        val report = newReport(food, user, photo = label)
            .copy(reason = ReportReason.MissingInfo, reasons = listOf(ReportReason.MissingInfo, ReportReason.WrongNumbers), frontPhoto = front)

        val created = assertIs<ReportOutcome.Created>(repo.report(report)).report
        assertEquals(listOf(ReportReason.MissingInfo, ReportReason.WrongNumbers), created.reasons)
        assertTrue(created.hasPhoto && created.hasFrontPhoto)
        assertContentEquals(TestImages.jpeg, repo.photo(report.id)?.bytes)
        assertEquals("image/png", repo.photo(report.id, PackSide.Front)?.mediaType)
        assertEquals(listOf(ReportReason.MissingInfo, ReportReason.WrongNumbers), repo.open(10).single().reasons)

        assertFalse(checkNotNull(catalog.findById(food)).checked)
        repo.applyCorrection(food, CorrectionValues(kcal = 2.1), report.snapshot, listOf(report.id), by = "Elijah")
        assertTrue(checkNotNull(catalog.findById(food)).checked, "a reviewer stood behind it")
        val live = repo.liveCorrections(listOf(food)).getValue(food)
        repo.revert(live.id, by = "Elijah")
        assertFalse(checkNotNull(catalog.findById(food)).checked, "and no longer does")
    }

    @Test
    fun theEnergyCheckFilesOneReportPerFoodEver() = runTest {
        val food = seedMonster()
        val served = checkNotNull(catalog.findById(food))
        assertEquals(setOf(EnergyFlag.ZeroWithMacros), served.energyFlags)

        assertTrue(repo.flagEnergy(served))
        assertFalse(repo.flagEnergy(served))
        assertFalse(PostgresFoodReportRepository(database, clock).flagEnergy(served), "another machine finds it filed")
        val filed = repo.open(10).single()
        assertEquals(ReportReason.EnergyCheck, filed.reason)
        assertNull(filed.userId)
        assertEquals(2.1, filed.suggested?.kcal)

        repo.reject(listOf(filed.id), "The can says 0", "elijah")
        assertFalse(PostgresFoodReportRepository(database, clock).flagEnergy(served), "a decided check does not come back")
    }

    @Test
    fun acceptingLaysACorrectionOverTheFoodThatAReimportDoesNotUndo() = runTest {
        val food = seedMonster()
        val user = seedAuthUser()
        val first = assertIs<ReportOutcome.Created>(repo.report(newReport(food, user))).report
        val second = assertIs<ReportOutcome.Created>(repo.report(newReport(food, seedAuthUser()))).report
        val before = checkNotNull(catalog.findById(food))

        val values = CorrectionValues(kcal = 2.1, servingLabel = "1 can (473 ml)", servingGrams = 473.0)
        val applied = assertIs<CorrectionOutcome.Applied>(
            repo.applyCorrection(food, values, FoodSnapshot.of(before), listOf(first.id, second.id), "elijah"),
        ).correction
        assertEquals("elijah", applied.createdBy)
        assertTrue(applied.live)

        val served = checkNotNull(catalog.findById(food))
        assertEquals(applied.id, served.correction?.id)
        assertEquals("1 can (473 ml)", served.portions.first().label)
        assertTrue(served.energyFlags.isEmpty(), "a person set the calories")
        assertEquals(listOf(ReportStatus.Accepted), repo.forFood(food).map { it.status }.distinct())
        assertEquals(0L, repo.countOpen())

        // The monthly Branded import rewrites the row; the correction stays on top.
        database.blockingTransaction { FoodsTable.update({ FoodsTable.id eq food }) { it[kcal100g] = 0f; it[name] = "ENERGY DRINK" } }
        val afterImport = checkNotNull(catalog.findById(food))
        assertEquals(2.1, afterImport.per100g.kcal)
        assertEquals("ENERGY DRINK", afterImport.name, "fields the correction does not set follow the source")
        assertEquals(afterImport, assertNotNull(catalog.findByBarcode("070847012474")))
        assertEquals(2.1, catalog.search("energy drink", 5, record = false).single().food.per100g.kcal)

        // Accepting again is refused: those reports are closed.
        assertEquals(CorrectionOutcome.NotOpen, repo.applyCorrection(food, values, FoodSnapshot.of(before), listOf(first.id), "elijah"))
    }

    @Test
    fun aSecondCorrectionSupersedesTheFirst_andRevertingItBringsTheFirstBack() = runTest {
        val food = seedMonster()
        val base = FoodSnapshot.of(checkNotNull(catalog.findById(food)))
        val r1 = assertIs<ReportOutcome.Created>(repo.report(newReport(food, seedAuthUser()))).report
        val r2 = assertIs<ReportOutcome.Created>(repo.report(newReport(food, seedAuthUser()))).report

        val first = assertIs<CorrectionOutcome.Applied>(repo.applyCorrection(food, CorrectionValues(kcal = 2.0), base, listOf(r1.id), "a")).correction
        now += 1.days
        val second = assertIs<CorrectionOutcome.Applied>(
            repo.applyCorrection(food, CorrectionValues(protein = 0.5), base, listOf(r2.id), "b"),
        ).correction
        // The second keeps what the first fixed.
        assertEquals(CorrectionValues(kcal = 2.0, protein = 0.5), second.values)
        assertEquals(listOf(second.id, first.id), repo.corrections(food).map { it.id })
        assertEquals(second.id, repo.corrections(food).first().takeIf { it.live }?.id)
        assertFalse(repo.corrections(food).last().live)

        assertEquals(RevertOutcome.NotLive, repo.revert(first.id, "c"))
        val reverted = assertIs<RevertOutcome.Reverted>(repo.revert(second.id, "c"))
        assertEquals(first.id, reverted.restored?.id)
        assertEquals("c", reverted.correction.revertedBy)
        val served = checkNotNull(catalog.findById(food))
        assertEquals(first.id, served.correction?.id)
        assertEquals(2.0, served.per100g.kcal)
        assertEquals(0.0, served.per100g.protein)

        assertIs<RevertOutcome.Reverted>(repo.revert(first.id, "c"))
        val original = checkNotNull(catalog.findById(food))
        assertNull(original.correction)
        assertEquals(2.1, original.per100g.kcal, "back to the guard's estimate")
        assertEquals(RevertOutcome.NotFound, repo.revert(UUID.randomUUID(), "c"))
    }

    @Test
    fun rejectAndDuplicateCloseOnlyOpenReports_andDecidedPhotosGoAfter30Days() = runTest {
        val food = seedMonster()
        val user = seedAuthUser()
        val withPhoto = assertIs<ReportOutcome.Created>(
            repo.report(newReport(food, user, photo = ReportPhoto(TestImages.jpeg, ImageType.Jpeg))),
        ).report
        val other = assertIs<ReportOutcome.Created>(repo.report(newReport(food, user, suggested = null))).report

        assertEquals(1, repo.reject(listOf(withPhoto.id), "Matches the label", "elijah"))
        assertEquals(0, repo.reject(listOf(withPhoto.id), null, "elijah"))
        val copyOf = UUID.randomUUID()
        assertEquals(1, repo.markDuplicate(listOf(other.id), copyOf, "elijah"))
        val decided = repo.forFood(food).associateBy { it.id }
        assertEquals(ReportStatus.Rejected, decided.getValue(withPhoto.id).status)
        assertEquals("Matches the label", decided.getValue(withPhoto.id).resolution)
        assertEquals(copyOf, decided.getValue(other.id).duplicateOf)

        assertEquals(0, repo.purgeDecidedPhotos(now - 30.days))
        now += 31.days
        assertEquals(1, repo.purgeDecidedPhotos(now - 30.days))
        assertNull(repo.photo(withPhoto.id))
    }

    @Test
    fun anOpenFoodFactsRowCanBeCorrectedAndKeepsItThroughTheMonthlySwap() = runTest {
        val offId = UUID.randomUUID()
        fun insertOff() = database.blockingTransaction {
            TransactionManager.current().exec(
                """
                INSERT INTO off_foods (id, barcode, name, kcal_100g, protein_100g, carbs_100g, fat_100g, source_version)
                VALUES ('$offId', '05060337500401', 'Monster Energy Ultra', 0, 0, 0.9, 0, '2026-10-26')
                """.trimIndent(),
            )
        }
        insertOff()
        val offCatalog = PostgresFoodCatalogRepository(database, clock, com.opson.server.config.FoodSourcesConfig(searchOpenFoodFacts = true))
        val before = checkNotNull(offCatalog.findById(offId))
        repo.applyCorrection(offId, CorrectionValues(kcal = 2.0), FoodSnapshot.of(before), emptyList(), "elijah")
        // The reload replaces the table; the id comes from the barcode, so it is the same.
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM off_foods") }
        insertOff()
        assertEquals(2.0, offCatalog.findById(offId)?.per100g?.kcal)
        database.blockingTransaction { TransactionManager.current().exec("DELETE FROM off_foods") }
    }
}
