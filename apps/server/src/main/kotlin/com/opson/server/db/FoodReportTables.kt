package com.opson.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** Reports about a food's data (`V28__food_reports.sql`). */
object FoodReportsTable : Table("food_reports") {
    val id = uuid("id")
    val foodId = uuid("food_id")
    val userId = uuid("user_id").nullable()
    val createdAt = timestamp("created_at")
    val reason = text("reason")
    val suggested = jsonb("suggested").nullable()
    val note = text("note").nullable()
    val snapshot = jsonb("snapshot")
    val status = text("status")
    val resolvedAt = timestamp("resolved_at").nullable()
    val resolvedBy = text("resolved_by").nullable()
    val resolution = text("resolution").nullable()
    val correctionId = uuid("correction_id").nullable()
    val duplicateOf = uuid("duplicate_of").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** A report's one photo of the label, deleted 30 days after the decision. */
object FoodReportPhotosTable : Table("food_report_photos") {
    val reportId = uuid("report_id")
    val mediaType = text("media_type")
    val bytes = binary("bytes")
    override val primaryKey = PrimaryKey(reportId)
}

/** Reviewers' corrections, laid over the stored food whenever it is served. */
object FoodCorrectionsTable : Table("food_corrections") {
    val id = uuid("id")
    val foodId = uuid("food_id")
    val createdAt = timestamp("created_at")
    val createdBy = text("created_by")
    val correctionFields = jsonb("fields")
    val base = jsonb("base")
    val revertedAt = timestamp("reverted_at").nullable()
    val revertedBy = text("reverted_by").nullable()
    val supersededBy = uuid("superseded_by").nullable()
    override val primaryKey = PrimaryKey(id)
}
