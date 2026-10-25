package com.opson.libraries.storage

interface CacheFactory {
    fun <T : Any> inMemory(
        defaultValue: () -> T,
    ): Cache<T>

    fun <T : Any> persistent(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean = true,
    ): Cache<T>
}