package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for adding a food from photos of its pack. Off, a
 * barcode miss and a search with no results offer what 0.1 did, and nothing
 * can open the pack photos. Turn it on once the server has
 * `ANTHROPIC_API_KEY` (without it the form still works, typed by hand) and
 * the privacy policy's section on submitted foods is live.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SubmitFoodEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Add a food from its pack"
    override val path = "release.submitFood"
    override val default = false
    override val description = "Add this food on a barcode miss or an empty search: photos of the pack, read and checked, sent for review"
}
