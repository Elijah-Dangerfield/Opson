package com.opson.libraries.progressphotos

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The release flag for progress photos (design sync 2, 8.4 and 8.5). Off, the
 * Weight page has no Progress photos row and nothing can open the photo
 * screens; nothing about photos runs at launch either way.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProgressPhotosEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Progress photos"
    override val path = "release.progressPhotos"
    override val default = false
    override val description = "The Progress photos row on Weight, and the private photo grid, camera and compare it opens"
}
