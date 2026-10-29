@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.qa.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.prompts.PromptBlock
import com.opson.libraries.prompts.PromptDebug
import com.opson.libraries.prompts.PromptInspection
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_checkin_title
import opson.libraries.resources.generated.resources.prompt_reminders_title
import opson.libraries.resources.generated.resources.qa_block_accepted
import opson.libraries.resources.generated.resources.qa_block_dismissed
import opson.libraries.resources.generated.resources.qa_block_not_onboarded
import opson.libraries.resources.generated.resources.qa_block_not_signed_in
import opson.libraries.resources.generated.resources.qa_block_not_this_moment
import opson.libraries.resources.generated.resources.qa_block_numbers_hard
import opson.libraries.resources.generated.resources.qa_block_prompted_recently
import opson.libraries.resources.generated.resources.qa_block_settling_in
import opson.libraries.resources.generated.resources.qa_block_shown_enough
import opson.libraries.resources.generated.resources.qa_block_shown_recently
import opson.libraries.resources.generated.resources.qa_block_shown_this_session
import opson.libraries.resources.generated.resources.qa_failed
import opson.libraries.resources.generated.resources.qa_no
import opson.libraries.resources.generated.resources.qa_prompts_body
import opson.libraries.resources.generated.resources.qa_prompts_counts
import opson.libraries.resources.generated.resources.qa_prompts_empty
import opson.libraries.resources.generated.resources.qa_prompts_held
import opson.libraries.resources.generated.resources.qa_prompts_meta
import opson.libraries.resources.generated.resources.qa_prompts_own_no
import opson.libraries.resources.generated.resources.qa_prompts_own_yes
import opson.libraries.resources.generated.resources.qa_prompts_reset
import opson.libraries.resources.generated.resources.qa_prompts_reset_all
import opson.libraries.resources.generated.resources.qa_prompts_reset_all_sub
import opson.libraries.resources.generated.resources.qa_prompts_reset_done
import opson.libraries.resources.generated.resources.qa_prompts_show_next
import opson.libraries.resources.generated.resources.qa_prompts_show_next_done
import opson.libraries.resources.generated.resources.qa_prompts_title
import opson.libraries.resources.generated.resources.qa_prompts_would_show
import opson.libraries.resources.generated.resources.qa_title
import opson.libraries.resources.generated.resources.qa_yes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/*
 * QA menu › Prompts: every prompt with the rule holding it back on Today opening (or none), its own
 * eligibility answer and its counts for this account, with Reset and Show next. Show next skips
 * every rule, so the owner can see each prompt without waiting out the gaps.
 */

@HiddenFromObjC
data class QaPromptsState(
    val prompts: List<PromptInspection> = emptyList(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
)

sealed interface QaPromptsAction {
    data object Load : QaPromptsAction
    data object ResetAllTapped : QaPromptsAction
    data class ResetTapped(val id: String) : QaPromptsAction
    data class ShowNextTapped(val id: String) : QaPromptsAction
    data object BackTapped : QaPromptsAction
}

sealed interface QaPromptsEvent {
    data class ShowToast(val toast: QaToast) : QaPromptsEvent
    data object Close : QaPromptsEvent
}

@HiddenFromObjC
@Inject
class QaPromptsViewModel(
    private val prompts: PromptDebug,
) : SEAViewModel<QaPromptsState, QaPromptsEvent, QaPromptsAction>(initialStateArg = QaPromptsState()) {

    init {
        takeAction(QaPromptsAction.Load)
    }

    override suspend fun handleAction(action: QaPromptsAction) {
        when (action) {
            QaPromptsAction.Load -> action.reload()
            QaPromptsAction.ResetAllTapped -> action.work {
                prompts.reset()
                sendEvent(QaPromptsEvent.ShowToast(QaToast(Res.string.qa_prompts_reset_done)))
            }
            is QaPromptsAction.ResetTapped -> action.work {
                prompts.reset(action.id)
                sendEvent(QaPromptsEvent.ShowToast(QaToast(Res.string.qa_prompts_reset_done)))
            }
            is QaPromptsAction.ShowNextTapped -> {
                prompts.showNext(action.id)
                sendEvent(QaPromptsEvent.ShowToast(QaToast(Res.string.qa_prompts_show_next_done)))
            }
            QaPromptsAction.BackTapped -> sendEvent(QaPromptsEvent.Close)
        }
    }

    private suspend fun QaPromptsAction.reload() {
        val list = Catching { prompts.inspect() }.logOnFailure { "QA prompts failed to load" }.getOrNull() ?: emptyList()
        updateState { it.copy(prompts = list, loaded = true) }
    }

    private suspend fun QaPromptsAction.work(block: suspend () -> Unit) {
        if (state.busy) return
        updateState { it.copy(busy = true) }
        Catching { block() }
            .logOnFailure { "QA prompts tool failed: $this" }
            .onFailure { sendEvent(QaPromptsEvent.ShowToast(QaToast(Res.string.qa_failed))) }
        reload()
        updateState { it.copy(busy = false) }
    }
}

@Composable
fun QaPromptsScreen(state: QaPromptsState, onAction: (QaPromptsAction) -> Unit, modifier: Modifier = Modifier) {
    QaPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.qa_prompts_title),
            backLabel = stringResource(Res.string.qa_title),
            onBack = { onAction(QaPromptsAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.qa_prompts_body),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        QaCard {
            QaRow(
                title = stringResource(Res.string.qa_prompts_reset_all),
                icon = AppIcons.DeleteOutline,
                subtitle = stringResource(Res.string.qa_prompts_reset_all_sub),
                onClick = if (state.busy) null else ({ onAction(QaPromptsAction.ResetAllTapped) }),
            )
        }
        if (state.loaded && state.prompts.isEmpty()) {
            QaSectionLabel(stringResource(Res.string.qa_prompts_empty))
        }
        state.prompts.forEach { prompt -> PromptCard(prompt, state.busy, onAction) }
    }
}

