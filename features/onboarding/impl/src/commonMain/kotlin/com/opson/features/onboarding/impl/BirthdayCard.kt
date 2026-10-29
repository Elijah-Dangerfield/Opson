@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opson.features.today.TodayCardContribution
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.navigation.Router
import com.opson.libraries.opson.AppCache
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.system.color.ToneColors
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_birthday_body
import opson.libraries.resources.generated.resources.today_birthday_dismiss
import opson.libraries.resources.generated.resources.today_birthday_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A friendly line on Today on someone's birthday, from the date onboarding or Profile took. Only
 * on today, never for a day looked back on, and not for anyone who turned celebrations off. It
 * asks nothing of them: "log only if you feel like it". Put away with Thanks, it stays away that
 * day on this phone.
 */
@HiddenFromObjC
@Inject
@ContributesBinding(AppScope::class, multibinding = true)
class BirthdayCardContribution(private val cardViewModel: () -> BirthdayCardViewModel) : TodayCardContribution {
    override val key = "birthday"

    @Composable
    override fun card(day: LocalDate, router: Router): (@Composable () -> Unit)? {
        val viewModel: BirthdayCardViewModel = viewModel(key = "today-birthday-card") { cardViewModel() }
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        LaunchedEffect(day) { viewModel.takeAction(BirthdayCardAction.DayShown(day)) }
        if (!state.shown) return null
        return remember { { BirthdayCard(onThanks = { viewModel.takeAction(BirthdayCardAction.ThanksTapped) }) } }
    }
}

@Composable
internal fun BirthdayCard(onThanks: () -> Unit, modifier: Modifier = Modifier) {
    PromptCard(
        icon = AppIcons.Favorite,
        tone = ToneColors(content = AppTheme.colors.accent, container = AppTheme.colors.accentTint),
        title = stringResource(Res.string.today_birthday_title),
        body = stringResource(Res.string.today_birthday_body),
        modifier = modifier,
    ) {
        PromptAction(label = stringResource(Res.string.today_birthday_dismiss), onClick = onThanks, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/** Whether today is their birthday and the line is still up. */
data class BirthdayCardState(val shown: Boolean = false)

sealed interface BirthdayCardAction {
    data class DayShown(val day: LocalDate) : BirthdayCardAction
    data class Inputs(val settings: Settings, val thankedOn: String?) : BirthdayCardAction
    data object ThanksTapped : BirthdayCardAction
}

@Inject
class BirthdayCardViewModel(
    private val settings: SettingsRepository,
    private val appCache: AppCache,
    private val calendar: LocalCalendar,
) : SEAViewModel<BirthdayCardState, Nothing, BirthdayCardAction>(initialStateArg = BirthdayCardState()) {

    private var day: LocalDate? = null
    private var inputs: BirthdayCardAction.Inputs? = null

    init {
        viewModelScope.launch {
            combine(settings.observe(), appCache.updates) { settings, data -> BirthdayCardAction.Inputs(settings, data.birthdayThankedOn) }
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: BirthdayCardAction) {
        when (action) {
            is BirthdayCardAction.DayShown -> day = action.day
            is BirthdayCardAction.Inputs -> inputs = action
            BirthdayCardAction.ThanksTapped -> {
                val today = calendar.today().toString()
                appCache.update { it.copy(birthdayThankedOn = today) }
                inputs = inputs?.copy(thankedOn = today)
            }
        }
        val (settings, thankedOn) = inputs ?: return
        val today = calendar.today()
        action.updateState { BirthdayCardState(shown = shows(settings, today, day, thankedOn)) }
    }

    companion object {
        /** On their birthday, on today only, with celebrations on, until they put it away. */
        fun shows(settings: Settings, today: LocalDate, day: LocalDate?, thankedOn: String?): Boolean =
            day == today && settings.celebrationsEnabled && settings.isBirthday(today) && thankedOn != today.toString()
    }
}

@Preview(widthDp = 390)
@Composable
private fun TodayBirthdayCardPreview() = PreviewContent { BirthdayCard(onThanks = {}) }

@Preview(widthDp = 390)
@Composable
private fun TodayBirthdayCardDarkPreview() = PreviewContent(darkTheme = true) { BirthdayCard(onThanks = {}) }
