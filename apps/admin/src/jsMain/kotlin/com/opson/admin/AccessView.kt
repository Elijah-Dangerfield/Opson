package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
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

private const val DAY_MS = 86_400_000L

/**
 * One account's role and entitlements, under its Users detail: what it holds and why, the
 * grants behind it, and every change. Each write goes through the confirm sheet and is audited
 * on the server with the console's actor. Resolved for a neutral client: a targeting rule on
 * the policy that names a platform or a version does not show here.
 */
@Composable
internal fun UserAccessPanel(ctx: AdminCtx, userId: String) {
    var access by remember(userId) { mutableStateOf<UserAccessDto?>(null) }
    var overview by remember { mutableStateOf<EntitlementsOverviewDto?>(null) }
    var failed by remember(userId) { mutableStateOf(false) }
    var reloadKey by remember(userId) { mutableStateOf(0) }

    LaunchedEffect(userId, reloadKey) {
        failed = false
        Catching {
            access = ctx.api.access.userAccess(userId)
            if (overview == null) overview = ctx.api.access.overview()
        }.onFailure {
            failed = true
            ctx.setStatus(Status(false, "Access: ${it.message ?: "couldn't load"}"))
        }
    }

    val shown = access
    Div(attrs = { classes("panel") }) {
        H3 { Text("Access") }
        when {
            shown != null -> AccessDetail(ctx, shown, overview) { reloadKey++ }
            failed -> P(attrs = { classes("muted") }) { Text("Couldn't load access. This server may predate roles and entitlements.") }
            else -> P(attrs = { classes("muted") }) { Text("Loading…") }
        }
    }
}

@Composable
private fun AccessDetail(ctx: AdminCtx, access: UserAccessDto, overview: EntitlementsOverviewDto?, reload: () -> Unit) {
    Table(attrs = { classes("flags") }) {
        Tbody {
            accessFact("Role", roleLabel(access.role, access.roleAssignment))
            accessFact("Permissions", access.permissions.joinToString().ifEmpty { "none" })
        }
    }

    RoleEditor(ctx, access, overview?.roles?.map { it.role } ?: DEFAULT_ROLES, reload)

    H3 { Text("Entitlements") }
    if (access.entitlements.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("None.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead { Tr { Th { Text("Entitlement") }; Th { Text("Why") } } }
            Tbody {
                access.entitlements.forEach { held ->
                    Tr {
                        Td {
                            Div { Span(attrs = { classes("mono") }) { Text(held.key) } }
                            Span(attrs = { classes("muted") }) { Text(held.description) }
                        }
                        Td { Text(held.sources.joinToString("; ") { sourceSentence(it) }) }
                    }
                }
            }
        }
    }
    if (access.revoked.isNotEmpty()) {
        P(attrs = { classes("err") }) {
            Text("Revoked: " + access.revoked.joinToString { r -> "${r.key} (${r.revocations.firstOrNull()?.reason ?: "no reason"})" })
        }
    }

    GrantEditor(ctx, access.userId, overview, reload)

    H3 { Text("Grants, newest first") }
    if (access.grants.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("No grants or revocations.") }
    } else {
        Table(attrs = { classes("flags") }) {
            Thead {
                Tr {
                    Th { Text("What") }; Th { Text("Source") }; Th { Text("Reason") }; Th { Text("By") }
                    Th { Text("Ends") }; Th { Text("Status") }; Th { }
                }
            }
            Tbody {
                access.grants.forEach { grant ->
                    Tr {
                        Td { Text(grantLabel(grant)) }
                        Td { Text(grant.source) }
                        Td { Text(grant.reason ?: "—") }
                        Td { Text("${grant.grantedBy}, ${relativeTime(grant.grantedAtEpochMs)}") }
                        Td { Text(grant.expiresAtEpochMs?.let(::shortDate) ?: "never") }
                        Td { Text(grantStatus(grant)) }
                        Td {
                            if (grant.active) {
                                Button(attrs = {
                                    onClick {
                                        ctx.confirmWrite(
                                            title = "Remove a grant",
                                            flagPath = null,
                                            before = grantLabel(grant),
                                            after = "removed",
                                            success = "Removed ${grantLabel(grant)}.",
                                        ) {
                                            ctx.api.access.removeGrant(access.userId, grant.id)
                                            reload()
                                        }
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }

    H3 { Text("Changes") }
    AccessAuditTable(access.audit, showUser = false)
}

@Composable
private fun RoleEditor(ctx: AdminCtx, access: UserAccessDto, roles: List<String>, reload: () -> Unit) {
    var role by remember(access.userId, access.role) { mutableStateOf(access.role) }
    var reason by remember(access.userId) { mutableStateOf("") }
    Div(attrs = { classes("row") }) {
        Label { Text("Set role") }
        roles.forEach { option ->
            Button(attrs = {
                if (option == role) classes("primary")
                onClick { role = option }
            }) { Text(option) }
        }
        TextInput(reason) { onInput { reason = it.value }; placeholder("why (audited)") }
        Button(attrs = {
            if (role == access.role) attr("disabled", "true")
            onClick {
                if (role == access.role) return@onClick
                val chosen = role
                ctx.confirmWrite(
                    title = "Set role",
                    flagPath = null,
                    before = access.role,
                    after = chosen,
                    success = "Role set to $chosen.",
                    warning = roleWarning(chosen),
                ) {
                    ctx.api.access.setRole(access.userId, chosen, reason.trim().ifEmpty { null })
                    reason = ""
                    reload()
                }
            }
        }) { Text("Save role") }
    }
}

@Composable
private fun GrantEditor(ctx: AdminCtx, userId: String, overview: EntitlementsOverviewDto?, reload: () -> Unit) {
    var kind by remember { mutableStateOf("bundle") }
    var key by remember { mutableStateOf("pro") }
    var effect by remember { mutableStateOf("grant") }
    var source by remember { mutableStateOf("manual") }
    var reason by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }

    val keys = when (kind) {
        "bundle" -> overview?.policy?.bundles?.keys?.sorted() ?: listOf("pro")
        else -> overview?.entitlements?.map { it.key }.orEmpty()
    }

    H3 { Text("Grant or revoke") }
    Div(attrs = { classes("row") }) {
        choice("Kind", listOf("bundle", "entitlement"), kind) {
            kind = it
            effect = "grant"
            key = if (it == "bundle") "pro" else overview?.entitlements?.firstOrNull()?.key.orEmpty()
        }
        choice("Effect", if (kind == "bundle") listOf("grant") else listOf("grant", "revoke"), effect) { effect = it }
    }
    Div(attrs = { classes("row") }) { choice(if (kind == "bundle") "Bundle" else "Entitlement", keys, key) { key = it } }
    Div(attrs = { classes("row") }) {
        Label { Text("Source") }
        TextInput(source) { onInput { source = it.value }; placeholder("manual, founding, builder, comp") }
        TextInput(reason) { onInput { reason = it.value }; placeholder("reason (audited)") }
        TextInput(days) { onInput { days = it.value }; placeholder("ends in N days (blank: never)") }
        Button(attrs = {
            classes("primary")
            onClick {
                val expiry = expiryFromDays(days, kotlin.js.Date.now().toLong())
                if (expiry is Expiry.Invalid || key.isBlank()) {
                    ctx.setStatus(Status(false, "Pick what to grant, and give the days as a whole number."))
                    return@onClick
                }
                val body = AddGrantBody(
                    kind = kind,
                    key = key,
                    effect = effect,
                    source = source.trim().ifEmpty { "manual" },
                    reason = reason.trim().ifEmpty { null },
                    expiresAtEpochMs = (expiry as? Expiry.At)?.epochMs,
                )
                ctx.confirmWrite(
                    title = if (effect == "revoke") "Revoke an entitlement" else "Grant",
                    flagPath = null,
                    before = "—",
                    after = describeNewGrant(body),
                    success = "Saved: ${describeNewGrant(body)}.",
                ) {
                    ctx.api.access.addGrant(userId, body)
                    reason = ""
                    days = ""
                    reload()
                }
            }
        }) { Text(if (effect == "revoke") "Revoke" else "Grant") }
    }
}

/**
 * The policy (`entitlements.everyone`, `.bundles`, `.roles`), the entitlements and roles the
 * server knows, and recent access changes across accounts. The policy is remote config, so the
 * editors below write its flags through the same confirmed, audited path as the Flags tab, and
 * the change reaches apps on their next config refresh (about 30 seconds on the server).
 */
@Composable
internal fun EntitlementsView(ctx: AdminCtx) {
    var overview by remember { mutableStateOf<EntitlementsOverviewDto?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        failed = false
        Catching { overview = ctx.api.access.overview() }
            .onFailure {
                failed = true
                ctx.setStatus(Status(false, it.message ?: "Couldn't load entitlements"))
            }
    }

    val shown = overview
    if (shown == null) {
        P(attrs = { classes("muted") }) {
            Text(if (failed) "Couldn't load entitlements. This server may predate them." else "Loading…")
        }
        return
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Everyone") }
        P(attrs = { classes("muted") }) {
            Text("Entitlements every account has, without a grant. Taking one out takes it from everyone who has no other source.")
        }
        PolicyEditor(
            ctx = ctx,
            path = shown.paths["everyone"] ?: "entitlements.everyone",
            current = JsonArray(shown.policy.everyone.map(::JsonPrimitive)),
            reload = { reloadKey++ },
        )
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Bundles") }
        P(attrs = { classes("muted") }) { Text("Bundle name to entitlements. Pro is the bundle \"pro\".") }
        PolicyEditor(
            ctx = ctx,
            path = shown.paths["bundles"] ?: "entitlements.bundles",
            current = JsonObject(shown.policy.bundles.mapValues { (_, keys) -> JsonArray(keys.map(::JsonPrimitive)) }),
            reload = { reloadKey++ },
        )
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Bundles by role") }
        P(attrs = { classes("muted") }) { Text("Role to the bundles it brings. What a role may do is code, listed below.") }
        PolicyEditor(
            ctx = ctx,
            path = shown.paths["roles"] ?: "entitlements.roles",
            current = JsonObject(shown.policy.roles.mapValues { (_, names) -> JsonArray(names.map(::JsonPrimitive)) }),
            reload = { reloadKey++ },
        )
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("What the server knows") }
        Table(attrs = { classes("flags") }) {
            Thead { Tr { Th { Text("Entitlement") }; Th { Text("What it is") }; Th { Text("Who has it now") } } }
            Tbody {
                shown.entitlements.forEach { info ->
                    Tr {
                        Td { Span(attrs = { classes("mono") }) { Text(info.key) } }
                        Td { Text(info.description) }
                        Td { Text(whoHas(info.key, shown.policy)) }
                    }
                }
            }
        }
        Table(attrs = { classes("flags") }) {
            Thead { Tr { Th { Text("Role") }; Th { Text("Permissions") } } }
            Tbody {
                shown.roles.forEach { role ->
                    Tr {
                        Td { Text(role.role) }
                        Td { Text(role.permissions.joinToString().ifEmpty { "none" }) }
                    }
                }
            }
        }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text("Recent role and grant changes") }
        AccessAuditTable(shown.audit, showUser = true)
    }
}

/** A JSON editor for one policy flag, saved through the config API (validated server-side). */
@Composable
private fun PolicyEditor(ctx: AdminCtx, path: String, current: JsonElement, reload: () -> Unit) {
    val currentText = current.toString()
    var draft by remember(currentText) { mutableStateOf(currentText) }
    Div(attrs = { classes("row") }) {
        Span(attrs = { classes("mono", "muted") }) { Text(path) }
    }
    Div(attrs = { classes("row") }) {
        TextInput(draft) {
            onInput { draft = it.value }
            style { property("min-width", "560px") }
        }
        Button(attrs = {
            classes("primary")
            onClick {
                val parsed = parseJsonOrNull(draft)
                if (parsed == null) {
                    ctx.setStatus(Status(false, "$path: not valid JSON"))
                    return@onClick
                }
                ctx.confirmWrite(
                    title = "Edit $path",
                    flagPath = path,
                    before = currentText,
                    after = parsed.toString(),
                    success = "Saved $path. Apps see it on their next config refresh.",
                    warning = policyWarning(path, current, parsed),
                ) {
                    ctx.api.upsertFlag(path, parsed)
                    reload()
                }
            }
        }) { Text("Save") }
    }
}

@Composable
private fun AccessAuditTable(entries: List<AccessAuditDto>, showUser: Boolean) {
    if (entries.isEmpty()) {
        P(attrs = { classes("muted") }) { Text("No changes yet.") }
        return
    }
    Table(attrs = { classes("flags") }) {
        Thead {
            Tr {
                Th { Text("When") }; Th { Text("Who") }; Th { Text("What") }
                if (showUser) Th { Text("Account") }
                Th { Text("After") }
            }
        }
        Tbody {
            entries.forEach { entry ->
                Tr {
                    Td { Text(relativeTime(entry.atEpochMs)) }
                    Td { Text(entry.actor) }
                    Td { Text(entry.action) }
                    if (showUser) Td { Span(attrs = { classes("mono") }) { Text(entry.userId) } }
                    Td { Span(attrs = { classes("mono", "muted") }) { Text(entry.after.inline()) } }
                }
            }
        }
    }
}

@Composable
private fun choice(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Label { Text(label) }
    options.forEach { option ->
        Button(attrs = {
            if (option == selected) classes("primary")
            onClick { onPick(option) }
        }) { Text(option) }
    }
}

@Composable
private fun accessFact(label: String, value: String) {
    Tr {
        Td { Span(attrs = { classes("muted") }) { Text(label) } }
        Td { Text(value) }
    }
}

private val DEFAULT_ROLES = listOf("member", "builder", "moderator", "admin", "owner")

private fun shortDate(epochMs: Long): String = kotlin.js.Date(epochMs.toDouble()).toLocaleDateString()

// ---------- pure, tested in AccessViewTest ----------

/** "owner, set by elijah (Opson owner)", or "member" for an account with no role row. */
internal fun roleLabel(role: String, assignment: RoleAssignmentDto?): String {
    if (assignment == null) return role
    val why = assignment.reason?.let { " ($it)" }.orEmpty()
    return "$role, set by ${assignment.grantedBy}$why"
}

/** One reason an account holds an entitlement, as a clause. */
internal fun sourceSentence(source: EntitlementSourceDto): String = when (source.type) {
    "everyone" -> "everyone has it"
    "role" -> "role ${source.role} brings ${source.bundle}"
    "grant" -> {
        val what = source.bundle?.let { "the $it bundle" } ?: "a direct grant"
        val grant = source.grant
        val detail = listOfNotNull(grant?.source, grant?.reason).joinToString(", ")
        "$what" + if (detail.isEmpty()) "" else " ($detail)"
    }
    else -> source.type
}

internal fun grantLabel(grant: GrantDto): String {
    val verb = if (grant.effect == "revoke") "revoke" else "grant"
    return "$verb ${grant.kind} ${grant.key}"
}

internal fun grantStatus(grant: GrantDto): String = when {
    grant.removedAtEpochMs != null -> "removed by ${grant.removedBy ?: "admin"}"
    grant.active -> "in force"
    else -> "ended"
}

internal fun describeNewGrant(body: AddGrantBody): String {
    val verb = if (body.effect == "revoke") "revoke" else "grant"
    val ends = if (body.expiresAtEpochMs == null) "" else ", ends ${shortDate(body.expiresAtEpochMs)}"
    return "$verb ${body.kind} ${body.key} (${body.source}$ends)"
}

/** The days field of the grant form. */
internal sealed interface Expiry {
    data object Never : Expiry
    data class At(val epochMs: Long) : Expiry
    data object Invalid : Expiry
}

internal fun expiryFromDays(raw: String, nowEpochMs: Long): Expiry {
    val text = raw.trim()
    if (text.isEmpty()) return Expiry.Never
    val days = text.toIntOrNull()?.takeIf { it > 0 } ?: return Expiry.Invalid
    return Expiry.At(nowEpochMs + days * DAY_MS)
}

/** Who holds [key] by policy alone: everyone, or the bundles and roles that bring it. */
internal fun whoHas(key: String, policy: EntitlementPolicyDto): String {
    if (key in policy.everyone) return "everyone"
    val bundles = policy.bundles.filterValues { key in it }.keys
    if (bundles.isEmpty()) return "only direct grants"
    val roles = policy.roles.filterValues { names -> names.any { it in bundles } }.keys
    val byRole = if (roles.isEmpty()) "" else "; roles ${roles.joinToString()}"
    return "bundles ${bundles.joinToString()}$byRole, and grants"
}

/** A stronger confirm for owner and admin, who can edit the catalog directly. */
internal fun roleWarning(role: String): String? = when (role) {
    "owner", "admin" -> "An $role's food edits go straight into the catalog and count as verified, with no second reviewer. Continue?"
    else -> null
}

/** A warning when an edit to `entitlements.everyone` takes something away from everyone. */
internal fun policyWarning(path: String, before: JsonElement, after: JsonElement): String? {
    if (path != "entitlements.everyone") return null
    val was = (before as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty().toSet()
    val now = (after as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty().toSet()
    val removed = was - now
    if (removed.isEmpty()) return null
    return "This takes ${removed.joinToString()} away from every account that has no grant, role or bundle for it. Continue?"
}
