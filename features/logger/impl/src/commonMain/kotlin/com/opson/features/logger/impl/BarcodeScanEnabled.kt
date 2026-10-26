package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for barcode scanning (docs/plans/0.2/README.md). Off,
 * the logger's search bar has no barcode button and nothing can open the
 * scanner, exactly as 0.1 shipped. The 0.2 release flips it remotely.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BarcodeScanEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Barcode scan"
    override val path = "release.barcode"
    override val default = false
    override val description = "The barcode button in the logger's search bar, and the scanner it opens"
}
