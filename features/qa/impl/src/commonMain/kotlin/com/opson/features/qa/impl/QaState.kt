package com.opson.features.qa.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.ConfiguredValue
import com.opson.libraries.config.DoubleConfigValue
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.IntConfigValue
import com.opson.libraries.config.LongConfigValue
import com.opson.libraries.config.StringConfigValue
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.buildType
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.reminders.ReminderKey
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.qa_fire_breakfast
import opson.libraries.resources.generated.resources.qa_fire_dinner
import opson.libraries.resources.generated.resources.qa_fire_lunch
import opson.libraries.resources.generated.resources.qa_fire_snack
import opson.libraries.resources.generated.resources.qa_fire_water
import opson.libraries.resources.generated.resources.qa_label_channel
import opson.libraries.resources.generated.resources.qa_label_commit
import opson.libraries.resources.generated.resources.qa_label_email
import opson.libraries.resources.generated.resources.qa_label_platform
import opson.libraries.resources.generated.resources.qa_label_provider
import opson.libraries.resources.generated.resources.qa_label_server
import opson.libraries.resources.generated.resources.qa_label_supabase
import opson.libraries.resources.generated.resources.qa_label_user
import opson.libraries.resources.generated.resources.qa_label_version
import opson.libraries.resources.generated.resources.qa_provider_account
import opson.libraries.resources.generated.resources.qa_provider_guest
import opson.libraries.resources.generated.resources.qa_provider_none
import org.jetbrains.compose.resources.StringResource

data class QaState(
    val build: List<QaInfo> = emptyList(),
    val identity: List<QaInfo> = emptyList(),
    val reminders: List<QaReminder> = QaReminder.entries,
    val config: List<QaConfigRow> = emptyList(),
    /** Something is running: the rows that start work wait for it. */
    val busy: Boolean = false,
)

/**
 * One label and value. A [value] of null reads "None"; [valueText] says it in words instead
 * (the provider), and then there is nothing worth copying.
 */
data class QaInfo(val label: StringResource, val value: String?, val valueText: StringResource? = null) {
    val copyable: Boolean get() = value != null && valueText == null
}

/** The reminders the menu can fire, the missed-meal nudges first. */
enum class QaReminder(val label: StringResource) {
    Breakfast(Res.string.qa_fire_breakfast),
    Lunch(Res.string.qa_fire_lunch),
    Snack(Res.string.qa_fire_snack),
    Dinner(Res.string.qa_fire_dinner),
    Water(Res.string.qa_fire_water),
    ;

    fun key(today: LocalDate): ReminderKey = when (this) {
        Breakfast -> ReminderKey.Meal(today, MealSlot.Breakfast)
        Lunch -> ReminderKey.Meal(today, MealSlot.Lunch)
        Snack -> ReminderKey.Meal(today, MealSlot.Snack)
        Dinner -> ReminderKey.Meal(today, MealSlot.Dinner)
        Water -> ReminderKey.Water(today, index = 0)
    }
}

/**
 * One config value as the QA menu shows it. [kind] decides the control: a switch for a flag, a
 * text field for a number or a string, and nothing for a composite value, which is shown as is.
 */
data class QaConfigRow(
    val name: String,
    val path: String,
    val group: String,
    val value: String,
    val kind: QaConfigKind,
    val overridden: Boolean,
)

enum class QaConfigKind { Flag, Int, Long, Double, Text, ReadOnly }

object QaStateBuilder {

    data class BuildFacts(val versionName: String, val buildNumber: Int, val channel: String, val commitSha: String, val branch: String, val platform: String)

    fun build(facts: BuildFacts): List<QaInfo> = listOf(
        QaInfo(Res.string.qa_label_version, "${facts.versionName} (${facts.buildNumber})"),
        QaInfo(Res.string.qa_label_channel, facts.channel),
        QaInfo(Res.string.qa_label_commit, "${facts.commitSha} on ${facts.branch}"),
        QaInfo(Res.string.qa_label_platform, facts.platform),
    )

