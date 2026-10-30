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
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
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

/** The queue tab that lists auto-approvals waiting for audit rather than a status. */
private const val AUDITS = "audits"

/** How many routing reasons a queue row shows before "and N more". */
private const val REASONS_IN_ROW = 2

/**
 * The food review queue (`/v1/admin/food-submissions`, `/v1/admin/barcode-links`): foods people
 * added from photos of a pack, and barcodes suggested by a miss followed by a search. A
 * submission opens with its photos side by side, how the server routed it and what the phone read,
 * then every field as the model read it, as the phone drafted it, as the person sent it, and as it
 * stands, with the checks the server ran. The reviewer can correct any field, approve (the food
 * joins search and barcode lookup for everyone) or reject with a reason and a code. The Audits tab
 * lists auto-approvals picked for a person to pass or fail.
 *
 * Below them, the database cleanup on foods already in the catalog: Builders' verifications that
 * need a person ([FoodVerificationsView]), reports ([FoodReportsView]: "Report a problem" from any
 * food's detail, and the server's own energy check), and how many each reviewer decided
 * ([ReviewerCountsView]).
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
        Catching { list = if (status == AUDITS) ctx.api.listAudits() else ctx.api.listSubmissions(status) }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load the queue")) }
        Catching { links = ctx.api.listBarcodeLinks() }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load barcode links")) }
    }

    val what = if (status == AUDITS) "waiting for audit" else status
    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            listOf("pending", "approved", "rejected", AUDITS).forEach { s ->
                Button(attrs = {
                    if (s == status) classes("primary")
                    onClick { status = s; selected = null }
                }) { Text(s.replaceFirstChar { it.uppercase() }) }
            }
            Span(attrs = { classes("spacer") })
            list?.let { Span(attrs = { classes("muted") }) { Text("${it.total} $what") } }
            Button(attrs = { onClick { refresh++ } }) { Text("Reload") }
        }
        Span(attrs = { classes("muted", "hint") }) {
            Text(
                if (status == AUDITS) {
                    "Auto-approvals picked for a person to check: every one of a newly Trusted Builder's first, then some at random. A fail puts the food back to pending."
                } else {
                    "Photos are shown only here, fetched with your token. They are deleted 30 days after the decision."
                },
            )
        }
    }

    val current = list
    when {
        current == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        current.submissions.isEmpty() -> P(attrs = { classes("muted") }) { Text("Nothing $what.") }
        else -> SubmissionTable(current.submissions, selected) { selected = it }
    }

    selected?.let { id ->
        SubmissionPanel(ctx, id, onDecided = { selected = null; refresh++ })
    }

    // Database cleanup: Builders' verifications and reports both fix foods already in the catalog.
    FoodVerificationsView(ctx, refresh, onDecided = { refresh++ })
    FoodReportsView(ctx, refresh)
    ReviewerCountsView(ctx, refresh)

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
                Th { Text("Routing") }
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
                    Td { RoutingCell(row) }
                }
            }
        }
    }
}

/** Where routing sent a row and why: the lane and confidence, the first two reasons, and a badge when it would auto-approve. */
@Composable
private fun RoutingCell(row: SubmissionSummaryDto) {
    val lane = ReviewForm.laneLine(row)
    if (lane == null) {
        Span(attrs = { classes("muted", "hint") }) { Text("before routing") }
        return
    }
    Div {
        Span(attrs = { classes("muted", "hint") }) { Text(lane) }
        if (row.wouldAutoApprove) {
            Text(" ")
            Span(attrs = { classes("chip", "ok") }) { Text(ReviewForm.autoApproveBadge(row.reasons)) }
        }
    }
    val shown = row.reasons.filterNot { it.startsWith("mode:") }.take(REASONS_IN_ROW)
    shown.forEach { Div(attrs = { classes("hint") }) { Text(ReviewForm.reasonLabel(it)) } }
    val more = row.reasons.count { !it.startsWith("mode:") } - shown.size
    if (more > 0) Div(attrs = { classes("muted", "hint") }) { Text("and $more more") }
}

