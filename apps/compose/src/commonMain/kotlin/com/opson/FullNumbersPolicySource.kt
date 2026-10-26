package com.opson

import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * Temporary: serves the Full preset to everyone. The diary's `SettingsRepository` replaces this
 * binding with one built from the user's persisted numbers settings; delete this file when it
 * lands, since two bindings for the source will not compile.
 *
 * The system time zone is read once, so a zone change mid-session is only picked up on the next
 * launch. The real source should rebuild the policy when the zone changes.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FullNumbersPolicySource(clock: Clock) : VisibilityPolicySource {
    override val policy: StateFlow<VisibilityPolicy> = MutableStateFlow(
        VisibilityPolicy(
            settings = NumbersSettings(preset = PresetId.Full),
            clock = clock,
            timeZone = TimeZone.currentSystemDefault(),
        ),
    ).asStateFlow()
}
