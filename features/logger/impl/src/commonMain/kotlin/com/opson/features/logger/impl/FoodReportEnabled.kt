package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for "Report a problem" on a catalog food. Off, the food detail's ⋮ item opens
 * the bug report with the food's id, as it did before. Turn it on once the server has
 * `POST /v1/foods/{id}/reports` (V29); against a server without it the sheet says the food can't
 * be reported and nothing is lost.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class FoodReportEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Report a problem with a food"
    override val path = "release.foodReports"
    override val default = false
    override val description = "Report a problem on a food's detail: what is wrong, the right numbers, a label photo, sent for review"
}
