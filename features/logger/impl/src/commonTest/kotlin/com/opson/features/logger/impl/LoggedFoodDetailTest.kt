package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The food detail opened on an entry already on the log ([DetailOpen.Entry]), from its row on
 * Today. The day has the same peanut butter twice at the snack, 2 tbsp each: the page edits and
 * removes the entry it was opened on and never the other, and its numbers are the entry's own.
 */
class LoggedFoodDetailTest : CoroutineTest() {
    private val first = LoggerFixtures.firstHelping
    private val second = LoggerFixtures.secondHelping

    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.peanutButter, LoggerFixtures.banana, LoggerFixtures.yogurt, LoggerFixtures.kabsa))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.twoHelpingsDay()))
    private var repeatsOn = true

    private fun vm(entryId: String = second.id) = FoodDetailViewModel(
        open = DetailOpen.Entry(LoggerFixtures.today, entryId),
        foodCatalog = catalog,
        customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola)),
        settingsRepository = settings,
        policySource = policySource,
        diary = diary,
        calendar = FakeCalendar(),
        selectedDay = FakeSelectedDay(),
        clock = Clock.fixed(NOON),
        logger = FoodLogger(diary, catalog, FakeRecentLogEvents(), RecordingTelemetry(), Clock.fixed(NOON)),
        submittedFoods = FakeSubmittedFoods(),
        repeats = RepeatsRelease { repeatsOn },
    )

    private fun TestScope.events(vm: FoodDetailViewModel): List<FoodDetailEvent> {
        val events = mutableListOf<FoodDetailEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private suspend fun today(): Day = diary.observeDay(LoggerFixtures.today).first()

    private suspend fun entry(id: String): LogItem? = today().allItems.firstOrNull { it.id == id }

    private fun Display.text(): String? = (this as? Display.Shown)?.text

    @Test
    fun opensOnTheEntryAsItWasLogged() = runUnitTest {
        val vm = vm()

        val state = vm.state
        assertFalse(state.loading)
        assertEquals("Peanut butter", state.name)
        assertEquals("2", state.amount)
        assertEquals("tbsp", state.unit?.label)
        assertEquals(MealSlot.Snack, state.slot)
        assertEquals(second.loggedAt.toLocalDateTime(TimeZone.UTC).time, state.time)
        assertFalse(state.timeIsNow, "an entry's time is when it was logged, not now")
        assertEquals(Precision.Estimate, state.precision)
        assertEquals("190", assertNotNull(state.breakdown).energy.text())
        val entry = assertNotNull(state.entry)
        assertFalse(entry.changed)
        assertFalse(state.canAdd, "nothing to save until something changes")
        assertFalse(state.inRecents, "Remove from recents is for adding, not for an entry")
    }

    @Test
    fun savingChangesTheOneEntryOfTwoIdenticalOnes() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodDetailAction.AmountTyped("3"))
        assertTrue(vm.state.canAdd)
        assertEquals("285", assertNotNull(vm.state.breakdown).energy.text(), "the entry's own numbers, half as much again")
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val saved = diary.updated.single()
        assertEquals(second.id, saved.id, "the entry it was opened on")
        assertEquals(3.0, saved.quantity)
        assertEquals(285.0, saved.kcal!!, 0.01)
        assertEquals(48.0, saved.grams!!, 0.01)
        assertEquals(second.loggedAt, saved.loggedAt, "a time left alone stays the exact instant")
        assertEquals(first, entry(first.id), "the other helping is untouched")
        assertEquals(saved, entry(second.id))
        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.Saved), events)
        assertTrue(diary.added.isEmpty(), "saving never logs a new entry")
    }

    @Test
    fun removingTakesOnlyThatEntryOff() = runUnitTest {
        val vm = vm(first.id)
        val events = events(vm)

        vm.takeAction(FoodDetailAction.RemoveTapped)
        vm.takeAction(FoodDetailAction.RemoveTapped)

        assertEquals(listOf(first.id), diary.removed, "once, and only the first helping")
        assertNull(entry(first.id))
        assertEquals(second, entry(second.id))
        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.Removed(first.id, "Peanut butter")), events, "the page closes once; Undo puts back this id")
    }

    @Test
    fun nothingChangedSavesNothing() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        // Typed back to what it was.
        vm.takeAction(FoodDetailAction.AmountTyped("3"))
        vm.takeAction(FoodDetailAction.AmountTyped("2"))
        assertFalse(assertNotNull(vm.state.entry).changed)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertTrue(diary.updated.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun anotherMealMovesThatEntryAlone() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.SlotPicked(MealSlot.Dinner))
        assertTrue(vm.state.canAdd)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val saved = diary.updated.single()
        assertEquals(second.id, saved.id)
        assertEquals(MealSlot.Dinner, saved.slot)
        assertEquals(second.kcal, saved.kcal, "moving changes no number")
        assertEquals(listOf(first), today().items(MealSlot.Snack))
        assertEquals(listOf(saved), today().items(MealSlot.Dinner))
    }

    @Test
    fun anotherTimeRestampsThatEntryOnItsDay() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.TimePicked(LocalTime(15, 0)))
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val saved = diary.updated.single()
        assertEquals(second.id, saved.id)
        assertEquals(LocalDateTime(LoggerFixtures.today, LocalTime(15, 0)).toInstant(TimeZone.UTC), saved.loggedAt)
        assertEquals(second.amountText, saved.amountText)
    }

    @Test
    fun theNumbersAreTheEntrysEvenWhenTheFoodHasChangedSince() = runUnitTest {
        // The catalog corrected peanut butter to 120 kcal a tablespoon after this was logged.
        catalog.foods = listOf(LoggerFixtures.peanutButter.copy(per100g = LoggerFixtures.peanutButter.per100g.copy(kcal = 750.0)))
        val vm = vm()

        assertEquals("190", assertNotNull(vm.state.breakdown).energy.text())
        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        assertEquals("32", vm.state.amount, "2 tbsp is 32 g")
        vm.takeAction(FoodDetailAction.AmountTyped("16"))
        assertEquals("95", assertNotNull(vm.state.breakdown).energy.text(), "half the entry's weight is half its energy, whatever the catalog says now")
        assertEquals(Precision.Weighed, vm.state.precision, "typed in grams, so weighed")
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val saved = diary.updated.single()
        assertEquals(16.0, saved.grams!!, 0.01)
        assertEquals("g", saved.unit)
        assertEquals(Precision.Weighed, saved.precision)
    }

    @Test
    fun todayWithThisCountsTheEntryOnce() = runUnitTest {
        val dayTotal = assertNotNull(LoggerFixtures.twoHelpingsDay().totals().kcal.value)
        val vm = vm()

        val energy = assertNotNull(vm.state.targets).rows.first { it.metric == Metric.Calories }
        // The bar's parts: the rest of the day, then this entry's 190, which together are the day as it is.
        assertEquals(190.0 / dayTotal, (energy.food / (energy.soFar + energy.food)).toDouble(), 0.001, "not the day plus another helping")
    }

    @Test
    fun aDeletedFoodStillOpensFromTheEntrysOwnNumbers() = runUnitTest {
        catalog.foods = emptyList()
        val vm = vm()

        assertFalse(vm.state.missing)
        assertEquals("190", assertNotNull(vm.state.breakdown).energy.text())
        assertEquals("tbsp", vm.state.unit?.label, "its own unit, worked out from what it weighed")
        vm.takeAction(FoodDetailAction.AmountTyped("1"))
        assertEquals("95", assertNotNull(vm.state.breakdown).energy.text())
        assertNull(assertNotNull(vm.state.source).source, "with the food gone, nothing to name")
    }

    @Test
    fun aQuickNumberHasNoAmountAndNoFood() = runUnitTest {
        val day = today()
        diary.put(day.copy(itemsBySlot = day.itemsBySlot + (MealSlot.Lunch to day.items(MealSlot.Lunch) + LoggerFixtures.quickNumber)))
        val vm = vm(LoggerFixtures.quickNumber.id)

        val state = vm.state
        assertNull(state.unit, "no Amount row")
        assertFalse(state.hasFood, "nothing to star or report")
        assertEquals("300", assertNotNull(state.breakdown).energy.text())
        assertEquals(FoodOrigin.Typed, assertNotNull(state.source).source)
        vm.takeAction(FoodDetailAction.FavoriteTapped)
        assertTrue(settings.state.value.favoriteFoods.isEmpty())

        vm.takeAction(FoodDetailAction.SlotPicked(MealSlot.Snack))
        vm.takeAction(FoodDetailAction.AddToMealTapped)
        val saved = diary.updated.single()
        assertEquals(LoggerFixtures.quickNumber.copy(slot = MealSlot.Snack), saved, "only the meal changed")
    }

    @Test
    fun anEntryGoneFromTheDayClosesThePage() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        diary.removeItem(second.id)

        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.Dismiss), events)
    }

    @Test
    fun anEntryChangedElsewhereStartsThePageAgainFromIt() = runUnitTest {
        val vm = vm()
        vm.takeAction(FoodDetailAction.AmountTyped("5"))

        // Edit a copy switched it, or another phone changed it.
        diary.updateItem(second.withQuantity(4.0)!!)

        assertEquals("4", vm.state.amount)
        assertFalse(assertNotNull(vm.state.entry).changed)
    }

    @Test
    fun aClosedDayRefusesTheSaveAndSaysSo() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        diary.close(LoggerFixtures.today)

        vm.takeAction(FoodDetailAction.AmountTyped("3"))
        vm.takeAction(FoodDetailAction.AddToMealTapped)
        vm.takeAction(FoodDetailAction.RemoveTapped)

        assertTrue(diary.updated.isEmpty())
        assertTrue(diary.removed.isEmpty())
        assertEquals(List(2) { FoodDetailEvent.DayClosed(LoggerFixtures.today) }, events)
    }

    @Test
    fun repeatAndEditACopyStartFromTheEntry() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        assertEquals(EntryRepeat.New, assertNotNull(vm.state.entry).repeat)

        vm.takeAction(FoodDetailAction.RepeatTapped)

        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.OpenRepeat(LoggerFixtures.today, second.id, ruleId = null)), events)

        val copy = vm()
        val copyEvents = events(copy)
        copy.takeAction(FoodDetailAction.EditCopyTapped)
        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.OpenEntryCopy(LoggerFixtures.today, second.id)), copyEvents)
    }

    @Test
    fun aRepeatsEntryEditsItsRepeat() = runUnitTest {
        diary.updateItem(second.copy(repeatRuleId = "weekday-snack"))
        val vm = vm()
        val events = events(vm)

        assertEquals(EntryRepeat.Edit, assertNotNull(vm.state.entry).repeat)
        vm.takeAction(FoodDetailAction.RepeatTapped)

        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.OpenRepeat(LoggerFixtures.today, second.id, "weekday-snack")), events)
    }

    @Test
    fun repeatsOffOffersNoRepeat() = runUnitTest {
        repeatsOn = false
        val vm = vm()

        assertNull(assertNotNull(vm.state.entry).repeat)
    }

    @Test
    fun theFavoriteIsTheEntrysFood() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.FavoriteTapped)

        assertEquals(listOf(FoodRef.Catalog(LoggerFixtures.peanutButter.id).key), settings.state.value.favoriteFoods)
        assertTrue(vm.state.favorite)
        assertTrue(diary.updated.isEmpty(), "starring the food leaves the entry as it is")
    }

    @Test
    fun withMealGroupsOffTheEntryKeepsItsMeal() = runUnitTest {
        settings.state.value = Settings(mealGroups = false)
        val vm = vm()

        vm.takeAction(FoodDetailAction.AmountTyped("1"))
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertEquals(MealSlot.Snack, diary.updated.single().slot)
    }

    @Test
    fun noNumbersSavesTheHungerAnswerToTheEntrysMeal() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        val vm = vm()

        assertNotNull(vm.state.hunger)
        vm.takeAction(FoodDetailAction.HungerPicked(Hunger.Hungry))
        assertTrue(vm.state.canAdd)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertTrue(diary.updated.isEmpty(), "the entry itself did not change")
        val (day, slot, feeling) = diary.feelings.single()
        assertEquals(LoggerFixtures.today, day)
        assertEquals(MealSlot.Snack, slot)
        assertEquals(Hunger.Hungry, feeling?.hunger)
    }

    @Test
    fun theSourceSaysWhatItIsWhenTapped() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        assertEquals(FoodOrigin.Usda, assertNotNull(vm.state.source).source)
        assertFalse(assertNotNull(vm.state.source).explained)

        vm.takeAction(FoodDetailAction.SourceTapped)
        assertTrue(assertNotNull(vm.state.source).explained)
        vm.takeAction(FoodDetailAction.CreditsTapped)

        assertIs<FoodDetailEvent.OpenCredits>(events.single())
    }
}
