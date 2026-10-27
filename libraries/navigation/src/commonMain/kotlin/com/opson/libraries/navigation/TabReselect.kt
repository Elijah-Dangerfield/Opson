package com.opson.libraries.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.reflect.KClass

/**
 * Runs [onReselected] when the bottom bar's tab for [route] is tapped while its root is already
 * showing. A tab root uses it to scroll back to the top:
 * ```
 * screen<TodayRoute> {
 *     val listState = rememberLazyListState()
 *     router.OnTabReselected(TodayRoute::class) { listState.animateScrollToItem(0) }
 *     TodayScreen(listState = listState, ...)
 * }
 * ```
 * A second tap while the first scroll is still running restarts it rather than queueing.
 */
@Composable
fun Router.OnTabReselected(route: KClass<out TabRoute>, onReselected: suspend () -> Unit) {
    OnTabReselected(tabReselects, route, onReselected)
}

@Composable
internal fun OnTabReselected(reselects: Flow<TabRoute>, route: KClass<out TabRoute>, onReselected: suspend () -> Unit) {
    val current by rememberUpdatedState(onReselected)
    LaunchedEffect(reselects, route) {
        reselects.filter { it::class == route }.collectLatest { current() }
    }
}
