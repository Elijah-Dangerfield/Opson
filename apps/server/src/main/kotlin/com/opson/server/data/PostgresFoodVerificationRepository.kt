package com.opson.server.data

import com.opson.server.db.Database
import com.opson.server.db.FoodVerificationsTable
import com.opson.server.db.toJavaInstant
import com.opson.server.db.toKotlinInstant
import com.opson.server.di.ServerScope
import com.opson.server.domain.FoodVerification
import com.opson.server.domain.FoodVerificationRepository
import com.opson.server.domain.UserId
import com.opson.server.domain.VerificationOutcome
import com.opson.server.domain.VerifyOutcome
import me.tatarka.inject.annotations.Inject
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import kotlin.time.ExperimentalTime

/** `food_verifications` (V30). The id the app made is the idempotency key. */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresFoodVerificationRepository(private val database: Database) : FoodVerificationRepository {

    override suspend fun record(verification: FoodVerification): VerifyOutcome = database.transaction {
        find(verification.id)?.let { found ->
            return@transaction if (found.userId == verification.userId) VerifyOutcome.Existing(found) else VerifyOutcome.Conflict
        }
        val confirmedBefore = verification.outcome == VerificationOutcome.Confirmed &&
            FoodVerificationsTable.selectAll().where {
                (FoodVerificationsTable.userId eq verification.userId.value) and
                    (FoodVerificationsTable.foodId eq verification.foodId) and
                    (FoodVerificationsTable.outcome eq VerificationOutcome.Confirmed.wire)
            }.any()
        val stored = if (confirmedBefore) verification.copy(outcome = VerificationOutcome.Unconfirmed) else verification
        FoodVerificationsTable.insert {
            it[id] = stored.id
            it[foodId] = stored.foodId
            it[userId] = stored.userId.value
            it[createdAt] = stored.createdAt.toJavaInstant()
            it[outcome] = stored.outcome.wire
            it[grounded] = stored.grounded
            it[groundable] = stored.groundable
            it[engine] = stored.engine?.take(MAX_ENGINE)
        }
        VerifyOutcome.Recorded(stored)
    }

    private fun find(id: UUID): FoodVerification? =
        FoodVerificationsTable.selectAll().where { FoodVerificationsTable.id eq id }.singleOrNull()?.toVerification()

    private fun ResultRow.toVerification() = FoodVerification(
        id = this[FoodVerificationsTable.id],
        foodId = this[FoodVerificationsTable.foodId],
        userId = UserId(this[FoodVerificationsTable.userId]),
        createdAt = this[FoodVerificationsTable.createdAt].toKotlinInstant(),
        outcome = if (this[FoodVerificationsTable.outcome] == VerificationOutcome.Confirmed.wire) VerificationOutcome.Confirmed else VerificationOutcome.Unconfirmed,
        grounded = this[FoodVerificationsTable.grounded],
        groundable = this[FoodVerificationsTable.groundable],
        engine = this[FoodVerificationsTable.engine],
    )

    private companion object {
        const val MAX_ENGINE = 32
    }
}
