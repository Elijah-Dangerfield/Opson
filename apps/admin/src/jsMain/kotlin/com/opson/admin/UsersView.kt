package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
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
 * Support's user lookup (`/v1/admin/users`): find an account by its id, or by part of its email
 * or display name, and see what it runs, what it chose in Settings and how it logs. Read-only,
 * apart from its role and entitlements ([UserAccessPanel]).
 * Everything here comes from the server's `support` views, which hold no food, amount, weight
 * or note, so neither can this tab. The Grafana User detail board has the rest: sessions, events
 * and errors, by the same id.
 */
@Composable
internal fun UsersView(ctx: AdminCtx, openId: String? = null) {
    var query by remember { mutableStateOf(openId.orEmpty()) }
    var results by remember { mutableStateOf<List<UserSummaryDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(openId?.let { AccountPick(it, 1) }) }
    val selected = pick?.userId
    var detail by remember { mutableStateOf<UserDetailDto?>(null) }
    var detailFailed by remember { mutableStateOf(false) }

    fun search(text: String) {
        searching = true
        ctx.scope.launch {
            Catching { results = ctx.api.searchUsers(text.trim()) }
                .onSuccess { searchFailed = false }
                .onFailure {
                    searchFailed = true
                    ctx.setStatus(Status(false, it.message ?: "Search failed"))
                }
            searching = false
        }
    }

    // The most recently seen accounts before anyone types, or the account a link named.
    LaunchedEffect(Unit) { search(openId.orEmpty()) }

    LaunchedEffect(pick) {
        detail = null
        detailFailed = false
        val id = pick?.userId ?: return@LaunchedEffect
        Catching { detail = ctx.api.userDetail(id) }
            .onFailure {
                detailFailed = true
                ctx.setStatus(Status(false, it.message ?: "Couldn't load that account"))
            }
    }

    Div(attrs = { classes("panel") }) {
        Div(attrs = { classes("row") }) {
            TextInput(query) {
                onInput { query = it.value }
                onKeyDown { if (it.key == "Enter") search(query) }
                placeholder("user id, or part of an email or display name")
                style { property("min-width", "360px") }
            }
            Button(attrs = {
                classes("primary")
                if (searching) attr("disabled", "true")
                onClick { if (!searching) search(query) }
            }) { Text(if (searching) "Searching…" else "Search") }
            Span(attrs = { classes("muted", "hint") }) {
                Text("Blank lists the 25 most recently seen. Email and name stay in this console; telemetry never has them.")
            }
        }
    }

    val found = results
    if (found.isNullOrEmpty()) {
        P(attrs = { classes("muted") }) { Text(userListNote(found, searchFailed)) }
    } else {
        UserTable(found, selected) { pick = pick.next(it) }
    }

    val shown = detail
    when {
        shown != null -> {
            UserDetailPanel(shown, ctx.isProd)
            UserAccessPanel(ctx, shown.user.userId)
            UserModerationPanel(ctx, shown.user.userId)
        }
        selected != null -> P(attrs = { classes("muted") }) {
            Text(userDetailNote(detailFailed))
        }
    }
}

/**
 * What the list says when it has no rows to show: failed, still loading, or nothing matched. A
 * failure wins over an earlier empty result, which belongs to a query that is no longer the one
 * in the box.
 */
internal fun userListNote(results: List<UserSummaryDto>?, searchFailed: Boolean): String = when {
    searchFailed -> "Couldn't load accounts. Search to try again."
    results != null -> "No account matches."
    else -> "Loading…"
}

/** What the detail area says while the picked account loads, or after it failed to. */
internal fun userDetailNote(detailFailed: Boolean): String =
    if (detailFailed) "Couldn't load that account. Pick it again to retry." else "Loading the account…"

/**
 * One click on an account row. [attempt] counts the clicks, so picking the row that is already
 * selected is still a new value and the detail load runs again: that is how a failed load retries.
 */
internal data class AccountPick(val userId: String, val attempt: Int)

internal fun AccountPick?.next(userId: String): AccountPick = AccountPick(userId, (this?.attempt ?: 0) + 1)

@Composable
private fun UserTable(users: List<UserSummaryDto>, selected: String?, onSelect: (String) -> Unit) {
    Table(attrs = { classes("flags") }) {
        Thead {
            Tr {
                Th { Text("Account") }
                Th { Text("Email") }
                Th { Text("Runs") }
                Th { Text("Mode") }
                Th { Text("Logged") }
                Th { Text("Last seen") }
            }
        }
        Tbody {
            users.forEach { user ->
                Tr(attrs = {
                    if (user.userId == selected) classes("selected")
                    style { property("cursor", "pointer") }
                    onClick { onSelect(user.userId) }
                }) {
                    Td {
                        Div { Text(user.displayName ?: "—") }
                        Span(attrs = { classes("mono", "muted") }) { Text(user.userId) }
                    }
                    Td { Text(user.email ?: if (user.isGuest) "guest" else "—") }
                    Td { Text(runsLabel(user.platform, user.appVersion, user.buildNumber)) }
                    Td { Text(user.preset ?: "—") }
                    Td { Text("${user.entries} entries on ${user.daysLogged} days") }
                    Td { Text(user.lastSeenAtEpochMs?.let(::relativeTime) ?: "never seen by this server") }
                }
            }
        }
    }
}

