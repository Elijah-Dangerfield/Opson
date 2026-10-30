package com.opson.features.foods.impl

import com.opson.features.foods.LogRecipeRoute
import com.opson.features.foods.PublicRecipeRoute
import com.opson.features.foods.RecipeEditRoute
import com.opson.features.foods.RecipeRoute
import com.opson.features.foods.RecipeShareRoute
import com.opson.features.foods.ReportRecipeRoute
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.diary.RecipesRelease
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.scanner.BarcodeFormat
import com.opson.libraries.scanner.ScannedCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_ingredients_full
import opson.libraries.resources.generated.resources.recipes_ingredients_left_out
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Recipes: making one, its page, sharing it, someone's shared one, logging a serving, reporting and scanning. */
class RecipesTest : CoroutineTest() {
    private val recipes = FakeRecipeRepository(listOf(RecipeFixtures.chili))
    private val publicRecipes = FakePublicRecipes()
    private val policySource = FakePolicySource()
    private val mealPicks = FakeMealPicks()
    private val diary = FakeDiaryRepository()

    private fun <E : Any> TestScope.events(vm: SEAViewModel<*, E, *>): List<E> {
        val events = mutableListOf<E>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private class FakeProfiles(private val profile: Profile) : ProfileRepository {
        override suspend fun current(): Profile = profile
        override fun observe(): Flow<Profile> = flowOf(profile)
        override suspend fun update(displayName: String?): UpdateProfileOutcome = error("unused")
    }

    private fun signedIn(name: String) = FakeProfiles(Profile.Authenticated("u1", name, null, isAnonymous = false, createdAt = Instant.parse("2026-01-01T00:00:00Z")))

    // ---- the numbers ----

    @Test
    fun aServingIsTheWholeRecipeDividedByItsServings() {
        val state = RecipeFixtures.viewState(policy(PresetId.Full))
        // 728 + 798 + 254 + 60 = 1840 for six.
        assertEquals("1,840 kcal", (state.whole.calories as Display.Shown).text)
        assertEquals("307 kcal", (state.perServing.calories as Display.Shown).text)
        assertEquals("6 servings", state.servings)
    }

    @Test
    fun noNumbersHidesEveryAmountOnEveryRecipeScreen() {
        val blind = policy(PresetId.NoNumbers)
        val view = RecipeFixtures.viewState(blind)
        assertEquals(Display.Hidden, view.perServing.calories)
        assertTrue(view.perServing.macros.isEmpty())
        assertTrue(view.ingredients.all { it.calories == Display.Hidden })
        val public = RecipeFixtures.publicState(blind)
        assertEquals(Display.Hidden, public.perServing.calories)
        assertTrue(public.ingredients.all { it.calories == Display.Hidden })
        assertEquals(Display.Hidden, RecipeFixtures.logState(blind).numbers.calories)
    }

    // ---- making one ----

    @Test
    fun aNewRecipeTakesPickedIngredientsAndSavesWithItsServings() = runUnitTest {
        val vm = RecipeEditViewModel(RecipeEditRoute(), recipes, mealPicks, policySource)
        val events = events(vm)
        advanceUntilIdle()
        assertTrue(!vm.state.canSave)

        vm.takeAction(RecipeEditAction.NameTyped("Chili"))
        vm.takeAction(RecipeEditAction.AddIngredientTapped)
        advanceUntilIdle()
        val request = assertIs<RecipeEditEvent.OpenPicker>(events.last())
        mealPicks.picked(PickedForMeal(request.requestId, RecipeFixtures.chiliIngredients))
        vm.takeAction(RecipeEditAction.MoreTapped)
        vm.takeAction(RecipeEditAction.MoreTapped)
        advanceUntilIdle()
        assertEquals("6 servings", vm.state.servings)
        assertEquals(4, vm.state.ingredients.size)

        vm.takeAction(RecipeEditAction.SaveTapped)
        vm.takeAction(RecipeEditAction.SaveTapped)
        advanceUntilIdle()
        val saved = recipes.recipes.value.values.single { it.name == "Chili" }
        assertEquals(6.0, saved.servings)
        assertEquals(RecipeEditEvent.Saved(saved.id, created = true), events.last())
        assertEquals(2, recipes.recipes.value.size, "a second Save writes nothing")
    }

    @Test
    fun picksPastTheLimitAreLeftOutAndSaidSo_andAFullRecipeOffersNoMore() = runUnitTest {
        val vm = RecipeEditViewModel(RecipeEditRoute(), recipes, mealPicks, policySource)
        val events = events(vm)
        advanceUntilIdle()
        vm.takeAction(RecipeEditAction.AddIngredientTapped)
        advanceUntilIdle()
        val request = assertIs<RecipeEditEvent.OpenPicker>(events.last())
        val many = List(Recipe.MAX_INGREDIENTS + 5) { RecipeFixtures.chiliIngredients.first() }

        mealPicks.picked(PickedForMeal(request.requestId, many))
        advanceUntilIdle()

        assertEquals(Recipe.MAX_INGREDIENTS, vm.state.ingredients.size)
        assertEquals(RecipeEditEvent.ShowToast(Toast(Res.string.recipes_ingredients_left_out, listOf(Recipe.MAX_INGREDIENTS))), events.last())
        assertFalse(vm.state.canAddIngredient)
        vm.takeAction(RecipeEditAction.AddIngredientTapped)
        advanceUntilIdle()
        assertEquals(RecipeEditEvent.ShowToast(Toast(Res.string.recipes_ingredients_full, listOf(Recipe.MAX_INGREDIENTS))), events.last())
        assertEquals(1, events.count { it is RecipeEditEvent.OpenPicker }, "no picker for a full recipe")
    }

    @Test
    fun servingsStepByOneThenByHalvesBelowOne() {
        assertEquals(3.0, RecipeDraftUi.fewer(4.0))
        assertEquals(0.5, RecipeDraftUi.fewer(1.0))
        assertEquals(0.25, RecipeDraftUi.fewer(0.5))
        assertEquals(0.5, RecipeDraftUi.more(0.25))
        assertEquals(1.0, RecipeDraftUi.more(0.5))
        assertEquals(5.0, RecipeDraftUi.more(4.0))
    }

    @Test
    fun anEditLoadsTheRecipeAndUpdatesIt() = runUnitTest {
        val vm = RecipeEditViewModel(RecipeEditRoute(RecipeFixtures.chili.id), recipes, mealPicks, policySource)
        val events = events(vm)
        advanceUntilIdle()
        assertEquals("Weeknight chili", vm.state.name)
        assertTrue(vm.state.editing)
        vm.takeAction(RecipeEditAction.FewerTapped)
        vm.takeAction(RecipeEditAction.SaveTapped)
        advanceUntilIdle()
        assertEquals(5.0, recipes.recipes.value.getValue(RecipeFixtures.chili.id).servings)
        assertEquals(RecipeEditEvent.Saved(RecipeFixtures.chili.id, created = false), events.last())
    }

    // ---- its page ----

    @Test
    fun deletingClosesThePageOnce() = runUnitTest {
        val vm = RecipeViewModel(RecipeRoute(RecipeFixtures.chili.id), recipes, policySource)
        val events = events(vm)
        advanceUntilIdle()
        vm.takeAction(RecipeViewAction.DeleteTapped)
        advanceUntilIdle()
        assertTrue(vm.state.confirmingDelete)
        vm.takeAction(RecipeViewAction.DeleteConfirmed)
        advanceUntilIdle()
        assertTrue(recipes.recipes.value.isEmpty())
        assertEquals(1, events.count { it == RecipeViewEvent.Close })
    }

    // ---- sharing ----

    @Test
    fun sharingAsksFirst_thenShowsTheLinkAndCode() = runUnitTest {
        publicRecipes.publishResult = PublishResult.Published(RecipeSharing("k7m2p9qx", RecipeShareState.Live, showName = true))
        val vm = RecipeShareViewModel(RecipeShareRoute(RecipeFixtures.chili.id), recipes, publicRecipes, signedIn("Elijah"))
        advanceUntilIdle()
        val consent = assertIs<ShareSheetState.Consent>(vm.state)
        assertEquals("Elijah", consent.displayName)
        assertEquals(false, consent.showName, "the name is off until they turn it on")
        assertTrue(publicRecipes.published.isEmpty(), "nothing is public before Share recipe")

        vm.takeAction(RecipeShareAction.ShowNameToggled(true))
        vm.takeAction(RecipeShareAction.ShareTapped)
        advanceUntilIdle()
        assertEquals(true, publicRecipes.published.single().second)
        val shared = assertIs<ShareSheetState.Shared>(vm.state)
        assertEquals("https://www.opsontracker.com/r/k7m2p9qx", shared.link)
        assertEquals("k7m2p9qx", recipes.recipes.value.getValue(RecipeFixtures.chili.id).sharing.code)
    }

    @Test
    fun aMadeUpNameIsNeverOfferedToShow() = runUnitTest {
        val vm = RecipeShareViewModel(RecipeShareRoute(RecipeFixtures.chili.id), recipes, publicRecipes, signedIn("user-1a2b3c4d"))
        advanceUntilIdle()
        assertNull(assertIs<ShareSheetState.Consent>(vm.state).displayName)
    }

    @Test
    fun aShareThatCantGoSaysWhy_andARemovedOneSaysItCantBeSharedAgain() = runUnitTest {
        val vm = RecipeShareViewModel(RecipeShareRoute(RecipeFixtures.chili.id), recipes, publicRecipes, signedIn("Elijah"))
        advanceUntilIdle()
        vm.takeAction(RecipeShareAction.ShareTapped)
        advanceUntilIdle()
        val offline = assertIs<ShareSheetState.Consent>(vm.state)
        assertEquals(false, offline.sharing)
        assertTrue(offline.problem != null)

        publicRecipes.publishResult = PublishResult.Removed
        vm.takeAction(RecipeShareAction.ShareTapped)
        advanceUntilIdle()
        assertEquals(RecipeShareState.Removed, recipes.recipes.value.getValue(RecipeFixtures.chili.id).sharing.state)
    }

    @Test
    fun stoppingSharingMarksItPrivateAndCloses() = runUnitTest {
        recipes.recipes.value += RecipeFixtures.sharedChili.id to RecipeFixtures.sharedChili
        val vm = RecipeShareViewModel(RecipeShareRoute(RecipeFixtures.chili.id), recipes, publicRecipes, signedIn("Elijah"))
        val events = events(vm)
        advanceUntilIdle()
        assertIs<ShareSheetState.Shared>(vm.state)
        vm.takeAction(RecipeShareAction.SaveQrTapped)
        vm.takeAction(RecipeShareAction.StopTapped)
        advanceUntilIdle()
        val png = assertIs<RecipeShareEvent.ShareQr>(events.first())
        assertEquals("opson-weeknight-chili.png", png.fileName)
        assertEquals(listOf(RecipeFixtures.chili.id), publicRecipes.unpublished)
        assertEquals(RecipeShareState.Private, recipes.recipes.value.getValue(RecipeFixtures.chili.id).sharing.state)
        assertEquals(RecipeShareEvent.Close, events.last())
    }

    // ---- someone's shared recipe ----

    @Test
    fun aSharedRecipeShowsHowManyIngredientsOpsonChecked_andSavesAsTheirs() = runUnitTest {
        publicRecipes.lookup = PublicRecipeLookup.Found(RecipeFixtures.oats)
        val vm = PublicRecipeViewModel(PublicRecipeRoute(RecipeFixtures.oats.code), publicRecipes, recipes, policySource)
        val events = events(vm)
        advanceUntilIdle()
        val loaded = assertIs<PublicRecipeState.Loaded>(vm.state)
        assertEquals(2, loaded.checkedCount)
        assertEquals(listOf(true, true, false), loaded.ingredients.map { it.checked })
        assertEquals("Sam", loaded.by)

        vm.takeAction(PublicRecipeAction.SaveTapped)
        vm.takeAction(PublicRecipeAction.SaveTapped)
        advanceUntilIdle()
        val copy = recipes.recipes.value.values.single { it.savedFrom == RecipeFixtures.oats.code }
        assertEquals(RecipeFixtures.oats.ingredients.size, copy.ingredients.size)
        assertEquals(listOf(RecipeFixtures.oats.code), publicRecipes.saves)
        assertEquals(PublicRecipeEvent.OpenRecipe(copy.id), events.last())
    }

    @Test
    fun aRecipeSavedBeforeOffersTheCopyInsteadOfASecond() = runUnitTest {
        publicRecipes.lookup = PublicRecipeLookup.Found(RecipeFixtures.oats)
        val first = PublicRecipeViewModel(PublicRecipeRoute(RecipeFixtures.oats.code), publicRecipes, recipes, policySource)
        advanceUntilIdle()
        first.takeAction(PublicRecipeAction.SaveTapped)
        advanceUntilIdle()
        val copy = recipes.recipes.value.values.single { it.savedFrom == RecipeFixtures.oats.code }

        val again = PublicRecipeViewModel(PublicRecipeRoute(RecipeFixtures.oats.code), publicRecipes, recipes, policySource)
        val events = events(again)
        advanceUntilIdle()
        val loaded = assertIs<PublicRecipeState.Loaded>(again.state)
        assertEquals(copy.id, loaded.savedId)
        assertNull(loaded.mineId, "a saved copy is not their own recipe")
        again.takeAction(PublicRecipeAction.SaveTapped)
        again.takeAction(PublicRecipeAction.OpenMineTapped)
        advanceUntilIdle()

        assertEquals(1, recipes.recipes.value.values.count { it.savedFrom == RecipeFixtures.oats.code }, "no second copy")
        assertEquals(listOf(RecipeFixtures.oats.code), publicRecipes.saves, "and no second count")
        assertEquals(PublicRecipeEvent.OpenRecipe(copy.id), events.last())
    }

    @Test
    fun aRecipeNoLongerSharedSaysSo() = runUnitTest {
        publicRecipes.lookup = PublicRecipeLookup.NotShared
        val vm = PublicRecipeViewModel(PublicRecipeRoute("abcd2345"), publicRecipes, recipes, policySource)
        advanceUntilIdle()
        assertEquals(PublicRecipeState.Missing(PublicRecipeState.Missing.Why.NotShared), vm.state)
    }

    @Test
    fun yourOwnSharedRecipeOffersYourCopyInsteadOfSaving() = runUnitTest {
        recipes.recipes.value += RecipeFixtures.sharedChili.id to RecipeFixtures.sharedChili
        publicRecipes.lookup = PublicRecipeLookup.Found(RecipeFixtures.oats.copy(code = "k7m2p9qx"))
        val vm = PublicRecipeViewModel(PublicRecipeRoute("k7m2p9qx"), publicRecipes, recipes, policySource)
        advanceUntilIdle()
        assertEquals(RecipeFixtures.chili.id, assertIs<PublicRecipeState.Loaded>(vm.state).mineId)
    }

    // ---- logging a serving ----

    @Test
    fun loggingAServingWritesOneEntryToTheMealPicked() = runUnitTest {
        val clock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-25T19:00:00Z")
        }
        val vm = LogRecipeViewModel(LogRecipeRoute(recipeId = RecipeFixtures.chili.id), recipes, publicRecipes, diary, FakeCalendar(), clock, policySource)
        val events = events(vm)
        advanceUntilIdle()
        assertEquals(MealSlot.Dinner, vm.state.slot, "seven in the evening is dinner")
        assertEquals("1 serving", vm.state.amount)

        vm.takeAction(LogRecipeAction.MoreTapped)
        vm.takeAction(LogRecipeAction.SlotPicked(MealSlot.Lunch))
        vm.takeAction(LogRecipeAction.LogTapped)
        advanceUntilIdle()
        val (day, slot, items) = diary.added.single()
        assertEquals(FoodsFixtures.today, day)
        assertEquals(MealSlot.Lunch, slot)
        assertEquals("Weeknight chili", items.single().name)
        assertEquals("1½ servings", items.single().amountText)
        assertEquals(1840.0 / 6 * 1.5, items.single().kcal!!, 1e-9)
        assertEquals(listOf(LogMethod.Saved), diary.addedMethods)
        assertEquals(LogRecipeEvent.Close, events.last())
    }

