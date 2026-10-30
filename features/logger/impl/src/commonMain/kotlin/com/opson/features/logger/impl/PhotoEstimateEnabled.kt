package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for Photo estimate (docs/plans/0.2/README.md). Off,
 * the + sheet has no Photo tile, search no "Photograph it", and nothing can open the photo sheet, exactly
 * as 0.1 shipped; nothing about photos runs at launch either way. It stays off
 * in v1: an estimate needs a model on the server (`AI_CALLS_ENABLED` and
 * `ANTHROPIC_API_KEY`), and v1 runs with no server AI.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class PhotoEstimateEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Photo estimate"
    override val path = "release.photo"
    override val default = false
    override val description = "Photo on the + sheet and in search, and the photo estimate sheet they open"
}
