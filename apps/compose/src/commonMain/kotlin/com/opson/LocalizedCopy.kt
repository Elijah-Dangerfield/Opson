@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.nutrition.AmountCopy
import com.opson.libraries.nutrition.NutritionCopy
import com.opson.libraries.nutrition.ui.loadAmountWords
import com.opson.libraries.nutrition.ui.loadNutritionWords
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.DateFormats
import com.opson.libraries.ui.time.loadDateWords
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Puts the device's language into the copy that is spelled outside a composable: [NutritionCopy]
 * ("1,245 kcal", "On target"), [AmountCopy] ("150 g" as shown) and [DateCopy] ("Today",
 * "Thu, Oct 22"). All speak English until this runs, which is what tests and previews rely on.
 *
 * At boot, so a widget refresh or a reminder that wakes the app with no screen still gets the
 * language, and again from `App.kt` whenever the locale changes under a running app.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class LocalizedCopy(
    dateFormats: DateFormats,
    private val appScope: AppCoroutineScope,
) : AutoInit {

    init {
        DateCopy.install(dateFormats)
        appScope.launch { reload() }
    }

    /** Reads every set of words in the current language. A failure leaves the last ones in place. */
    suspend fun reload() {
        Catching {
            NutritionCopy.install(loadNutritionWords { getString(it) })
            AmountCopy.install(loadAmountWords { getString(it) })
            DateCopy.install(loadDateWords { getString(it) })
        }.logOnFailure("Couldn't load the nutrition, amount and date words; keeping the ones in place")
    }
}
