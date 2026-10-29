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
import org.jetbrains.compose.web.dom.Tr

/**
 * One account in the Builder program (`/v1/admin/users/{id}/builder`), under its Access: where
 * it stands, its trust level and the record behind it, and the owner's levers. Remove takes the
 * builder role (and the Pro it brings) and only an admin can undo it; hold keeps a Builder from
 * becoming Trusted whatever the record says. [onChanged] reloads Access, whose role a remove or
 * restore changes.
 */
@Composable
internal fun BuilderSection(ctx: AdminCtx, userId: String, onChanged: () -> Unit) {
    var builder by remember(userId) { mutableStateOf<AdminBuilderDto?>(null) }
    var failed by remember(userId) { mutableStateOf(false) }

    LaunchedEffect(userId) {
        failed = false
        Catching { builder = ctx.api.builder(userId) }
            .onFailure {
                failed = true
                ctx.setStatus(Status(false, "Builder: ${it.message ?: "couldn't load"}"))
            }
    }

    H3 { Text("Builder") }
    val shown = builder
    when {
        shown != null -> BuilderDetail(ctx, userId, shown, onUpdated = { builder = it; onChanged() })
        failed -> P(attrs = { classes("muted") }) { Text("Couldn't load the Builder record. This server may predate the Builder program.") }
        else -> P(attrs = { classes("muted") }) { Text("Loading…") }
    }
}

@Composable
private fun BuilderDetail(ctx: AdminCtx, userId: String, builder: AdminBuilderDto, onUpdated: (AdminBuilderDto) -> Unit) {
    var reason by remember(userId) { mutableStateOf("") }

    if (builder.me.status == "none") {
        P(attrs = { classes("muted") }) {
            Text("Not a Builder." + ((builder.me.applyBlock ?: builder.me.joinBlock)?.let { " Can't apply now: ${joinBlockLabel(it)}." }.orEmpty()))
        }
        return
    }

    Table(attrs = { classes("flags") }) {
        Tbody {
            builderFacts(builder).forEach { (label, value) ->
                Tr {
                    Td { Span(attrs = { classes("muted") }) { Text(label) } }
                    Td { Text(value) }
                }
            }
        }
    }

    Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
        if (builder.me.status == "removed") {
            Button(attrs = {
                classes("primary")
                onClick {
                    ctx.confirmWrite(
                        title = "Restore to the Builder program",
                        flagPath = null,
                        before = "removed" + (builder.removedReason?.let { ": $it" }.orEmpty()),
                        after = "active, at Builder level",
                        success = "Restored. They are a Builder again.",
                    ) { onUpdated(ctx.api.builderAction(userId, "restore")) }
                }
            }) { Text("Restore") }
        } else {
            TextInput(reason) {
                onInput { reason = it.value }
                placeholder("why they are removed (audited)")
                style { property("min-width", "300px") }
            }
            Button(attrs = {
                classes("danger")
                if (reason.isBlank()) attr("disabled", "true")
                onClick {
                    ctx.confirmWrite(
                        title = "Remove from the Builder program",
                        flagPath = null,
                        before = "${builder.me.status}, ${builder.me.trust}",
                        after = "removed: ${reason.trim()}",
                        success = "Removed from the Builder program.",
                        warning = "This takes the builder role and the Pro it brings, and they can't join again. Only Restore here undoes it. Continue?",
                    ) {
                        onUpdated(ctx.api.builderAction(userId, "remove", reason.trim()))
                        reason = ""
                    }
                }
            }) { Text("Remove") }
        }
        Span(attrs = { classes("spacer") })
        val hold = !builder.trustHold
        Button(attrs = {
            onClick {
                ctx.confirmWrite(
                    title = if (hold) "Hold at Builder" else "Release the hold",
                    flagPath = null,
                    before = if (builder.trustHold) "held at Builder" else "trust follows the record (${builder.me.trust})",
                    after = if (hold) "held at Builder, whatever the record says" else "trust follows the record",
                    success = if (hold) "Held at Builder." else "Hold released. Trust follows the record again.",
                ) { onUpdated(ctx.api.builderAction(userId, if (hold) "hold" else "release")) }
            }
        }) { Text(if (hold) "Hold at Builder" else "Release the hold") }
    }
}

// ---------- pure, tested in AccessViewTest ----------

/** The Builder record as label and value rows, in the order the owner reads them. */
internal fun builderFacts(builder: AdminBuilderDto, date: (Long) -> String = ::shortDate): List<Pair<String, String>> {
    val me = builder.me
    return buildList {
        add(
            "Status" to when (me.status) {
                "removed" -> "removed by ${builder.removedBy ?: "admin"}" + (builder.removedReason?.let { ": $it" }.orEmpty())
                else -> me.status
            },
        )
        me.joinedAtEpochMs?.let { add("Joined" to date(it)) }
        add(
            "Trust" to listOfNotNull(
                me.trust,
                builder.trustedSinceEpochMs?.let { "Trusted since ${date(it)}" },
                "held at Builder".takeIf { builder.trustHold },
            ).joinToString(", "),
        )
        add("Toward Pro" to "${me.accepted30d} of ${me.needed} accepted in the last 30 days")
        add("Pro until" to (me.proUntilEpochMs?.let(date) ?: "not earned"))
        add("Photo retention" to if (me.photoRetention) "yes, label photos kept for the labelled set" else "no")
        add("Reviewed" to "${builder.reviewedTotal} in all")
        add("Agreement, last 50" to (builder.agreementLast50?.let { "${ReviewForm.percent(it)}%" } ?: "nothing reviewed yet"))
        add("Last 30 days" to "${builder.decided30d} decided, ${builder.rejected30d} rejected")
        add(
            "Fake or copied" to if (builder.fakeOrCopied == 0) "none" else "${builder.fakeOrCopied}, so never Trusted",
        )
        add("Audits, 30 days" to "${builder.audits30d}, ${builder.auditsFailed30d} failed")
        add("Auto-approved since Trusted" to builder.autoApprovedSinceTrusted.toString())
    }
}

internal fun joinBlockLabel(code: String): String = when (code) {
    "guest" -> "a guest account"
    "too_new" -> "the account is too new"
    "removed" -> "removed from the program"
    "full" -> "the program is full"
    "pending" -> "an application is waiting in the Builders tab"
    "declined" -> "declined, and waiting to apply again"
    else -> code
}
