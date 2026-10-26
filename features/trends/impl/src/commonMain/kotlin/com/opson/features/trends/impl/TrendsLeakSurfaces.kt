package com.opson.features.trends.impl

import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Settings
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
            WeightContent(WeightStateBuilder.build(TrendsFixtures.weightInputs(), WeightUi(), policy), onAction = {})
        },
    )
}
