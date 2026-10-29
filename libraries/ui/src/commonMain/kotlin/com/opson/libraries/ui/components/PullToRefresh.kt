package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Pull down at the top of a scrolling list to refresh: Material's gesture and spinner, in Opson's
 * colours. The pull is read from the list's overscroll, so a list that isn't at the top scrolls as
 * usual. Two halves so a screen can add it to the box it already has without re-nesting its list:
 * [Modifier.pullRefresh] on the box, [PullRefreshIndicator] inside it. [PullToRefresh] is both in one.
 */
@Stable
class PullRefresh @OptIn(ExperimentalMaterial3Api::class) internal constructor(
    internal val material: PullToRefreshState,
    val refreshing: Boolean,
    val enabled: Boolean,
    internal val onRefresh: () -> Unit,
)

/** With [enabled] false the gesture is off; the content stays the same composition, so nothing is lost by switching it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPullRefresh(refreshing: Boolean, onRefresh: () -> Unit, enabled: Boolean = true): PullRefresh {
    val material = rememberPullToRefreshState()
    val latest = rememberUpdatedState(onRefresh)
    return PullRefresh(material, refreshing, enabled) { latest.value() }
}

@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.pullRefresh(pull: PullRefresh): Modifier =
    pullToRefresh(isRefreshing = pull.refreshing, state = pull.material, enabled = pull.enabled, onRefresh = pull.onRefresh)

/** The spinner that follows the pull, for the top of the box [Modifier.pullRefresh] is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefreshIndicator(pull: PullRefresh, modifier: Modifier = Modifier) {
    PullToRefreshDefaults.Indicator(
        state = pull.material,
        isRefreshing = pull.refreshing,
        modifier = modifier,
        containerColor = AppTheme.colors.surface.color,
        color = AppTheme.colors.accent.color,
    )
}

/** [content] (a scrolling list) with pull to refresh and its spinner at the top. */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val pull = rememberPullRefresh(refreshing, onRefresh, enabled)
    Box(modifier.pullRefresh(pull)) {
        content()
        PullRefreshIndicator(pull, Modifier.align(Alignment.TopCenter))
    }
}

@Preview(widthDp = 390)
@Composable
private fun PullToRefreshPreview() {
    PreviewLightDark {
        PullToRefresh(refreshing = false, onRefresh = {}) {
            Text(
                text = "Pull down to refresh",
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(16.dp),
            )
        }
    }
}
