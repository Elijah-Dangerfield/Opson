package com.opson.libraries.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
enum class AnimationType {
    None,
    FadeIn,
    FadeOut,

    SlideInFromRight,
    SlideInFromLeft,

    SlideOutToRight,
    SlideOutToLeft,

    SlideUp,
    SlideDown,
    ExpandIn,
    ShrinkOut;

    fun opposite(): AnimationType {
        return when (this) {
            None -> None
            FadeIn -> FadeOut
            FadeOut -> FadeIn
            SlideOutToRight -> SlideInFromLeft
            SlideOutToLeft -> SlideInFromRight
            SlideInFromRight -> SlideOutToLeft
            SlideInFromLeft -> SlideOutToRight
            SlideUp -> SlideDown
            SlideDown -> SlideUp
            ExpandIn -> ShrinkOut
            ShrinkOut -> ExpandIn
        }
    }
}

fun AnimationType.toEnterTransition(): EnterTransition = when (this) {
    AnimationType.None -> fadeIn(tween(0))
    AnimationType.FadeIn -> fadeIn()
    AnimationType.SlideInFromLeft -> slideInHorizontally { -it }
    AnimationType.SlideInFromRight -> slideInHorizontally { it }
    AnimationType.SlideOutToRight -> slideInHorizontally { it }
    AnimationType.SlideOutToLeft -> slideInHorizontally { -it }
    AnimationType.SlideUp -> slideInVertically { it }
    AnimationType.SlideDown -> slideInVertically { -it }
    AnimationType.ExpandIn -> expandIn { it }
    AnimationType.ShrinkOut -> fadeIn(tween(0))
    AnimationType.FadeOut -> fadeIn(tween(0))
}

fun AnimationType.toExitTransition(): ExitTransition = when (this) {
    AnimationType.None -> fadeOut(tween(0))
    AnimationType.FadeOut -> fadeOut()
    AnimationType.SlideInFromLeft -> slideOutHorizontally { -it }
    AnimationType.SlideInFromRight -> slideOutHorizontally { it }
    AnimationType.SlideOutToRight -> slideOutHorizontally { it }
    AnimationType.SlideOutToLeft -> slideOutHorizontally { -it }
    AnimationType.SlideUp -> slideOutVertically { it }
    AnimationType.SlideDown -> slideOutVertically { -it }
    AnimationType.ShrinkOut -> shrinkOut { it }
    AnimationType.ExpandIn -> fadeOut(tween(0))
    AnimationType.FadeIn -> fadeOut(tween(0))
}

@Serializable
open class Route(
    val enter: AnimationType = AnimationType.SlideInFromRight,
    val exit: AnimationType = AnimationType.SlideOutToLeft,
    val popExit: AnimationType = AnimationType.SlideOutToRight,
)  {
    fun getEnterTransition(): EnterTransition = enter.toEnterTransition()
    fun getExitTransition(): ExitTransition = exit.toExitTransition()

    fun getPopExitTransition(): ExitTransition = popExit.toExitTransition()

    companion object
}

/**
 * A bottom-bar tab root. Still a [Route], so the nav graph registers it with `screen<>` like any
 * other, but its own type so [Router.switchTab] can be the only way to reach one: plain
 * [Router.navigate] of a `TabRoute` does not compile, because a tab pushed that way lands on top
 * of the current tab's stack and every later tab switch saves and restores the wrong stack.
 *
 * Tab roots take no arguments. `restoreState` matches a saved stack by destination, so a tab root
 * with arguments would restore whichever instance was saved last rather than the one asked for.
 *
 * `@Serializable` so the cross-fade below travels as nav arguments like every other [Route]'s
 * animations do. Without it the base-class properties are not serialized, and the host, which
 * reads animations back off the entry as a plain [Route], would get the slide defaults instead.
 */
@Serializable
abstract class TabRoute : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)

inline fun <reified T> NavBackStackEntry.toRouteOrNull(): T? = Catching<T> { toRoute(T::class) }
    .logOnFailure()
    .getOrNull()


inline fun <reified T : Any> serializableType(
    isNullableAllowed: Boolean = false,
    json: Json = Json,
) = object : NavType<T>(isNullableAllowed = isNullableAllowed) {

    override fun put(bundle: SavedState, key: String, value: T) {
        bundle.write { putString(key, json.encodeToString(value)) }
    }

    override fun get(bundle: SavedState, key: String): T? {
        return json.decodeFromString<T?>(bundle.read { getString(key) })
    }

    override fun parseValue(value: String): T = json.decodeFromString(value)

    override fun serializeAsValue(value: T): String = json.encodeToString(value)
}

/**
 * [serializableType] for a nullable argument (`slot: MealSlot? = null`). Register it under the
 * nullable type, `typeOf<MealSlot?>()`: a non-null registration does not match a nullable arg
 * and the graph fails to build on iOS.
 *
 * A null travels as the literal `"null"`, the same as navigation's own nullable `StringType`.
 */
inline fun <reified T : Any> nullableSerializableType(json: Json = Json) = object : NavType<T?>(isNullableAllowed = true) {

    override fun put(bundle: SavedState, key: String, value: T?) {
        bundle.write { if (value == null) putNull(key) else putString(key, json.encodeToString(value)) }
    }

    override fun get(bundle: SavedState, key: String): T? = bundle.read {
        if (!contains(key) || isNull(key)) null else json.decodeFromString<T>(getString(key))
    }

    override fun parseValue(value: String): T? = if (value == "null") null else json.decodeFromString<T>(value)

    override fun serializeAsValue(value: T?): String = value?.let { json.encodeToString(it) } ?: "null"
}