    /**
     * Who is signed in and where the app talks to. The project ref is the Supabase URL's first
     * host label, which is what the dashboard and the CLI call the project.
     */
    fun identity(auth: AuthState?, serverUrl: String, supabaseUrl: String): List<QaInfo> {
        val signedIn = auth as? AuthState.Authenticated
        val provider = when {
            signedIn == null -> Res.string.qa_provider_none
            signedIn.isAnonymous -> Res.string.qa_provider_guest
            else -> Res.string.qa_provider_account
        }
        return listOf(
            QaInfo(Res.string.qa_label_user, signedIn?.userId),
            QaInfo(Res.string.qa_label_provider, value = "", valueText = provider),
            QaInfo(Res.string.qa_label_email, signedIn?.email),
            QaInfo(Res.string.qa_label_server, serverUrl.ifBlank { null }),
            QaInfo(Res.string.qa_label_supabase, supabaseProject(supabaseUrl)),
        )
    }

    fun supabaseProject(url: String): String? =
        url.substringAfter("://").substringBefore("/").substringBefore(".").ifBlank { null }

    /** Every value that asks to be in the QA menu, by group then name, read from [config]. */
    fun config(values: Collection<ConfiguredValue<*>>, config: AppConfigMap, overriddenPaths: Set<String>): List<QaConfigRow> =
        values.filter { it.showInQADashboard }
            .map { value ->
                QaConfigRow(
                    name = value.name,
                    path = value.path,
                    group = value.group,
                    value = value.value.toString(),
                    kind = kindOf(value),
                    overridden = value.path in overriddenPaths,
                )
            }
            .sortedWith(compareBy({ it.group }, { it.name }))

    private fun kindOf(value: ConfiguredValue<*>): QaConfigKind = when (value) {
        is FlagConfigValue -> QaConfigKind.Flag
        is IntConfigValue -> QaConfigKind.Int
        is LongConfigValue -> QaConfigKind.Long
        is DoubleConfigValue -> QaConfigKind.Double
        is StringConfigValue -> QaConfigKind.Text
        else -> QaConfigKind.ReadOnly
    }

    /** The typed value for an override typed into a field, or null when it does not parse. */
    fun parse(kind: QaConfigKind, raw: String): Any? {
        val text = raw.trim()
        return when (kind) {
            QaConfigKind.Flag -> text.toBooleanStrictOrNull()
            QaConfigKind.Int -> text.toIntOrNull()
            QaConfigKind.Long -> text.toLongOrNull()
            QaConfigKind.Double -> text.toDoubleOrNull()
            QaConfigKind.Text -> text
            QaConfigKind.ReadOnly -> null
        }
    }
}

/** This binary's facts, from [BuildInfo]; previews and tests build their own. */
object QaBuild {
    fun current(): QaStateBuilder.BuildFacts = QaStateBuilder.BuildFacts(
        versionName = BuildInfo.versionName,
        buildNumber = BuildInfo.buildNumber,
        channel = BuildInfo.releaseChannel,
        commitSha = BuildInfo.commitSha,
        branch = BuildInfo.commitBranch,
        platform = "${BuildInfo.platform.name}, ${BuildInfo.buildType}",
    )
}

sealed interface QaEvent {
    data class ShowToast(val toast: QaToast) : QaEvent

    /** The check-in for [dayIso], today. */
    data class OpenCheckIn(val dayIso: String) : QaEvent

    /** QA menu › Prompts. */
    data object OpenPrompts : QaEvent
    data object Close : QaEvent
}

sealed interface QaAction {
    data class InputsArrived(val auth: AuthState?, val config: AppConfigMap, val overriddenPaths: Set<String>) : QaAction
    data class Copied(val label: StringResource) : QaAction
    data class FireTapped(val reminder: QaReminder) : QaAction
    data object CheckInTapped : QaAction
    data object PromptsTapped : QaAction
    data object ResetOnboardingTapped : QaAction
    data object ClearCachesTapped : QaAction
    data class FlagToggled(val path: String, val on: Boolean) : QaAction
    data class ValueSet(val row: QaConfigRow, val raw: String) : QaAction
    data object ClearOverridesTapped : QaAction
    data object BackTapped : QaAction
}
