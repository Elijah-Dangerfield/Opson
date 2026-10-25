package com.opson.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import com.opson.baselineprofile.BenchmarkJourney.PACKAGE
import com.opson.baselineprofile.BenchmarkJourney.launchIntent
import com.opson.baselineprofile.BenchmarkJourney.reachHome
import com.opson.baselineprofile.BenchmarkJourney.visitDetailScreens
import org.junit.Rule
import org.junit.Test

/**
 * The **baseline** profile: Home, then into the app's detail screens.
 *
 * `includeInStartupProfile = false` on purpose — see [StartupProfileGenerator]
 * for why putting the deep journey in the startup profile made cold start worse.
 * This one covers the first run of navigation, the ViewModels and the form UI,
 * which is where the app spends its time after launch.
 *
 * ## Iterations
 *
 * Capped well below the framework default of 15. Each iteration relaunches cold,
 * and every cold boot fans out into config fetches, a session call and the
 * user-scoped syncers. Fifteen of those is what put HTTP 429s into a downstream
 * dev backend; it was never the journey itself.
 */
class JourneyProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = false,
        maxIterations = 6,
        stableIterations = 3,
    ) {
        pressHome()
        startActivityAndWait(launchIntent())
        reachHome()
        visitDetailScreens()
    }
}
