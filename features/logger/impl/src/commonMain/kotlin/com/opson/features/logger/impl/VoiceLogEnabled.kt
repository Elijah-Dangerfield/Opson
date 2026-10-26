package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for voice log (docs/plans/0.2/README.md). Off, the
 * Describe box has no mic, nothing asks for the microphone and the recognizer
 * is never called, exactly as 0.1 shipped. Read when the logger opens.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class VoiceLogEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Voice log"
    override val path = "release.voice"
    override val default = false
    override val description = "The mic in Describe it, which turns speech into the Describe text"
}
