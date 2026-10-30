package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
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

private const val PAGE_SIZE = 50

/**
 * The website waitlist (`/v1/admin/waitlist`): how many addresses, the newest
 * ones, a CSV export, and removal for someone who asked to be taken off.
 * Removal deletes the address and goes through the confirm sheet like every
 * other write in the console.
 */
@Composable
internal fun WaitlistView(ctx: AdminCtx) {
    var page by remember { mutableStateOf<WaitlistPageDto?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0L) }
    var reloadKey by remember { mutableStateOf(0) }
    var removeDraft by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(offset, reloadKey) {
        loadFailed = false
        Catching { page = ctx.api.listWaitlist(PAGE_SIZE, offset) }
            .onFailure {
                loadFailed = true
                ctx.setStatus(Status(false, it.message ?: "Failed to load the waitlist"))
            }
    }

    fun confirmRemove(email: String) {
        ctx.confirmWrite(
            title = "Remove from the waitlist",
            flagPath = null,
            before = email,
            after = "deleted",
            success = "Removed $email from the waitlist.",
        ) {
            ctx.api.removeFromWaitlist(email)
            reloadKey++
        }
    }

    fun export() {
        exporting = true
        ctx.scope.launch {
            Catching {
                val csv = ctx.api.exportWaitlistCsv()
                downloadText("opson-waitlist-${ctx.envName.lowercase()}.csv", csv, "text/csv;charset=utf-8")
                val rows = csv.trimEnd().lines().size - 1
                ctx.setStatus(Status(true, "Exported $rows address(es) from ${ctx.envName}."))
            }.onFailure { ctx.setStatus(Status(false, it.message ?: "Export failed")) }
            exporting = false
        }
    }

    val current = page
    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            Span(attrs = { classes("flag-path") }) {
                Text(
                    when {
                        current != null -> "${current.total} on the waitlist"
                        loadFailed -> "Couldn't load the waitlist. Refresh to try again."
                        else -> "Loading…"
                    },
                )
            }
            Div(attrs = { classes("spacer") }) {}
            Button(attrs = {
                if (exporting) attr("disabled", "true")
                onClick { if (!exporting) export() }
            }) { Text(if (exporting) "Exporting…" else "Export CSV") }
            Button(attrs = { onClick { reloadKey++ } }) { Text("Refresh") }
        }
        Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
            TextInput(removeDraft) {
                onInput { removeDraft = it.value }
                placeholder("address to remove, for a removal request")
                style { property("min-width", "320px") }
            }
            Button(attrs = {
                classes("danger")
                onClick {
                    val email = removeDraft.trim()
                    if (email.isNotEmpty()) {
                        confirmRemove(email)
                        removeDraft = ""
                    }
                }
            }) { Text("Remove") }
        }
    }

    if (current == null) return
    if (current.entries.isEmpty()) {
        P(attrs = { classes("muted") }) { Text(if (offset == 0L) "Nobody has signed up yet." else "No more addresses.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("Email") }
                    Th { Text("Source") }
                    Th { Text("Added") }
                    Th {}
                }
            }
            Tbody {
                current.entries.forEach { entry ->
                    Tr {
                        Td { Span(attrs = { classes("mono") }) { Text(entry.email) } }
                        Td { Span(attrs = { classes("muted") }) { Text(entry.source ?: "—") } }
                        Td {
                            Span(attrs = { attr("title", kotlin.js.Date(entry.createdAtEpochMs.toDouble()).toLocaleString()) }) {
                                Text(relativeTime(entry.createdAtEpochMs))
                            }
                        }
                        Td {
                            Button(attrs = { classes("danger"); onClick { confirmRemove(entry.email) } }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
        if (offset > 0) {
            Button(attrs = { onClick { offset = (offset - PAGE_SIZE).coerceAtLeast(0) } }) { Text("Newer") }
        }
        if (offset + current.entries.size < current.total) {
            Button(attrs = { onClick { offset += PAGE_SIZE } }) { Text("Older") }
        }
        if (current.total > 0) {
            Span(attrs = { classes("muted") }) {
                Text("${offset + 1} to ${offset + current.entries.size} of ${current.total}, newest first")
            }
        }
    }
}
