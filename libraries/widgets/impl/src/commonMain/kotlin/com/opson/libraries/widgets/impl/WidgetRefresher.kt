package com.opson.libraries.widgets.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.widgets.WidgetPublisher
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotSource
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
 * It publishes only for someone signed in whose settings are known. While the settings are nobody's
 * yet ([SettingsRepository.isKnown] false: a user change, until the next account's first pull) it
 * clears what it published, or the home screen would keep the last account's day in the last
 * account's mode. Signed out, the settings read as known, but they are the defaults (All numbers),
 * and a No numbers widget would turn into a calorie card; a sign-out or an expired session clears
 * the widget instead, even one an earlier run wrote. [AuthRepository] is reached through a provider,
 * only once the flow starts, as in `SyncTriggers`: auth depends on much of the boot-time graph.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class WidgetRefresher(
    private val settingsRepository: SettingsRepository,
    private val source: WidgetSnapshotSource,
    private val publisher: WidgetPublisher,
    authRepositoryProvider: () -> AuthRepository,
    appScope: AppCoroutineScope,
) : AutoInit {

    private enum class Show { Snapshot, Nothing, WhatWasPublished }

    private var published = false

    private val auth = flow { emitAll(authRepositoryProvider().observe()) }

    init {
        appScope.launch {
            combine(auth, settingsRepository.isKnown, ::show).distinctUntilChanged().collectLatest { show ->
                when (show) {
                    Show.Snapshot -> source.observe().collect(::publish)
                    Show.Nothing -> clear()
                    Show.WhatWasPublished -> if (published) clear()
                }
            }
        }
    }

    private fun show(auth: AuthState, settingsKnown: Boolean): Show = when (auth) {
        is AuthState.Authenticated -> if (settingsKnown) Show.Snapshot else Show.WhatWasPublished
        // Nobody signed in at launch, or a session that can't refresh offline: what the widget holds
        // was written for whoever was signed in last, in their mode, so it can stay.
        is AuthState.Unauthenticated -> when (auth.reason) {
            AuthState.Unauthenticated.Reason.None -> Show.WhatWasPublished
            AuthState.Unauthenticated.Reason.SignedOut, AuthState.Unauthenticated.Reason.SessionExpired -> Show.Nothing
        }
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
