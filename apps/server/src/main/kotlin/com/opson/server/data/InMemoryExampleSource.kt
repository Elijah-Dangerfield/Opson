package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.ExampleData
import com.opson.server.domain.ExampleSource
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * In-memory example source. The `InMemory*` prefix names the backing store; a
 * database-backed impl would be `PostgresExampleSource`. Bind exactly one impl
 * per interface to [ServerScope] and it is wired into the [ServerComponent]
 * automatically.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class InMemoryExampleSource : ExampleSource {
    override suspend fun get(): ExampleData = ExampleData(
        message = "Hello from the template server.",
        items = listOf("alpha", "bravo", "charlie"),
    )
}
