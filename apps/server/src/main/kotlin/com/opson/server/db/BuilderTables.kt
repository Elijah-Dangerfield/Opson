package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One row per person who joined the Builder program. See `V30__builders.sql`. */
object BuilderMembersTable : Table("builder_members") {
    val userId = uuid("user_id")
    val status = text("status")
    val joinedAt = timestamp("joined_at")
    val termsVersion = text("terms_version")
    val photoRetention = bool("photo_retention")
    val trustedSince = timestamp("trusted_since").nullable()
    val trustHold = bool("trust_hold")
    val leftAt = timestamp("left_at").nullable()
    val removedAt = timestamp("removed_at").nullable()
    val removedBy = text("removed_by").nullable()
    val removedReason = text("removed_reason").nullable()
    override val primaryKey = PrimaryKey(userId)
}

/** A Builder's check of a catalog food against the pack (V30). No photo. */
object FoodVerificationsTable : Table("food_verifications") {
    val id = uuid("id")
    val foodId = uuid("food_id")
    val userId = uuid("user_id")
    val createdAt = timestamp("created_at")
    val outcome = text("outcome")
    val grounded = integer("grounded")
    val groundable = integer("groundable")
    val engine = text("engine").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** Who contributed what, kept past account deletion (V30). Written by a trigger on approval. */
object FoodProvenanceTable : Table("food_provenance") {
    val id = uuid("id")
    val foodId = uuid("food_id")
    val kind = text("kind")
    val refId = uuid("ref_id")
    val userId = uuid("user_id").nullable()
    val lane = text("lane")
    val acceptedAt = timestamp("accepted_at")
    val acceptedBy = text("accepted_by").nullable()
    val rolledBackAt = timestamp("rolled_back_at").nullable()
    val rolledBackBy = text("rolled_back_by").nullable()
    override val primaryKey = PrimaryKey(id)
}
