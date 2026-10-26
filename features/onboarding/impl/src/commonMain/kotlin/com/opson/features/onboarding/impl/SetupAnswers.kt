package com.opson.features.onboarding.impl

import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalDate

/** The four setup questions, in order. Every one can be skipped. */
enum class SetupQuestion(val number: Int, val multiSelect: Boolean) {
    /** Why are you tracking? */
    Reason(1, multiSelect = true),

    /** How do you feel about seeing numbers? */
    Feeling(2, multiSelect = false),

    /** What do you want to notice? */
    Focus(3, multiSelect = true),

    /** The optional one: numbers have been hard before. A single toggle. */
    Hard(4, multiSelect = true),
    ;

    val options: List<SetupOption>
        get() = when (this) {
            Reason -> Why.entries
            Feeling -> NumbersFeel.entries
            Focus -> Notice.entries
            Hard -> listOf(HardBefore)
        }

    companion object {
        val Count: Int = entries.size

        fun of(number: Int): SetupQuestion? = entries.firstOrNull { it.number == number }
    }
}

/** One answer on one question. [key] is what `Settings.onboarding` stores. */
sealed interface SetupOption {
    val key: String
}

enum class Why(override val key: String) : SetupOption {
    Curious("curious"),
    Performance("performance"),
    EatingEnough("eating_enough"),
    EatingRegularly("eating_regularly"),
    MedicalNeed("medical_need"),
    ChangingWeight("changing_weight"),
    WorkingWithProvider("working_with_provider"),
}

enum class NumbersFeel(override val key: String) : SetupOption {
    LoveThem("love_them"),
    SomeAreFine("some_are_fine"),
    RatherNot("rather_not"),
    NotSure("not_sure"),
}

enum class Notice(override val key: String) : SetupOption {
    Amounts("amounts"),
    Nutrients("nutrients"),
    Patterns("patterns"),
    HowEatingFeels("how_eating_feels"),
}

/** "Numbers or tracking have been hard for me before". Stored as `OnboardingAnswers.hard`. */
data object HardBefore : SetupOption {
    override val key: String = "hard"
}

/** An option as the screen shows it. */
data class SetupChoice(val option: SetupOption, val selected: Boolean)

/**
 * What the user said. Never sent to telemetry: the hard answer is close to a
 * clinical disclosure.
 */
data class SetupAnswers(
    val why: Set<Why> = emptySet(),
    val feel: NumbersFeel? = null,
    val notice: Set<Notice> = emptySet(),
    val hard: Boolean = false,
) {
    fun has(option: SetupOption): Boolean = when (option) {
        is Why -> option in why
        is NumbersFeel -> feel == option
        is Notice -> option in notice
        HardBefore -> hard
    }

    /** The answers after tapping [option]: the feel question replaces, everything else toggles. */
    fun tap(option: SetupOption): SetupAnswers = when (option) {
        is Why -> copy(why = why.toggle(option))
        is NumbersFeel -> copy(feel = option)
        is Notice -> copy(notice = notice.toggle(option))
        HardBefore -> copy(hard = !hard)
    }

    fun choices(question: SetupQuestion): List<SetupChoice> =
        question.options.map { SetupChoice(it, selected = has(it)) }

    /** Kept in question order, so the stored lists read the same every time. */
    fun toSettings(): OnboardingAnswers = OnboardingAnswers(
        why = Why.entries.filter { it in why }.map { it.key },
        feel = feel?.key,
        notice = Notice.entries.filter { it in notice }.map { it.key },
        hard = hard,
    )

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}

/**
 * The settings onboarding leaves behind: [preset] with no overrides and the
 * pause off, the answers, and for Sprint the day it started. The caller saves
 * this without stamping `changedAt`, so Modes says "Set by you during setup".
 */
internal fun Settings.startingWith(preset: PresetId, answers: SetupAnswers, today: LocalDate): Settings = copy(
    numbers = NumbersSettings(preset = preset),
    onboarding = answers.toSettings(),
    sprintStartedOn = if (preset == PresetId.Sprint) today else sprintStartedOn,
)
