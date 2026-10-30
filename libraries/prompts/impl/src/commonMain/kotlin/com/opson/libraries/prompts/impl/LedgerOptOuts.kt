@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.prompts.OptOut
import com.opson.libraries.prompts.OptOuts
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * [OptOuts] on the prompt ledger: the showings are counted per account on this phone, beside the
 * prompts' own counts, so they survive signing out and back in and are never synced. A second
 * phone offers the way out a few more times, which costs nothing. The switches are settings.
 */
@HiddenFromObjC
@ContributesBinding(AppScope::class)
@Inject
class LedgerOptOuts(
    private val ledger: PromptLedgerStore,
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
) : OptOuts {

    override suspend fun showing(optOut: OptOut): Boolean {
        val userId = userId() ?: return false
        val after = ledger.update(userId) { it.copy(optOutShows = it.optOutShows + (optOut.wire to shows(it, optOut) + 1)) }
        return offers(shows(after, optOut))
    }

    override suspend fun offered(optOut: OptOut): Boolean {
        val userId = userId() ?: return false
        return offers(shows(ledger.account(userId), optOut))
    }

    override suspend fun turnOff(optOut: OptOut) {
        settings.update { it.without(optOut) }
    }

    private suspend fun userId(): String? = (auth.current() as? AuthState.Authenticated)?.userId

    private fun shows(account: AccountLedger, optOut: OptOut): Int = account.optOutShows[optOut.wire] ?: 0

    companion object {
        /** The first [OptOuts.OFFERED_SHOWINGS] showings offer it; a showing not yet counted does not. */
        fun offers(shows: Int): Boolean = shows in 1..OptOuts.OFFERED_SHOWINGS

        /** [this] with [optOut]'s switch off. */
        fun Settings.without(optOut: OptOut): Settings = when (optOut) {
            OptOut.Celebrations -> copy(celebrationsEnabled = false)
            OptOut.Streak -> copy(todayStreak = false)
            OptOut.RecapBanner -> copy(recapBanner = false)
            OptOut.Tips -> copy(tips = false)
            OptOut.Suggestions -> copy(suggestions = false)
        }
    }
}
