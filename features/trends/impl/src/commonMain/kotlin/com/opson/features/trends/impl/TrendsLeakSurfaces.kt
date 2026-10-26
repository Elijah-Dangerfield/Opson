package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * The Trends feature's surfaces for `BlindModeLeakTest`. Each builds its state
 * through the real builder from [TrendsFixtures] and the policy the test passes,
 * and renders the page body without its scaffold so everything composes at once.
 */
object TrendsLeakSurfaces {

    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "trends", label = "Trends (week history)", showsCaloriesInFull = true) { policy ->
            TrendsContent(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(), policy), onAction = {})
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
            TrendsSettingsSheetContent(TrendsSettingsStateBuilder.build(Settings(), policy), onAction = {})
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
        LeakSurface(id = "weight", label = "Weight", showsCaloriesInFull = false) { policy ->
            WeightContent(WeightStateBuilder.build(TrendsFixtures.weightInputs(), WeightUi(input = "72.4", saved = true), policy), onAction = {})
        },
        LeakSurface(id = "patterns", label = "Patterns", showsCaloriesInFull = true) { policy ->
            PatternsContent(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(), policy), onAction = {})
        },
        // Hunger tags off: the page Blind users who skip the tags see.
        LeakSurface(id = "patterns-blind", label = "Patterns, no hunger tags", showsCaloriesInFull = true) { policy ->
            PatternsContent(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(Settings(askTags = false)), policy), onAction = {})
        },
    )
}
