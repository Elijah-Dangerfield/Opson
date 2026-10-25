package com.opson.server.domain

import kotlin.time.Instant

/**
 * A user's profile. Pure domain model — no framework imports live in `domain/`;
 * the wire shape is a separate DTO (see routes/MeDto.kt).
 */
data class Profile(
    val userId: UserId,
    val displayName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