@Composable
private fun SubmissionPanel(ctx: AdminCtx, id: String, onDecided: () -> Unit) {
    var detail by remember(id) { mutableStateOf<SubmissionDetailDto?>(null) }
    var edits by remember(id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unit by remember(id) { mutableStateOf<String?>(null) }
    var reason by remember(id) { mutableStateOf("") }
    var code by remember(id) { mutableStateOf("") }
    var auditNote by remember(id) { mutableStateOf("") }

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
                Photo(ctx, id, "ingredients", "Ingredients", optional = true)
            }
        } else {
            P(attrs = { classes("muted") }) { Text("The photos have been deleted (30 days after the decision).") }
        }

        loaded.routing?.let { RoutingFacts(it, loaded.summary) }
        loaded.capture?.let { CaptureFacts(it) }

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

        val hasDraft = loaded.capture?.draft != null
        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Field") }
                    Th { Text("Model read") }
                    if (hasDraft) Th { Text("Phone draft") }
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
                        if (hasDraft) Td { Text(row.draft ?: "") }
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
                Select(attrs = {
                    onChange { code = it.value.orEmpty() }
                    attr("title", "What kind of problem, for the submitter's record")
                }) {
                    Option("", attrs = { if (code.isEmpty()) selected() }) { Text("No code") }
                    ReviewForm.REJECT_CODES.forEach { (wire, label) ->
                        Option(wire, attrs = { if (code == wire) selected() }) { Text(label) }
                    }
                }
                Button(attrs = {
                    classes("danger")
                    if (reason.isBlank()) attr("disabled", "true")
                    onClick {
                        val chosen = code.ifEmpty { null }
                        val label = chosen?.let { " (${ReviewForm.rejectCodeLabel(it)})" }.orEmpty()
                        ctx.confirmWrite(
                            title = "Reject",
                            flagPath = null,
                            before = loaded.summary.name ?: "(no name)",
                            after = "Rejected$label: ${reason.trim()}",
                            success = "Rejected.",
                            warning = chosen?.takeIf { it in ReviewForm.TRUST_ENDING_CODES }?.let {
                                "${ReviewForm.rejectCodeLabel(it)} ends the submitter's chance of Trusted, for good. Continue?"
                            },
                        ) {
                            ctx.api.rejectSubmission(id, reason.trim(), chosen)
                            onDecided()
                        }
                    }
                }) { Text("Reject") }
            }
            Span(attrs = { classes(if (code in ReviewForm.TRUST_ENDING_CODES) "err" else "muted", "hint") }) {
                Text("The code goes on the submitter's record. Fake data or copied data ends their chance of Trusted.")
            }
        } else {
            P(attrs = { classes("muted") }) {
                val codeLabel = loaded.rejectCode?.let { " (${ReviewForm.rejectCodeLabel(it)})" }.orEmpty()
                Text(
                    "${loaded.summary.status.replaceFirstChar { it.uppercase() }} by ${loaded.reviewedBy ?: "?"}" +
                        (loaded.reviewedAtEpochMs?.let { " ${relativeTime(it)}" } ?: "") +
                        codeLabel +
                        (loaded.rejectReason?.let { ": $it" } ?: "."),
                )
            }
        }

        loaded.summary.audit?.let { audit ->
            AuditBlock(
                audit = audit,
                note = loaded.auditNote,
                draft = auditNote,
                onDraft = { auditNote = it },
                onPass = {
                    ctx.confirmWrite(
                        title = "Pass the audit",
                        flagPath = null,
                        before = "${loaded.summary.name ?: "(no name)"}: approved, audit pending",
                        after = "Audit passed" + (auditNote.trim().ifEmpty { null }?.let { ": $it" } ?: ""),
                        success = "Audit passed.",
                    ) {
                        ctx.api.auditSubmission(id, passed = true, note = auditNote.trim().ifEmpty { null })
                        onDecided()
                    }
                },
                onFail = {
                    ctx.confirmWrite(
                        title = "Fail the audit",
                        flagPath = null,
                        before = "${loaded.summary.name ?: "(no name)"}: approved, audit pending",
                        after = "Back to pending, and the Builder's record takes the miss: ${auditNote.trim()}",
                        success = "Audit failed. The food is back to pending.",
                    ) {
                        ctx.api.auditSubmission(id, passed = false, note = auditNote.trim())
                        onDecided()
                    }
                },
            )
        }
    }
}

/** How routing judged it: the lane, the trust and mode behind it, the grounding and the second read. */
@Composable
private fun RoutingFacts(routing: RoutingDto, summary: SubmissionSummaryDto) {
    P { Text("Routing:") }
    Table(attrs = { classes("flags") }) {
        Tbody {
            fact("Lane", routing.lane + if (routing.wouldAutoApprove) ", and the auto lane would approve it" else "")
            fact("Trust", routing.trust)
            fact("Mode", routing.mode + if (routing.mode == "shadow") ": records what the lane would do, a person still decides" else "")
            fact("Confidence", routing.confidence?.let { "${ReviewForm.percent(it)}%" } ?: "none (the phone sent no score)")
            fact("Reasons", routing.reasons.joinToString("; ") { ReviewForm.reasonLabel(it) }.ifEmpty { "none" })
            fact("Grounding", routing.grounding?.let(ReviewForm::groundingLine) ?: "not checked (no text from the phone)")
            fact("Second read", routing.cloud?.let(ReviewForm::cloudLine) ?: "none")
            if (routing.audit || summary.audit != null) fact("Audit", summary.audit ?: "picked")
        }
    }
}

