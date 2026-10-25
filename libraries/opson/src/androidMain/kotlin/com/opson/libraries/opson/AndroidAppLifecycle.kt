package com.opson.libraries.opson

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.opson.libraries.opson.AppLifecycle
import com.opson.libraries.opson.AppLifecycleObserver
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@ContributesBinding(AppScope::class, boundType = AppLifecycle::class)
@SingleIn(AppScope::class)
@Inject
class AndroidAppLifecycle(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.Companion.get().lifecycle,
    /**
     * How registration reaches the main thread.
     *
     * `LifecycleRegistry` throws rather than synchronize, so `addObserver` off
     * the main thread is an `IllegalStateException`, and an uncaught one inside
     * a coroutine takes the process with it. Which thread gets here is not
     * something this class chooses: callers reach it through the object graph,
     * so it is whichever thread first touched a dependency that registers an
     * [AppLifecycleObserver]. That makes it a race rather than a bug you can
     * see — a downstream app had a background sign-in coroutine win it on a
     * cold boot of a shipped release build and crash at launch, while the same
     * binary started fine whenever the main thread got there first.
     *
     * Posting rather than running inline when already on main keeps add and
     * remove in the order they were called, which an inline fast path would
     * invert: a remove running immediately on main while an add sits queued
     * behind it leaves the registry holding an observer nobody asked for.
     * Nothing is lost by the one-frame delay, because a registry replays its
     * current state to an observer added later.
     *
     * Defaulted rather than injected so a JVM unit test can observe the hop
     * without a real `Looper`.
     */
    private val postToMainThread: (block: () -> Unit) -> Unit = { mainHandler.post(it) },
) : AppLifecycle, DefaultLifecycleObserver {

    private val observerLock = Any()
    private val observers = LinkedHashSet<AppLifecycleObserver>()

    override fun addObserver(observer: AppLifecycleObserver) {
        val shouldRegister = synchronized(observerLock) {
            val wasEmpty = observers.isEmpty()
            observers.add(observer)
            wasEmpty
        }

        if (shouldRegister) {
            postToMainThread { lifecycle.addObserver(this) }
        }
    }

    override fun removeObserver(observer: AppLifecycleObserver) {
        val shouldUnregister = synchronized(observerLock) {
            observers.remove(observer)
            observers.isEmpty()
        }

        if (shouldUnregister) {
            postToMainThread { lifecycle.removeObserver(this) }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        notify { it.onEnterForeground() }
    }

    override fun onStop(owner: LifecycleOwner) {
        notify { it.onEnterBackground() }
    }

    private inline fun notify(invocation: (AppLifecycleObserver) -> Unit) {
        val snapshot = synchronized(observerLock) { observers.toList() }
        snapshot.forEach(invocation)
    }
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }