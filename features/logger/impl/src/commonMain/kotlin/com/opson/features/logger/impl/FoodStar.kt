package com.opson.features.logger.impl

import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import me.tatarka.inject.annotations.Inject

/**
 * A food's star, in its two places: settings say which foods are starred, and a starred catalog
 * food is also kept on the phone (`food_cache.favorite`) so the Foods tab shows it offline before
 * it is ever logged. The star and its snackbar's Undo both go through here, so the two never
 * disagree. [food] is the catalog food, or null for the person's own food, which settings alone hold.
 */
@Inject
class FoodStar(
    private val settings: SettingsRepository,
    private val catalog: FoodCatalog,
) {
    /** Stars it, or takes the star off; true when it is starred now. */
    suspend fun toggle(key: String, food: Food?): Boolean {
        val starred = settings.update { it.toggleFavorite(key) }.isFavorite(key)
        food?.let { catalog.setFavorite(it, starred) }
        return starred
    }

    /** Sets the star to [on], however many times it is asked. */
    suspend fun set(key: String, food: Food?, on: Boolean) {
        settings.update { it.setFavorite(key, on) }
        food?.let { catalog.setFavorite(it, on) }
    }
}
