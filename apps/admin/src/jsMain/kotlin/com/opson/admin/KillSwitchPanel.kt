package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

private const val MODE_PATH = "upgrade.maintenanceMode"
private const val MESSAGE_PATH = "upgrade.maintenanceMessage"
private const val MIN_VERSION_PATH = "upgrade.minSupportedVersionCode"
private const val LATEST_VERSION_PATH = "upgrade.latestVersionCode"

/**
 * The emergency flags, pinned above the tabs so incident response never
 * involves scrolling a flag list: maintenance mode (stop/banner), the
 * maintenance message, the force-upgrade floor and the soft update offer. Big
 * obvious state, and every flip still goes through the confirm sheet.
 *
 * The app guard in `features/upgrade` reads them from 0.2 on; 0.1 shipped
 * without it and never will. Every line here says which builds a value
 * reaches, because an operator who believes 0.1 users were stopped when they
 * were not is worse off than one with no switch at all.
 */
@Composable
internal fun KillSwitchPanel(rows: List<FlagRow>, manifest: ManifestResponse?, ctx: AdminCtx) {
    val mode = rows.find { it.path == MODE_PATH }
    val message = rows.find { it.path == MESSAGE_PATH }
    val minVersion = rows.find { it.path == MIN_VERSION_PATH }
    val latestVersion = rows.find { it.path == LATEST_VERSION_PATH }
    if (mode == null && message == null && minVersion == null && latestVersion == null) return

    val modeValue = mode?.resolved.stringContent() ?: "off"
    val stateClass = when (modeValue) {
        "blocking" -> "kill-blocking"
        "banner" -> "kill-banner"
        else -> "kill-clear"
    }
    val stateLabel = when (modeValue) {
        "blocking" -> "MAINTENANCE: BLOCKING (0.2+ stopped)"
        "banner" -> "MAINTENANCE: BANNER (0.2+ see it)"
        else -> "ALL CLEAR — no maintenance served"
    }

    Div(attrs = { classes("panel", "kill-panel", stateClass) }) {
        Div(attrs = { classes("kill-state") }) { Text(stateLabel) }
        Div(attrs = { classes("muted", "hint") }) { Text(WHO_READS_THESE) }

        mode?.let { row ->
            KillRow(row, "maintenance mode") {
                val options = row.allowedValues ?: listOf("off", "banner", "blocking")
                options.forEach { option ->
                    Button(attrs = {
                        if (option == modeValue) classes("primary")
                        onClick {
                            if (option == modeValue) return@onClick
                            val json = JsonPrimitive(option).toString()
                            ctx.confirmWrite(
                                title = "Set maintenance mode",
                                flagPath = row.path,
                                before = serverValueLabel(row),
                                after = json,
                                success = "Maintenance mode set to $option",
                                warning = dangerousWarning(row.path, json),
                            ) { ctx.api.upsertFlag(row.path, JsonPrimitive(option)) }
                        }
                    }) { Text(option) }
                }
            }
        }

        message?.let { row ->
            var draft by remember(row.path, row.base) {
                mutableStateOf(row.resolved.stringContent() ?: "")
            }
            KillRow(row, "maintenance message") {
                TextInput(draft) { onInput { draft = it.value }; placeholder("shown to users during maintenance") }
                Button(attrs = {
                    onClick {
                        ctx.confirmWrite(
                            title = "Set maintenance message",
                            flagPath = row.path,
                            before = serverValueLabel(row),
                            after = JsonPrimitive(draft).toString(),
                            success = "Maintenance message saved",
                        ) { ctx.api.upsertFlag(row.path, JsonPrimitive(draft)) }
                    }
                }) { Text("Save") }
            }
        }

        minVersion?.let { row ->
            var draft by remember(row.path, row.base) { mutableStateOf(row.resolved.inline()) }
            KillRow(row, "min supported build") {
                TextInput(draft) { onInput { draft = it.value }; placeholder("e.g. 1") }
                Button(attrs = {
                    onClick {
                        val parsed = parseTypedValue("int", draft)
                        val element = parsed.element
                        if (element == null) {
                            ctx.setStatus(Status(false, parsed.problem ?: "Must be a whole number"))
                            return@onClick
                        }
                        ctx.confirmWrite(
                            title = "Set min supported build",
                            flagPath = row.path,
                            before = serverValueLabel(row),
                            after = element.inline(),
                            success = "Min supported build set to ${element.inline()}",
                            warning = dangerousWarning(row.path, draft),
                        ) { ctx.api.upsertFlag(row.path, element) }
                    }
                }) { Text("Save") }
                manifest?.versionCode?.let {
                    Span(attrs = { classes("muted", "hint") }) {
                        Text("current build is $it")
                    }
                }
            }
        }

        latestVersion?.let { row ->
            var draft by remember(row.path, row.base) { mutableStateOf(row.resolved.inline()) }
            KillRow(row, "offer update below build") {
                TextInput(draft) { onInput { draft = it.value }; placeholder("0 offers nothing") }
                Button(attrs = {
                    onClick {
                        val parsed = parseTypedValue("int", draft)
                        val element = parsed.element
                        if (element == null) {
                            ctx.setStatus(Status(false, parsed.problem ?: "Must be a whole number"))
                            return@onClick
                        }
                        ctx.confirmWrite(
                            title = "Set latest build",
                            flagPath = row.path,
                            before = serverValueLabel(row),
                            after = element.inline(),
                            success = "Builds below ${element.inline()} are offered the update",
                        ) { ctx.api.upsertFlag(row.path, element) }
                    }
                }) { Text("Save") }
            }
        }
    }
}

/** One kill-switch line: label, the three layers at a glance, then controls. */
@Composable
private fun KillRow(row: FlagRow, label: String, controls: @Composable () -> Unit) {
    Div(attrs = { classes("row", "kill-row") }) {
        Span(attrs = { classes("muted", "kill-label") }) { Text(label) }
        Span(attrs = { classes("muted", "hint") }) {
            Text("baked ${row.default.inline()} · server ${if (row.inDb) row.base.inline() else "not set"} · clients get ${row.resolved.inline()}")
        }
        Div(attrs = { classes("spacer") }) {}
        controls()
    }
}

/** Which builds honour these. 0.1 has no guard, so nothing here reaches it. */
internal const val WHO_READS_THESE =
    "Opson 0.2 and later read these on launch and on every return to the app (a stopped app checks " +
        "every time). 0.1 does not read them at all: 0.1 users see no banner, no stop and no update " +
        "screen. To act on one build only, add a rule with a build range instead of changing the base."

private fun serverValueLabel(row: FlagRow): String =
    if (row.inDb) row.base.inline() else "not set (baked ${row.default.inline()})"

private fun kotlinx.serialization.json.JsonElement?.stringContent(): String? =
    (this as? JsonPrimitive)?.contentOrNull
