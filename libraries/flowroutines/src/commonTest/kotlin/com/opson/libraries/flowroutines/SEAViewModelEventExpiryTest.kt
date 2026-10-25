package com.opson.libraries.flowroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Covers event expiry in [SEAViewModel]: events wait in an unbounded channel
 * while the screen is below STARTED, and the ones that waited too long are
 * dropped instead of replayed in a burst.
 *
 * What is asserted here: an event with a collector attached is delivered, an
 * event that sat unwatched past the threshold is not, and the init-before-
 * composition gap (a view model sending in `init`, the screen collecting a
 * frame later) still delivers.
 *
 * NOT covered here: the lifecycle gating itself, which belongs to
 * `repeatOnLifecycle` in `Compose.kt` and to androidx, not to this class. The
 * expiry is tested through an injected [TimeSource] because `runTest`'s virtual
 * clock does not advance a monotonic one — without injection the only way to
 * age an event would be a real sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SEAViewModelEventExpiryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = ManualTimeSource()

    @BeforeTest
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun eventWithCollectorAttached_isDelivered() = runTest(dispatcher) {
        val viewModel = TestViewModel(clock)
        val received = mutableListOf<TestEvent>()

        val collection = launch { viewModel.eventFlow.toList(received) }
        runCurrent()

        viewModel.sendEvent(TestEvent.Navigate)
        runCurrent()

        assertEquals(listOf<TestEvent>(TestEvent.Navigate), received)
        collection.cancel()
    }

    @Test
    fun eventThatWaitedPastTheThreshold_isDropped() = runTest(dispatcher) {
        val viewModel = TestViewModel(clock)

        // Nothing is collecting: the screen is backgrounded, the channel banks.
        viewModel.sendEvent(TestEvent.Navigate)
        clock.advance(8.minutes)

        val received = mutableListOf<TestEvent>()
        val collection = launch { viewModel.eventFlow.toList(received) }
        runCurrent()

        assertEquals(emptyList<TestEvent>(), received, "a side effect from eight minutes ago is not a side effect")
        collection.cancel()
    }

    @Test
    fun eventSentInInitBeforeTheScreenComposes_survives() = runTest(dispatcher) {
        val viewModel = TestViewModel(clock, sendOnInit = TestEvent.Navigate)

        // The screen takes a couple of frames to compose and attach its collector.
        clock.advance(32.milliseconds)

        val received = mutableListOf<TestEvent>()
        val collection = launch { viewModel.eventFlow.toList(received) }
        runCurrent()

        assertEquals(listOf<TestEvent>(TestEvent.Navigate), received, "the init-to-composition gap must not expire events")
        collection.cancel()
    }
}

private sealed interface TestEvent {
    data object Navigate : TestEvent
}

private class TestViewModel(
    timeSource: TimeSource,
    sendOnInit: TestEvent? = null,
) : SEAViewModel<Unit, TestEvent, Unit>(initialStateArg = Unit, timeSource = timeSource) {

    init {
        if (sendOnInit != null) sendEvent(sendOnInit)
    }

    override suspend fun handleAction(action: Unit) = Unit
}

/** A [TimeSource] the test moves by hand, since a monotonic one ignores virtual time. */
private class ManualTimeSource : TimeSource {
    private var now: Duration = Duration.ZERO

    fun advance(by: Duration) {
        now += by
    }

    override fun markNow(): TimeMark = Mark(this, now)

    private class Mark(private val source: ManualTimeSource, private val at: Duration) : TimeMark {
        override fun elapsedNow(): Duration = source.now - at
    }
}
