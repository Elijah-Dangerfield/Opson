package com.opson.libraries.opson.storage.db

/**
 * Runs a block as one write transaction on the app database, so writes made
 * through different DAOs inside it commit or roll back together.
 *
 * The outbox needs this: a diary row and the outbox mutation that carries it
 * to the server are written by two DAOs, and a crash between them would either
 * lose the change on the way to the server or send a change the device no
 * longer has. Every DAO call made inside [block] runs on the transaction's
 * connection, because Room confines the connection to the calling coroutine.
 *
 * Do not launch coroutines or switch to another database inside [block]: work
 * that leaves the coroutine leaves the transaction too.
 */
interface DatabaseTransactor {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
