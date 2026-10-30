package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

/**
 * Builders' verifications that need a person (`/v1/admin/food-verifications`), beside reports in
 * the Review area: a Builder checked a food against the pack in their hand and said something was
 * wrong, or the label's text did not show the food's numbers. Oldest first. A verification opens
 * with the food as served now beside what the Builder read, what they flagged as wrong or missing,
 * their note and both photos. Accept stands behind it (the food reads Verified, and it counts
 * toward the Builder's Pro); values in the form lay a correction over the same food, never a new
 * one. Reject changes nothing.
 */
@Composable
internal fun FoodVerificationsView(ctx: AdminCtx, refresh: Int, onDecided: () -> Unit) {
    var queue by remember { mutableStateOf<FoodVerificationQueueDto?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(refresh, reload) {
        Catching { queue = ctx.api.listFoodVerifications() }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load the verifications")) }
    }

    H3 { Text("Builders' verifications") }
    P(attrs = { classes("muted") }) {
        Text("A Builder checked a food against its pack and something didn't match. Fix the food it is about; accepting marks it Verified.")
    }
    val current = queue
    when {
        current == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        current.verifications.isEmpty() -> P(attrs = { classes("muted") }) { Text("No verifications waiting.") }
        else -> {
            Span(attrs = { classes("muted") }) { Text("${current.open} waiting") }
            Table(attrs = { classes("flags") }) {
                Thead {
                    Tr {
                        Th { Text("Food") }
                        Th { Text("What the Builder said") }
                        Th { Text("Photos") }
                        Th { Text("Waiting") }
                    }
                }
                Tbody {
                    current.verifications.forEach { item ->
                        Tr(attrs = {
                            if (item.id == selected) classes("selected")
                            style { property("cursor", "pointer") }
                            onClick { selected = item.id }
                        }) {
                            Td {
                                val name = item.now?.name ?: item.snapshot?.name ?: item.foodId
                                Div { Text(name) }
                                Span(attrs = { classes("muted") }) {
                                    Text(listOfNotNull(item.now?.brand ?: item.snapshot?.brand, item.now?.source ?: item.snapshot?.source).joinToString(" · "))
                                }
                            }
                            Td {
                                val flags = VerificationReview.flags(item)
                                if (flags.isEmpty()) Span(attrs = { classes("muted") }) { Text(VerificationReview.why(item)) }
                                flags.forEach { Span(attrs = { classes("chip") }) { Text(it) } }
                            }
                            Td { Text(listOfNotNull("front".takeIf { item.hasFrontPhoto }, "label".takeIf { item.hasLabelPhoto }).joinToString(", ").ifEmpty { "none" }) }
                            Td { Text(relativeTime(item.createdAtEpochMs)) }
                        }
                    }
                }
            }
            current.verifications.firstOrNull { it.id == selected }?.let { item ->
                VerificationPanel(ctx, item, onDecided = { selected = null; reload++; onDecided() })
            }
        }
    }
}

@Composable
private fun VerificationPanel(ctx: AdminCtx, item: FoodVerificationDto, onDecided: () -> Unit) {
    var edits by remember(item.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var reason by remember(item.id) { mutableStateOf("") }

    val rows = VerificationReview.rows(item)
    val values = ReportDiff.values(rows, edits)
    // Only what differs from the food as served is a correction; the rest is the food standing.
    val changes = values?.takeIf { ReportDiff.describe(rows, it) != "no change" }

    Div(attrs = { classes("panel") }) {
        H3 { Text(item.now?.name ?: item.snapshot?.name ?: item.foodId) }
        P(attrs = { classes("muted") }) {
            Text(
                listOfNotNull(
                    item.now?.brand,
                    item.now?.source ?: item.snapshot?.source,
                    item.now?.barcode?.let { "barcode $it" },
                    "food ${item.foodId}",
                    "Builder ${item.userId}",
                ).joinToString(" · "),
            )
        }
        if (item.now == null) P(attrs = { classes("err") }) { Text("This food is no longer in the catalog.") }
        P { Text("${VerificationReview.why(item)}, ${relativeTime(item.createdAtEpochMs)}.") }
        VerificationReview.flags(item).takeIf { it.isNotEmpty() }?.let { flags ->
            Div { flags.forEach { Span(attrs = { classes("chip") }) { Text(it) } } }
        }
        item.values?.basisLabel?.let { basis ->
            P(attrs = { classes("muted") }) { Text("Read for $basis${item.values.basisGrams?.let { " (${ReportDiff.number(it)} g)" }.orEmpty()}") }
        }
        VerificationReview.barcodeNote(item)?.let { P(attrs = { classes("err") }) { Text(it) } }
        item.note?.let { P(attrs = { classes("muted") }) { Text("“$it”") } }

        Div(attrs = { classes("photos") }) {
            if (item.hasFrontPhoto) PackPhoto(ctx, "Front of the pack") { ctx.api.foodVerificationPhoto(item.id, "front") }
            if (item.hasLabelPhoto) PackPhoto(ctx, "Label") { ctx.api.foodVerificationPhoto(item.id, "label") }
        }

        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Field") }
                    Th { Text("Now") }
                    Th { Text("The pack") }
                    Th { Text("Correction (edit)") }
                }
            }
            Tbody {
                rows.forEach { row ->
                    Tr(attrs = { if (row.differs) classes("selected") }) {
                        Td { Text(if (row.unit.isEmpty()) row.label else "${row.label} (${row.unit})") }
                        Td { Text(row.now ?: "unknown") }
                        Td { Text(row.suggested.joinToString(" / ")) }
                        Td {
                            TextInput(edits[row.key] ?: row.proposed) {
                                onInput { value -> edits = edits + (row.key to value.value) }
                                placeholder("as the source has it")
                            }
                        }
                    }
                }
            }
        }

        Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
            Button(attrs = {
                classes("primary")
                onClick {
                    ctx.confirmWrite(
                        title = if (changes != null) "Correct this food and mark it Verified" else "Mark this food Verified as it is",
                        flagPath = null,
                        before = rows.joinToString(" · ") { "${it.label} ${it.now ?: "?"}" },
                        after = changes?.let { ReportDiff.describe(rows, it) } ?: "no change, Verified",
                        success = "Accepted. The food reads Verified and the Builder's check counts.",
                        warning = if (ctx.isProd && changes != null) "Everyone who finds this food will see these numbers." else null,
                    ) {
                        ctx.api.acceptFoodVerification(item.id, changes)
                        onDecided()
                    }
                }
            }) { Text(if (changes != null) "Accept with correction" else "Accept as it is") }
            TextInput(reason) {
                onInput { reason = it.value }
                placeholder("why it is rejected (optional)")
                style { property("min-width", "260px") }
            }
            Button(attrs = {
                classes("danger")
                onClick {
                    ctx.confirmWrite(
                        title = "Reject this verification",
                        flagPath = null,
                        before = "waiting",
                        after = "rejected" + reason.trim().ifEmpty { null }?.let { ": $it" }.orEmpty(),
                        success = "Rejected. The food is unchanged.",
                    ) {
                        ctx.api.rejectFoodVerification(item.id, reason.trim().ifEmpty { null })
                        onDecided()
                    }
                }
            }) { Text("Reject") }
        }
    }
}

