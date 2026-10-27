package com.opson.libraries.config

import kotlinx.coroutines.flow.Flow

/**
 * Repository responsible for storing and streaming QA/debug config overrides.
 */
interface ConfigOverrideRepository {
    /** The persisted overrides: emitted once they have been read from disk, then on every change. */
    fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>>

    /** Persists or updates a single override value for the given path. */
    suspend fun addOverride(override: ConfigOverride<Any>)

    /** Wipes every override. Useful from the QA menu or a debug recovery affordance. */
    suspend fun clearAll()
}
