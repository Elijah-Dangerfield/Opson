package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
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
import org.jetbrains.compose.web.dom.Ul
import org.khronos.webgl.Int8Array
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * The food review queue (`/v1/admin/food-submissions`, `/v1/admin/barcode-links`): foods people
 * added from photos of a pack, and barcodes suggested by a miss followed by a search. A
 * submission opens with both photos side by side, then every field as the model read it, as the
 * person sent it, and as it stands, with the checks the server ran. The reviewer can correct any
 * field, approve (the food joins search and barcode lookup for everyone) or reject with a reason.
 *
 * Below them, reports on foods already in the catalog ([FoodReportsView]): "Report a problem" from
 * any food's detail, and the server's own energy check.
 *
 * The photos never have a URL anyone else could open: they are fetched with the admin token and
 * shown through a blob URL that lives in this tab. Grafana's review board links here as
 * `/admin/#review/<id>`.
 */
@Composable
internal fun ReviewView(ctx: AdminCtx, openId: String?) {
    var status by remember { mutableStateOf("pending") }
    var list by remember { mutableStateOf<SubmissionListDto?>(null) }
    var selected by remember { mutableStateOf(openId) }
    var links by remember { mutableStateOf<List<BarcodeLinkDto>?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(status, refresh) {
        Catching { list = ctx.api.listSubmissions(status) }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load the queue")) }
        Catching { links = ctx.api.listBarcodeLinks() }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load barcode links")) }
    }

    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            listOf("pending", "approved", "rejected").forEach { s ->
                Button(attrs = {
                    if (s == status) classes("primary")
                    onClick { status = s; selected = null }
                }) { Text(s.replaceFirstChar { it.uppercase() }) }
            }
            Span(attrs = { classes("spacer") })
            list?.let { Span(attrs = { classes("muted") }) { Text("${it.total} $status") } }
            Button(attrs = { onClick { refresh++ } }) { Text("Reload") }
        }
        Span(attrs = { classes("muted", "hint") }) {
            Text("Photos are shown only here, fetched with your token. They are deleted 30 days after the decision.")
        }
    }

    val current = list
    when {
        current == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        current.submissions.isEmpty() -> P(attrs = { classes("muted") }) { Text("Nothing $status.") }
        else -> SubmissionTable(current.submissions, selected) { selected = it }
    }

    selected?.let { id ->
        SubmissionPanel(ctx, id, onDecided = { selected = null; refresh++ })
    }

    FoodReportsView(ctx, refresh)

    BarcodeLinks(ctx, links, onDecided = { refresh++ })
}

