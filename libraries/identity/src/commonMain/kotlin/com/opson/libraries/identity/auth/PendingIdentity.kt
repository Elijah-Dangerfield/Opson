package com.opson.libraries.identity.auth

/** A display name chosen while there was no session to save it to. */
data class PendingIdentity(
    val displayName: String?,
)
