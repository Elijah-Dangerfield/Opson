package com.opson.features.logger.impl

import com.opson.libraries.foods.LabelDraft
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.foods.LabelTextParser
import com.opson.libraries.scanner.TextRecognition
import com.opson.libraries.scanner.TextRecognizer
import me.tatarka.inject.annotations.Inject

/** A label photo read on the phone: its OCR lines and the parser's draft of them. */
class PhoneReading(val engine: String, val lines: List<LabelTextLine>, val draft: LabelDraft)

sealed interface PhoneRead {
    data class Read(val reading: PhoneReading) : PhoneRead

    /** This phone can't read text right now (no engine, or its model is still downloading). */
    data object Unavailable : PhoneRead
    data object Failed : PhoneRead
}

/**
 * Builder mode's label reading, all on the phone (ai-options.md, "On the phone"): text
 * recognition (Vision on iOS, ML Kit on Android) over the still, then [LabelTextParser]. Free,
 * offline, and nothing leaves the phone until the person sends what they checked.
 */
@Inject
class PhoneLabelReader(private val recognizer: TextRecognizer) {
    suspend fun read(jpeg: ByteArray): PhoneRead = when (val result = recognizer.recognize(jpeg)) {
        is TextRecognition.Read -> {
            val lines = result.text.lines.map { LabelTextLine(it.text, it.confidence, it.left, it.top, it.width, it.height) }
            PhoneRead.Read(PhoneReading(result.text.engine, lines, LabelTextParser.parse(lines)))
        }
        TextRecognition.Unavailable -> PhoneRead.Unavailable
        TextRecognition.Failed -> PhoneRead.Failed
    }
}
