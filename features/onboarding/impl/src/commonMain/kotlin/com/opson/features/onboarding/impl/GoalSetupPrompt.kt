@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.onboarding.impl

import com.opson.features.onboarding.GoalSetupRoute
import com.opson.features.onboarding.OnboardingSetupEnabled
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.weightPlansOff
import kotlinx.datetime.LocalDate
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_goal_accept
import opson.libraries.resources.generated.resources.prompt_goal_body
import opson.libraries.resources.generated.resources.prompt_goal_title
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.days

/**
 * "Set up your goal in a minute", a card in Today's list that opens the goal flow
 * ([GoalSetupRoute]) for an account that never answered it: set up before onboarding asked, or one
 * that skipped the goal. A card, because it can wait; it comes back at most twice more, days apart,
 * until it is answered either way.
 *
 * Only where a weight goal makes sense: the setup flag on, the mode showing weight, and not while
 * pregnant or breastfeeding. Never after numbers have been hard, or for anyone under 18: the flow
 * is about a weight ([weightPlansOff]).
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class GoalSetupPrompt(
    private val flag: OnboardingSetupEnabled,
    private val settings: SettingsRepository,
    private val policy: VisibilityPolicySource,
    private val calendar: LocalCalendar,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.NORMAL
    override val presentation = PromptPresentation.Card
    override val content = PromptContent(
        icon = AppIcons.Favorite,
        title = Res.string.prompt_goal_title,
        body = Res.string.prompt_goal_body,
        accept = Res.string.prompt_goal_accept,
        dismiss = Res.string.prompt_not_now,
    )
    override val frequency = PromptFrequency(maxShows = 3, maxDismissals = 1, minGap = 3.days)

    override suspend fun isEligible(): Boolean = flag() && worthOffering(settings.settings.value, policy.policy.value, calendar.today())

    override suspend fun accept(): PromptAccept = PromptAccept.Open(GoalSetupRoute(GoalSetupRoute.FROM_TODAY))

    companion object {
        /** Never rename: it keys the counts and the app events. */
        const val ID = "set_up_goal"

        fun worthOffering(settings: Settings, policy: VisibilityPolicy, today: LocalDate): Boolean =
            !settings.weightGoal.isSet &&
                !settings.weightPlansOff(today) &&
                settings.bodyStage == BodyStage.None &&
                policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show
    }
}
