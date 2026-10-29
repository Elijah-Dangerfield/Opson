package com.opson.libraries.medication

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What the person takes and how they like it tracked. A record, not a regimen: nothing here is a
 * rule Opson checks, and nothing tells anyone what to take.
 *
 * It lives on this phone only for now (the prod server has no medication storage), in a cache the
 * user change wipes. It is shaped as the spec's `Settings.medication` key so moving it into synced
 * settings later is a move, not a migration: every field has a default and the enums carry wire
 * names. [updatedAtEpochMs] is there for that merge.
 *
 * @property medications the UI offers one; the list keeps room for a second.
 * @property sites the injection sites the person uses, which the next-site suggestion picks from.
 * @property todayCard "Show on Today".
 * @property skippedDue "Skipped" on the Today card for this dose day: the prompt stays away until
 *   the next one. Nothing is written for a skipped dose and nothing counts them.
 */
@Serializable
data class MedicationPlan(
    val on: Boolean = false,
    val medications: List<Medication> = emptyList(),
    val sites: Set<InjectionSite> = InjectionSite.DEFAULT,
    val todayCard: Boolean = true,
    val skippedDue: LocalDate? = null,
    val updatedAtEpochMs: Long = 0,
) {
    /** The one medication v1 tracks. */
    val medication: Medication? get() = medications.firstOrNull()

    /** Tracking on, with something to track. */
    val tracking: Boolean get() = on && medication != null

    /** Puts [next] in the first place, the one v1 tracks, keeping any second. */
    fun withMedication(next: Medication): MedicationPlan =
        copy(medications = listOf(next) + medications.drop(1).filterNot { it.id == next.id })
}

/**
 * One medication. [drug], [brand] and [form] are copied onto every dose, so history reads the same
 * after the plan changes.
 *
 * @property brand the person's own name for it, for [MedicationProduct.Other] and compounded
 *   medicines. A listed product's name comes from [product].
 * @property currentDose what the person says they are on now; the dose sheet starts from it.
 * @property startedOn when they started it, if they said. The weight chart's "Started" tick.
 * @property addedOn when it was set up here. A dose day before it is never "not logged".
 */
@Serializable
data class Medication(
    val id: String,
    val product: MedicationProduct,
    val drug: MedicationDrug = product.drug,
    val brand: String? = null,
    val form: MedicationForm = product.form,
    val doseUnit: DoseUnit = DoseUnit.Mg,
    val currentDose: Double? = null,
    val schedule: DoseSchedule = product.usualSchedule,
    val reminder: Boolean = false,
    val startedOn: LocalDate? = null,
    val addedOn: LocalDate,
) {
    val injected: Boolean get() = form == MedicationForm.Injection
}

/** The medicine itself, by its generic name. */
@Serializable
enum class MedicationDrug(val wire: String) {
    @SerialName("semaglutide")
    Semaglutide("semaglutide"),

    @SerialName("tirzepatide")
    Tirzepatide("tirzepatide"),

    @SerialName("liraglutide")
    Liraglutide("liraglutide"),

    @SerialName("dulaglutide")
    Dulaglutide("dulaglutide"),

    @SerialName("other")
    Other("other"),
    ;

    companion object {
        fun fromWire(wire: String?): MedicationDrug = entries.firstOrNull { it.wire == wire } ?: Other
    }
}

@Serializable
enum class MedicationForm(val wire: String) {
    @SerialName("injection")
    Injection("injection"),

    @SerialName("pill")
    Pill("pill"),
    ;

    companion object {
        fun fromWire(wire: String?): MedicationForm = entries.firstOrNull { it.wire == wire } ?: Injection
    }
}

@Serializable
enum class DoseUnit(val wire: String) {
    @SerialName("mg")
    Mg("mg"),

    /** Compounded vials, drawn up in syringe units. */
    @SerialName("units")
    Units("units"),

    @SerialName("mcg")
    Mcg("mcg"),
    ;

    companion object {
        fun fromWire(wire: String?): DoseUnit = entries.firstOrNull { it.wire == wire } ?: Mg
    }
}

/**
 * The picker's entries (spec, "Medication list"). The dose [chips] are the label's steps, as
 * shortcuts: a typed dose is always accepted. `TODO(clinician)`: check the steps against the
 * current labels before launch.
 */
