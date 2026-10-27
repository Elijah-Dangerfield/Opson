package com.opson.libraries.flowroutines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.throwIfDebug
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private class SentEvent<E : Any>(val event: E, val sentAt: TimeMark)


/**
 * A view model that revolves around State (S), Events (E) and Actions (A)
 * Encourages a unidirectional flow of data from actions to state.
 *
 * S - State - The state of the view. Should be an immutable class that represents the
 * current state of the view. Should represent view state, NOT view element state
 * See https://developer.android.com/topic/architecture/ui-layer/stateholders#elements-ui
 *
 * E - Event - Events are used to trigger one time events that should not be stored in the state.
 * Examples: Navigation, Showing a toast, etc...
 * Storing one time events in the state requires acknowledgment of the state from the view and can
 * lead to complications and bugs if not careful. Turns out to be easier to just roll with events in
 * a channel.
 *

 * A - Action - Actions are the only way to update state. They represent work to be done either user
 * triggered or from the view model itself.
 * Tips:
 * - If you need data to load on init, have the view model take an action in the init block.
 *
 * This viewmodel backs the state into the saved state handle if the state is savable.
 * See [SavedStateHandle.ACCEPTABLE_CLASSES]
 */
abstract class SEAViewModel<S : Any, E : Any, A : Any>(
    private val initialStateArg: S? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {

    private val actions = Channel<A>(Channel.UNLIMITED)
    private val events = Channel<SentEvent<E>>(Channel.UNLIMITED)

    // Lazy so that we do not let initialState() from the child get called before the child is initialized
    private val mutableStateFlow: MutableStateFlow<S> by lazy {
        MutableStateFlow(
            Catching {
                savedStateHandle.get<S>(STATE_KEY)
            }.getOrNull() ?: initialState()
        )
    }

    /**
     * The state of the view model. It is the backing flow itself, not a copy
     * collected in [viewModelScope], so [state] read straight after
     * [updateState] already holds the update. A guard such as "ignore a second
     * Export while one is working" depends on that when two actions arrive in
     * the same frame.
     */
    val stateFlow: StateFlow<S> by lazy { mutableStateFlow.asStateFlow() }

    /**
     * The flow exposing events from the view mode.
     *
     * Events that waited longer than [EVENT_EXPIRY] for a collector are dropped
     * here rather than delivered late. See [sendEvent] for why.
     */
    val eventFlow: Flow<E> = events.receiveAsFlow().mapNotNull { sent ->
        val age = sent.sentAt.elapsedNow()
        if (age > EVENT_EXPIRY) {
            KLog.w(
                "Dropping ${sent.event::class.simpleName} after ${age.inWholeMilliseconds}ms " +
                    "with no collector — it is a stale side effect now, not a fresh one.",
            )
            null
        } else {
            sent.event
        }
    }

    /**
     * The current state value
     */
    val state: S get() = stateFlow.value

    init {
        viewModelScope.launch {
            for (action in actions) {
                handleAction(action = action)
            }
        }
    }

    /**
     * Submits an action to be handled by the view model.
     */
    fun takeAction(action: A) {
        actions.trySend(action)
    }

    /**
     * Updates the state of the view model.
     * This is the only way to update the state.
     *
     * Callers must have an action to update the state, this helps maintain UDF.
     */
    suspend fun A.updateState(f: suspend (S) -> S) {
        Catching { mutableStateFlow.update { f(it) } }
            .logOnFailure("Could not update state for: ${state::class.simpleName ?: "missing name"}")
            .throwIfDebug()
    }

    /**
     * Sets the initial state to be emitted by the `states` flow.
     *
     * We use a function to force for lazy initialization of the state, allowing the view model to
     * define the initial state in a more flexible way. Including using SavedStateHandle Args.
     */
    protected open fun initialState(): S {
        return initialStateArg
            ?: throw IllegalStateException("Initial state must be passed in or overridden in the initialState function.")
    }

    /**
     * Events in this code base are synonymous with side effects. They are used to trigger
     * one time events that should not be stored in the state.
     *
     * Examples:
     * - Navigation
     * - Showing a toast
     * - etc...
     *
     * Events carry the time they were sent and expire after [EVENT_EXPIRY].
     * The channel is unbounded and the collector is lifecycle-gated, so a screen
     * below STARTED banks side effects instead of losing them, then replays the
     * whole pile the instant collection resumes. Downstream that looked like an
     * app that had stopped navigating while still logging every send, and ended
     * minutes later with a dozen navigations to the same route arriving
     * milliseconds apart and NavController refusing to pop a destination that
     * was no longer on top of the back stack.
     *
     * A shake is only meaningful in the moment it happens, and so is a
     * navigation. Dropping the stale ones is the whole fix.
     */
    fun sendEvent(event: E) {
        KLog.i("Sending event ${event::class.simpleName}")
        events.trySend(SentEvent(event, timeSource.markNow()))
    }

    /**
     * Function that ensures all children handle their actions
     *
     * This helps to ensure that the flow is unidirectional.
     *
     * View -> Action -> State -> View
     *
     * @param action the action to be handled
     */
    protected abstract suspend fun handleAction(action: A)

    /**
     * Best-effort save for process-death restoration. **Failing here is the
     * normal case, not an error.**
     *
     * This is opportunistic on purpose, and pairs with the equally opportunistic
     * read in [mutableStateFlow]. If [S] happens to be something
     * `SavedStateHandle` can put in a Bundle, the screen gets process-death
     * restoration for free and nobody had to ask for it. If it doesn't — and a
     * plain Kotlin data class doesn't, which is most states — the write throws,
     * the read falls back to `initialState()`, and the screen rebuilds exactly
     * as it would have anyway.
     *
     * So the failure is logged in debug only. At error level in release it is
     * one line per screen exit on nearly every screen in the app, which teaches
     * people to ignore the log rather than telling them anything. In a minified
     * build it is worse than useless: R8 renames the class, so it reads
     * `Can't put value with type class x6.j` and looks like an obfuscation bug.
     *
     * Want restoration on a particular screen? Make that screen's `State`
     * Bundle-able. Nothing here needs to change.
     */
    override fun onCleared() {
        val saved = Catching { savedStateHandle[STATE_KEY] = state }
        if (BuildInfo.isDebug) {
            saved.logOnFailure(
                "${state::class.simpleName} isn't Bundle-able, so this screen won't restore " +
                    "after process death. Expected for a plain data class — make the State " +
                    "Bundle-able if you want restoration.",
            )
        }
    }

    companion object {
        private const val STATE_KEY = "state"

        /**
         * How long an event may wait for a collector before it is dropped.
         *
         * This threshold only bites when *nothing* is collecting. With a
         * collector attached, `receiveAsFlow()` hands the event over
         * immediately and it is microseconds old when it is checked, so no
         * value here can drop an event merely because the view model was slow
         * to send it or the UI was slow to handle the previous one.
         *
         * The case that must not break is the normal one: a view model sends in
         * `init` and the screen composes its collector a frame or two later.
         * That gap is milliseconds, and a zero threshold would strand exactly
         * those events. So keep this comfortably longer than composition —
         * seconds, not milliseconds. Anything still queued after that is a
         * backgrounded screen banking side effects, which is the case worth
         * discarding.
         */
        private val EVENT_EXPIRY = 5.seconds
    }
}

