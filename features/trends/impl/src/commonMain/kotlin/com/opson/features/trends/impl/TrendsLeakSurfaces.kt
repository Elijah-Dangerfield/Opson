package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.impl.photos.CompareContent
import com.opson.features.trends.impl.photos.ProgressPhotosContent
import com.opson.features.trends.impl.photos.ProgressPhotosFixtures
import com.opson.features.trends.impl.recap.RecapBannerCard
import com.opson.features.trends.impl.recap.RecapContent
import com.opson.features.trends.impl.recap.RecapFixtures
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The Trends feature's surfaces for `CaloriesHiddenLeakTest`. Each builds its state
 * through the real builder from [TrendsFixtures] and the policy the test passes,
 * and renders the page body without its scaffold so everything composes at once.
 */
object TrendsLeakSurfaces {

    val all: List<LeakSurface> = listOf(
        // With release.recaps: the links to last week's and last month's recaps name dates, never an amount.
        LeakSurface(id = "trends", label = "Trends (week history)", showsCaloriesInFull = true) { policy ->
            TrendsContent(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(expended = TrendsFixtures.expended(), recapsOn = true), policy), onAction = {})
        },
        // The design's Blind and Coast screens. The test passes the policy, so these differ from
        // `trends` in what they are built from: Month (every card follows the range) and the
        // hunger-free settings a Coast user is likely to keep.
        LeakSurface(id = "trends-blind", label = "Trends, Blind (the week leads)", showsCaloriesInFull = true) { policy ->
            TrendsContent(
                TrendsStateBuilder.build(TrendsFixtures.trendsInputs(Settings(trendsRange = TrendsRange.FourWeeks)), policy),
                onAction = {},
            )
        },
        LeakSurface(id = "trends-coast", label = "Trends, Coast (before the weekly check)", showsCaloriesInFull = true) { policy ->
            TrendsContent(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(Settings(askTags = false)), policy), onAction = {})
        },
        // A set deficit with Burned known: the on-track calendar against it, the running total, and
        // the check-ins' hunger. Where calories or energy out are short of Show the calendar only
        // says which days were logged and the running total is not drawn.
        LeakSurface(id = "trends-deficit", label = "Trends, a set deficit (calendar and running total)", showsCaloriesInFull = true) { policy ->
            TrendsContent(TrendsStateBuilder.build(TrendsFixtures.deficitInputs(TrendsFixtures.settings(policy)), policy), onAction = {})
        },
        // The calendar's day sheet for Thursday the 22nd, against the deficit: eaten, burned, the difference and the plan.
        LeakSurface(id = "trends-day", label = "A day from the on-track calendar", showsCaloriesInFull = true) { policy ->
            val inputs = TrendsFixtures.deficitInputs(TrendsFixtures.settings(policy))
            val date = kotlinx.datetime.LocalDate(2026, 10, 22)
            val day = inputs.days.first { it.date == date }
            TrendsDaySheetContent(
                TrendsDayStateBuilder.build(day, inputs.today, inputs.settings, policy, inputs.burnedByDay?.get(date)),
                onAction = {},
            )
        },
        LeakSurface(id = "trends-settings", label = "Trends settings", showsCaloriesInFull = false) { policy ->
            TrendsSettingsSheetContent(TrendsSettingsStateBuilder.build(Settings(), policy, healthConnected = true), onAction = {})
        },
        LeakSurface(id = "nutrients", label = "Nutrients detail", showsCaloriesInFull = true) { policy ->
            NutrientsContent(NutrientsStateBuilder.build(TrendsFixtures.nutrientsInputs(NutrientsRange.Day), policy), onAction = {})
        },
        LeakSurface(id = "nutrients-week", label = "Nutrients detail, week average", showsCaloriesInFull = true) { policy ->
            NutrientsContent(
                NutrientsStateBuilder.build(TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage), policy),
                onAction = {},
            )
        },
        // Which foods gave a nutrient. Never energy, so no calories in any mode; iron is a micro,
        // so it must vanish where micros do, and protein's grams where macros soften.
        LeakSurface(id = "nutrient-foods", label = "Which foods gave iron", showsCaloriesInFull = false) { policy ->
            val nutrient = Nutrient.Micro(Micronutrient.Iron)
            NutrientFoodsSheetContent(NutrientFoodsStateBuilder.build(TrendsFixtures.nutrientFoodsInputs(nutrient), policy), nutrient, onAction = {})
        },
        LeakSurface(id = "nutrient-foods-week", label = "Which foods gave sodium, over the week", showsCaloriesInFull = false) { policy ->
            val nutrient = Nutrient.Micro(Micronutrient.Sodium)
            NutrientFoodsSheetContent(
                NutrientFoodsStateBuilder.build(TrendsFixtures.nutrientFoodsInputs(nutrient, NutrientsRange.WeekAverage), policy),
                nutrient,
                onAction = {},
            )
        },
        LeakSurface(id = "nutrient-foods-protein", label = "Which foods gave protein", showsCaloriesInFull = false) { policy ->
            val nutrient = Nutrient.Macro(Metric.Protein)
            NutrientFoodsSheetContent(NutrientFoodsStateBuilder.build(TrendsFixtures.nutrientFoodsInputs(nutrient), policy), nutrient, onAction = {})
        },
        LeakSurface(id = "weight", label = "Weight", showsCaloriesInFull = false) { policy ->
            WeightContent(WeightStateBuilder.build(TrendsFixtures.weightInputs(), WeightUi(input = "72.4", saved = true), policy), onAction = {})
        },
        LeakSurface(id = "patterns", label = "Patterns", showsCaloriesInFull = true) { policy ->
            PatternsContent(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(checkInOn = true), policy), onAction = {})
        },
        // Hunger tags off: the page Blind users who skip the tags see.
        LeakSurface(id = "patterns-blind", label = "Patterns, no hunger tags", showsCaloriesInFull = true) { policy ->
            PatternsContent(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(Settings(askTags = false)), policy), onAction = {})
        },
        // Recaps (release.recaps): the week read on the Monday after, with Burned, check-ins and the
        // run; a month; the week still going; and after numbers have been hard, which never shows an
        // amount even in All numbers.
        LeakSurface(id = "recap-week", label = "A week's recap", showsCaloriesInFull = true) { policy ->
            RecapContent(RecapFixtures.state(policy), onAction = {})
        },
        LeakSurface(id = "recap-deficit", label = "A week's recap, against a set deficit", showsCaloriesInFull = true) { policy ->
            RecapContent(RecapFixtures.state(policy, deficit = true), onAction = {})
        },
        LeakSurface(id = "recap-goals", label = "A week's recap, against each goal", showsCaloriesInFull = true) { policy ->
            RecapContent(RecapFixtures.state(policy, goals = true), onAction = {})
        },
        LeakSurface(id = "recap-month", label = "A month's recap", showsCaloriesInFull = true) { policy ->
            RecapContent(RecapFixtures.state(policy, RecapFixtures.month), onAction = {})
        },
        LeakSurface(id = "recap-in-progress", label = "This week's recap, so far", showsCaloriesInFull = true) { policy ->
            RecapContent(RecapFixtures.inProgress(policy), onAction = {})
        },
        LeakSurface(id = "recap-hard", label = "A week's recap, after numbers have been hard", showsCaloriesInFull = false) { policy ->
            RecapContent(RecapFixtures.state(policy, hard = true), onAction = {})
        },
        LeakSurface(id = "recap-empty", label = "A week's recap, nothing logged", showsCaloriesInFull = false) { policy ->
            RecapContent(RecapFixtures.empty(policy), onAction = {})
        },
        LeakSurface(id = "recap-banner", label = "Your week is ready, on Today", showsCaloriesInFull = false) { _ ->
            RecapBannerCard(RecapFixtures.week, onOpen = {}, onNotNow = {}, onTurnOff = {})
        },
        // Progress photos carry no numbers in any mode; listed so one slipped in later fails.
        LeakSurface(id = "progress-photos", label = "Progress photos", showsCaloriesInFull = false) { _ ->
            ProgressPhotosContent(ProgressPhotosFixtures.grid(), onAction = {})
        },
        LeakSurface(id = "progress-photos-compare", label = "Progress photos, compare", showsCaloriesInFull = false) { _ ->
            CompareContent(ProgressPhotosFixtures.compare(), onAction = {})
        },
    )
}
