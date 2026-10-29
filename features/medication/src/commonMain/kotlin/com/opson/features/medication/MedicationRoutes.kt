package com.opson.features.medication

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * More › Medication (`release.medication`): set up the medication, its schedule, reminder and
 * injection sites, see the dose history and say how you feel today. Check
 * `MedicationEnabled` before linking here.
 */
@Serializable
class MedicationRoute : Route()

/**
 * The dose sheet: log a dose, or edit one.
 *
 * @param doseId the dose to edit; null logs a new one.
 * @param dueIso the dose day a reminder was for, so a late tap logs against it; null is now.
 * @param via where it was opened from, a `DoseVia` wire name, for the one event a logged dose sends.
 */
@Serializable
data class LogDoseRoute(
    val doseId: String? = null,
    val dueIso: String? = null,
    val via: String = VIA_MEDICATION,
) : Route() {
    companion object {
        const val VIA_MEDICATION = "medication"
        const val VIA_TODAY_CARD = "today_card"
        const val VIA_REMINDER = "reminder"
    }
}