@Serializable
enum class MedicationProduct(
    val wire: String,
    val drug: MedicationDrug,
    val form: MedicationForm,
    val usualSchedule: DoseSchedule,
    val chips: List<Double>,
    /** Free text names it: compounded and other medicines. */
    val named: Boolean = false,
    /** The person picks the form: only for other medicines. */
    val eitherForm: Boolean = false,
) {
    @SerialName("wegovy")
    Wegovy("wegovy", MedicationDrug.Semaglutide, MedicationForm.Injection, DoseSchedule.WEEKLY, listOf(0.25, 0.5, 1.0, 1.7, 2.4)),

    @SerialName("ozempic")
    Ozempic("ozempic", MedicationDrug.Semaglutide, MedicationForm.Injection, DoseSchedule.WEEKLY, listOf(0.25, 0.5, 1.0, 2.0)),

    @SerialName("rybelsus")
    Rybelsus("rybelsus", MedicationDrug.Semaglutide, MedicationForm.Pill, DoseSchedule.DAILY, emptyList()),

    @SerialName("wegovy_pill")
    WegovyPill("wegovy_pill", MedicationDrug.Semaglutide, MedicationForm.Pill, DoseSchedule.DAILY, emptyList()),

    @SerialName("zepbound")
    Zepbound("zepbound", MedicationDrug.Tirzepatide, MedicationForm.Injection, DoseSchedule.WEEKLY, listOf(2.5, 5.0, 7.5, 10.0, 12.5, 15.0)),

    @SerialName("mounjaro")
    Mounjaro("mounjaro", MedicationDrug.Tirzepatide, MedicationForm.Injection, DoseSchedule.WEEKLY, listOf(2.5, 5.0, 7.5, 10.0, 12.5, 15.0)),

    @SerialName("saxenda")
    Saxenda("saxenda", MedicationDrug.Liraglutide, MedicationForm.Injection, DoseSchedule.DAILY, listOf(0.6, 1.2, 1.8, 2.4, 3.0)),

    @SerialName("victoza")
    Victoza("victoza", MedicationDrug.Liraglutide, MedicationForm.Injection, DoseSchedule.DAILY, listOf(0.6, 1.2, 1.8, 2.4, 3.0)),

    @SerialName("trulicity")
    Trulicity("trulicity", MedicationDrug.Dulaglutide, MedicationForm.Injection, DoseSchedule.WEEKLY, listOf(0.75, 1.5, 3.0, 4.5)),

    @SerialName("compounded_semaglutide")
    CompoundedSemaglutide("compounded_semaglutide", MedicationDrug.Semaglutide, MedicationForm.Injection, DoseSchedule.WEEKLY, emptyList(), named = true),

    @SerialName("compounded_tirzepatide")
    CompoundedTirzepatide("compounded_tirzepatide", MedicationDrug.Tirzepatide, MedicationForm.Injection, DoseSchedule.WEEKLY, emptyList(), named = true),

    @SerialName("other")
    Other("other", MedicationDrug.Other, MedicationForm.Injection, DoseSchedule.WEEKLY, emptyList(), named = true, eitherForm = true),
    ;

    /** Units are offered for compounded vials and other medicines; listed pens and pills are in mg. */
    val units: List<DoseUnit>
        get() = if (named) listOf(DoseUnit.Mg, DoseUnit.Units, DoseUnit.Mcg) else listOf(DoseUnit.Mg)

    companion object {
        fun fromWire(wire: String?): MedicationProduct = entries.firstOrNull { it.wire == wire } ?: Other
    }
}

/** When doses are due. Nothing is enforced: a late dose is just a dose logged on another day. */
@Serializable
sealed interface DoseSchedule {
    /** [isoDay] is ISO, Monday 1 to Sunday 7. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val isoDay: Int, val time: LocalTime) : DoseSchedule {
        val day: DayOfWeek get() = DayOfWeek(isoDay)

        constructor(day: DayOfWeek, time: LocalTime) : this(day.isoDayNumber, time)
    }

    @Serializable
    @SerialName("daily")
    data class Daily(val time: LocalTime) : DoseSchedule

    @Serializable
    @SerialName("as_needed")
    data object AsNeeded : DoseSchedule

    /** The time a reminder fires, for the schedules that have one. */
    val reminderTime: LocalTime?
        get() = when (this) {
            is Weekly -> time
            is Daily -> time
            is AsNeeded -> null
        }

    companion object {
        val DEFAULT_TIME = LocalTime(9, 0)
        val WEEKLY: DoseSchedule = Weekly(DayOfWeek.MONDAY, DEFAULT_TIME)
        val DAILY: DoseSchedule = Daily(DEFAULT_TIME)
        val AS_NEEDED: DoseSchedule = AsNeeded
    }
}

