package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One row per person who joined the Builder program. See `V31__builders.sql`. */
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

/**
 * A Builder's check of a catalog food against the pack (V31). One that needs a person (V33) is
 * [status] `open`, with what they said was wrong ([problem]), the food as they saw it
 * ([snapshot]) and their photos in [FoodVerificationPhotosTable].
 */
object FoodVerificationsTable : Table("food_verifications") {
    val id = uuid("id")
    val foodId = uuid("food_id")
    val userId = uuid("user_id")
    val createdAt = timestamp("created_at")
    val outcome = text("outcome")
    val grounded = integer("grounded")
    val groundable = integer("groundable")
    val engine = text("engine").nullable()
    val status = text("status").nullable()
    val problem = jsonb("problem").nullable()
    val snapshot = jsonb("snapshot").nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    val resolvedBy = text("resolved_by").nullable()
    val resolution = text("resolution").nullable()
    val correctionId = uuid("correction_id").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** A verification's front and label photos (V33), deleted 30 days after the decision. */
object FoodVerificationPhotosTable : Table("food_verification_photos") {
    val verificationId = uuid("verification_id")
    val kind = text("kind")
    val mediaType = text("media_type")
    val bytes = binary("bytes")
    override val primaryKey = PrimaryKey(verificationId, kind)
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

/** One application to Builders (V32). At most one pending per account. */
object BuilderApplicationsTable : Table("builder_applications") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val status = text("status")
    val createdAt = timestamp("created_at")
    val termsVersion = text("terms_version")
    val photoRetention = bool("photo_retention")
    val motivation = text("motivation").nullable()
    val shopping = text("shopping").nullable()
    val region = text("region").nullable()
    val via = text("via").nullable()
    val decidedAt = timestamp("decided_at").nullable()
    val decidedBy = text("decided_by").nullable()
    val decisionNote = text("decision_note").nullable()
    val withdrawnAt = timestamp("withdrawn_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
