@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl.candidates

import com.opson.features.settings.IntegrationsRoute
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.prompts.PromptPriority
import com.opson.libraries.ui.components.icon.AppIcons
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_health_accept
import opson.libraries.resources.generated.resources.prompt_health_body_apple
import opson.libraries.resources.generated.resources.prompt_health_body_connect
import opson.libraries.resources.generated.resources.prompt_health_title_apple
import opson.libraries.resources.generated.resources.prompt_health_title_connect
import opson.libraries.resources.generated.resources.prompt_not_now
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/*
 * The three prompts Opson ships live here rather than with the pages they open, because each is
 * a nudge across features and the processor's rules are easiest to read beside them. A prompt
 * owned by one feature (the Builder program's) belongs in that feature's impl instead.
 */

/**
 * "Bring in Apple Health" (or Health Connect), a sheet that opens Watch and health apps, where
 * Connect asks for permission from a tap. Only where the phone has a health store that is not
 * connected yet, and only where calories are on show: what Health adds is the energy used, and
 * nudging toward it in a mode that hides calories, or after numbers have been hard, would be the
 * wrong push.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ConnectHealthPrompt(
    private val health: HealthRepository,
    private val flag: HealthReleaseFlag,
    private val settings: SettingsRepository,
) : PromptCandidate {
    override val id = ID
    override val priority = PromptPriority.HIGH
    override val presentation = PromptPresentation.Sheet
    override val content = contentFor(health.source)

    override suspend fun isEligible(): Boolean {
        if (!flag()) return false
        if (settings.settings.value.numbers.level(Metric.Calories) == Visibility.Hide) return false
        health.refresh()
        return health.status.value == HealthStatus.NotConnected
    }

    override suspend fun accept(): PromptAccept = PromptAccept.Open(IntegrationsRoute())

    companion object {
        const val ID = "connect_health"

        fun contentFor(source: HealthSource): PromptContent = when (source) {
            HealthSource.AppleHealth -> PromptContent(
                icon = AppIcons.Watch,
                title = Res.string.prompt_health_title_apple,
                body = Res.string.prompt_health_body_apple,
                accept = Res.string.prompt_health_accept,
                dismiss = Res.string.prompt_not_now,
            )
            HealthSource.HealthConnect -> PromptContent(
                icon = AppIcons.Watch,
                title = Res.string.prompt_health_title_connect,
                body = Res.string.prompt_health_body_connect,
                accept = Res.string.prompt_health_accept,
                dismiss = Res.string.prompt_not_now,
            )
        }
    }
}