    // ---- reporting ----

    @Test
    fun aReportNeedsAReason_andARetryIsTheSameReport() = runUnitTest {
        publicRecipes.reportResult = RecipeActionResult.Failed(offline = true)
        val vm = ReportRecipeViewModel(ReportRecipeRoute("abcd2345", "Overnight oats"), publicRecipes)
        val events = events(vm)
        assertTrue(!vm.state.canSend)
        vm.takeAction(ReportRecipeAction.ReasonPicked(RecipeReportReason.Unsafe))
        vm.takeAction(ReportRecipeAction.SendTapped)
        advanceUntilIdle()
        assertTrue(vm.state.problem != null)
        publicRecipes.reportResult = RecipeActionResult.Done
        vm.takeAction(ReportRecipeAction.SendTapped)
        advanceUntilIdle()
        assertEquals(1, publicRecipes.reports.map { it.second }.distinct().size)
        assertIs<ReportRecipeEvent.Sent>(events.single())
    }

    // ---- scanning ----

    @Test
    fun aRecipeCodeOpensItOnce_andAnyOtherCodeSaysSo() = runUnitTest {
        val vm = ScanRecipeViewModel(FakeCameraPermissions())
        val events = events(vm)
        vm.takeAction(ScanRecipeAction.CodeRead(ScannedCode("https://example.com", BarcodeFormat.Qr)))
        advanceUntilIdle()
        assertTrue(vm.state.notRecipe)
        vm.takeAction(ScanRecipeAction.CodeRead(ScannedCode("https://www.opsontracker.com/r/k7m2p9qx", BarcodeFormat.Qr)))
        vm.takeAction(ScanRecipeAction.CodeRead(ScannedCode("https://www.opsontracker.com/r/k7m2p9qx", BarcodeFormat.Qr)))
        advanceUntilIdle()
        assertEquals(listOf(ScanRecipeEvent.Scanned, ScanRecipeEvent.OpenRecipe("k7m2p9qx")), events)
    }

    // ---- the Foods tab ----

    @Test
    fun theRecipesCardIsOnlyThereWithTheFlag() {
        val off = FoodsFixtures.foodsState(policy(PresetId.Full))
        assertTrue(!off.recipesOn)
        val on = FoodsFixtures.foodsState(policy(PresetId.Full), recipesOn = true)
        assertTrue(on.recipesOn)
        assertEquals(listOf("Overnight oats", "Weeknight chili"), on.recipes.map { it.name }.sorted())
        assertEquals(listOf(true), on.recipes.filter { it.name == "Weeknight chili" }.map { it.shared })
        assertTrue(RecipesRelease { false }.isOn().not())
    }
}
