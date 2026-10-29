package com.opson.libraries.reminders.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * A scheduled notification as the platform keeps it: Android's alarm extras and its record of
 * what is scheduled, iOS's `userInfo`. Self-contained, so a notification can be posted and
 * answered without the rest of the app having started.
 */
@Serializable
internal data class ReminderWire(
    val id: String,
    val atMs: Long,
    val kind: String,
    val date: String,
    val slot: String? = null,
    val index: Int? = null,
    /** A dose reminder's medication id: a client uuid, never its name or dose. */
    val medication: String? = null,
    val snoozed: Boolean = false,
    val title: String = "",
    val body: String = "",
    val style: String = ReminderStyle.Subtle.name,
    val actions: List<ActionWire> = emptyList(),
) {
    /** Null for a record this build cannot read (written by a newer one): it is dropped, not guessed at. */
    fun reminder(): Reminder? {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val key = when (kind) {
            KIND_MEAL -> slot?.let(MealSlot::fromWire)?.let { ReminderKey.Meal(day, it) }
            KIND_WATER -> index?.let { ReminderKey.Water(day, it) }
            KIND_DOSE -> medication?.let { ReminderKey.Dose(day, it) }
            else -> null
        } ?: return null
        return Reminder(key, Instant.fromEpochMilliseconds(atMs), snoozed)
    }

    fun notification(): ReminderNotification? {
        val reminder = reminder() ?: return null
        return ReminderNotification(
            reminder = reminder,
            title = title,
            body = body,
            style = ReminderStyle.entries.firstOrNull { it.name == style } ?: ReminderStyle.Subtle,
            actions = actions.mapNotNull { wire ->
                ReminderAction.entries.firstOrNull { it.wire == wire.action }?.let { it to wire.label }
            },
        )
    }

    fun encode(): String = WireJson.encodeToString(serializer(), this)

    companion object {
        private const val KIND_MEAL = "meal"
        private const val KIND_WATER = "water"
        private const val KIND_DOSE = "dose"

        fun decode(json: String?): ReminderWire? =
            json?.let { runCatching { WireJson.decodeFromString(serializer(), it) }.getOrNull() }

        fun of(notification: ReminderNotification): ReminderWire {
            val reminder = notification.reminder
            return when (val key = reminder.key) {
                is ReminderKey.Meal -> base(notification, KIND_MEAL).copy(slot = key.slot.wire)
                is ReminderKey.Water -> base(notification, KIND_WATER).copy(index = key.index)
                is ReminderKey.Dose -> base(notification, KIND_DOSE).copy(medication = key.medicationId)
            }
        }

        private fun base(notification: ReminderNotification, kind: String) = ReminderWire(
            id = notification.reminder.id,
            atMs = notification.reminder.at.toEpochMilliseconds(),
            kind = kind,
            date = notification.reminder.key.date.toString(),
            snoozed = notification.reminder.snoozed,
            title = notification.title,
            body = notification.body,
            style = notification.style.name,
            actions = notification.actions.map { (action, label) -> ActionWire(action.wire, label) },
        )
    }
}

@Serializable
internal data class ActionWire(val action: String, val label: String)

internal val WireJson = Json { ignoreUnknownKeys = true }
