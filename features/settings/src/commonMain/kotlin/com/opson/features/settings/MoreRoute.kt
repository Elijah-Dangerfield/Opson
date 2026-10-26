package com.opson.features.settings

import com.opson.libraries.navigation.TabRoute
import kotlinx.serialization.Serializable

/** The More tab, the root of settings: modes and numbers, targets, water, look and feel, and support. Reach it with `router.switchTab`. */
@Serializable
class MoreRoute : TabRoute()