@Composable
private fun PromptCard(prompt: PromptInspection, busy: Boolean, onAction: (QaPromptsAction) -> Unit) {
    QaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = stringResource(prompt.title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(
                text = stringResource(Res.string.qa_prompts_meta, prompt.id, prompt.presentation.wire, prompt.priority),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
            val blocked = prompt.blockedBy
            Text(
                text = if (blocked == null) {
                    stringResource(Res.string.qa_prompts_would_show)
                } else {
                    stringResource(Res.string.qa_prompts_held, stringResource(blocked.label()))
                },
                typography = AppTheme.type.secondarySmallStrong,
                color = if (blocked == null && prompt.eligible) AppTheme.colors.accent else AppTheme.colors.ink,
            )
            Text(
                text = stringResource(if (prompt.eligible) Res.string.qa_prompts_own_yes else Res.string.qa_prompts_own_no),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
            Text(
                text = stringResource(
                    Res.string.qa_prompts_counts,
                    prompt.shows,
                    prompt.dismissals,
                    stringResource(if (prompt.accepted) Res.string.qa_yes else Res.string.qa_no),
                ),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatButton(
                    text = stringResource(Res.string.qa_prompts_show_next),
                    onClick = { onAction(QaPromptsAction.ShowNextTapped(prompt.id)) },
                    tone = FlatButtonTone.Accent,
                    enabled = !busy,
                )
                FlatButton(
                    text = stringResource(Res.string.qa_prompts_reset),
                    onClick = { onAction(QaPromptsAction.ResetTapped(prompt.id)) },
                    tone = FlatButtonTone.Soft,
                    enabled = !busy,
                )
            }
        }
    }
}

private fun PromptBlock.label(): StringResource = when (this) {
    PromptBlock.NotSignedIn -> Res.string.qa_block_not_signed_in
    PromptBlock.NotOnboarded -> Res.string.qa_block_not_onboarded
    PromptBlock.Accepted -> Res.string.qa_block_accepted
    PromptBlock.Dismissed -> Res.string.qa_block_dismissed
    PromptBlock.ShownEnough -> Res.string.qa_block_shown_enough
    PromptBlock.ShownRecently -> Res.string.qa_block_shown_recently
    PromptBlock.NotThisMoment -> Res.string.qa_block_not_this_moment
    PromptBlock.NumbersHard -> Res.string.qa_block_numbers_hard
    PromptBlock.ShownThisSession -> Res.string.qa_block_shown_this_session
    PromptBlock.SettlingIn -> Res.string.qa_block_settling_in
    PromptBlock.PromptedRecently -> Res.string.qa_block_prompted_recently
}

@Preview
@Composable
private fun QaPromptsPreview() {
    PreviewContent {
        QaPromptsScreen(
            state = QaPromptsState(
                loaded = true,
                prompts = listOf(
                    PromptInspection(
                        id = "meal_reminders",
                        title = Res.string.prompt_reminders_title,
                        presentation = PromptPresentation.Dialog,
                        priority = 200,
                        blockedBy = null,
                        eligible = true,
                        shows = 0,
                        dismissals = 0,
                        accepted = false,
                    ),
                    PromptInspection(
                        id = "try_check_in",
                        title = Res.string.prompt_checkin_title,
                        presentation = PromptPresentation.Card,
                        priority = 100,
                        blockedBy = PromptBlock.PromptedRecently,
                        eligible = false,
                        shows = 1,
                        dismissals = 0,
                        accepted = false,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
