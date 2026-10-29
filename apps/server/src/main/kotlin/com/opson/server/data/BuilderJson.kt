package com.opson.server.data

import com.opson.server.domain.AutoApproveMode
import com.opson.server.domain.CaptureLine
import com.opson.server.domain.CaptureSource
import com.opson.server.domain.CloudCheck
import com.opson.server.domain.Grounding
import com.opson.server.domain.LabelCapture
import com.opson.server.domain.Lane
import com.opson.server.domain.RoutingDecision
import com.opson.server.domain.TrustLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One OCR line as the app sends it and the submission keeps it. */
@Serializable
data class CaptureLineJson(val text: String, val confidence: Double = 1.0)

/**
 * What the phone read off the label, as `POST /v1/foods/submissions` takes it (`capture`, from
 * app 0.3 in Builder mode) and `food_submissions.capture` keeps it. [signals] is the phone's
 * measured signals object, stored as sent for the reviewer; nothing routes on it but [score].
 */
@Serializable
data class CaptureJson(
    val engine: String? = null,
    val source: String? = null,
    val lines: List<CaptureLineJson> = emptyList(),
    val draft: LabelFactsJson? = null,
    val score: Double? = null,
    val signals: JsonObject? = null,
) {
    fun toCapture(): LabelCapture = LabelCapture(
        engine = engine,
        source = CaptureSource.fromWire(source),
        lines = lines.map { CaptureLine(it.text, it.confidence) },
        draft = draft?.toFacts(),
        score = score,
        signalsJson = signals?.let { BuilderJson.json.encodeToString(JsonObject.serializer(), it) },
    )

    companion object {
        fun of(capture: LabelCapture) = CaptureJson(
            engine = capture.engine,
            source = capture.source.wire,
            lines = capture.lines.map { CaptureLineJson(it.text, it.confidence) },
            draft = capture.draft?.let { LabelFactsJson.of(it) },
            score = capture.score,
            signals = capture.signalsJson?.let { BuilderJson.json.parseToJsonElement(it) as? JsonObject },
        )
    }
}

@Serializable
data class GroundingJson(val grounded: Int, val groundable: Int, val missing: List<String> = emptyList())

@Serializable
data class CloudCheckJson(
    val model: String,
    val readable: Boolean,
    val agrees: Boolean,
    val differs: List<String> = emptyList(),
    val latencyMs: Long = 0,
)

/** `food_submissions.routing`: codes and numbers only, never a value from the label. */
@Serializable
data class RoutingJson(
    val lane: String,
    val wouldAutoApprove: Boolean,
    val reasons: List<String> = emptyList(),
    val confidence: Double? = null,
    val trust: String,
    val mode: String,
    val grounding: GroundingJson? = null,
    val cloud: CloudCheckJson? = null,
    val audit: Boolean = false,
) {
    fun toDecision(): RoutingDecision = RoutingDecision(
        lane = Lane.fromWire(lane) ?: Lane.Review,
        wouldAutoApprove = wouldAutoApprove,
        reasons = reasons,
        confidence = confidence,
        trust = TrustLevel.entries.firstOrNull { it.wire == trust } ?: TrustLevel.Anyone,
        mode = AutoApproveMode.fromWire(mode) ?: AutoApproveMode.Shadow,
        grounding = grounding?.let { Grounding(it.grounded, it.groundable, it.missing) },
        cloud = cloud?.let { CloudCheck(it.model, it.readable, it.agrees, it.differs, it.latencyMs) },
        audit = audit,
    )

    companion object {
        fun of(decision: RoutingDecision) = RoutingJson(
            lane = decision.lane.wire,
            wouldAutoApprove = decision.wouldAutoApprove,
            reasons = decision.reasons,
            confidence = decision.confidence,
            trust = decision.trust.wire,
            mode = decision.mode.wire,
            grounding = decision.grounding?.let { GroundingJson(it.grounded, it.groundable, it.missing) },
            cloud = decision.cloud?.let { CloudCheckJson(it.model, it.readable, it.agrees, it.differs, it.latencyMs) },
            audit = decision.audit,
        )
    }
}

internal object BuilderJson {
    val json = Json { ignoreUnknownKeys = true }

    fun capture(capture: LabelCapture): String = json.encodeToString(CaptureJson.serializer(), CaptureJson.of(capture))

    fun capture(text: String): LabelCapture = json.decodeFromString(CaptureJson.serializer(), text).toCapture()

    fun routing(decision: RoutingDecision): String = json.encodeToString(RoutingJson.serializer(), RoutingJson.of(decision))

    fun routing(text: String): RoutingDecision = json.decodeFromString(RoutingJson.serializer(), text).toDecision()

    fun element(text: String): JsonElement = json.parseToJsonElement(text)
}
