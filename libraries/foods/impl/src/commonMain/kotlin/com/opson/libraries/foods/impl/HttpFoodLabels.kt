package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.foods.BarcodeLinkReporter
import com.opson.libraries.foods.CarbBasis
import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelCheck
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.LabelSignals
import com.opson.libraries.foods.ServingUnit
import com.opson.libraries.foods.SubmissionCapture
import com.opson.libraries.foods.SubmitResult
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.isOfflineError
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The server's `LabelFactsJson`: nutrients by their `micros` key. */
@Serializable
data class LabelFactsDto(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val servingLabel: String? = null,
    val servingsPerContainer: Double? = null,
    val basis: String = CarbBasis.Unknown.wire,
    val perServing: Map<String, Double> = emptyMap(),
    val per100: Map<String, Double> = emptyMap(),
    val acknowledged: List<String> = emptyList(),
) {
    fun toFacts(): LabelFacts = LabelFacts(
        name = name,
        brand = brand,
        barcode = barcode,
        servingSize = servingSize,
        servingUnit = ServingUnit.fromWire(servingUnit),
        servingLabel = servingLabel,
        servingsPerContainer = servingsPerContainer,
        basis = CarbBasis.fromWire(basis),
        perServing = perServing.keyed(),
        per100 = per100.keyed(),
    )

    companion object {
        fun of(facts: LabelFacts, acknowledged: List<LabelCheckCode> = emptyList()) = LabelFactsDto(
            name = facts.name,
            brand = facts.brand,
            barcode = facts.barcode,
            servingSize = facts.servingSize,
            servingUnit = facts.servingUnit?.wire,
            servingLabel = facts.servingLabel,
            servingsPerContainer = facts.servingsPerContainer,
            basis = facts.basis.wire,
            perServing = facts.perServing.mapKeys { it.key.wire },
            per100 = facts.per100.mapKeys { it.key.wire },
            acknowledged = acknowledged.map { it.wire },
        )

        /** A key this app does not know yet is dropped, so a newer server never breaks an older app. */
        private fun Map<String, Double>.keyed(): Map<LabelNutrient, Double> =
            mapNotNull { (key, value) -> LabelNutrient.fromWire(key)?.let { it to value } }.toMap()
    }
}

@Serializable
data class LabelCheckDto(val code: String, val fields: List<String> = emptyList(), val expected: Double? = null, val actual: Double? = null) {
    fun toCheck(): LabelCheck? = LabelCheckCode.fromWire(code)?.let { LabelCheck(it, fields, expected, actual) }
}

@Serializable
data class LabelParseRequest(val front: String, val label: String)

@Serializable
data class LabelParseResponse(
    val parseId: String,
    val model: String = "",
    val readable: Boolean = true,
    val food: LabelFactsDto = LabelFactsDto(),
    val confidence: Map<String, Double> = emptyMap(),
    val checks: List<LabelCheckDto> = emptyList(),
)

@Serializable
data class FoodSubmissionRequest(
    val id: String,
    val parseId: String? = null,
    val origin: String,
    val barcode: String? = null,
    val front: String,
    val label: String,
    val food: LabelFactsDto,
    val customFoodId: String? = null,
    /** Builder mode only; a server from before it ignores both. */
    val ingredients: String? = null,
    val capture: CaptureDto? = null,
)

@Serializable
data class CaptureLineDto(val text: String, val confidence: Double = 1.0)

/** The phone's measured signals, as numbers and flags only, for the reviewer. */
@Serializable
data class LabelSignalsDto(
    val panelFound: Boolean,
    val headlineFound: Int,
    val ocrMinConfidence: Double? = null,
    val atwaterGap: Double? = null,
    val servingGap: Double? = null,
    val columns: Int,
    val asPrepared: Boolean,
    val dualLanguage: Boolean,
    val grounded: Int,
    val groundable: Int,
    val ungrounded: List<String> = emptyList(),
    val edited: Int,
    val medianLineHeight: Double? = null,
    val checksFailed: Int,
    val blockingFailed: Boolean,
) {
    companion object {
        fun of(s: LabelSignals) = LabelSignalsDto(
            s.panelFound, s.headlineFound, s.ocrMinConfidence, s.atwaterGap, s.servingGap, s.columns, s.asPrepared,
            s.dualLanguage, s.grounded, s.groundable, s.ungrounded, s.edited, s.medianLineHeight, s.checksFailed, s.blockingFailed,
        )
    }
}

/** The server's `CaptureJson`. Boxes stay on the phone: the server only needs the words and numbers. */
@Serializable
data class CaptureDto(
    val engine: String,
    val source: String,
    val lines: List<CaptureLineDto>,
    val draft: LabelFactsDto? = null,
    val score: Double? = null,
    val signals: LabelSignalsDto? = null,
) {
    companion object {
        fun of(capture: SubmissionCapture) = CaptureDto(
            engine = capture.engine,
            source = if (capture.fromCamera) "camera" else "library",
            lines = capture.lines.map { CaptureLineDto(it.text, it.confidence) },
            draft = capture.draft?.let { LabelFactsDto.of(it) },
            score = capture.score,
            signals = capture.signals?.let(LabelSignalsDto::of),
        )
    }
}

