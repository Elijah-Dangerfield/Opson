package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.VisibilityPolicy

/**
 * Describe it with the mic, for previews and the leak test. Built through
 * [LoggerStateBuilder] over [LoggerFixtures], so the lines under a spoken
 * description are the same policy-resolved lines as under a typed one.
 */
object VoiceFixtures {
    /** What someone says in the plan's example, as the recognizer writes it. */
    const val SPOKEN = "2 eggs, toast with butter and a coffee"

    /**
     * Describe for breakfast with `release.voice` on. [parsedLines] puts the
     * read of "2 eggs and toast" under the field, as after an earlier
     * description.
     */
    fun describeState(
        policy: VisibilityPolicy,
        voice: VoiceCapture = VoiceCapture.Idle(),
        text: String = "",
        parsedLines: Boolean = false,
        enabled: Boolean = true,
    ): LoggerState {
        val review = if (parsedLines) LoggerFixtures.describeReview(policy) else DescribeReview()
        return LoggerStateBuilder.build(
            inputs = LoggerFixtures.inputs(basket = review.rows.map { it.toBasketItem() }),
            policy = policy,
            choices = LoggerChoices(
                slot = MealSlot.Breakfast,
                pane = LoggerPane.Describe,
                describeText = text,
                describeStatus = if (parsedLines) DescribeStatus.Parsed else DescribeStatus.Idle,
                review = review,
                voiceEnabled = enabled,
                voice = voice,
            ),
        )
    }

    /** Partway through saying it: the words so far fill the field live. */
    fun listening(policy: VisibilityPolicy): LoggerState =
        describeState(policy, VoiceCapture.Listening(prefix = "", heard = PARTIAL), text = PARTIAL)

    /**
     * Speaking more after a first description: "2 eggs and toast" was read
     * into lines, and "and a coffee" is being heard after it.
     */
    fun listeningOverLines(policy: VisibilityPolicy): LoggerState = describeState(
        policy = policy,
        voice = VoiceCapture.Listening(prefix = "2 eggs and toast ", heard = "and a coffee"),
        text = "2 eggs and toast and a coffee",
        parsedLines = true,
    )

    fun transcribing(policy: VisibilityPolicy): LoggerState =
        describeState(policy, VoiceCapture.Transcribing(prefix = "", heard = SPOKEN), text = SPOKEN)

    fun notice(policy: VisibilityPolicy, notice: VoiceNotice): LoggerState =
        describeState(policy, VoiceCapture.Notice(notice))

    private const val PARTIAL = "2 eggs, toast with butter and"
}
