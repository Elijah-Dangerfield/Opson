package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Label
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

private const val MODERATION_DAY_MS = 86_400_000L

/**
 * Removing a bad actor, under the account in Users: its standing, what it contributed and from
 * how many devices, and the four buttons (Ban, Suspend, Restrict contributions, Lift), plus
 * Roll back contributions. Every write goes through the confirm sheet, is audited on the server
 * with the console's actor, and shows in the trail at the bottom.
 */
@Composable
internal fun UserModerationPanel(ctx: AdminCtx, userId: String) {
    var shown by remember(userId) { mutableStateOf<UserModerationDto?>(null) }
    var failed by remember(userId) { mutableStateOf(false) }
    var reloadKey by remember(userId) { mutableStateOf(0) }

    LaunchedEffect(userId, reloadKey) {
        failed = false
        Catching { shown = ctx.api.moderation.user(userId) }
            .onFailure {
                failed = true
                ctx.setStatus(Status(false, "Moderation: ${it.message ?: "couldn't load"}"))
            }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Moderation") }
        val moderation = shown
        when {
            moderation != null -> ModerationDetail(ctx, moderation) { reloadKey++ }
            failed -> P(attrs = { classes("muted") }) { Text("Couldn't load moderation. This server may predate it.") }
            else -> P(attrs = { classes("muted") }) { Text("Loading…") }
        }
    }
}

@Composable
private fun ModerationDetail(ctx: AdminCtx, moderation: UserModerationDto, reload: () -> Unit) {
    val now = kotlin.js.Date.now().toLong()
    Table(attrs = { classes("flags") }) {
        Tbody {
            fact("Standing", standingSentence(moderation.standing, now))
            fact("Contributions", contributionSentence(moderation.contributions))
            fact("Free Pro programs", if (moderation.freeProAllowed) "allowed" else "barred (account or a device it used)")
            fact("Device checks", moderation.deviceChecks.joinToString().ifEmpty { "install keys only (DeviceCheck and Play Integrity not configured)" })
        }
    }

    ActionForm(ctx, moderation, reload)
    RollbackForm(ctx, moderation.userId, reload)

    H3 { Text("Actions, newest first") }
    if (moderation.actions.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("None. This account is in good standing.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("What") }; Th { Text("Why") }; Th { Text("By") }; Th { Text("Ends") }
                    Th { Text("Status") }; Th { Text("Appeal") }; Th { }
                }
            }
            Tbody { moderation.actions.forEach { ActionRow(ctx, it, now, reload) } }
        }
    }

    H3 { Text("Devices marked") }
    if (moderation.markers.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("None.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead { Tr { Th { Text("Platform") }; Th { Text("Marked") }; Th { Text("Apple / Google bit") }; Th { Text("Status") }; Th { } } }
            Tbody {
                moderation.markers.forEach { marker ->
                    Tr {
                        Td { Text(marker.platform) }
                        Td { Text("${marker.createdBy}, ${relativeTime(marker.createdAtEpochMs)}") }
                        Td { Text(remoteStateLabel(marker.remoteState)) }
                        Td { Text(marker.clearedAtEpochMs?.let { "cleared by ${marker.clearedBy ?: "admin"}" } ?: "in force") }
                        Td {
                            if (marker.clearedAtEpochMs == null) {
                                Button(attrs = {
                                    onClick {
                                        ctx.confirmWrite(
                                            title = "Clear a device marker",
                                            flagPath = null,
                                            before = "device barred from contributing",
                                            after = "cleared",
                                            success = "Marker cleared. A DeviceCheck or recall bit is reset the next time that phone contributes.",
                                        ) {
                                            ctx.api.moderation.clearMarker(marker.id, "cleared in the console")
                                            reload()
                                        }
                                    }
                                }) { Text("Clear") }
                            }
                        }
                    }
                }
            }
        }
    }

    H3 { Text("Trail") }
    if (moderation.events.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("Nothing yet.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead { Tr { Th { Text("When") }; Th { Text("Who") }; Th { Text("What") }; Th { Text("After") } } }
            Tbody {
                moderation.events.forEach { event ->
                    Tr {
                        Td { Text(relativeTime(event.atEpochMs)) }
                        Td { Text(event.actor) }
                        Td { Text(event.action) }
                        Td { Span(attrs = { classes("mono", "muted") }) { Text(event.after.inline()) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionForm(ctx: AdminCtx, moderation: UserModerationDto, reload: () -> Unit) {
    var kind by remember(moderation.userId) { mutableStateOf("restriction") }
    var reason by remember(moderation.userId) { mutableStateOf("fake_data") }
    var note by remember(moderation.userId) { mutableStateOf("") }
    var days by remember(moderation.userId) { mutableStateOf("") }
    var markDevices by remember(moderation.userId) { mutableStateOf(true) }
    var rollback by remember(moderation.userId) { mutableStateOf(false) }

    H3 { Text("Act") }
    Div(attrs = { classes("row") }) {
        Label { Text("Do") }
        MODERATION_KINDS.forEach { (wire, label) ->
            Button(attrs = {
                if (wire == kind) classes("primary")
                onClick { kind = wire }
            }) { Text(label) }
        }
    }
    Div(attrs = { classes("row") }) {
        Label { Text("Reason") }
        moderation.reasons.ifEmpty { DEFAULT_REASONS }.forEach { option ->
            Button(attrs = {
                if (option == reason) classes("primary")
                onClick { reason = option }
            }) { Text(reasonLabel(option)) }
        }
    }
    Div(attrs = { classes("row") }) {
        TextInput(note) { onInput { note = it.value }; placeholder("what you saw (audited, not shown to them)") }
        TextInput(days) { onInput { days = it.value }; placeholder(if (kind == "suspension") "for N days (required)" else "for N days (blank: until lifted)") }
    }
    Div(attrs = { classes("row") }) {
        Label {
            CheckboxInput(markDevices) { onInput { markDevices = !markDevices } }
            Text(" Mark their devices (contributing and free Pro only, never logging)")
        }
        Label {
            CheckboxInput(rollback) { onInput { rollback = !rollback } }
            Text(" Also roll back their contributions")
        }
    }
    Div(attrs = { classes("row") }) {
        Button(attrs = {
            classes("primary")
            onClick {
                val body = when (val built = moderateBody(kind, reason, note, days, markDevices, rollback, kotlin.js.Date.now().toLong())) {
                    is Built.Ok -> built.body
                    is Built.Invalid -> {
                        ctx.setStatus(Status(false, built.message))
                        return@onClick
                    }
                }
                ctx.confirmWrite(
                    title = kindLabel(kind),
                    flagPath = null,
                    before = standingSentence(moderation.standing, kotlin.js.Date.now().toLong()),
                    after = describeAction(body),
                    success = "${kindLabel(kind)}: done.",
                    warning = actionWarning(body),
                ) {
                    val done = ctx.api.moderation.moderate(moderation.userId, body)
                    if (done.perksRemoved.isNotEmpty()) {
                        ctx.setStatus(Status(true, "Done. Builder perks removed: ${done.perksRemoved.joinToString()}."))
                    }
                    note = ""
                    days = ""
                    reload()
                }
            }
        }) { Text(kindLabel(kind)) }
    }
}

@Composable
private fun RollbackForm(ctx: AdminCtx, userId: String, reload: () -> Unit) {
    var reason by remember(userId) { mutableStateOf("") }
    Div(attrs = { classes("row") }) {
        Label { Text("Roll back contributions") }
        TextInput(reason) { onInput { reason = it.value }; placeholder("why (audited)") }
        Button(attrs = {
            onClick {
                val why = reason.trim()
                if (why.isEmpty()) {
                    ctx.setStatus(Status(false, "Say why before rolling anything back."))
                    return@onClick
                }
                ctx.confirmWrite(
                    title = "Roll back contributions",
                    flagPath = null,
                    before = "their approved foods and accepted corrections are live",
                    after = "unpublished and reverted; anything waiting is rejected",
                    success = "Rolled back.",
                    warning = "Approved foods leave search and barcode lookup for everyone, and corrections from their reports are reverted.",
                ) {
                    val result = ctx.api.moderation.rollback(userId, why)
                    ctx.setStatus(Status(true, rollbackSentence(result.rolledBack)))
                    reason = ""
                    reload()
                }
            }
        }) { Text("Roll back") }
    }
}

@Composable
private fun ActionRow(ctx: AdminCtx, action: ModerationActionDto, now: Long, reload: () -> Unit) {
    Tr {
        Td { Text(kindLabel(action.kind)) }
        Td {
            Div { Text(reasonLabel(action.reason)) }
            action.note?.let { Span(attrs = { classes("muted") }) { Text(it) } }
        }
        Td { Text("${action.createdBy}, ${relativeTime(action.createdAtEpochMs)}") }
        Td { Text(action.untilEpochMs?.let { kotlin.js.Date(it.toDouble()).toLocaleDateString() } ?: "until lifted") }
        Td { Text(actionStatus(action, now)) }
        Td {
            Div { Text(action.appealState) }
            APPEAL_STATES.filter { it != action.appealState }.forEach { state ->
                Button(attrs = {
                    onClick {
                        ctx.confirmWrite(
                            title = "Appeal",
                            flagPath = null,
                            before = action.appealState,
                            after = state,
                            success = "Appeal marked $state.",
                        ) {
                            ctx.api.moderation.setAppeal(action.id, state, null)
                            reload()
                        }
                    }
                }) { Text(state) }
            }
        }
        Td {
            if (action.liftedAtEpochMs == null) {
                Button(attrs = {
                    onClick {
                        ctx.confirmWrite(
                            title = "Lift",
                            flagPath = null,
                            before = kindLabel(action.kind),
                            after = "lifted",
                            success = "Lifted. Builder perks don't come back by themselves.",
                        ) {
                            ctx.api.moderation.lift(action.id, null)
                            reload()
                        }
                    }
                }) { Text("Lift") }
            }
        }
    }
}

@Composable
private fun fact(label: String, value: String) {
    Tr {
        Td { Span(attrs = { classes("muted") }) { Text(label) } }
        Td { Text(value) }
    }
}

// ---------- pure, tested in ModerationViewTest ----------

internal val MODERATION_KINDS = listOf("ban" to "Ban", "suspension" to "Suspend", "restriction" to "Restrict contributions")
internal val APPEAL_STATES = listOf("none", "open", "upheld", "overturned")
private val DEFAULT_REASONS = listOf("fake_data", "copied_data", "spam", "abuse", "ban_evasion", "other")

internal fun kindLabel(kind: String): String = MODERATION_KINDS.firstOrNull { it.first == kind }?.second ?: kind

internal fun reasonLabel(reason: String): String = reason.replace('_', ' ')

internal fun standingSentence(standing: StandingDto?, nowEpochMs: Long): String {
    if (standing == null) return "good standing"
    val what = when (standing.kind) {
        "ban" -> "banned: every call answers with the blocked screen"
        "suspension" -> "suspended: every call answers with the blocked screen"
        else -> "restricted: logs food, can't submit foods, reports or corrections"
    }
    val until = standing.untilEpochMs ?: return "$what, until lifted"
    val daysLeft = ((until - nowEpochMs + MODERATION_DAY_MS - 1) / MODERATION_DAY_MS).coerceAtLeast(0)
    return "$what, $daysLeft more day${if (daysLeft == 1L) "" else "s"}"
}

internal fun contributionSentence(c: ContributionSummaryDto): String =
    "${c.accepted} accepted, ${c.rolledBack} rolled back, ${c.pending} waiting; contributed from ${c.devices} device${if (c.devices == 1) "" else "s"}"

internal fun remoteStateLabel(state: String): String = when (state) {
    "pending" -> "to be written on its next contribution"
    "set" -> "set"
    "clear_pending" -> "to be reset on its next contribution"
    "cleared" -> "reset"
    else -> state
}

internal fun actionStatus(action: ModerationActionDto, nowEpochMs: Long): String = when {
    action.liftedAtEpochMs != null -> "lifted by ${action.liftedBy ?: "admin"}"
    action.untilEpochMs != null && action.untilEpochMs <= nowEpochMs -> "ended"
    else -> "in force"
}

internal sealed interface Built {
    data class Ok(val body: ModerateBody) : Built
    data class Invalid(val message: String) : Built
}

/** The request the form describes, or what is wrong with it. */
@Suppress("LongParameterList")
internal fun moderateBody(
    kind: String,
    reason: String,
    note: String,
    days: String,
    markDevices: Boolean,
    rollback: Boolean,
    nowEpochMs: Long,
): Built {
    val text = days.trim()
    val until = if (text.isEmpty()) {
        null
    } else {
        val n = text.toIntOrNull()?.takeIf { it > 0 } ?: return Built.Invalid("Give the days as a whole number, or leave it blank.")
        nowEpochMs + n * MODERATION_DAY_MS
    }
    if (kind == "suspension" && until == null) return Built.Invalid("A suspension needs a number of days.")
    return Built.Ok(
        ModerateBody(
            kind = kind,
            reason = reason,
            note = note.trim().ifEmpty { null },
            untilEpochMs = until,
            markDevices = markDevices,
            rollback = rollback,
        ),
    )
}

internal fun describeAction(body: ModerateBody): String = buildList {
    add("${kindLabel(body.kind)} (${reasonLabel(body.reason)})")
    add(body.untilEpochMs?.let { "until ${kotlin.js.Date(it.toDouble()).toLocaleDateString()}" } ?: "until lifted")
    if (body.markDevices) add("devices marked")
    if (body.rollback) add("contributions rolled back")
}.joinToString(", ")

/** A ban, or a rollback, is the kind of thing to type the environment's name for on prod. */
internal fun actionWarning(body: ModerateBody): String? = when {
    body.kind == "ban" && body.rollback -> "This blocks the app for them and unpublishes everything they contributed."
    body.kind == "ban" -> "This blocks the app for them. Their log stays theirs; they can still export or delete it by email."
    body.rollback -> "This unpublishes everything they contributed."
    else -> null
}

internal fun rollbackSentence(results: List<RevertedDto>): String =
    "Rolled back: " + results.sortedBy { it.kind }.joinToString("; ") { r ->
        val skipped = if (r.skipped > 0) ", ${r.skipped} replaced since (check those foods)" else ""
        "${r.kind}s ${r.undone} undone, ${r.closed} waiting rejected$skipped"
    } + "."
