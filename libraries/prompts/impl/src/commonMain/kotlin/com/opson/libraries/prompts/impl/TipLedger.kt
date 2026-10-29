@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.opson.AppCache
import com.opson.libraries.prompts.Tips
import com.opson.libraries.prompts.TipsDebug
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import com.opson.libraries.ui.components.CoachMarks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/** Which tips each account has seen on this phone, by user id. Ids only. */
@Serializable
data class TipLedger(val seen: Map<String, Set<String>> = emptyMap())

/**
 * The tips' memory and the one rule that picks them ([CoachMarks.pick]): a signed-in account that
 * finished onboarding, and among the tips on screen the first in [Tips.all] it has not seen. The
 * pick is written down at once, so a tip shows once even if the app is killed while it is up.
 *
 * Kept on the phone and per account, as the prompts' counts are (no `UserScopedClearer`): a tip
 * seen once shouldn't show again after signing out and back in. Built at launch ([AutoInit]) so
 * the current account's ids are in memory before Today first asks.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = CoachMarks::class)
@ContributesBinding(AppScope::class, boundType = TipsDebug::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class TipMemory(
    cacheFactory: CacheFactory,
    auth: AuthRepository,
    appCache: AppCache,
    scope: AppCoroutineScope,
) : CoachMarks, TipsDebug, AutoInit {

    private val cache: Cache<TipLedger> = cacheFactory.persistent(
        name = "tip_ledger",
        serializer = versionedJsonSerializer(defaultValue = { TipLedger() }),
    )

    private val writes: CoroutineScope = scope

    /** Who is signed in and onboarded, with what they've seen; null for nobody who may see a tip. */
    private val current = MutableStateFlow<Viewer?>(null)

    private data class Viewer(val userId: String, val seen: Set<String>)

    init {
        scope.launch {
            combine(auth.observe(), appCache.updates, cache.updates) { state, data, ledger ->
                val user = (state as? AuthState.Authenticated)?.takeIf { data.hasUserOnboarded }?.userId
                user?.let { Viewer(it, ledger.seen[it].orEmpty()) }
            }.collect { current.value = it }
        }
    }

    override fun pick(candidates: Collection<String>): String? {
        val viewer = current.value ?: return null
        val tip = choose(candidates, viewer.seen) ?: return null
        current.value = viewer.copy(seen = viewer.seen + tip)
        writes.launch { cache.update { it.copy(seen = it.seen + (viewer.userId to it.seen[viewer.userId].orEmpty() + tip)) } }
        return tip
    }

    override suspend fun reset() {
        val viewer = current.value
        cache.update { ledger -> if (viewer == null) TipLedger() else ledger.copy(seen = ledger.seen - viewer.userId) }
        current.value = viewer?.copy(seen = emptySet())
    }

    companion object {
        /** The first tip in [Tips.all] that is on screen and not [seen]. */
        fun choose(candidates: Collection<String>, seen: Set<String>): String? =
            Tips.all.firstOrNull { it.id in candidates && it.id !in seen }?.id
    }
}
