package com.opson

import com.opson.libraries.core.ShakeIntensity
import com.opson.libraries.core.ShakeMessage
import com.opson.libraries.core.ShakeMessageContext
import com.opson.libraries.core.ShakeMessageProvider
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.shake_early_10
import opson.libraries.resources.generated.resources.shake_early_10_sub
import opson.libraries.resources.generated.resources.shake_early_11
import opson.libraries.resources.generated.resources.shake_early_11_sub
import opson.libraries.resources.generated.resources.shake_early_12
import opson.libraries.resources.generated.resources.shake_early_12_sub
import opson.libraries.resources.generated.resources.shake_early_13_sub
import opson.libraries.resources.generated.resources.shake_early_14
import opson.libraries.resources.generated.resources.shake_early_14_sub
import opson.libraries.resources.generated.resources.shake_early_15
import opson.libraries.resources.generated.resources.shake_early_15_sub
import opson.libraries.resources.generated.resources.shake_early_16
import opson.libraries.resources.generated.resources.shake_early_16_sub
import opson.libraries.resources.generated.resources.shake_early_2
import opson.libraries.resources.generated.resources.shake_early_3
import opson.libraries.resources.generated.resources.shake_early_3_sub
import opson.libraries.resources.generated.resources.shake_early_4
import opson.libraries.resources.generated.resources.shake_early_5
import opson.libraries.resources.generated.resources.shake_early_5_sub
import opson.libraries.resources.generated.resources.shake_early_6
import opson.libraries.resources.generated.resources.shake_early_6_sub
import opson.libraries.resources.generated.resources.shake_early_7
import opson.libraries.resources.generated.resources.shake_early_7_sub
import opson.libraries.resources.generated.resources.shake_early_8
import opson.libraries.resources.generated.resources.shake_early_8_sub
import opson.libraries.resources.generated.resources.shake_early_9
import opson.libraries.resources.generated.resources.shake_early_9_sub
import opson.libraries.resources.generated.resources.shake_first_1
import opson.libraries.resources.generated.resources.shake_first_10
import opson.libraries.resources.generated.resources.shake_first_11
import opson.libraries.resources.generated.resources.shake_first_12
import opson.libraries.resources.generated.resources.shake_first_12_sub
import opson.libraries.resources.generated.resources.shake_first_13
import opson.libraries.resources.generated.resources.shake_first_13_sub
import opson.libraries.resources.generated.resources.shake_first_14
import opson.libraries.resources.generated.resources.shake_first_14_sub
import opson.libraries.resources.generated.resources.shake_first_15
import opson.libraries.resources.generated.resources.shake_first_15_sub
import opson.libraries.resources.generated.resources.shake_first_16
import opson.libraries.resources.generated.resources.shake_first_17
import opson.libraries.resources.generated.resources.shake_first_17_sub
import opson.libraries.resources.generated.resources.shake_first_18
import opson.libraries.resources.generated.resources.shake_first_18_sub
import opson.libraries.resources.generated.resources.shake_first_19
import opson.libraries.resources.generated.resources.shake_first_2
import opson.libraries.resources.generated.resources.shake_first_20
import opson.libraries.resources.generated.resources.shake_first_20_sub
import opson.libraries.resources.generated.resources.shake_first_3
import opson.libraries.resources.generated.resources.shake_first_4
import opson.libraries.resources.generated.resources.shake_first_5
import opson.libraries.resources.generated.resources.shake_first_5_sub
import opson.libraries.resources.generated.resources.shake_first_6
import opson.libraries.resources.generated.resources.shake_first_7
import opson.libraries.resources.generated.resources.shake_first_8
import opson.libraries.resources.generated.resources.shake_first_8_sub
import opson.libraries.resources.generated.resources.shake_first_9
import opson.libraries.resources.generated.resources.shake_frequent_10
import opson.libraries.resources.generated.resources.shake_frequent_10_sub
import opson.libraries.resources.generated.resources.shake_frequent_11
import opson.libraries.resources.generated.resources.shake_frequent_11_sub
import opson.libraries.resources.generated.resources.shake_frequent_12
import opson.libraries.resources.generated.resources.shake_frequent_12_sub
import opson.libraries.resources.generated.resources.shake_frequent_13
import opson.libraries.resources.generated.resources.shake_frequent_13_sub
import opson.libraries.resources.generated.resources.shake_frequent_14
import opson.libraries.resources.generated.resources.shake_frequent_14_sub
import opson.libraries.resources.generated.resources.shake_frequent_15
import opson.libraries.resources.generated.resources.shake_frequent_15_sub
import opson.libraries.resources.generated.resources.shake_frequent_16
import opson.libraries.resources.generated.resources.shake_frequent_16_sub
import opson.libraries.resources.generated.resources.shake_frequent_17
import opson.libraries.resources.generated.resources.shake_frequent_17_sub
import opson.libraries.resources.generated.resources.shake_frequent_18
import opson.libraries.resources.generated.resources.shake_frequent_19
import opson.libraries.resources.generated.resources.shake_frequent_19_sub
import opson.libraries.resources.generated.resources.shake_frequent_2
import opson.libraries.resources.generated.resources.shake_frequent_20
import opson.libraries.resources.generated.resources.shake_frequent_20_sub
import opson.libraries.resources.generated.resources.shake_frequent_21
import opson.libraries.resources.generated.resources.shake_frequent_21_sub
import opson.libraries.resources.generated.resources.shake_frequent_22
import opson.libraries.resources.generated.resources.shake_frequent_22_sub
import opson.libraries.resources.generated.resources.shake_frequent_23
import opson.libraries.resources.generated.resources.shake_frequent_23_sub
import opson.libraries.resources.generated.resources.shake_frequent_24_sub
import opson.libraries.resources.generated.resources.shake_frequent_25
import opson.libraries.resources.generated.resources.shake_frequent_25_sub
import opson.libraries.resources.generated.resources.shake_frequent_26
import opson.libraries.resources.generated.resources.shake_frequent_26_sub
import opson.libraries.resources.generated.resources.shake_frequent_27
import opson.libraries.resources.generated.resources.shake_frequent_27_sub
import opson.libraries.resources.generated.resources.shake_frequent_28
import opson.libraries.resources.generated.resources.shake_frequent_28_sub
import opson.libraries.resources.generated.resources.shake_frequent_29
import opson.libraries.resources.generated.resources.shake_frequent_29_sub
import opson.libraries.resources.generated.resources.shake_frequent_2_sub
import opson.libraries.resources.generated.resources.shake_frequent_30
import opson.libraries.resources.generated.resources.shake_frequent_30_sub
import opson.libraries.resources.generated.resources.shake_frequent_31
import opson.libraries.resources.generated.resources.shake_frequent_31_sub
import opson.libraries.resources.generated.resources.shake_frequent_32
import opson.libraries.resources.generated.resources.shake_frequent_32_sub
import opson.libraries.resources.generated.resources.shake_frequent_33
import opson.libraries.resources.generated.resources.shake_frequent_33_sub
import opson.libraries.resources.generated.resources.shake_frequent_3_sub
import opson.libraries.resources.generated.resources.shake_frequent_4
import opson.libraries.resources.generated.resources.shake_frequent_5
import opson.libraries.resources.generated.resources.shake_frequent_5_sub
import opson.libraries.resources.generated.resources.shake_frequent_6
import opson.libraries.resources.generated.resources.shake_frequent_6_sub
import opson.libraries.resources.generated.resources.shake_frequent_7
import opson.libraries.resources.generated.resources.shake_frequent_7_sub
import opson.libraries.resources.generated.resources.shake_frequent_8
import opson.libraries.resources.generated.resources.shake_frequent_8_sub
import opson.libraries.resources.generated.resources.shake_frequent_9
import opson.libraries.resources.generated.resources.shake_frequent_9_sub
import opson.libraries.resources.generated.resources.shake_repeat_10
import opson.libraries.resources.generated.resources.shake_repeat_11
import opson.libraries.resources.generated.resources.shake_repeat_11_sub
import opson.libraries.resources.generated.resources.shake_repeat_12
import opson.libraries.resources.generated.resources.shake_repeat_13
import opson.libraries.resources.generated.resources.shake_repeat_13_sub
import opson.libraries.resources.generated.resources.shake_repeat_14
import opson.libraries.resources.generated.resources.shake_repeat_14_sub
import opson.libraries.resources.generated.resources.shake_repeat_15
import opson.libraries.resources.generated.resources.shake_repeat_15_sub
import opson.libraries.resources.generated.resources.shake_repeat_16
import opson.libraries.resources.generated.resources.shake_repeat_16_sub
import opson.libraries.resources.generated.resources.shake_repeat_17
import opson.libraries.resources.generated.resources.shake_repeat_17_sub
import opson.libraries.resources.generated.resources.shake_repeat_18
import opson.libraries.resources.generated.resources.shake_repeat_18_sub
import opson.libraries.resources.generated.resources.shake_repeat_19
import opson.libraries.resources.generated.resources.shake_repeat_19_sub
import opson.libraries.resources.generated.resources.shake_repeat_2
import opson.libraries.resources.generated.resources.shake_repeat_2_sub
import opson.libraries.resources.generated.resources.shake_repeat_4
import opson.libraries.resources.generated.resources.shake_repeat_4_sub
import opson.libraries.resources.generated.resources.shake_repeat_5
import opson.libraries.resources.generated.resources.shake_repeat_5_sub
import opson.libraries.resources.generated.resources.shake_repeat_6
import opson.libraries.resources.generated.resources.shake_repeat_7
import opson.libraries.resources.generated.resources.shake_repeat_7_sub
import opson.libraries.resources.generated.resources.shake_repeat_8
import opson.libraries.resources.generated.resources.shake_repeat_8_sub
import opson.libraries.resources.generated.resources.shake_repeat_9
import opson.libraries.resources.generated.resources.shake_repeat_9_sub
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The shake dialog's easter-egg copy, from `strings_shake.xml`: a pool of lines that grows more
 * familiar the more the phone is shaken this run, with the person's name when there is one, and
 * never the same headline twice in five shakes.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ResourceShakeMessageProvider : ShakeMessageProvider {

    private val recentHeadlines = mutableListOf<String>()

    override suspend fun getMessage(context: ShakeMessageContext): ShakeMessage {
        val candidates = linesFor(context).map { it.resolve() }
        val available = candidates.filter { it.headline !in recentHeadlines }
        val chosen = available.ifEmpty { candidates }.random()
        recentHeadlines.add(chosen.headline)
        if (recentHeadlines.size > MAX_RECENT) recentHeadlines.removeAt(0)
        return chosen
    }

    private fun linesFor(context: ShakeMessageContext): List<Line> = when {
        context.shakeCount <= 0 -> firstShakeLines(context)
        context.shakeCount <= 2 -> earlyShakeLines(context)
        context.shakeCount <= 6 -> repeatShakeLines(context)
        else -> frequentShakerLines(context)
    }

    private fun firstShakeLines(context: ShakeMessageContext): List<Line> {
        val name = context.userName
        return buildList {
            add(Line.Plain(Res.string.shake_first_1, null))
            add(Line.Plain(Res.string.shake_first_2, null))
            add(Line.Plain(Res.string.shake_first_3, null))
            add(Line.Plain(Res.string.shake_first_4, null))
            add(Line.Plain(Res.string.shake_first_5, Res.string.shake_first_5_sub))
            add(Line.Plain(Res.string.shake_first_6, null))
            add(Line.Plain(Res.string.shake_first_7, null))
            add(Line.Plain(Res.string.shake_first_8, Res.string.shake_first_8_sub))
            add(Line.Plain(Res.string.shake_first_9, null))
            add(Line.Plain(Res.string.shake_first_10, null))
            if (name != null) {
                add(Line.Plain(Res.string.shake_first_11, null, name))
                add(Line.Plain(Res.string.shake_first_12, Res.string.shake_first_12_sub, name))
                add(Line.Plain(Res.string.shake_first_13, Res.string.shake_first_13_sub, name))
            }
            when (context.intensity) {
                ShakeIntensity.GENTLE -> {
                    add(Line.Plain(Res.string.shake_first_14, Res.string.shake_first_14_sub))
                    add(Line.Plain(Res.string.shake_first_15, Res.string.shake_first_15_sub))
                    add(Line.Plain(Res.string.shake_first_16, null))
                }
                ShakeIntensity.VIGOROUS -> {
                    add(Line.Plain(Res.string.shake_first_17, Res.string.shake_first_17_sub))
                    add(Line.Plain(Res.string.shake_first_18, Res.string.shake_first_18_sub))
                    add(Line.Plain(Res.string.shake_first_19, null))
                    add(Line.Plain(Res.string.shake_first_20, Res.string.shake_first_20_sub))
                }
                else -> {}
            }
        }
    }

    private fun earlyShakeLines(context: ShakeMessageContext): List<Line> {
        val name = context.userName
        return buildList {
            add(Line.Plain(Res.string.shake_first_1, null))
            add(Line.Plain(Res.string.shake_early_2, null))
            add(Line.Plain(Res.string.shake_early_3, Res.string.shake_early_3_sub))
            add(Line.Plain(Res.string.shake_early_4, null))
            add(Line.Plain(Res.string.shake_early_5, Res.string.shake_early_5_sub))
            add(Line.Plain(Res.string.shake_early_6, Res.string.shake_early_6_sub))
            add(Line.Plain(Res.string.shake_early_7, Res.string.shake_early_7_sub))
            add(Line.Plain(Res.string.shake_early_8, Res.string.shake_early_8_sub))
            add(Line.Plain(Res.string.shake_early_9, Res.string.shake_early_9_sub))
            add(Line.Plain(Res.string.shake_early_10, Res.string.shake_early_10_sub))
            if (name != null) {
                add(Line.Plain(Res.string.shake_early_11, Res.string.shake_early_11_sub, name))
                add(Line.Plain(Res.string.shake_early_12, Res.string.shake_early_12_sub, name))
                add(Line.Plain(Res.string.shake_first_12, Res.string.shake_early_13_sub, name))
            }
            when (context.intensity) {
                ShakeIntensity.GENTLE -> {
                    add(Line.Plain(Res.string.shake_early_14, Res.string.shake_early_14_sub))
                }
                ShakeIntensity.VIGOROUS -> {
                    add(Line.Plain(Res.string.shake_early_15, Res.string.shake_early_15_sub))
                    add(Line.Plain(Res.string.shake_early_16, Res.string.shake_early_16_sub))
                }
                else -> {}
            }
        }
    }

    private fun repeatShakeLines(context: ShakeMessageContext): List<Line> {
        val name = context.userName
        return buildList {
            add(Line.Plain(Res.string.shake_first_1, null))
            add(Line.Plain(Res.string.shake_repeat_2, Res.string.shake_repeat_2_sub))
            add(Line.Plain(Res.string.shake_early_8, null))
            add(Line.Plain(Res.string.shake_repeat_4, Res.string.shake_repeat_4_sub, context.shakeCount + 1))
            add(Line.Plain(Res.string.shake_repeat_5, Res.string.shake_repeat_5_sub))
            add(Line.Plain(Res.string.shake_repeat_6, null))
            add(Line.Plain(Res.string.shake_repeat_7, Res.string.shake_repeat_7_sub))
            add(Line.Plain(Res.string.shake_repeat_8, Res.string.shake_repeat_8_sub))
            add(Line.Plain(Res.string.shake_repeat_9, Res.string.shake_repeat_9_sub))
            add(Line.Plain(Res.string.shake_repeat_10, null))
            add(Line.Plain(Res.string.shake_repeat_11, Res.string.shake_repeat_11_sub))
            add(Line.Plain(Res.string.shake_repeat_12, null))
            add(Line.Plain(Res.string.shake_repeat_13, Res.string.shake_repeat_13_sub))
            add(Line.Plain(Res.string.shake_repeat_14, Res.string.shake_repeat_14_sub))
            if (name != null) {
                add(Line.Plain(Res.string.shake_repeat_15, Res.string.shake_repeat_15_sub, name))
                add(Line.Plain(Res.string.shake_repeat_16, Res.string.shake_repeat_16_sub, name))
                add(Line.Plain(Res.string.shake_repeat_17, Res.string.shake_repeat_17_sub, name))
            }
            when (context.intensity) {
                ShakeIntensity.GENTLE -> {
                    add(Line.Plain(Res.string.shake_repeat_18, Res.string.shake_repeat_18_sub))
                }
                ShakeIntensity.VIGOROUS -> {
                    add(Line.Plain(Res.string.shake_repeat_19, Res.string.shake_repeat_19_sub))
                }
                else -> {}
            }
        }
    }

    private fun frequentShakerLines(context: ShakeMessageContext): List<Line> {
        val name = context.userName
        val count = context.shakeCount + 1
        return buildList {
            add(Line.Plain(Res.string.shake_first_1, null))
            add(Line.Plain(Res.string.shake_frequent_2, Res.string.shake_frequent_2_sub))
            add(Line.Plain(Res.string.shake_repeat_7, Res.string.shake_frequent_3_sub))
            add(Line.Plain(Res.string.shake_frequent_4, null))
            add(Line.Counted(Res.plurals.shake_frequent_5, count, Res.string.shake_frequent_5_sub, count))
            add(Line.Plain(Res.string.shake_frequent_6, Res.string.shake_frequent_6_sub))
            add(Line.Plain(Res.string.shake_frequent_7, Res.string.shake_frequent_7_sub))
            add(Line.Plain(Res.string.shake_frequent_8, Res.string.shake_frequent_8_sub))
            add(Line.Plain(Res.string.shake_frequent_9, Res.string.shake_frequent_9_sub))
            add(Line.Plain(Res.string.shake_frequent_10, Res.string.shake_frequent_10_sub))
            add(Line.Plain(Res.string.shake_frequent_11, Res.string.shake_frequent_11_sub))
            add(Line.Plain(Res.string.shake_frequent_12, Res.string.shake_frequent_12_sub))
            add(Line.Plain(Res.string.shake_frequent_13, Res.string.shake_frequent_13_sub))
            add(Line.Plain(Res.string.shake_frequent_14, Res.string.shake_frequent_14_sub, count))
            add(Line.Plain(Res.string.shake_frequent_15, Res.string.shake_frequent_15_sub))
            add(Line.Plain(Res.string.shake_frequent_16, Res.string.shake_frequent_16_sub))
            add(Line.Plain(Res.string.shake_frequent_17, Res.string.shake_frequent_17_sub))
            add(Line.Plain(Res.string.shake_frequent_18, null))
            add(Line.Plain(Res.string.shake_frequent_19, Res.string.shake_frequent_19_sub))
            add(Line.Plain(Res.string.shake_frequent_20, Res.string.shake_frequent_20_sub))
            if (name != null) {
                add(Line.Plain(Res.string.shake_frequent_21, Res.string.shake_frequent_21_sub, name))
                add(Line.Plain(Res.string.shake_frequent_22, Res.string.shake_frequent_22_sub, name))
                add(Line.Counted(Res.plurals.shake_frequent_23, count, Res.string.shake_frequent_23_sub, name, count))
                add(Line.Plain(Res.string.shake_early_11, Res.string.shake_frequent_24_sub, name))
            }
            when (context.intensity) {
                ShakeIntensity.GENTLE -> {
                    add(Line.Plain(Res.string.shake_frequent_25, Res.string.shake_frequent_25_sub))
                    add(Line.Plain(Res.string.shake_frequent_26, Res.string.shake_frequent_26_sub))
                }
                ShakeIntensity.VIGOROUS -> {
                    add(Line.Plain(Res.string.shake_frequent_27, Res.string.shake_frequent_27_sub))
                    add(Line.Plain(Res.string.shake_frequent_28, Res.string.shake_frequent_28_sub))
                }
                else -> {}
            }
            if (count == 10) {
                add(Line.Plain(Res.string.shake_frequent_29, Res.string.shake_frequent_29_sub))
            } else if (count == 25) {
                add(Line.Plain(Res.string.shake_frequent_30, Res.string.shake_frequent_30_sub))
            } else if (count == 50) {
                add(Line.Plain(Res.string.shake_frequent_31, Res.string.shake_frequent_31_sub))
            } else if (count == 100) {
                add(Line.Plain(Res.string.shake_frequent_32, Res.string.shake_frequent_32_sub))
            } else if (count > 100) {
                add(Line.Plain(Res.string.shake_frequent_33, Res.string.shake_frequent_33_sub))
            }
        }
    }

    /** One candidate: a headline, with the name or the count it takes, and the line under it. */
    private sealed interface Line {
        suspend fun resolve(): ShakeMessage

        class Plain(val headline: StringResource, val subtext: StringResource?, vararg val args: Any) : Line {
            override suspend fun resolve() = ShakeMessage(getString(headline, *args), subtext?.let { getString(it) })
        }

        class Counted(val headline: PluralStringResource, val count: Int, val subtext: StringResource?, vararg val args: Any) : Line {
            override suspend fun resolve() = ShakeMessage(getPluralString(headline, count, *args), subtext?.let { getString(it) })
        }
    }

    private companion object {
        const val MAX_RECENT = 5
    }
}
