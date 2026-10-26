package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The 0.2 release flag for fasting. Off means nothing about fasting renders: no More row, no
 * page, no Today card, no Edit cards row, and no fast ever starts. The 0.2 release flips it
 * remotely; the QA menu can flip it locally.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = FastingRelease::class)
class FastingReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), FastingRelease {
    override val name = "Fasting"
    override val path = "release.fasting"
    override val default = false
    override val description = "0.2: the Fasting page, its More row, the Today card and the timer"

    override fun isOn(): Boolean = value
}