@Composable
private fun SubmissionTable(rows: List<SubmissionSummaryDto>, selected: String?, onSelect: (String) -> Unit) {
    Table(attrs = { classes("flags") }) {
        Thead {
            Tr {
                Th { Text("Submitted") }
                Th { Text("Food") }
                Th { Text("Barcode") }
                Th { Text("From") }
                Th { Text("Checks") }
            }
        }
        Tbody {
            rows.forEach { row ->
                Tr(attrs = {
                    if (row.id == selected) classes("selected")
                    style { property("cursor", "pointer") }
                    onClick { onSelect(row.id) }
                }) {
                    Td { Text(relativeTime(row.createdAtEpochMs)) }
                    Td {
                        Div { Text(row.name ?: "(no name)") }
                        row.brand?.let { Span(attrs = { classes("muted") }) { Text(it) } }
                    }
                    Td { Span(attrs = { classes("mono") }) { Text(row.barcode ?: "none") } }
                    Td { Text(if (row.origin == "barcode") "scan miss" else "search") }
                    Td {
                        if (row.failedChecks.isEmpty()) {
                            Span(attrs = { classes("muted") }) { Text("all pass") }
                        } else {
                            row.failedChecks.forEach { Span(attrs = { classes("chip") }) { Text(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionPanel(ctx: AdminCtx, id: String, onDecided: () -> Unit) {
    var detail by remember(id) { mutableStateOf<SubmissionDetailDto?>(null) }
    var edits by remember(id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unit by remember(id) { mutableStateOf<String?>(null) }
    var reason by remember(id) { mutableStateOf("") }

    LaunchedEffect(id) {
        Catching { detail = ctx.api.submission(id) }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load that submission")) }
    }

    val loaded = detail ?: return P(attrs = { classes("muted") }) { Text("Loading submission…") }
    val base = loaded.reviewed ?: loaded.submitted
    val pending = loaded.summary.status == "pending"
    val edited = ReviewForm.facts(base, edits, unit ?: base.servingUnit)

    Div(attrs = { classes("panel") }) {
        H3 { Text(loaded.summary.name ?: "(no name)") }
        P(attrs = { classes("muted") }) {
            Text(
                "Submitted ${relativeTime(loaded.summary.createdAtEpochMs)} from a " +
                    (if (loaded.summary.origin == "barcode") "scan miss" else "search") +
                    ", read by ${loaded.model ?: "nobody (typed in, or the reading had expired)"}. " +
                    "Food ${loaded.foodId}.",
            )
        }
        if (loaded.summary.hasPhotos) {
            Div(attrs = { classes("photos") }) {
                Photo(ctx, id, "front", "Front of the pack")
                Photo(ctx, id, "label", "Nutrition label")
            }
        } else {
            P(attrs = { classes("muted") }) { Text("The photos have been deleted (30 days after the decision).") }
        }

        Checks("The checks on it as it stands", loaded.checks)
        if (loaded.parsedChecks.isNotEmpty()) Checks("The checks on the model's reading", loaded.parsedChecks)
        if (loaded.submitted.acknowledged.isNotEmpty()) {
            P { Text("The person kept the label's figure through: ${loaded.submitted.acknowledged.joinToString(", ")}.") }
        }

        Div(attrs = { classes("row") }) {
            Span { Text("Serving in") }
            listOf("g", "ml").forEach { u ->
                Button(attrs = {
                    if ((unit ?: base.servingUnit) == u) classes("primary")
                    if (!pending) attr("disabled", "true")
                    onClick { unit = u }
                }) { Text(u) }
            }
            base.servingLabel?.let { Span(attrs = { classes("muted") }) { Text("label says: $it") } }
        }

        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Field") }
                    Th { Text("Model read") }
                    Th { Text("Sent") }
                    Th { Text(if (pending) "Now (edit)" else "Final") }
                }
            }
            Tbody {
                ReviewForm.rows(loaded).forEach { row ->
                    Tr(attrs = { if (row.needsLook) classes("selected") }) {
                        Td { Text(if (row.unit.isEmpty()) row.label else "${row.label} (${row.unit})") }
                        Td {
                            Text(row.read ?: "")
                            row.confidence?.let { c ->
                                Span(attrs = { classes(if (c < ReviewRow.LOW_CONFIDENCE) "err" else "muted", "hint") }) {
                                    Text(" ${(c * 100).toInt()}%")
                                }
                            }
                        }
                        Td { Text(row.sent ?: "") }
                        Td {
                            if (pending) {
                                TextInput(edits[row.key] ?: row.current) {
                                    onInput { value -> edits = edits + (row.key to value.value) }
                                    placeholder(row.label)
                                }
                            } else {
                                Text(row.current)
                            }
                        }
                    }
                }
            }
        }

        if (pending) {
            Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
                Button(attrs = {
                    if (edits.isEmpty() && unit == null) attr("disabled", "true")
                    onClick {
                        ctx.confirmWrite(
                            title = "Save the corrections",
                            flagPath = null,
                            before = describeFacts(base),
                            after = describeFacts(edited),
                            success = "Saved. The checks were run again.",
                        ) {
                            detail = ctx.api.editSubmission(id, edited)
                            edits = emptyMap()
                            unit = null
                        }
                    }
                }) { Text("Save corrections") }
                Button(attrs = {
                    classes("primary")
                    onClick {
                        ctx.confirmWrite(
                            title = "Approve for everyone",
                            flagPath = null,
                            before = "Only its submitter sees this food",
                            after = describeFacts(edited),
                            success = "Approved. Search and barcode lookup find it now.",
                            warning = if (ctx.isProd) "Everyone who searches or scans this product will see these numbers." else null,
                        ) {
                            ctx.api.approveSubmission(id, if (edits.isEmpty() && unit == null) null else edited)
                            onDecided()
                        }
                    }
                }) { Text("Approve") }
                TextInput(reason) {
                    onInput { reason = it.value }
                    placeholder("why it is rejected (the submitter keeps their own copy)")
                    style { property("min-width", "340px") }
                }
                Button(attrs = {
                    classes("danger")
                    if (reason.isBlank()) attr("disabled", "true")
                    onClick {
                        ctx.confirmWrite(
                            title = "Reject",
                            flagPath = null,
                            before = loaded.summary.name ?: "(no name)",
                            after = "Rejected: ${reason.trim()}",
                            success = "Rejected.",
                        ) {
                            ctx.api.rejectSubmission(id, reason.trim())
                            onDecided()
                        }
                    }
                }) { Text("Reject") }
            }
        } else {
            P(attrs = { classes("muted") }) {
                Text(
                    "${loaded.summary.status.replaceFirstChar { it.uppercase() }} by ${loaded.reviewedBy ?: "?"}" +
                        (loaded.reviewedAtEpochMs?.let { " ${relativeTime(it)}" } ?: "") +
                        (loaded.rejectReason?.let { ": $it" } ?: "."),
                )
            }
        }
    }
}

@Composable
private fun Checks(title: String, checks: List<LabelCheckDto>) {
    if (checks.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("$title: all pass.") }
        return
    }
    P { Text("$title:") }
    Ul { checks.forEach { Li { Span(attrs = { classes("err") }) { Text(ReviewForm.describe(it)) } } } }
}

/** One photo, fetched with the token into a blob URL that is revoked when the panel goes. */
@Composable
private fun Photo(ctx: AdminCtx, id: String, kind: String, alt: String) {
    var url by remember(id, kind) { mutableStateOf<String?>(null) }
    var failed by remember(id, kind) { mutableStateOf(false) }

    LaunchedEffect(id, kind) {
        Catching { ctx.api.submissionPhoto(id, kind) }
            .onSuccess { bytes ->
                val blob = Blob(arrayOf(bytes.unsafeCast<Int8Array>()), BlobPropertyBag(type = "image/jpeg"))
                url = URL.createObjectURL(blob)
            }
            .onFailure { failed = true }
    }
    DisposableEffect(url) {
        val held = url
        onDispose { held?.let { URL.revokeObjectURL(it) } }
    }

    val src = url
    when {
        src != null -> Img(src = src, alt = alt, attrs = { attr("title", alt) })
        failed -> P(attrs = { classes("err") }) { Text("Couldn't load the $kind photo.") }
        else -> P(attrs = { classes("muted") }) { Text("Loading the $kind photo…") }
    }
}

@Composable
private fun BarcodeLinks(ctx: AdminCtx, links: List<BarcodeLinkDto>?, onDecided: () -> Unit) {
    H3 { Text("Suggested barcodes") }
    P(attrs = { classes("muted") }) {
        Text("A barcode nobody had, followed within ten minutes by logging this food from search. Counted across people; nothing else is kept.")
    }
    when {
        links == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        links.isEmpty() -> P(attrs = { classes("muted") }) { Text("No suggestions waiting.") }
        else -> Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Barcode") }
                    Th { Text("Food") }
                    Th { Text("Times") }
                    Th { Text("Last") }
                    Th { Text("") }
                }
            }
            Tbody {
                links.forEach { link ->
                    Tr {
                        Td { Span(attrs = { classes("mono") }) { Text(link.barcode) } }
                        Td {
                            Div { Text(link.foodName) }
                            Span(attrs = { classes("muted") }) {
                                Text(listOfNotNull(link.foodBrand, link.foodBarcode?.let { "has $it" }).joinToString(" · "))
                            }
                        }
                        Td { Text(link.pairings.toString()) }
                        Td { Text(relativeTime(link.lastAtEpochMs)) }
                        Td {
                            Button(attrs = {
                                classes("primary")
                                onClick {
                                    ctx.confirmWrite(
                                        title = "Attach the barcode",
                                        flagPath = null,
                                        before = "${link.foodName}: ${link.foodBarcode ?: "no barcode"}",
                                        after = "${link.foodName}: ${link.gtin}",
                                        success = "Attached. Scanning ${link.barcode} finds ${link.foodName} now.",
                                    ) {
                                        ctx.api.attachBarcodeLink(link.gtin, link.foodId)
                                        onDecided()
                                    }
                                }
                            }) { Text("Attach") }
                            Button(attrs = {
                                onClick {
                                    ctx.confirmWrite(
                                        title = "Dismiss the suggestion",
                                        flagPath = null,
                                        before = "${link.barcode} → ${link.foodName}",
                                        after = "dismissed",
                                        success = "Dismissed.",
                                    ) {
                                        ctx.api.dismissBarcodeLink(link.gtin, link.foodId)
                                        onDecided()
                                    }
                                }
                            }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}

/** The fields a confirm sheet shows: name, serving and the four that approval needs. */
internal fun describeFacts(facts: LabelFactsDto): String = buildList {
    add(facts.name ?: "(no name)")
    facts.brand?.let(::add)
    facts.barcode?.let { add("barcode $it") }
    add("serving ${ReviewForm.text(facts, "serving_size") ?: "?"} ${facts.servingUnit.orEmpty()}")
    listOf("energy_kcal", "protein_g", "carbs_g", "fat_g").forEach { key ->
        add("${ReviewForm.labelOf(key)} ${ReviewForm.text(facts, key) ?: "?"}")
    }
}.joinToString(" · ")

/** `#review/<id>` in the address: Grafana's review board links straight to one submission. */
internal fun reviewIdFromHash(): String? =
    window.location.hash.removePrefix("#").takeIf { it.startsWith("review/") }?.removePrefix("review/")?.ifEmpty { null }
