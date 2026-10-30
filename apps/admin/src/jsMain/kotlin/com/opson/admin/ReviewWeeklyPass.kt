package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import kotlin.js.Date
import kotlin.js.Promise

/** How many pending foods one export carries: the server's page limit. */
private const val EXPORT_LIMIT = 200

/**
 * The weekly pass's shortcuts above the pending queue (docs/practices/food-review-runbook.md):
 * approve every food whose phone reading passed every check, export the queue for an offline
 * pre-check with an AI tool of the owner's choosing, and import its decisions back for a look
 * before anything is applied.
 */
@Composable
internal fun WeeklyPass(ctx: AdminCtx, pending: List<SubmissionSummaryDto>, onChanged: () -> Unit) {
    val clean = pending.filter(ReviewBundle::eligible)
    var bulkReport by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importRows by remember { mutableStateOf<List<ImportRow>?>(null) }
    var ticked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var importResults by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    fun bulkApprove() {
        val chosen = clean
        ctx.confirmWrite(
            title = "Approve ${chosen.size} that passed every check",
            flagPath = null,
            before = chosen.joinToString("; ") { listOfNotNull(it.name ?: "(no name)", it.brand).joinToString(", ") },
            after = "Approved for everyone, one at a time",
            success = "Bulk approve finished.",
            warning = if (ctx.isProd) "Everyone who searches or scans these products will see their numbers." else null,
        ) {
            val failed = mutableListOf<String>()
            var approved = 0
            chosen.forEach { row ->
                Catching { ctx.api.approveSubmission(row.id, null) }
                    .onSuccess { approved++ }
                    .onFailure { failed += "${row.name ?: "(no name)"}: ${it.message ?: "failed"}" }
            }
            bulkReport = "Approved $approved." + if (failed.isEmpty()) "" else " ${failed.size} could not be approved: ${failed.joinToString("; ")}"
            onChanged()
        }
    }

    fun export() {
        exporting = true
        ctx.scope.launch {
            Catching {
                val list = ctx.api.listSubmissions("pending", EXPORT_LIMIT)
                val items = list.submissions.map { summary ->
                    ReviewBundle.item(ctx.api.baseUrl, ctx.api.submission(summary.id), isoTime(summary.createdAtEpochMs))
                }
                val now = isoTime(Date.now().toLong())
                downloadText(ReviewBundle.fileName(ctx.envName, now), ReviewBundle.encode(ReviewBundle.bundle(ctx.envName, now, items)), "application/json")
                val partial = if (list.total > items.size) ", the oldest ${items.size} of ${list.total}" else ""
                ctx.setStatus(Status(true, "Exported ${items.size} pending food(s)$partial. Photos need the token: run scripts/review_bundle.py photos."))
            }.onFailure { ctx.setStatus(Status(false, it.message ?: "Export failed")) }
            exporting = false
        }
    }

    fun readImport(text: String) {
        importing = true
        ctx.scope.launch {
            Catching {
                val parsed = ReviewBundle.parse(text)
                when {
                    parsed.problem != null -> ctx.setStatus(Status(false, parsed.problem))
                    parsed.decisions.isEmpty() -> ctx.setStatus(Status(false, "No decisions in that file yet."))
                    else -> {
                        val details = parsed.decisions.map { it.id }.filter { it.isNotBlank() }.distinct().associateWith { id ->
                            Catching { ctx.api.submission(id) }.getOrElse { error ->
                                // An id this server doesn't know is a row problem, not a failed import.
                                val code = (error as? AdminApiException)?.status?.value
                                if (code == 404 || code == 400) null else throw error
                            }
                        }
                        val rows = ReviewBundle.plan(
                            decisions = parsed.decisions,
                            statusOf = { details[it]?.summary?.status },
                            nameOf = { details[it]?.summary?.name },
                            baseOf = { id -> details[id]?.let { it.reviewed ?: it.submitted } },
                        )
                        importRows = rows
                        importResults = emptyMap()
                        ticked = rows.indices.filter { rows[it].applicable }.toSet()
                    }
                }
            }.onFailure { ctx.setStatus(Status(false, it.message ?: "Import failed")) }
            importing = false
        }
    }

    fun apply(rows: List<ImportRow>) {
        val chosen = rows.withIndex().filter { it.index in ticked && it.value.applicable }
        if (chosen.isEmpty()) return
        val approves = chosen.count { it.value.decision.action == "approve" }
        val rejects = chosen.count { it.value.decision.action == "reject" }
        val trustEnding = chosen.any { it.value.decision.code in ReviewForm.TRUST_ENDING_CODES }
        ctx.confirmWrite(
            title = "Apply the imported decisions",
            flagPath = null,
            before = "${chosen.size} pending food(s)",
            after = "$approves approved for everyone, $rejects rejected",
            success = "Imported decisions applied. Each row says how it went.",
            warning = listOfNotNull(
                if (ctx.isProd) "Approved foods are seen by everyone who searches or scans them." else null,
                if (trustEnding) "Fake data or copied data ends the submitter's chance of Trusted, for good." else null,
            ).joinToString(" ").ifEmpty { null },
        ) {
            val results = mutableMapOf<Int, String>()
            chosen.forEach { (index, row) ->
                val decision = row.decision
                val outcome = Catching {
                    when (decision.action) {
                        "approve" -> ctx.api.approveSubmission(decision.id, row.food)
                        "reject" -> ctx.api.rejectSubmission(decision.id, checkNotNull(decision.reason), decision.code)
                        else -> Unit
                    }
                }
                results[index] = outcome.fold(
                    onSuccess = { if (decision.action == "approve") "Approved" else "Rejected" },
                    onFailure = { "Failed: ${it.message ?: "unknown"}" },
                )
            }
            importResults = results
            onChanged()
        }
    }

    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            Button(attrs = {
                classes("primary")
                if (clean.isEmpty()) attr("disabled", "true")
                attr("title", "Pending foods with photos, every check passing and nothing about the numbers in their routing reasons")
                onClick { bulkApprove() }
            }) { Text("Approve ${clean.size} that passed every check") }
            Span(attrs = { classes("spacer") })
            Button(attrs = {
                if (exporting) attr("disabled", "true")
                onClick { export() }
            }) { Text(if (exporting) "Exporting…" else "Export pending (JSON)") }
            Label {
                Text(if (importing) "Reading…" else "Import decisions")
                Input(InputType.File) {
                    attr("accept", "application/json,.json")
                    onChange { event ->
                        val input = event.target
                        val file = input.files?.item(0) ?: return@onChange
                        ctx.scope.launch {
                            val text = Catching { file.asDynamic().text().unsafeCast<Promise<String>>().await() }
                                .getOrElse {
                                    ctx.setStatus(Status(false, "Couldn't read that file."))
                                    return@launch
                                }
                            input.value = ""
                            readImport(text)
                        }
                    }
                }
            }
        }
        Span(attrs = { classes("muted", "hint") }) {
            Text(
                "Export, pre-check offline with your own AI tool (scripts/prompts/weekly-food-review.md), then import. " +
                    "Nothing is applied until you confirm.",
            )
        }
        bulkReport?.let { P { Text(it) } }
    }

    importRows?.let { rows ->
        ImportPreview(
            rows = rows,
            ticked = ticked,
            results = importResults,
            onTick = { index -> ticked = if (index in ticked) ticked - index else ticked + index },
            onApply = { apply(rows) },
            onDiscard = {
                importRows = null
                importResults = emptyMap()
            },
        )
    }
}

