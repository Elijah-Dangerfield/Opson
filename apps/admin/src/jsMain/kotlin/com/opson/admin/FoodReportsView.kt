package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * "Report a problem" from any food's detail (`/v1/admin/food-reports`), beside the submissions in
 * the Review area. Open reports are grouped per food, most reported first, then oldest. A food
 * opens with what it is served as now beside every suggestion (per 100 g, as accepting stores
 * it), the label photos and the notes. Accept writes a correction laid over the source's data,
 * which a re-import never undoes and Undo takes back; Reject and Duplicate close the reports and
 * change nothing.
 */
@Composable
internal fun FoodReportsView(ctx: AdminCtx, refresh: Int) {
    var queue by remember { mutableStateOf<FoodReportQueueDto?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(refresh, reload) {
        Catching { queue = ctx.api.listFoodReports() }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load the reports")) }
    }

    H3 { Text("Reports on foods") }
    P(attrs = { classes("muted") }) {
        Text("Someone said a food's data is wrong, or the server's energy check caught a 0 kcal the macros contradict. A report changes nothing until it is accepted here.")
    }
    val current = queue
    when {
        current == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        current.foods.isEmpty() -> P(attrs = { classes("muted") }) { Text("No open reports.") }
        else -> {
            Span(attrs = { classes("muted") }) { Text("${current.openReports} open on ${current.foods.size} foods") }
            Table(attrs = { classes("flags") }) {
                Thead {
                    Tr {
                        Th { Text("Food") }
                        Th { Text("Reports") }
                        Th { Text("Why") }
                        Th { Text("Waiting") }
                    }
                }
                Tbody {
                    current.foods.forEach { group ->
                        Tr(attrs = {
                            if (group.foodId == selected) classes("selected")
                            style { property("cursor", "pointer") }
                            onClick { selected = group.foodId }
                        }) {
                            Td {
                                Div { Text(group.name) }
                                Span(attrs = { classes("muted") }) { Text(listOfNotNull(group.brand, group.source).joinToString(" · ")) }
                            }
                            Td { Text(group.reportCount.toString() + if (group.hasPhoto) " · photo" else "") }
                            Td { group.reasons.forEach { (reason, n) -> Span(attrs = { classes("chip") }) { Text("${ReportDiff.reasonLabel(reason)} $n") } } }
                            Td { Text(relativeTime(group.oldestAtEpochMs)) }
                        }
                    }
                }
            }
            current.foods.firstOrNull { it.foodId == selected }?.let { group ->
                FoodReportPanel(ctx, group, onDecided = { selected = null; reload++ })
            }
        }
    }
}

@Composable
private fun FoodReportPanel(ctx: AdminCtx, group: FoodReportGroupDto, onDecided: () -> Unit) {
    var edits by remember(group.foodId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var reason by remember(group.foodId) { mutableStateOf("") }
    var duplicateOf by remember(group.foodId) { mutableStateOf("") }
    var history by remember(group.foodId) { mutableStateOf<FoodReportFoodDto?>(null) }

    LaunchedEffect(group.foodId) {
        Catching { history = ctx.api.foodReports(group.foodId) }
    }

    val rows = ReportDiff.rows(group)
    val values = ReportDiff.values(rows, edits)
    val ids = group.reports.map { it.id }

    Div(attrs = { classes("panel") }) {
        H3 { Text(group.now?.name ?: group.name) }
        P(attrs = { classes("muted") }) {
            Text(
                listOfNotNull(
                    group.now?.brand,
                    group.now?.source ?: group.source,
                    group.now?.barcode?.let { "barcode $it" },
                    "food ${group.foodId}",
                ).joinToString(" · "),
            )
        }
        if (group.now == null) P(attrs = { classes("err") }) { Text("This food is no longer in the catalog.") }
        group.now?.energyFlags?.takeIf { it.isNotEmpty() }?.let { flags ->
            P(attrs = { classes("err") }) { Text("Energy check: ${flags.joinToString(", ")}. What is served now is an estimate.") }
        }
        group.correction?.let { correction ->
            P { Text("Corrected by ${correction.createdBy} ${relativeTime(correction.createdAtEpochMs)}. Accepting again adds to that correction.") }
        }

        Div(attrs = { classes("photos") }) {
            group.reports.forEach { report ->
                if (report.hasFrontPhoto) PackPhoto(ctx, "Front of the pack") { ctx.api.foodReportPhoto(report.id, "front") }
                if (report.hasPhoto) PackPhoto(ctx, "Label") { ctx.api.foodReportPhoto(report.id, "label") }
            }
        }

        Ul {
            group.reports.forEach { report ->
                Li {
                    report.allReasons.forEach { reason -> Span(attrs = { classes("chip") }) { Text(ReportDiff.reasonLabel(reason)) } }
                    Text(" ${relativeTime(report.createdAtEpochMs)}")
                    report.suggested?.basisLabel?.let { basis ->
                        Span(attrs = { classes("muted") }) {
                            Text(" · read for $basis${report.suggested.basisGrams?.let { " (${ReportDiff.number(it)} g)" }.orEmpty()}")
                        }
                    }
                    report.note?.let { Div(attrs = { classes("muted") }) { Text("“$it”") } }
                }
            }
        }

        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Field") }
                    Th { Text("Now") }
                    Th { Text("Suggested") }
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
                if (values == null) attr("disabled", "true")
                onClick {
                    val accepted = values ?: return@onClick
                    ctx.confirmWrite(
                        title = "Correct this food",
                        flagPath = null,
                        before = rows.joinToString(" · ") { "${it.label} ${it.now ?: "?"}" },
                        after = ReportDiff.describe(rows, accepted),
                        success = "Corrected. Search, scans and the detail serve it now; a re-import keeps it.",
                        warning = if (ctx.isProd) "Everyone who finds this food will see these numbers." else null,
                    ) {
                        ctx.api.acceptFoodReports(group.foodId, ids, accepted)
                        onDecided()
                    }
                }
            }) { Text("Accept") }
            TextInput(reason) {
                onInput { reason = it.value }
                placeholder("why it is rejected (optional)")
                style { property("min-width", "260px") }
            }
            Button(attrs = {
                classes("danger")
                onClick {
                    ctx.confirmWrite(
                        title = "Reject these reports",
                        flagPath = null,
                        before = "${ids.size} open",
                        after = "rejected" + reason.trim().ifEmpty { null }?.let { ": $it" }.orEmpty(),
                        success = "Rejected. The food is unchanged.",
                    ) {
                        ctx.api.rejectFoodReports(ids, reason.trim().ifEmpty { null })
                        onDecided()
                    }
                }
            }) { Text("Reject") }
            TextInput(duplicateOf) {
                onInput { duplicateOf = it.value }
                placeholder("the food it copies (id, optional)")
                style { property("min-width", "260px") }
            }
            Button(attrs = {
                onClick {
                    ctx.confirmWrite(
                        title = "Mark as a duplicate",
                        flagPath = null,
                        before = group.name,
                        after = "duplicate" + duplicateOf.trim().ifEmpty { null }?.let { " of $it" }.orEmpty(),
                        success = "Marked. Search still shows it.",
                    ) {
                        ctx.api.markFoodReportsDuplicate(ids, duplicateOf.trim().ifEmpty { null })
                        onDecided()
                    }
                }
            }) { Text("Duplicate") }
        }

        history?.corrections?.takeIf { it.isNotEmpty() }?.let { corrections ->
            P { Text("Corrections") }
            Ul {
                corrections.forEach { correction ->
                    Li {
                        Text(
                            "${relativeTime(correction.createdAtEpochMs)} by ${correction.createdBy}: ${ReportDiff.describeCorrection(correction)} ",
                        )
                        when {
                            correction.live -> Button(attrs = {
                                onClick {
                                    ctx.confirmWrite(
                                        title = "Undo the correction",
                                        flagPath = null,
                                        before = "corrected by ${correction.createdBy}",
                                        after = "as it was before it",
                                        success = "Undone.",
                                    ) {
                                        ctx.api.revertFoodCorrection(correction.id)
                                        onDecided()
                                    }
                                }
                            }) { Text("Undo") }
                            correction.revertedBy != null -> Span(attrs = { classes("muted") }) { Text(" (undone by ${correction.revertedBy})") }
                            else -> Span(attrs = { classes("muted") }) { Text(" (superseded)") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One photo of a pack, fetched with the token into a blob URL that is revoked when the panel goes.
 * Reports and verifications both have a front and a label.
 */
@Composable
internal fun PackPhoto(ctx: AdminCtx, alt: String, fetch: suspend () -> ByteArray) {
    var url by remember(alt, fetch) { mutableStateOf<String?>(null) }
    var failed by remember(alt, fetch) { mutableStateOf(false) }

    LaunchedEffect(alt, fetch) {
        Catching { fetch() }
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
        failed -> P(attrs = { classes("err") }) { Text("Couldn't load the photo (deleted 30 days after the decision).") }
        else -> P(attrs = { classes("muted") }) { Text("Loading the photo…") }
    }
}
