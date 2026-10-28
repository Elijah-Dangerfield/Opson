package com.opson.libraries.widgets.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.opson.AppCache
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Keeps the platform's widgets showing today's snapshot: publishes each new one from
 * [WidgetSnapshotSource].
 *
 * Not gated on `release.widgets`. The iOS extension ships in every build, so it is in the widget
 * gallery whatever the flag says, and a widget the app never wrote for says "Open Opson" forever.
 * The flag only decides the More row. On Android, with no receiver in the manifest yet, a publish
 * stores the JSON and the redraw finds no widget to update.
 *
 * [AutoInit] because a widget should be current whenever the app has run, not only once someone
 * opens a screen.
 *
 * It publishes only while the app is someone's ([isSomeones]) and their settings are known.
 * While the settings are nobody's yet ([SettingsRepository.isKnown] false: a user change, until
 * the next account's first pull) it clears what it published, or the home screen would keep the
 * last account's day in the last account's mode. Signed out, the settings read as known, but they
 * are the defaults (All numbers), and a No numbers widget would turn into a calorie card; a
 * sign-out or an expired session clears the widget instead, even one an earlier run wrote.
 * [AuthRepository] is reached through a provider, only once the flow starts, as in `SyncTriggers`:
 * auth depends on much of the boot-time graph.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class WidgetRefresher(
    private val settingsRepository: SettingsRepository,
    private val source: WidgetSnapshotSource,
    private val publisher: WidgetPublisher,
    appCache: AppCache,
    authRepositoryProvider: () -> AuthRepository,
    appScope: AppCoroutineScope,
) : AutoInit {

    private enum class Show { Snapshot, Nothing, WhatWasPublished }

    private var published = false

    private val auth = flow { emitAll(authRepositoryProvider().observe()) }

    private val onboarded = appCache.updates.map { it.hasUserOnboarded }.distinctUntilChanged()

    init {
        appScope.launch {
            val shows = combine(auth, settingsRepository.isKnown, onboarded, ::show).distinctUntilChanged()
            shows.collectLatest { show ->
                when (show) {
                    Show.Snapshot -> source.observe().collect(::publish)
                    Show.Nothing -> clear()
                    Show.WhatWasPublished -> if (published) clear()
                }
            }
        }
    }

    private fun show(auth: AuthState, settingsKnown: Boolean, onboarded: Boolean): Show = when {
        auth.isSomeones(onboarded) -> if (settingsKnown) Show.Snapshot else Show.WhatWasPublished
        auth is AuthState.Unauthenticated && auth.reason != AuthState.Unauthenticated.Reason.None -> Show.Nothing
        else -> Show.WhatWasPublished
    }

    private suspend fun publish(snapshot: WidgetSnapshot) {
        Catching { publisher.publish(snapshot) }
            .onSuccess { published = true }
            .logOnFailure { "Publishing the widget snapshot failed" }
    }

    private suspend fun clear() {
        Catching { publisher.clear() }
            .onSuccess { published = false }
            .logOnFailure { "Clearing the widget snapshot failed" }
    }
}

/**
 * Whether the app is someone's right now, for the widget: signed in, or unauthenticated with no
 * reason for someone past onboarding. That is a session that can't refresh offline (logging in a
 * basement on an hour-old token still counts), and also an onboarded device whose session is gone,
 * which still opens on Today until `SessionHealer` revives the session or sends it to sign in. Not
 * after a sign-out or an expired session, and not on a fresh install: a user change clears the
 * onboarding flag, so a relaunch afterwards with no session is nobody's either.
 */
internal fun AuthState.isSomeones(onboarded: Boolean): Boolean = when (this) {
    is AuthState.Authenticated -> true
    is AuthState.Unauthenticated -> reason == AuthState.Unauthenticated.Reason.None && onboarded
}
