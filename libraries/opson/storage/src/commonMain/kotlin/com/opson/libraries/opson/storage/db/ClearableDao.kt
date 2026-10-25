package com.opson.libraries.opson.storage.db

/**
 * Every user-scoped `@Dao` implements this and is multibound into the set
 * `UserScopedDaoCleaner` consumes — adding a new DAO is a compile-time
 * wire-up, not a list edit. On user change (sign-out / account switch) the
 * cleaner calls [deleteAll] on each so the departing user's rows never leak
 * into the next session.
 */
interface ClearableDao {
    suspend fun deleteAll()
}