/** What the phone read off the label: the engine, where the photo came from, its score, and the lines. */
@Composable
private fun CaptureFacts(capture: CaptureDto) {
    var showLines by remember(capture) { mutableStateOf(false) }
    P { Text("The phone's reading:") }
    Table(attrs = { classes("flags") }) {
        Tbody {
            fact("Engine", capture.engine ?: "unknown")
            fact(
                "Photo from",
                when (capture.source) {
                    "camera" -> "the camera"
                    "library" -> "the photo library"
                    else -> "unknown"
                },
            )
            fact("Score", capture.score?.let { "${ReviewForm.percent(it)}%" } ?: "none")
        }
    }
    if (capture.lines.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("No lines of text were sent.") }
        return
    }
    Div(attrs = { classes("row"); style { property("margin-top", "6px") } }) {
        Button(attrs = { onClick { showLines = !showLines } }) {
            Text(if (showLines) "Hide the text" else "Show the ${capture.lines.size} lines it read")
        }
    }
    if (showLines) {
        Pre {
            Text(
                capture.lines.joinToString("\n") { line ->
                    if (line.confidence < 1.0) "${line.text}  (${ReviewForm.percent(line.confidence)}%)" else line.text
                },
            )
        }
    }
}

/** An auto-approval picked for audit: pass or fail it while pending, the outcome and note after. */
@Composable
private fun AuditBlock(
    audit: String,
    note: String?,
    draft: String,
    onDraft: (String) -> Unit,
    onPass: () -> Unit,
    onFail: () -> Unit,
) {
    if (audit != "pending") {
        P(attrs = { classes(if (audit == "failed") "err" else "muted") }) {
            Text("Audit $audit" + (note?.let { ": $it" } ?: "."))
        }
        return
    }
    P { Text("Picked for audit. Check the photos against the food as it stands, then pass or fail it.") }
    Div(attrs = { classes("row") }) {
        Button(attrs = {
            classes("primary")
            onClick { onPass() }
        }) { Text("Pass") }
        TextInput(draft) {
            onInput { onDraft(it.value) }
            placeholder("what was wrong, in plain words (the Builder is told)")
            style { property("min-width", "340px") }
        }
        Button(attrs = {
            classes("danger")
            if (draft.isBlank()) attr("disabled", "true")
            onClick { onFail() }
        }) { Text("Fail") }
    }
    note?.let { P(attrs = { classes("muted") }) { Text("Note: $it") } }
}

@Composable
private fun fact(label: String, value: String) {
    Tr {
        Td { Span(attrs = { classes("muted") }) { Text(label) } }
        Td { Text(value) }
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

/**
 * One photo, fetched with the token into a blob URL that is revoked when the panel goes. An
 * [optional] one (the ingredients, which not every submission has) shows nothing when the
 * server has none.
 */
@Composable
private fun Photo(ctx: AdminCtx, id: String, kind: String, alt: String, optional: Boolean = false) {
    var url by remember(id, kind) { mutableStateOf<String?>(null) }
    var failed by remember(id, kind) { mutableStateOf(false) }
    var absent by remember(id, kind) { mutableStateOf(false) }

    LaunchedEffect(id, kind) {
        Catching { ctx.api.submissionPhoto(id, kind) }
            .onSuccess { bytes ->
                val blob = Blob(arrayOf(bytes.unsafeCast<Int8Array>()), BlobPropertyBag(type = "image/jpeg"))
                url = URL.createObjectURL(blob)
            }
            .onFailure { error ->
                if (optional && (error as? AdminApiException)?.status?.value == 404) absent = true else failed = true
            }
    }
    DisposableEffect(url) {
        val held = url
        onDispose { held?.let { URL.revokeObjectURL(it) } }
    }

    val src = url
    when {
        src != null -> Img(src = src, alt = alt, attrs = { attr("title", alt) })
        absent -> Unit
        failed -> P(attrs = { classes("err") }) { Text("Couldn't load the $kind photo.") }
        optional -> Unit
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
