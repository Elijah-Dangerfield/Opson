package com.opson.features.logger.impl

import com.opson.features.logger.SubmitFoodAvailability
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for adding a packaged food from photos of its pack.
 * Off, a barcode miss and search offer what 0.1 did, the + sheet and the
 * Foods tab's Add a food don't offer it, and nothing can open the pack photos. The label is read
 * on the phone, so it needs nothing from the server but the review queue.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SubmitFoodEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Add a food from its pack"
    override val path = "release.submitFood"
    override val default = false
    override val description = "Add a packaged food from photos of the pack (barcode miss, search, + sheet, Foods), read, checked and sent for review"
}

/** [SubmitFoodAvailability] for the Foods tab, read from the same flag. */
@Inject
@ContributesBinding(AppScope::class)
class FlagSubmitFoodAvailability(private val flag: SubmitFoodEnabled) : SubmitFoodAvailability {
    override fun available(): Boolean = flag()
}
