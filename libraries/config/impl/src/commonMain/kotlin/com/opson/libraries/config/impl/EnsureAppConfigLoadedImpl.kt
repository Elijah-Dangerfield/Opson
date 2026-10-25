package com.opson.libraries.config.impl

import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.config.EnsureAppConfigLoaded
import com.opson.libraries.core.Catching
import com.opson.libraries.core.ignoreValue
import com.opson.libraries.core.throwIfDebug
import kotlinx.coroutines.flow.first
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EnsureAppConfigLoadedImpl @Inject constructor(
    private val repository: AppConfigRepository
) : EnsureAppConfigLoaded {
    override suspend fun invoke(): Catching<Unit> =
        Catching {
            repository.configStream().first()
        }
            .throwIfDebug()
            .ignoreValue()
}
