package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * The columns of `foods` a submission writes, `review_status` (V25) among
 * them. A projection of its own rather than more columns on [FoodsTable], so
 * nothing that reads or upserts the catalog has to know about review.
 */
object SubmittedFoodsTable : Table("foods") {
    val id = uuid("id")
    val sourceKind = text("source")
    val sourceId = text("source_id")
    val name = text("name")
    val brand = text("brand").nullable()
    val barcode = text("barcode").nullable()
    val kcal100g = float("kcal_100g").nullable()
    val protein100g = float("protein_100g").nullable()
    val carbs100g = float("carbs_100g").nullable()
    val fat100g = float("fat_100g").nullable()
    val fiber100g = float("fiber_100g").nullable()
    val micros = jsonb("micros")
    val portions = jsonb("portions")
    val reviewStatus = text("review_status")
    val verified = bool("verified")
    override val primaryKey = PrimaryKey(id)
}

/** People's submissions and their review. See `V25__food_submissions.sql` and V27. */
object FoodSubmissionsTable : Table("food_submissions") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val foodId = uuid("food_id")
    val createdAt = timestamp("created_at")
    val status = text("status")
    val barcode = text("barcode").nullable()
    val gtin = text("gtin").nullable()
    val origin = text("origin")
    val model = text("model").nullable()
    val parsed = jsonb("parsed").nullable()
    val submitted = jsonb("submitted")
    val reviewed = jsonb("reviewed").nullable()
    val checks = jsonb("checks")
    val reviewedAt = timestamp("reviewed_at").nullable()
    val reviewedBy = text("reviewed_by").nullable()
    val rejectReason = text("reject_reason").nullable()

    /** The submitter's own copy, a custom food (V27). */
    val customFoodId = uuid("custom_food_id").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** The two photos of each submission, deleted 30 days after the decision. */
object FoodSubmissionPhotosTable : Table("food_submission_photos") {
    val submissionId = uuid("submission_id")
    val kind = text("kind")
    val mediaType = text("media_type")
    val bytes = binary("bytes")
    override val primaryKey = PrimaryKey(submissionId, kind)
}

/** Barcode and food pairs from a miss followed by a search. */
object BarcodeLinkSuggestionsTable : Table("barcode_link_suggestions") {
    val gtin = text("gtin")
    val foodId = uuid("food_id")
    val barcode = text("barcode")
    val pairings = integer("pairings")
    val firstAt = timestamp("first_at")
    val lastAt = timestamp("last_at")
    val status = text("status")
    val reviewedAt = timestamp("reviewed_at").nullable()
    override val primaryKey = PrimaryKey(gtin, foodId)
}
