package com.opson.libraries.navigation.impl

import com.opson.libraries.navigation.DeepLinkBridge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * A queue, not a replaying flow: a cold launch from a link emits before the App composable
 * listens, so every link waits until it is collected, and each is handed out once. A replayed
 * link would run again for every new listener (an OAuth callback re-imported, a widget's Log
 * opening the Logger a second time).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DeepLinkBridgeImpl : DeepLinkBridge {

    private val pending = Channel<String>(Channel.UNLIMITED)

    override val urls: Flow<String> = pending.receiveAsFlow()

    override fun emit(url: String) {
        pending.trySend(url)
    }
}
