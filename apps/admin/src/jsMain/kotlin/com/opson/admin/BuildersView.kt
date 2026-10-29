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
 * Applications to Builders (`/v1/admin/builders/applications`): the queue, oldest first, each
 * with the account behind it, and Accept or Decline. Accept makes them a Builder (the membership
 * and the builder role, which brings Pro while they're active); Decline takes an optional note
 * the applicant sees, and they can apply again in 30 days. Below, the latest decisions, with
 * Remove for a Builder who turns out badly. [onPending] hears the queue's size for the tab badge;
 * [onOpenUser] opens an account under Users, for its Access, Builder and Moderation.
 */
@Composable
internal fun BuildersView(ctx: AdminCtx, onPending: (Long) -> Unit, onOpenUser: (String) -> Unit) {
    var queue by remember { mutableStateOf<BuilderApplicationListDto?>(null) }
    var decided by remember { mutableStateOf<List<BuilderApplicationDto>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        failed = false
        Catching {
            val pending = ctx.api.builderApplications("pending")
            queue = pending
            onPending(pending.pending)
            decided = ctx.api.builderApplications("decided").applications
        }.onFailure {
            failed = true
            ctx.setStatus(Status(false, "Builders: ${it.message ?: "couldn't load"}"))
        }
    }

    val current = queue
    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            Span(attrs = { classes("flag-path") }) {
                Text(
                    when {
                        current != null -> pendingHeadline(current.pending)
                        failed -> "Couldn't load applications. This server may predate them."
                        else -> "Loading…"
                    },
                )
            }
            Div(attrs = { classes("spacer") }) {}
            Button(attrs = { onClick { reloadKey++ } }) { Text("Refresh") }
        }
        P(attrs = { classes("muted") }) {
            Text("Accept makes them a Builder: the builder role, and Pro while they're active. Decline can carry a note they'll see; they can apply again in 30 days.")
        }
    }

    if (current == null) return
    current.applications.forEach { application ->
        ApplicationCard(ctx, application, onDecided = { reloadKey++ }, onOpenUser = onOpenUser)
    }

    H3 { Text("Recent decisions") }
    if (decided.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("Nothing decided yet.") }
    } else {
        decided.forEach { DecidedRow(ctx, it, onChanged = { reloadKey++ }) }
    }
}

@Composable
private fun ApplicationCard(ctx: AdminCtx, application: BuilderApplicationDto, onDecided: () -> Unit, onOpenUser: (String) -> Unit) {
    var note by remember(application.id) { mutableStateOf("") }
    val who = applicantName(application)

    fun decide(decision: String) {
        val trimmed = note.trim().ifEmpty { null }
        val accept = decision == "accept"
        ctx.confirmWrite(
            title = if (accept) "Accept into Builders" else "Decline the application",
            flagPath = null,
            before = "$who: pending",
            after = if (accept) "a Builder, with the builder role" else "declined" + (trimmed?.let { ": $it" }.orEmpty()),
            success = if (accept) "Accepted. $who is a Builder." else "Declined.",
            warning = if (accept) null else "They'll see this the next time they open Builders, with the note if you wrote one. Continue?",
        ) {
            ctx.api.decideBuilderApplication(application.id, decision, trimmed)
            note = ""
            onDecided()
        }
    }

    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            Span(attrs = { classes("flag-path") }) { Text(who) }
            Span(attrs = { classes("muted") }) { Text("applied ${relativeTime(application.submittedAtEpochMs)}") }
            Div(attrs = { classes("spacer") }) {}
            Button(attrs = { onClick { onOpenUser(application.userId) } }) { Text("Open account") }
        }
        application.motivation?.let { words ->
            P { Text("“$words”") }
        }
        Table(attrs = { classes("flags") }) {
            Tbody {
                applicantFacts(application).forEach { (label, value) ->
                    Tr {
                        Td { Span(attrs = { classes("muted") }) { Text(label) } }
                        Td { Text(value) }
                    }
                }
            }
        }
        Div(attrs = { classes("row"); style { property("margin-top", "10px") } }) {
            TextInput(note) {
                onInput { note = it.value }
                placeholder("note for them (optional, they see it)")
                style { property("min-width", "320px") }
            }
            Button(attrs = { classes("primary"); onClick { decide("accept") } }) { Text("Accept") }
            Button(attrs = { classes("danger"); onClick { decide("decline") } }) { Text("Decline") }
        }
    }
}