/** Every field the server sends: debug builds decode strictly, so a field left out here fails the call. */
@Serializable
data class FoodSubmissionResponse(
    val submissionId: String,
    val foodId: String,
    val status: String = "pending",
    val checks: List<LabelCheckDto> = emptyList(),
    val lane: String? = null,
)

@Serializable
data class BarcodeLinkRequest(val barcode: String, val foodId: String)

/**
 * `POST /v1/foods/labels/parse` and `POST /v1/foods/submissions`. Neither is
 * retried automatically: a read is a paid model call, and both carry two
 * photos; "Try again" is the person's. A read can take a while (the server
 * gives the model 90 s, and one more try on another model), so both the
 * request and socket timeouts are long. Nothing about the photos or the food
 * is logged.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpFoodLabels(
    private val networkClient: NetworkClient,
    private val attestation: ContributionAttestation = ContributionAttestation.None,
) : FoodLabels {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun parse(front: ByteArray, label: ByteArray): LabelParse {
        val request = LabelParseRequest(front = Base64.encode(front), label = Base64.encode(label))
        return networkClient.authedCall("foods.label_parse") { client ->
            val response = client.post(PARSE_PATH) {
                expectSuccess = false
                timeout {
                    requestTimeoutMillis = PARSE_TIMEOUT_MS
                    socketTimeoutMillis = PARSE_TIMEOUT_MS
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> response.body<LabelParseResponse>().let { body ->
                    LabelParse.Read(
                        parseId = body.parseId,
                        facts = body.food.toFacts(),
                        confidence = body.confidence,
                        checks = body.checks.mapNotNull { it.toCheck() },
                        readable = body.readable,
                    )
                }
                // 503: no key on the server. 404: a server from before the route.
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.NotFound -> LabelParse.Unavailable
                HttpStatusCode.TooManyRequests -> LabelParse.TooMany
                else -> LabelParse.Failed(offline = false)
            }
        }.getOrElse { LabelParse.Failed(offline = it.isOfflineError()) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun submit(submission: FoodSubmission): SubmitResult {
        val request = FoodSubmissionRequest(
            id = submission.id,
            parseId = submission.parseId,
            origin = submission.origin.wire,
            barcode = submission.barcode,
            front = Base64.encode(submission.front),
            label = Base64.encode(submission.label),
            food = LabelFactsDto.of(submission.facts, submission.acknowledged),
            customFoodId = submission.customFoodId,
            ingredients = submission.ingredients?.let { Base64.encode(it) },
            capture = submission.capture?.let(CaptureDto::of),
        )
        val device = attestation.headers(submission.id)
        return networkClient.authedCall("foods.submission") { client ->
            val response = client.post(SUBMIT_PATH) {
                expectSuccess = false
                device.forEach { (name, value) -> header(name, value) }
                timeout {
                    requestTimeoutMillis = SUBMIT_TIMEOUT_MS
                    socketTimeoutMillis = SUBMIT_TIMEOUT_MS
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK -> response.body<FoodSubmissionResponse>().let {
                    SubmitResult.Submitted(it.submissionId, it.foodId, it.lane)
                }
                HttpStatusCode.TooManyRequests -> SubmitResult.TooMany
                // Only a contribution refusal; any other 403 reads as an ordinary failure.
                HttpStatusCode.Forbidden -> if (RESTRICTED in response.bodyAsText()) SubmitResult.Restricted else SubmitResult.Failed(offline = false)
                else -> SubmitResult.Failed(offline = false)
            }
        }.getOrElse { SubmitResult.Failed(offline = it.isOfflineError()) }
    }

    internal companion object {
        const val PARSE_PATH = "/v1/foods/labels/parse"
        const val SUBMIT_PATH = "/v1/foods/submissions"

        /** The server's problem code when it won't take contributions from this account or device. */
        const val RESTRICTED = "contributions_restricted"

        /** The server allows the model 90 s, and a second model as long again when the first refuses. */
        const val PARSE_TIMEOUT_MS = 190_000L

        /** Two photos up a slow connection. */
        const val SUBMIT_TIMEOUT_MS = 60_000L
    }
}

/**
 * `POST /v1/foods/barcode-links`, in [appScope] like the miss report, so the
 * report outlives the screen that logged the food. Not retried: the server
 * counts one more pairing per call, and a lost report only undercounts.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpBarcodeLinkReporter(
    private val networkClient: NetworkClient,
    private val appScope: AppCoroutineScope,
) : BarcodeLinkReporter {

    override suspend fun report(barcode: String, foodId: String) {
        val request = BarcodeLinkRequest(barcode = barcode, foodId = foodId)
        appScope.launch {
            networkClient.authedCall("foods.barcode_link") { client ->
                client.post("/v1/foods/barcode-links") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
        }.join()
    }
}
