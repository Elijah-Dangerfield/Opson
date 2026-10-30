package com.opson.libraries.prompts.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

fun interface PromptAudienceSource {
    suspend fun current(): PromptAudience
}

@ContributesBinding(AppScope::class)
@Inject
class LivePromptAudienceSource(
    private val auth: AuthRepository,
    private val appCache: AppCache,
    private val settings: SettingsRepository,
) : PromptAudienceSource {
    override suspend fun current(): PromptAudience {
        val signedIn = auth.current() as? AuthState.Authenticated
        val onboarded = Catching { appCache.get().hasUserOnboarded }.getOrNull() == true
        // Until this account's settings are in, the "numbers have been hard" answer is a default,
        // not theirs. Nothing shows until it is known, rather than risk the wrong prompt.
        val known = settings.isKnown.value
        return PromptAudience(
            userId = signedIn?.userId,
            onboarded = onboarded && known,
            numbersHard = settings.settings.value.onboarding.hard,
            suggestions = settings.settings.value.suggestions,
        )
    }
}
