package com.opson.features.logger.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelModelMerge
import com.opson.libraries.foods.LabelModelResult
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.foods.LabelTextModel
import com.opson.libraries.foods.LabelTextParser
import com.opson.libraries.foods.PackFrontText
import com.opson.libraries.scanner.TextRecognition
import com.opson.libraries.scanner.TextRecognizer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * A label photo read on the phone: its OCR lines and the parser's draft of them. [model] names the
 * on-device model that had a say in the draft, when one did (`labels.onDeviceModel`).
 */
class PhoneReading(val engine: String, val lines: List<LabelTextLine>, val draft: LabelDraft, val model: String? = null)

sealed interface PhoneRead {
    data class Read(val reading: PhoneReading) : PhoneRead

    /** This phone can't read text right now (no engine, or its model is still downloading). */
    data object Unavailable : PhoneRead
    data object Failed : PhoneRead
}

/**
 * Label reading, all on the phone, for everyone (ai-options.md, "On the phone"; v1 has no server
 * AI): text recognition (Vision on iOS, ML Kit on Android) over the still, then [LabelTextParser].
 * Free, offline, and nothing leaves the phone until the person sends what they checked.
 *
 * With `labels.onDeviceModel` on and a model on the phone (Foundation Models on iOS 26), the OCR
 * text also goes to that model and [LabelModelMerge] lays its reading over the parser's: it can
 * fill a gap with a number the text holds, or mark a field the two disagree on.
 */
@Inject
class PhoneLabelReader(
    private val recognizer: TextRecognizer,
    private val model: LabelTextModel,
    private val modelEnabled: LabelModelEnabled,
) {
    suspend fun read(jpeg: ByteArray): PhoneRead = when (val result = recognizer.recognize(jpeg)) {
        is TextRecognition.Read -> {
            val lines = result.text.lines.map { LabelTextLine(it.text, it.confidence, it.left, it.top, it.width, it.height) }
            PhoneRead.Read(withModel(PhoneReading(result.text.engine, lines, LabelTextParser.parse(lines))))
        }
        TextRecognition.Unavailable -> PhoneRead.Unavailable
        TextRecognition.Failed -> PhoneRead.Failed
    }

    /** A guess at the product's name from a photo of the front of the pack, or null. */
    suspend fun guessName(jpeg: ByteArray): String? = when (val result = recognizer.recognize(jpeg)) {
        is TextRecognition.Read -> PackFrontText.guessName(
            result.text.lines.map { LabelTextLine(it.text, it.confidence, it.left, it.top, it.width, it.height) },
        )
        else -> null
    }

    private suspend fun withModel(reading: PhoneReading): PhoneReading {
        if (!modelEnabled() || reading.lines.isEmpty()) return reading
        return when (val answer = model.extract(reading.lines)) {
            is LabelModelResult.Read -> PhoneReading(
                engine = reading.engine,
                lines = reading.lines,
                draft = LabelModelMerge.merge(reading.draft, answer.facts, reading.lines),
                model = answer.model,
            )
            LabelModelResult.Unavailable, LabelModelResult.Failed -> reading
        }
    }
}

/**
 * The on-device model's second opinion on a label's OCR text ([PhoneLabelReader]). A prototype,
 * off by default and not seeded on dev (it is not a release flag): turn it on for yourself in the
 * console to try it on an iPhone with Apple Intelligence. Phones without a model read as before.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LabelModelEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "On-device label model"
    override val path = "labels.onDeviceModel"
    override val default = false
    override val description = "Ask the phone's own language model (Foundation Models on iOS 26) to check the label's OCR text beside the parser"
}