/**
 * Decisions on reports and verifications per reviewer, so "who is cleaning up the database" is
 * one table. The Builders board in Grafana has the same counts over time.
 */
@Composable
internal fun ReviewerCountsView(ctx: AdminCtx, refresh: Int) {
    var counts by remember { mutableStateOf<ReviewerCountsDto?>(null) }
    LaunchedEffect(refresh) {
        Catching { counts = ctx.api.reviewerCounts() }
    }
    val current = counts ?: return
    H3 { Text("Decided in the last ${current.days} days") }
    if (current.reviewers.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("Nothing decided yet.") }
        return
    }
    Table(attrs = { classes("flags") }) {
        Thead {
            Tr {
                Th { Text("Reviewer") }
                Th { Text("Reports accepted") }
                Th { Text("Reports rejected") }
                Th { Text("Duplicates") }
                Th { Text("Verifications accepted") }
                Th { Text("Verifications rejected") }
            }
        }
        Tbody {
            (current.reviewers + VerificationReview.totals(current.reviewers)).forEach { row ->
                Tr {
                    Td { Text(row.reviewer) }
                    Td { Text(row.reportsAccepted.toString()) }
                    Td { Text(row.reportsRejected.toString()) }
                    Td { Text(row.reportsDuplicate.toString()) }
                    Td { Text(row.verificationsAccepted.toString()) }
                    Td { Text(row.verificationsRejected.toString()) }
                }
            }
        }
    }
}
