package com.opson.features.today.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.BodyDetail
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_resting_needs_age
import opson.libraries.resources.generated.resources.settings_resting_needs_height
import opson.libraries.resources.generated.resources.settings_resting_needs_sex
import opson.libraries.resources.generated.resources.settings_resting_needs_weight
import opson.libraries.resources.generated.resources.today_burned_fix_profile
import opson.libraries.resources.generated.resources.today_burned_fix_weight
import opson.libraries.resources.generated.resources.today_burned_unavailable
import opson.libraries.resources.generated.resources.today_burned_unavailable_setup
import opson.libraries.resources.generated.resources.today_burned_unknown_action
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Why the day's Burned can't be worked out: the profile [details] resting energy is still waiting
 * on, missing or outside what the equation takes. Empty when nothing says which (an own number
 * that isn't valid, a health app with no reading and no details to fall back on).
 *
 * Today shows this wherever Burned would be, so the line never just disappears.
 */
data class BurnedGap(val details: Set<BodyDetail> = emptySet()) {
    /** Where Fix goes: Weight when a weigh-in is all that's needed, Profile for the rest, else Resting energy. */
    val fix: BurnedFix
        get() = when {
            details == setOf(BodyDetail.Weight) -> BurnedFix.Weight
            details.isNotEmpty() -> BurnedFix.Profile
            else -> BurnedFix.RestingEnergy
        }
}

enum class BurnedFix { Weight, Profile, RestingEnergy }

/** "Burned unavailable: needs a weigh-in", or "needs age, height", in the Resting energy page's words. */
@Composable
internal fun burnedUnavailableText(gap: BurnedGap): String {
    if (gap.details.isEmpty()) return stringResource(Res.string.today_burned_unavailable_setup)
    val needs = BodyDetail.entries.filter { it in gap.details }.map { stringResource(it.needsWord) }
    return stringResource(Res.string.today_burned_unavailable, needs.joinToString(", "))
}

private val BodyDetail.needsWord: StringResource
    get() = when (this) {
        BodyDetail.Weight -> Res.string.settings_resting_needs_weight
        BodyDetail.Height -> Res.string.settings_resting_needs_height
        BodyDetail.Age -> Res.string.settings_resting_needs_age
        BodyDetail.Sex -> Res.string.settings_resting_needs_sex
    }

/** The Activity card's Fix button: what it opens, in words. */
internal val BurnedFix.label: StringResource
    get() = when (this) {
        BurnedFix.Weight -> Res.string.today_burned_fix_weight
        BurnedFix.Profile -> Res.string.today_burned_fix_profile
        BurnedFix.RestingEnergy -> Res.string.today_burned_unknown_action
    }