@Composable
private fun DecidedRow(ctx: AdminCtx, application: BuilderApplicationDto, onChanged: () -> Unit) {
    var reason by remember(application.id) { mutableStateOf("") }
    val who = applicantName(application)
    Div(attrs = { classes("row") }) {
        Span { Text(who) }
        Span(attrs = { classes("muted") }) { Text(decisionLine(application)) }
        Div(attrs = { classes("spacer") }) {}
        if (application.status == "accepted" && application.account.membership == "active") {
            TextInput(reason) {
                onInput { reason = it.value }
                placeholder("why they're removed (audited)")
            }
            Button(attrs = {
                classes("danger")
                if (reason.isBlank()) attr("disabled", "true")
                onClick {
                    ctx.confirmWrite(
                        title = "Remove from the Builder program",
                        flagPath = null,
                        before = "$who: a Builder",
                        after = "removed: ${reason.trim()}",
                        success = "Removed from the Builder program.",
                        warning = "This takes the builder role and the Pro it brings, and they can't apply again. Only Restore under Users undoes it. Continue?",
                    ) {
                        ctx.api.builderAction(application.userId, "remove", reason.trim())
                        reason = ""
                        onChanged()
                    }
                }
            }) { Text("Remove") }
        }
    }
}

// ---------- pure, tested in BuildersViewTest ----------

internal fun pendingHeadline(pending: Long): String = when (pending) {
    0L -> "No applications waiting"
    1L -> "1 application waiting"
    else -> "$pending applications waiting"
}

/** The tab's label, with the queue's size once there is one. */
internal fun buildersTabLabel(pending: Long?): String = if (pending == null || pending == 0L) "Builders" else "Builders ($pending)"

internal fun applicantName(application: BuilderApplicationDto): String =
    application.account.displayName?.takeIf { it.isNotBlank() }
        ?: application.account.email
        ?: application.userId.take(8)

/** What the owner reads before deciding, in the order they'd weigh it. */
internal fun applicantFacts(
    application: BuilderApplicationDto,
    now: Long = kotlin.js.Date.now().toLong(),
    date: (Long) -> String = ::shortDate,
): List<Pair<String, String>> {
    val a = application.account
    return buildList {
        add("Account" to listOfNotNull(
            a.email,
            "guest".takeIf { a.isGuest },
            a.accountCreatedAtEpochMs?.let { "${daysBetween(it, now)} days old (since ${date(it)})" },
        ).joinToString(", ").ifEmpty { "unknown" })
        add("Logging" to if (a.entries == 0L) "nothing logged" else "${a.entries} foods over ${a.daysLogged} days" + (a.lastLogDay?.let { ", last on $it" }.orEmpty()))
        add("Foods sent" to "${a.submissionsApproved} approved, ${a.submissionsRejected} rejected, ${a.submissionsPending} waiting")
        add("Reports" to "${a.reportsAccepted} accepted, ${a.reportsRejected} turned down, ${a.reportsOpen} open")
        add("Checks" to a.checks.toString())
        add(
            "Moderation" to when {
                a.moderationActions == 0 -> "none"
                a.moderationActive > 0 -> "${a.moderationActive} in force of ${a.moderationActions}" + (a.lastModerationReason?.let { ", last for $it" }.orEmpty())
                else -> "${a.moderationActions}, all lifted or ended" + (a.lastModerationReason?.let { ", last for $it" }.orEmpty())
            },
        )
        add(
            "Before" to listOfNotNull(
                if (a.earlierApplications == 0) "first application" else "${a.earlierApplications} earlier, ${a.earlierDeclines} declined",
                a.membership?.let { "was a Builder ($it)" },
            ).joinToString(", "),
        )
        add("Shops" to (shoppingLabel(application.shopping) ?: "not asked (an older app)"))
        add("Labels" to (regionLabel(application.region) ?: "not asked (an older app)"))
        add("Photos" to if (application.photoRetention) "may keep label photos for 2 years" else "30 days, as usual")
        add("From" to (application.via ?: "unknown"))
    }
}

internal fun decisionLine(application: BuilderApplicationDto, date: (Long) -> String = ::shortDate): String =
    listOfNotNull(
        application.status,
        application.decidedBy?.let { "by $it" },
        application.decidedAtEpochMs?.let { "on ${date(it)}" },
        application.note?.let { "“$it”" },
    ).joinToString(" ")

internal fun shoppingLabel(wire: String?): String? = when (wire) {
    "weekly" -> "packaged food every week"
    "monthly" -> "packaged food a few times a month"
    "rarely" -> "packaged food less often"
    null -> null
    else -> wire
}

internal fun regionLabel(wire: String?): String? = when (wire) {
    "us" -> "United States"
    "ca" -> "Canada"
    "uk" -> "United Kingdom"
    "eu" -> "European Union"
    "au_nz" -> "Australia or New Zealand"
    "other" -> "somewhere else"
    null -> null
    else -> wire
}

private fun daysBetween(from: Long, to: Long): Long = ((to - from) / DAY_MS).coerceAtLeast(0)

private const val DAY_MS = 86_400_000L
