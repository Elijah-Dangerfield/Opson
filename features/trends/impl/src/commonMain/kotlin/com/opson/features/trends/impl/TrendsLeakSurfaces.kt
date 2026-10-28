package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.features.trends.impl.photos.CompareContent
import com.opson.features.trends.impl.photos.ProgressPhotosContent
import com.opson.features.trends.impl.photos.ProgressPhotosFixtures
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
        LeakSurface(id = "trends", label = "Trends (week history)", showsCaloriesInFull = true) { policy ->
            TrendsContent(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(expended = TrendsFixtures.expended()), policy), onAction = {})
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
        // Progress photos carry no numbers in any mode; listed so one slipped in later fails.
        LeakSurface(id = "progress-photos", label = "Progress photos", showsCaloriesInFull = false) { _ ->
            ProgressPhotosContent(ProgressPhotosFixtures.grid(), onAction = {})
        },
        LeakSurface(id = "progress-photos-compare", label = "Progress photos, compare", showsCaloriesInFull = false) { _ ->
            CompareContent(ProgressPhotosFixtures.compare(), onAction = {})
        },
    )
}