@Composable
private fun UserDetailPanel(detail: UserDetailDto, isProd: Boolean) {
    val user = detail.user
    Div(attrs = { classes("panel") }) {
        H3 { Text(user.displayName ?: user.email ?: user.userId) }
        Div(attrs = { classes("row") }) {
            A(href = userDetailDashboardUrl(user.userId, isProd), attrs = { target(ATarget.Blank) }) {
                Text("Open in Grafana (sessions, events, errors)")
            }
            A(href = sentryIssuesUrl(user.userId), attrs = { target(ATarget.Blank) }) { Text("Sentry issues") }
        }
        Table(attrs = { classes("flags") }) {
            Tbody {
                fact("User id", user.userId)
                fact("Email", user.email ?: "none")
                fact("Account", if (user.isGuest) "guest (on this phone only)" else "claimed")
                fact("Joined", user.joinedAtEpochMs?.let(::dateLabel) ?: "—")
                fact("Last sign-in", user.lastSignInAtEpochMs?.let(::dateLabel) ?: "—")
                fact("Last seen by the server", user.lastSeenAtEpochMs?.let(::dateLabel) ?: "—")
                fact("Last change synced", detail.lastSyncedChangeAtEpochMs?.let(::dateLabel) ?: "—")
                fact("Logged", "${user.entries} entries on ${user.daysLogged} days, last ${user.lastLogDay ?: "never"}")
                fact("Last 7 and 30 days", "${detail.entriesLast7Days} and ${detail.entriesLast30Days} entries")
                fact("Days closed", detail.daysClosed.toString())
            }
        }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Builds, newest first") }
        if (detail.installs.isEmpty()) {
            P(attrs = { classes("muted") }) { Text("No signed-in request recorded yet. Builds are recorded from the 0.2 server on.") }
        } else {
            Table(attrs = { classes("flags") }) {
                Thead { Tr { Th { Text("Install") }; Th { Text("Runs") }; Th { Text("First seen") }; Th { Text("Last seen") } } }
                Tbody {
                    detail.installs.forEach { install ->
                        Tr {
                            Td { Span(attrs = { classes("mono") }) { Text(install.installId.ifEmpty { "(no id yet)" }) } }
                            Td { Text(runsLabel(install.platform, install.appVersion, install.buildNumber)) }
                            Td { Text(dateLabel(install.firstSeenAtEpochMs)) }
                            Td { Text(dateLabel(install.lastSeenAtEpochMs)) }
                        }
                    }
                }
            }
        }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("How they log, all time") }
        Table(attrs = { classes("flags") }) {
            Tbody { detail.methods.filterValues { it > 0 }.forEach { (method, count) -> fact(method, count.toString()) } }
        }
        if (detail.methods.values.all { it == 0L }) P(attrs = { classes("muted") }) { Text("Nothing logged.") }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Settings they chose") }
        if (detail.settings.isEmpty()) {
            P(attrs = { classes("muted") }) { Text("No settings synced yet.") }
        } else {
            Table(attrs = { classes("flags") }) {
                Tbody { detail.settings.forEach { fact(it.key, it.value ?: "—") } }
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

private fun dateLabel(epochMs: Long): String =
    "${kotlin.js.Date(epochMs.toDouble()).toLocaleString()} (${relativeTime(epochMs)})"

/** "ios 0.2.0 (52)", or a dash when the server has not seen the account yet. */
internal fun runsLabel(platform: String?, appVersion: String?, buildNumber: Int?): String {
    if (platform == null && appVersion == null) return "—"
    val build = buildNumber?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
    return listOfNotNull(platform, appVersion?.takeIf { it.isNotEmpty() }).joinToString(" ") + build
}

private const val GRAFANA_URL = "https://cards.grafana.net"
private const val SENTRY_ORG = "elijah-dangerfield"

/** The Grafana User detail board for [userId], on the database of the environment this console manages. */
internal fun userDetailDashboardUrl(userId: String, isProd: Boolean): String {
    val database = if (isProd) "opson-prod-db" else "opson-db"
    return "$GRAFANA_URL/d/opson-user/opson-user-detail?var-env=$database&var-user=$userId"
}

/** Sentry's issues for [userId]: the client binds the account id as Sentry's user id. */
internal fun sentryIssuesUrl(userId: String): String =
    "https://$SENTRY_ORG.sentry.io/issues/?query=user.id%3A$userId&statsPeriod=90d"

/** `#users/<id>`, as the Builders board in Grafana links each contributor. */
internal fun userIdFromHash(): String? =
    kotlinx.browser.window.location.hash.removePrefix("#").takeIf { it.startsWith("users/") }?.removePrefix("users/")?.ifEmpty { null }
