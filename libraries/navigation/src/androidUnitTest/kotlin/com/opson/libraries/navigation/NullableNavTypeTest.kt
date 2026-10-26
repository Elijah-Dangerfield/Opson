package com.opson.libraries.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
internal enum class NavTestMeal {
    @SerialName("lunch")
    Lunch,

    @SerialName("dinner")
    Dinner,
}

@Serializable
internal class NavTestStartRoute : Route()

@Serializable
internal data class NavTestOptionalMealRoute(val meal: NavTestMeal? = null, val note: String? = null) : Route()

/**
 * A nullable enum route argument (the logger's `slot: MealSlot? = null`) survives a real
 * `NavController` both ways: null and a value. Only a real graph build proves the typeMap key
 * matches the nullable type, which is the failure that crashes iOS at startup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NullableNavTypeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aNullArgumentArrivesAsNull() {
        val navController = host()

        compose.runOnIdle { navController.navigate(NavTestOptionalMealRoute()) }
        compose.waitForIdle()

        val route = assertNotNull(navController.currentBackStackEntry).toRoute<NavTestOptionalMealRoute>()
        assertEquals(NavTestOptionalMealRoute(meal = null), route)
    }

    @Test
    fun aValueArrivesAsTheSameValue() {
        val navController = host()

        compose.runOnIdle { navController.navigate(NavTestOptionalMealRoute(meal = NavTestMeal.Dinner, note = "x")) }
        compose.waitForIdle()

        val route = assertNotNull(navController.currentBackStackEntry).toRoute<NavTestOptionalMealRoute>()
        assertEquals(NavTestOptionalMealRoute(meal = NavTestMeal.Dinner, note = "x"), route)
    }

    @Test
    fun theValueRoundTripsAsText() {
        val type = nullableSerializableType<NavTestMeal>()

        assertEquals(null, type.parseValue(type.serializeAsValue(null)))
        assertEquals(NavTestMeal.Lunch, type.parseValue(type.serializeAsValue(NavTestMeal.Lunch)))
    }

    private fun host(): NavHostController {
        lateinit var navController: NavHostController
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = NavTestStartRoute(), typeMap = baseRouteTypeMap) {
                screen<NavTestStartRoute> { }
                screen<NavTestOptionalMealRoute>(
                    typeMap = mapOf(typeOf<NavTestMeal?>() to nullableSerializableType<NavTestMeal>()),
                ) { }
            }
        }
        compose.waitForIdle()
        return navController
    }
}
