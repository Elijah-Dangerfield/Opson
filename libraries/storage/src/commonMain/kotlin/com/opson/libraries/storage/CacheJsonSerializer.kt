package com.opson.libraries.storage

interface CacheJsonSerializer<T : Any> {
    suspend fun read(bytes: ByteArray?): T
    suspend fun write(value: T): ByteArray
}
