package com.opson.features.qa

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The QA menu: fire notifications, open the check-in, reset onboarding, override config flags,
 * identity details and clearing caches. Opened from More's Testing card, which this module adds.
 *
 * Store builds leave this module out, so neither the route nor the screen exists there. Where the
 * module is in, the route is still only registered when `QaAccess` allows it, so a beta binary
 * promoted to the App Store offers no deep link into it.
 */
@Serializable
class QaMenuRoute : Route()

/** The QA menu's Prompts page: each prompt, why it would or would not show on Today, and resets. Gated like [QaMenuRoute]. */
@Serializable
class QaPromptsRoute : Route()