@Composable
private fun ImportPreview(
    rows: List<ImportRow>,
    ticked: Set<Int>,
    results: Map<Int, String>,
    onTick: (Int) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    val count = rows.indices.count { it in ticked && rows[it].applicable }
    Div(attrs = { classes("panel") }) {
        H3 { Text("Imported decisions") }
        P(attrs = { classes("muted") }) {
            Text("${rows.size} decision(s), ${rows.count { it.problem != null }} with a problem. Untick anything you don't agree with.")
        }
        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("") }
                    Th { Text("Food") }
                    Th { Text("Decision") }
                    Th { Text("Changes") }
                    Th { Text("Note") }
                    Th { Text("Result") }
                }
            }
            Tbody {
                rows.forEachIndexed { index, row ->
                    Tr {
                        Td {
                            if (row.applicable) {
                                CheckboxInput(index in ticked) { onInput { onTick(index) } }
                            }
                        }
                        Td {
                            Div { Text(row.name ?: "(no name)") }
                            Span(attrs = { classes("muted", "mono", "hint") }) { Text(row.decision.id.ifBlank { "no id" }) }
                        }
                        Td {
                            Div { Text(row.decision.action) }
                            row.decision.reason?.let { Div(attrs = { classes("hint") }) { Text(it) } }
                            row.decision.code?.let {
                                Span(attrs = { classes("chip") }) { Text(ReviewForm.rejectCodeLabel(it)) }
                            }
                        }
                        Td {
                            if (row.changes.isEmpty()) {
                                Span(attrs = { classes("muted") }) { Text("none") }
                            } else {
                                row.changes.forEach { Div(attrs = { classes("hint") }) { Text(it) } }
                            }
                        }
                        Td { Span(attrs = { classes("hint") }) { Text(row.decision.note.orEmpty()) } }
                        Td {
                            row.problem?.let { Span(attrs = { classes("err") }) { Text(it) } }
                            results[index]?.let { Span(attrs = { classes(if (it.startsWith("Failed")) "err" else "muted") }) { Text(it) } }
                        }
                    }
                }
            }
        }
        Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
            Button(attrs = {
                classes("primary")
                if (count == 0) attr("disabled", "true")
                onClick { onApply() }
            }) { Text("Apply $count") }
            Button(attrs = { onClick { onDiscard() } }) { Text("Discard") }
        }
    }
}
