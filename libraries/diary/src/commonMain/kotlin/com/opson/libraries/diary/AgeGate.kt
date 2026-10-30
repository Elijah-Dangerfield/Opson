package com.opson.libraries.diary

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Who Opson is for, by age. Pure, so onboarding's gate, the goal flow and Today ask the same
 * question the same way. The reasoning, the sources and what other apps do are in
 * `docs/research/ed-safety-review.md`.
 *
 * - Under [MINIMUM_AGE]: no account. Calorie tracking is linked with disordered eating in
 *   teenagers, and Opson has no coaching or parent supervision to offer instead.
 * - [MINIMUM_AGE] to [ADULT_AGE]: an account, with no weight goal or deficit set up for them and
 *   celebrations without counts ([AgeBand.OlderTeen]).
 * - Unknown: someone who never said. Treated as an adult, as before the gate existed.
 *
 * TODO(clinician): review the ages and what the older-teen defaults leave out before 1.0.
 */
object AgeGate {
    const val MINIMUM_AGE: Int = 16
    const val ADULT_AGE: Int = 18

    /** Whole years from [birthDate] to [today]; null for a birth date in the future. */
    fun ageOn(birthDate: LocalDate, today: LocalDate): Int? {
        if (birthDate > today) return null
        var years = today.year - birthDate.year
        if (today < birthdayIn(birthDate, today.year)) years -= 1
        return years
    }

    fun bandOf(birthDate: LocalDate?, today: LocalDate): AgeBand {
        val age = birthDate?.let { ageOn(it, today) } ?: return AgeBand.Unknown
        return when {
            age < MINIMUM_AGE -> AgeBand.TooYoung
            age < ADULT_AGE -> AgeBand.OlderTeen
            else -> AgeBand.Adult
        }
    }

    /** The day someone born on [birthDate] turns [MINIMUM_AGE], when the gate opens for them. */
    fun opensOn(birthDate: LocalDate): LocalDate = birthdayIn(birthDate, birthDate.year + MINIMUM_AGE)

    /** The birthday in [year]. Someone born on 29 February has it on 1 March in a year without one. */
    private fun birthdayIn(birthDate: LocalDate, year: Int): LocalDate {
        val leapDay = birthDate.month == Month.FEBRUARY && birthDate.day == LEAP_DAY
        return if (leapDay && !isLeapYear(year)) {
            LocalDate(year, Month.FEBRUARY, LEAP_DAY - 1).plus(DatePeriod(days = 1))
        } else {
            LocalDate(year, birthDate.month, birthDate.day)
        }
    }

    private const val LEAP_DAY = 29

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

enum class AgeBand {
    /** No birth date to go on. */
    Unknown,

    /** Under [AgeGate.MINIMUM_AGE]. */
    TooYoung,

    /** [AgeGate.MINIMUM_AGE] up to [AgeGate.ADULT_AGE]. */
    OlderTeen,

    Adult,
}

/** This account's [AgeBand] on [today], from the birthday it gave. */
fun Settings.ageBand(today: LocalDate): AgeBand = AgeGate.bandOf(knownBirthDate, today)

/**
 * Whether the goal flow leaves out weights, weight goals and deficits: after "numbers have been
 * hard", and for anyone under [AgeGate.ADULT_AGE]. Guidance for teenagers is to avoid dieting
 * and weight talk altogether (`docs/research/ed-safety-review.md`), so Opson never plans a loss
 * for them. An account under [AgeGate.MINIMUM_AGE] can only exist from before the gate.
 */
fun Settings.weightPlansOff(today: LocalDate): Boolean =
    onboarding.hard || ageBand(today).let { it == AgeBand.TooYoung || it == AgeBand.OlderTeen }
