package com.opson.features.builder

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * More › Builders: what Opson Builders is and Apply, then where the application stands, and once
 * accepted a Builder's home. Check [BuildersEnabled] before linking here.
 *
 * @param via where it was opened from (`more`, `prompt`, `report`), sent with the application.
 */
@Serializable
data class BuilderProgramRoute(val via: String = VIA_MORE) : Route() {
    companion object {
        const val VIA_MORE = "more"
        const val VIA_PROMPT = "prompt"
        const val VIA_REPORT = "report"
    }
}

/**
 * The release flag for Opson Builders (builder-program.md): More › Builders, the join prompt,
 * Builder mode in Add a packaged food, Check against the pack on a food's detail and the
 * scanner's "Also read the label". Off, none of them shows. Turn it on once the server has
 * `/v1/builders` (V30); against a server without it the page says Builders isn't open yet.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BuildersEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Opson Builders"
    override val path = "release.builders"
    override val default = false
    override val description = "Opson Builders: join for Pro, Builder mode with on-device label reading, check foods against the pack"
}
