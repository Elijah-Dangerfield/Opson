package com.opson.features.logger.impl

import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.opson.UserScopedClearer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Where in the logger a food was picked. Telemetry counts these; the draft's [LogMethod] follows from it. */
@Serializable
enum class PickSource {
    Search,
    Favorite,
    Usual,
    Recent,
    Saved,

    /** The Foods tab's + opened the logger with this food already picked. */
    Foods,
    ;

    /**
     * The method the item is logged with. A custom food is always `Custom`,
     * however it was found; the server enum has no "usual", so Usual counts
     * as Favorite.
     */
    fun methodFor(ref: FoodRef?): LogMethod = when {
        ref is FoodRef.Custom -> LogMethod.Custom
        this == Search -> LogMethod.Search
        this == Favorite || this == Usual -> LogMethod.Favorite
        this == Saved -> LogMethod.Saved
        else -> LogMethod.Recent
    }
}

/**
 * One food in the basket, ready to log: [draft] carries the amount, precision,
 * nutrients and method. [key] is [ref]'s key, or a saved meal's position for a
 * name-only item that has no food behind it. [food] is the catalog food when
 * the logger has it, so committing can mark it used without a lookup.
 */
data class BasketItem(
    val key: String,
    val ref: FoodRef?,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
)

/**
 * The logger's picks, shared by the logger sheet and the amount sheet so
 * neither needs a navigation result. It outlives a dismissed logger on
 * purpose: a stray swipe should not throw away three picks. Committing clears
 * it, and so does a change of user, since a basket is somebody's meal.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class BasketStore : UserScopedClearer {
    private val state = MutableStateFlow<List<BasketItem>>(emptyList())

    val basket: StateFlow<List<BasketItem>> = state.asStateFlow()

    /** Picks [item], or takes it out if its food is already picked. */
    fun toggle(item: BasketItem) {
        state.update { items ->
            if (items.any { it.key == item.key }) items.filterNot { it.key == item.key } else items + item
        }
    }

    /** Adds [item], replacing the entry for the same food in place. */
    fun upsert(item: BasketItem) {
        state.update { items ->
            val index = items.indexOfFirst { it.key == item.key }
            if (index < 0) items + item else items.toMutableList().apply { set(index, item) }
        }
    }

    fun clear() {
        state.value = emptyList()
    }

    override suspend fun clear(previousUserId: String) = clear()
}
