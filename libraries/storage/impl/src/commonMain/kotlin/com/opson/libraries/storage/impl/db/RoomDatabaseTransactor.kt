package com.opson.libraries.storage.impl.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.opson.libraries.opson.storage.db.DatabaseTransactor
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * An IMMEDIATE transaction on the writer connection. Room confines that
 * connection to the calling coroutine, so DAO calls made inside [inTransaction]
 * reuse it and commit or roll back with the block. IMMEDIATE takes the write
 * lock up front, so a read-then-write inside the block cannot be overtaken by
 * another writer between the two.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RoomDatabaseTransactor @Inject constructor(
    private val provider: AppDatabaseProvider,
) : DatabaseTransactor {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        provider.database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
