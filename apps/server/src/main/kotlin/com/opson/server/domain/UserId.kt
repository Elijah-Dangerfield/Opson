package com.opson.server.domain

import java.util.UUID

/**
 * The authenticated user's id — the `sub` claim of a Supabase JWT, which is a
 * UUID that matches `auth.users.id`. A value class so a raw `UUID` can't be
 * passed where a user id is expected.
 */
@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        /** Parse from a JWT `sub` string; null if it isn't a valid UUID. */
        fun parse(raw: String): UserId? = try {
            UserId(UUID.fromString(raw))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
