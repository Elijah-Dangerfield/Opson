@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.today.TodayCardContribution
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.bottomSheet
import com.opson.libraries.navigation.dialog
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptHost
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.dialog.DialogState
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.system.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration.Companion.seconds

/** A prompt drawn as a dialog over Today. Only the host opens it. */
@Serializable
data class PromptDialogRoute(val id: String) : Route()

/** A prompt drawn as a sheet over Today. Only the host opens it. */
@Serializable
data class PromptSheetRoute(val id: String) : Route()

/**
 * The prompt's dialog and sheet destinations. Each is a real back stack entry, so while one is
 * open Today is not the top of the stack and the host asks for nothing more.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class PromptFeatureEntryPoint(
    private val viewModelFactory: (String) -> PromptViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        dialog<PromptDialogRoute> { backStackEntry, dialogState ->
            val viewModel: PromptViewModel = viewModel { viewModelFactory(backStackEntry.toRoute<PromptDialogRoute>().id) }
            PromptDialog(viewModel, router, dialogState)
        }
        bottomSheet<PromptSheetRoute> { backStackEntry, sheetState ->
            val viewModel: PromptViewModel = viewModel { viewModelFactory(backStackEntry.toRoute<PromptSheetRoute>().id) }
            PromptSheet(viewModel, router, sheetState)
        }
    }

    @Composable
    private fun PromptDialog(viewModel: PromptViewModel, router: Router, dialogState: DialogState) {
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        viewModel.ObserveEvents { event -> onEvent(event, router, close = { dialogState.dismiss() }) }
        val content = state.content
        if (content == null) {
            LaunchedEffect(Unit) { router.goBack() }
            return
        }
        Dialog(
            state = dialogState,
            onDismissRequest = {
                viewModel.takeAction(PromptAction.Gone)
                router.goBack()
            },
        ) {
            PromptDialogPanel(
                content = content,
                onAccept = { viewModel.takeAction(PromptAction.AcceptTapped) },
                onDismiss = { viewModel.takeAction(PromptAction.NotNowTapped) },
            )
        }
    }

    @Composable
    private fun PromptSheet(viewModel: PromptViewModel, router: Router, sheetState: BottomSheetState) {
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        viewModel.ObserveEvents { event -> onEvent(event, router, close = { sheetState.dismiss() }) }
        val content = state.content
        if (content == null) {
            LaunchedEffect(Unit) { router.goBack() }
            return
        }
        // A few lines and two buttons: nothing to scroll, so the sheet takes its content's height.
        BottomSheet(
            onDismissRequest = {
                viewModel.takeAction(PromptAction.Gone)
                router.goBack()
            },
            state = sheetState,
            showDragHandle = true,
            backgroundColor = AppTheme.colors.bg,
        ) {
            PromptSheetContent(
                content = content,
                onAccept = { viewModel.takeAction(PromptAction.AcceptTapped) },
                onDismiss = { viewModel.takeAction(PromptAction.NotNowTapped) },
            )
        }
    }

    private suspend fun onEvent(event: PromptUiEvent, router: Router, close: suspend () -> Unit) {
        when (event) {
            is PromptUiEvent.Open -> router.batch {
                goBack()
                navigate(event.route)
            }
            is PromptUiEvent.OpenLink -> {
                router.goBack()
                router.openWebLink(event.url)
            }
            PromptUiEvent.Close -> close()
        }
    }
}

/**
 * The host the app draws beside its nav host. While Today is the top of the stack and the app is
 * in front, it waits [SETTLE] (the launch splash, a sheet's exit, a tab switch passing through),
 * then asks the processor, and asks again on every wake-up (a moment, the QA menu). Leaving Today
 * or going to the background cancels the wait, so nothing opens over a screen it was not meant for.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PromptHostImpl(private val processor: PromptProcessor) : PromptHost {

    @Composable
    override fun Host(onToday: Boolean, router: Router) {
        if (LocalInspectionMode.current) return
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(onToday, lifecycle, router) {
            if (!onToday) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(SETTLE)
                processor.wakeUps.onStart { emit(Unit) }.collect {
                    val showing = processor.next() ?: return@collect
                    when (showing.presentation) {
                        PromptPresentation.Dialog -> router.navigate(PromptDialogRoute(showing.id))
                        PromptPresentation.Sheet -> router.navigate(PromptSheetRoute(showing.id))
                        // Today's list draws it (PromptTodayCardContribution).
                        PromptPresentation.Card -> Unit
                        PromptPresentation.Screen -> processor.screen(showing.id)?.let { router.navigate(it) }
                        // The operating system draws it (the store's rating sheet).
                        PromptPresentation.System -> processor.launch(showing.id)
                    }
                }
            }
        }
    }

    private companion object {
        val SETTLE = 2.seconds
    }
}

/** A prompt drawn as a card goes in Today's list, on today only, while it is up. */
@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class PromptTodayCardContribution(
    private val processor: PromptProcessor,
    private val calendar: LocalCalendar,
) : TodayCardContribution {
    override val key = "prompt"

    @Composable
    override fun card(day: LocalDate, router: Router): (@Composable () -> Unit)? {
        val scope = rememberCoroutineScope()
        val showing by processor.card.collectAsStateWithLifecycle()
        val id = showing?.id ?: return null
        if (day != calendar.today()) return null
        val content = processor.candidate(id)?.content ?: return null
        return {
            PromptTodayCard(
                content = content,
                onAccept = {
                    scope.launch {
                        when (val accept = processor.accepted(id)) {
                            is PromptAccept.Open -> router.navigate(accept.route)
                            is PromptAccept.OpenLink -> router.openWebLink(accept.url)
                            PromptAccept.Done, null -> Unit
                        }
                    }
                },
                onDismiss = { scope.launch { processor.dismissed(id) } },
            )
        }
    }
}
