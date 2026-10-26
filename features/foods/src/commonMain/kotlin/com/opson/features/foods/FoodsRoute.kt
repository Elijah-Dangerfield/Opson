package com.opson.features.foods

import com.opson.libraries.navigation.TabRoute
import kotlinx.serialization.Serializable

/** The Foods tab: saved meals, the user's own foods and recent ones. Reach it with `router.switchTab`. */
@Serializable
class FoodsRoute : TabRoute()
