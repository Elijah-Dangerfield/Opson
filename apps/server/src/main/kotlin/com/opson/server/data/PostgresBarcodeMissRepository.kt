package com.opson.server.data

import com.opson.server.db.BarcodeMissesTable
import com.opson.server.db.Database
import com.opson.server.db.toJavaInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeMiss
import com.opson.server.domain.BarcodeMissRepository
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.insertIgnore
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/** `barcode_misses`, insert-only. `ON CONFLICT DO NOTHING` on the app's id makes a retried report a no-op. */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresBarcodeMissRepository(
    private val database: Database,
    private val clock: Clock,
) : BarcodeMissRepository {

    override suspend fun record(miss: BarcodeMiss) {
        database.transaction {
            BarcodeMissesTable.insertIgnore {
                it[id] = miss.id
                it[createdAt] = clock.now().toJavaInstant()
                it[barcode] = miss.barcode
                it[gtin] = miss.gtin
                it[country] = miss.country
                it[platform] = miss.platform
                it[appVersion] = miss.appVersion
            }
        }
    }
}
