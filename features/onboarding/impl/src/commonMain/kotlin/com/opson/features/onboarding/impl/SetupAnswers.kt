package com.opson.features.onboarding.impl

import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId

/**
 * The setup steps that take an answer, in order: three questions, then the optional statement.
 * Every one can be skipped. [eventName] is what `onboarding.step_viewed` carries, never an index.
 */
enum class SetupQuestion(val number: Int, val multiSelect: Boolean, val eventName: String) {
    /** Why are you tracking? */
    Reason(1, multiSelect = true, eventName = "question_1"),

    /** How do you feel about seeing numbers? */
    Feeling(2, multiSelect = false, eventName = "question_2"),

    /** What do you want to notice? */
    Focus(3, multiSelect = false, eventName = "question_3"),

    /**
     * "Numbers or tracking have been hard for me before": a statement to agree with, never a
     * question about a diagnosis. Agreeing unlocks the gentler presets.
     */
    Statement(4, multiSelect = false, eventName = "statement"),
    ;

    val options: List<SetupOption>
        get() = when (this) {
            Reason -> Why.entries
            Feeling -> NumbersFeel.entries
            Focus -> Notice.entries
            Statement -> StatementAnswer.entries
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

/** The answer to the statement. Only [Agree] is stored, as `OnboardingAnswers.hard`. */
enum class StatementAnswer(override val key: String) : SetupOption {
    Agree("agree"),
    NotReally("not_really"),
}

/** An option as the screen shows it. */
data class SetupChoice(val option: SetupOption, val selected: Boolean)

/**
 * What the user said. Never sent to telemetry: agreeing with the statement is close to a
 * clinical disclosure.
 */
data class SetupAnswers(
    val why: Set<Why> = emptySet(),
    val feel: NumbersFeel? = null,
    val notice: Notice? = null,
    val statement: StatementAnswer? = null,
) {
    /** The person agreed that numbers or tracking have been hard for them. */
    val hard: Boolean get() = statement == StatementAnswer.Agree

    /** Nothing said that could shape a starting point. "Not really" says nothing on its own. */
    val isEmpty: Boolean get() = why.isEmpty() && feel == null && notice == null && !hard

    fun has(option: SetupOption): Boolean = when (option) {
        is Why -> option in why
        is NumbersFeel -> feel == option
        is Notice -> notice == option
        is StatementAnswer -> statement == option
    }

    /** The answers after tapping [option]: the multi-select question toggles, the others replace. */
    fun tap(option: SetupOption): SetupAnswers = when (option) {
        is Why -> copy(why = why.toggle(option))
        is NumbersFeel -> copy(feel = option)
        is Notice -> copy(notice = option)
        is StatementAnswer -> copy(statement = option)
    }

    fun choices(question: SetupQuestion): List<SetupChoice> =
        question.options.map { SetupChoice(it, selected = has(it)) }

    /** Kept in question order, so the stored lists read the same every time. */
    fun toSettings(): OnboardingAnswers = OnboardingAnswers(
        why = Why.entries.filter { it in why }.map { it.key },
        feel = feel?.key,
        notice = listOfNotNull(notice?.key),
        hard = hard,
    )

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}

/**
 * The settings onboarding leaves behind: [preset] with no overrides and the
 * pause off, and the answers. The caller saves this without stamping
 * `changedAt`, so Modes says "Set by you during setup".
 */
internal fun Settings.startingWith(preset: PresetId, answers: SetupAnswers): Settings = copy(
    numbers = NumbersSettings(preset = preset),
    onboarding = answers.toSettings(),
)
