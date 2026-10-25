package com.opson.server.domain

/**
 * The reference domain interface. Routes depend on this, not on the concrete
 * impl, so swapping the in-memory source for a database-backed one is a one-line
 * DI change (bind a `PostgresExampleSource` with the same `@ContributesBinding`
 * and this one drops out automatically).
 *
 * This pair — interface in `domain/`, impl in `data/` — is the canonical shape
 * for every server-side service. Copy it.
 */
fun interface ExampleSource {
    suspend fun get(): ExampleData
}

/**
 * A pure domain model. No framework imports live in `domain/`; the wire shape is
 * a separate DTO (see routes/ExampleDto.kt).
 */
data class ExampleData(
    val message: String,
    val items: List<String>,
)
