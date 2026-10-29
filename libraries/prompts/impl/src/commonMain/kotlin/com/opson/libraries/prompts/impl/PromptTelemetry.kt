package com.opson.libraries.prompts.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.prompts.PromptPresentation
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The prompts' app events (`docs/practices/app-events.md`, Prompts). Typed so a call site can
 * pass only a prompt id, how it was drawn and what called it up: no account, nothing typed.
 */
interface PromptTelemetry {
    fun shown(id: String, presentation: PromptPresentation, trigger: PromptTrigger)

    fun dismissed(id: String)

    fun accepted(id: String)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsPromptTelemetry : PromptTelemetry {
    private val logger = KLog.withTag("Prompts")

    override fun shown(id: String, presentation: PromptPresentation, trigger: PromptTrigger) {
        logger.logEvent("prompt.shown", "prompt" to id, "presentation" to presentation.wire, "trigger" to trigger.wire)
    }

    override fun dismissed(id: String) {
        logger.logEvent("prompt.dismissed", "prompt" to id)
    }

    override fun accepted(id: String) {
        logger.logEvent("prompt.accepted", "prompt" to id)
    }
}