/**
 * Where an injection went, with the spec's wire values. Left and right are the person's own. The
 * list order is the tie-break for the next-site suggestion.
 */
@Serializable
enum class InjectionSite(val wire: String) {
    @SerialName("abdomen_upper_left")
    AbdomenUpperLeft("abdomen_upper_left"),

    @SerialName("abdomen_upper_right")
    AbdomenUpperRight("abdomen_upper_right"),

    @SerialName("abdomen_lower_left")
    AbdomenLowerLeft("abdomen_lower_left"),

    @SerialName("abdomen_lower_right")
    AbdomenLowerRight("abdomen_lower_right"),

    @SerialName("thigh_left")
    ThighLeft("thigh_left"),

    @SerialName("thigh_right")
    ThighRight("thigh_right"),

    @SerialName("arm_left")
    ArmLeft("arm_left"),

    @SerialName("arm_right")
    ArmRight("arm_right"),
    ;

    companion object {
        val DEFAULT: Set<InjectionSite> = entries.toSet()

        fun fromWire(wire: String?): InjectionSite? = entries.firstOrNull { it.wire == wire }
    }
}

/** A side effect, as the spec lists them. */
@Serializable
enum class Symptom(val wire: String) {
    @SerialName("nausea")
    Nausea("nausea"),

    @SerialName("vomiting")
    Vomiting("vomiting"),

    @SerialName("constipation")
    Constipation("constipation"),

    @SerialName("diarrhea")
    Diarrhea("diarrhea"),

    @SerialName("heartburn")
    Heartburn("heartburn"),

    @SerialName("stomach_pain")
    StomachPain("stomach_pain"),

    @SerialName("fatigue")
    Fatigue("fatigue"),

    @SerialName("headache")
    Headache("headache"),

    @SerialName("dizziness")
    Dizziness("dizziness"),

    @SerialName("injection_site")
    InjectionSite("injection_site"),

    @SerialName("other")
    Other("other"),
    ;

    companion object {
        fun fromWire(wire: String?): Symptom? = entries.firstOrNull { it.wire == wire }
    }
}

@Serializable
enum class Severity(val wire: String) {
    @SerialName("mild")
    Mild("mild"),

    @SerialName("moderate")
    Moderate("moderate"),

    @SerialName("severe")
    Severe("severe"),
    ;

    companion object {
        fun fromWire(wire: String?): Severity? = entries.firstOrNull { it.wire == wire }
    }
}

/** A day's side effects. Empty is a day with none, or none said. */
data class SymptomDay(val day: LocalDate, val symptoms: Map<Symptom, Severity>)

object Symptoms {
    /**
     * Whether these call for the line about getting care: severe vomiting or stomach pain.
     * `TODO(clinician)`: the wording and which symptoms trigger it.
     */
    fun needCare(symptoms: Map<Symptom, Severity>): Boolean =
        symptoms[Symptom.Vomiting] == Severity.Severe || symptoms[Symptom.StomachPain] == Severity.Severe
}

/**
 * A logged dose. A change of dose is simply a dose with a different amount; the history derives
 * "stepped up" from consecutive doses, so there is no titration table.
 *
 * @property day the local date of [takenAt], the key Trends and the summary read by.
 * @property site null for pills.
 */
data class MedicationDose(
    val id: String,
    val medicationId: String,
    val product: MedicationProduct,
    val drug: MedicationDrug,
    val brand: String?,
    val dose: Double,
    val unit: DoseUnit,
    val takenAt: Instant,
    val day: LocalDate,
    val site: InjectionSite?,
    val note: String?,
)

/** What the dose sheet saves. The medication's name and drug are copied in from the plan. */
data class DoseDraft(
    val medicationId: String,
    val dose: Double,
    val unit: DoseUnit,
    val takenAt: Instant,
    val site: InjectionSite?,
    val note: String? = null,
) {
    /** Why this can't be saved, or null. The spec's server check: more than 0, at most 100. */
    fun problem(): String? = when {
        !dose.isFinite() || dose <= 0.0 -> "dose"
        dose > MAX_DOSE -> "dose"
        (note?.length ?: 0) > MAX_NOTE -> "note"
        else -> null
    }

    companion object {
        const val MAX_DOSE = 100.0
        const val MAX_NOTE = 280
    }
}

/** Where a dose was logged from, for the one event that says a dose was logged. */
enum class DoseVia(val wire: String) {
    TodayCard("today_card"),
    Medication("medication"),
    Reminder("reminder"),
    AlreadyDid("already_did"),
}
