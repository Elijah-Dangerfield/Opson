@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.logger.impl

import com.opson.features.builder.BuildersEnabled
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.foods.BuilderApplicationForm
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.FoodCheckResult
import com.opson.libraries.foods.FoodChecks
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.foods.LabelModelResult
import com.opson.libraries.foods.LabelTextLine
import com.opson.libraries.foods.LabelTextModel
import com.opson.libraries.scanner.RecognizedLine
import com.opson.libraries.scanner.RecognizedText
import com.opson.libraries.scanner.TextRecognition
import com.opson.libraries.scanner.TextRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

fun buildersFlag(on: Boolean): BuildersEnabled = BuildersEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("builders" to on))
    },
)

/** An active Builder, or nobody in the program. */
class FakeBuilderProgram(active: Boolean = false) : BuilderProgram {
    private val state = MutableStateFlow<BuilderStanding?>(
        BuilderStanding(status = if (active) BuilderStatus.Active else BuilderStatus.None, trust = if (active) BuilderTrust.Builder else BuilderTrust.Anyone),
    )
    override val standing: StateFlow<BuilderStanding?> = state

    override suspend fun refresh(): BuilderResult = BuilderResult.Done(checkNotNull(state.value))
    override suspend fun sendApplication(form: BuilderApplicationForm): BuilderResult = refresh()
    override suspend fun withdraw(): BuilderResult = refresh()
    override suspend fun leave(): BuilderResult = refresh()
    override suspend fun setPhotoRetention(keep: Boolean): BuilderResult = refresh()
    override suspend fun decisionSeen(id: String): Boolean = false
    override suspend fun markDecisionSeen(id: String) = Unit
}

/**
 * Reads [lines] off any photo, or answers [result] when set. [front] answers for the photo whose
 * bytes are [frontJpeg] instead, so a test can give the front of a pack its own text. A line in
 * [hazy] reads with a low confidence, as a blurry digit does.
 */
@HiddenFromObjC
class FakeTextRecognizer(
    var lines: List<String> = emptyList(),
    var result: TextRecognition? = null,
    var frontJpeg: ByteArray? = null,
    var front: List<RecognizedLine> = emptyList(),
    var hazy: Set<String> = emptySet(),
) : TextRecognizer {
    var calls = 0

    override suspend fun recognize(jpeg: ByteArray): TextRecognition {
        calls++
        if (frontJpeg?.contentEquals(jpeg) == true) return TextRecognition.Read(RecognizedText(front, 1200, 1600, "vision"))
        return result ?: TextRecognition.Read(
            RecognizedText(lines.map { RecognizedLine(it, if (it in hazy) 0.4 else 0.95, 0.1, 0.0, 0.8, 0.02) }, 1200, 1600, "vision"),
        )
    }
}

/** Answers [answer] for any text, and counts the asks. */
@HiddenFromObjC
class FakeLabelTextModel(var answer: LabelModelResult = LabelModelResult.Unavailable) : LabelTextModel {
    var calls = 0

    override suspend fun extract(lines: List<LabelTextLine>): LabelModelResult {
        calls++
        return answer
    }
}

fun labelModelFlag(on: Boolean): LabelModelEnabled = LabelModelEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("labels" to mapOf("onDeviceModel" to on))
    },
)

fun phoneReader(
    recognizer: TextRecognizer,
    model: LabelTextModel = FakeLabelTextModel(),
    modelOn: Boolean = false,
): PhoneLabelReader = PhoneLabelReader(recognizer, model, labelModelFlag(modelOn))

class FakeFoodChecks(var answer: FoodCheckResult = FoodCheckResult.Confirmed) : FoodChecks {
    val sent = mutableListOf<List<LabelTextLine>>()

    override suspend fun check(id: String, foodId: String, engine: String, lines: List<LabelTextLine>): FoodCheckResult {
        sent += lines
        return answer
    }
}

class RecordingFoodReports(var answer: FoodReportResult = FoodReportResult.Sent(applied = false)) : FoodReports {
    val sent = mutableListOf<FoodReport>()

    override suspend fun send(report: FoodReport): FoodReportResult {
        sent += report
        return answer
    }
}

/** A US granola panel, as the phone's OCR returns it: 230 kcal, 3 g protein, 37 g carbs, 8 g fat in 55 g. */
val GRANOLA_PANEL = listOf(
    "Nutrition Facts",
    "8 servings per container",
    "Serving size 2/3 cup (55g)",
    "Amount per serving",
    "Calories 230",
    "Total Fat 8g 10%",
    "Sodium 160mg 7%",
    "Total Carbohydrate 37g 13%",
    "Dietary Fiber 4g",
    "Total Sugars 12g",
    "Protein 3g",
)
