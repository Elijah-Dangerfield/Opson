package com.opson.features.today.impl.celebration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_streak_cd
import opson.libraries.resources.generated.resources.celebrate_streak_gentle_cd
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The streak in Today's header: the run's count, or in the gentle style a sparkle alone. Null draws nothing. */
sealed interface StreakBadge {
    data class Counts(val days: Int) : StreakBadge

    data object Gentle : StreakBadge

    companion object {
        /** Nothing until there is a run: a zero in the header would be a score of nothing. */
        fun of(celebrating: Celebrating?): StreakBadge? {
            val run = celebrating?.progress?.current ?: return null
            if (run == 0) return null
            return if (celebrating.style == CelebrationStyle.Counts) Counts(run) else Gentle
        }
    }
}

data class StreakIndicatorState(val badge: StreakBadge? = null)

sealed interface StreakIndicatorAction {
    data class Arrived(val celebrating: Celebrating?) : StreakIndicatorAction
    data object Tapped : StreakIndicatorAction
}

sealed interface StreakIndicatorEvent {
    data object OpenProgress : StreakIndicatorEvent
}

/** Its own view model beside Today's, so the header's streak is all Today needs to know of celebrations. */
@Inject
class StreakIndicatorViewModel(source: CelebrationsSource) :
    SEAViewModel<StreakIndicatorState, StreakIndicatorEvent, StreakIndicatorAction>(initialStateArg = StreakIndicatorState()) {

    init {
        viewModelScope.launch { source.observe().collect { takeAction(StreakIndicatorAction.Arrived(it)) } }
    }

    override suspend fun handleAction(action: StreakIndicatorAction) {
        when (action) {
            is StreakIndicatorAction.Arrived -> action.updateState { StreakIndicatorState(StreakBadge.of(action.celebrating)) }
            StreakIndicatorAction.Tapped -> sendEvent(StreakIndicatorEvent.OpenProgress)
        }
    }
}

/** A small pill beside the header's buttons: the sparkle and the count. Tapping opens Your progress. */
@Composable
fun StreakIndicator(badge: StreakBadge, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = when (badge) {
        is StreakBadge.Counts -> pluralStringResource(Res.plurals.celebrate_streak_cd, badge.days, badge.days)
        StreakBadge.Gentle -> stringResource(Res.string.celebrate_streak_gentle_cd)
    }
    Row(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .bounceClick(scaleDown = 0.9f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clip(CircleShape).background(AppTheme.colors.accentTint).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon = AppIcons.AutoAwesome, size = 18.dp, color = AppTheme.colors.accent)
            if (badge is StreakBadge.Counts) {
                Text(text = badge.days.toString(), typography = AppTheme.type.numericSmall, color = AppTheme.colors.accent)
            }
        }
    }
}

@Preview
@Composable
private fun StreakIndicatorPreview() = PreviewLightDark {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakIndicator(StreakBadge.Counts(4), onClick = {})
        StreakIndicator(StreakBadge.Counts(128), onClick = {})
        StreakIndicator(StreakBadge.Gentle, onClick = {})
    }
}
