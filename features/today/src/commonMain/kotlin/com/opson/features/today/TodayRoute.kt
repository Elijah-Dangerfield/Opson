package com.opson.features.today

import com.opson.libraries.navigation.TabRoute
import kotlinx.serialization.Serializable

/** The Today tab: the day's meals, water and the sticky summary block. Where the app lands after onboarding. Reach it with `router.switchTab`. */
@Serializable
class TodayRoute : TabRoute()
