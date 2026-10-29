package com.opson.server.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** Where a submission went: a person's queue, the auto-approval lane, or straight in from the owner. */
enum class Lane(val wire: String) {
    Review("review"),
    Auto("auto"),
    Owner("owner"),
    ;

    companion object {
        fun fromWire(value: String?): Lane? = entries.firstOrNull { it.wire == value }
    }
}

/** How the label photo reached the app: the camera (a pack in hand) or the photo library. */
enum class CaptureSource(val wire: String) {
    Camera("camera"),
    Library("library"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): CaptureSource = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** One line the phone's OCR read off the label photo. */
data class CaptureLine(val text: String, val confidence: Double)

/**
 * What the phone read off the label before the person edited anything, sent with a Builder's
 * submission (V30 `capture`). [draft] is its parser's reading; [score] and [signalsJson] are its
 * measured confidence and the signals behind it, kept as sent for review. The server never
 * trusts these on their own: it re-derives grounding and the checks itself.
 */
data class LabelCapture(
    val engine: String?,
    val source: CaptureSource,
    val lines: List<CaptureLine>,
    val draft: LabelFacts?,
    val score: Double?,
    val signalsJson: String?,
)

/** The headline fields that decide approval (ai-options.md, "When the backend's answer can skip a person"). */
object Headline {
    val NUTRIENTS = listOf(NutrientKey.Energy, NutrientKey.Protein, NutrientKey.Carbs, NutrientKey.Fat)
    const val SERVING = "serving_size"

    /** Every headline form key. */
    val KEYS: List<String> = listOf(SERVING) + NUTRIENTS.map { it.wire }
}

/** Which submitted headline numbers appear in the phone's OCR text. */
data class Grounding(val grounded: Int, val groundable: Int, val missing: List<String>) {
    val complete: Boolean get() = groundable > 0 && grounded == groundable
}

/**
 * The cheapest strong check that a number came from the photo: each submitted headline value
 * must appear among the number tokens of the label's OCR text. The phone runs its own copy
 * (`LabelConfidence` in `:libraries:foods`); this one is what the server relies on.
 *
 * Numbers are compared at the precision the label printed them ("12,5 g" matches 12.5, "12 g"
 * matches 12.4), energy also as kJ (a panel may print only kJ beside the kcal the form holds),
 * and a few OCR slips are repaired first ("Og" is 0 g, "l" before a digit is 1).
 */
object LabelGrounding {
    // A number followed by % is a daily value, never an amount, so it grounds nothing.
    private val NUMBER = Regex("""(?<![\d.,])(\d{1,5}(?:[.,]\d{1,3})?)(?![\d])(?![.,]\d)(?!\s?%)""")
    private val ZERO_AS_O = Regex("""(?<=^|[\s(/<])[Oo](?=\s?(?:g|mg|mcg|µg|kcal|kj|kJ|%)\b)""")
    private val ONE_AS_L = Regex("""(?<=^|[\s(/<])[lI](?=\d)""")
    private const val KJ_PER_KCAL = 4.184
    private const val KJ_TOLERANCE = 0.01

    fun tokens(lines: List<CaptureLine>): List<Number> = lines.flatMap { line ->
        val repaired = line.text.replace(ZERO_AS_O, "0").replace(ONE_AS_L, "1")
        NUMBER.findAll(repaired).map { match ->
            val raw = match.groupValues[1]
            val separator = raw.indexOfAny(charArrayOf('.', ','))
            val decimals = if (separator < 0) 0 else raw.length - separator - 1
            // "1,200" is a thousands comma on a US panel, not 1.2.
            val value = if (raw.contains(',') && decimals == 3) raw.replace(",", "").toDouble() else raw.replace(',', '.').toDouble()
            Number(value, if (raw.contains(',') && decimals == 3) 0 else decimals)
        }.toList()
    }

    data class Number(val value: Double, val decimals: Int)

    fun grounded(value: Double, tokens: List<Number>, isEnergy: Boolean = false): Boolean = tokens.any { token ->
        matches(value, token) || (isEnergy && abs(token.value - value * KJ_PER_KCAL) <= max(1.0, KJ_TOLERANCE * token.value))
    }

    private fun matches(value: Double, token: Number): Boolean {
        val step = 10.0.pow(-token.decimals)
        val rounded = (value / step).roundToLong() * step
        return abs(rounded - token.value) < step / 2 + 1e-9
    }

    /**
     * Row words per headline field, in the languages the phone's parser reads, folded to plain
     * lowercase. A field's number must sit on a line naming it (or the line after, where OCR
     * split the row), so a protein of 3 isn't grounded by the 3 in "2/3 cup".
     */
    private val ROW_WORDS: Map<String, List<String>> = mapOf(
        Headline.SERVING to listOf("serving", "portion", "porcion", "porzione", "per ", "pour ", "pro ", "size"),
        NutrientKey.Energy.wire to listOf("calorie", "energ", "brennwert", "kcal", "kj"),
        NutrientKey.Protein.wire to listOf("protein", "eiweiss", "eiwit"),
        NutrientKey.Carbs.wire to listOf("carbohydrate", "glucide", "kohlenhydrat", "carboidrat", "hidratos", "koolhydrat", "carbs"),
        NutrientKey.Fat.wire to listOf("fat", "lipide", "grasse", "fett", "grassi", "grasa", "vet"),
    )

    private fun fold(text: String): String = text.lowercase()
        .replace('ß', 's').replace("ss", "s")
        .map { c -> FOLDS[c] ?: c }.joinToString("")

    private val FOLDS = mapOf('é' to 'e', 'è' to 'e', 'ê' to 'e', 'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ö' to 'o', 'ü' to 'u', 'ó' to 'o', 'í' to 'i', 'ç' to 'c')

    /** The tokens on the lines that name [key], and on the line after each. Every token when no line names it. */
    fun tokensFor(key: String, lines: List<CaptureLine>): List<Number> {
        val words = ROW_WORDS[key]?.map(::fold) ?: return tokens(lines)
        val rows = lines.indices.filter { index -> fold(lines[index].text).let { text -> words.any { it in text } } }
        if (rows.isEmpty()) return tokens(lines)
        val picked = rows.flatMap { listOfNotNull(lines[it], lines.getOrNull(it + 1)) }.distinct()
        return tokens(picked)
    }

    /**
     * Grounds [facts] in [lines]: per serving where the submission has it, per 100 otherwise,
     * since a panel prints one or both and the form keeps what was printed.
     */
    fun check(facts: LabelFacts, lines: List<CaptureLine>): Grounding {
        val missing = mutableListOf<String>()
        var groundable = 0
        facts.servingSize?.let { size ->
            groundable++
            if (!grounded(size, tokensFor(Headline.SERVING, lines))) missing += Headline.SERVING
        }
        for (key in Headline.NUTRIENTS) {
            val printed = listOfNotNull(facts.perServing[key], facts.per100[key])
            if (printed.isEmpty()) continue
            groundable++
            val tokens = tokensFor(key.wire, lines)
            if (printed.none { grounded(it, tokens, isEnergy = key == NutrientKey.Energy) }) missing += key.wire
        }
        return Grounding(groundable - missing.size, groundable, missing)
    }
}

/** Two readings of the same headline fields, compared. */
object HeadlineAgreement {
    /**
     * Two independent reads of one printed panel should come to the same numbers once units are
     * converted, so this is tight: 1 kcal or 2% for energy, 0.2 g or 2% for grams, 2% for the
     * serving. Returns the fields that differ, or that [read] could not see.
     */
    fun readsDiffer(sent: LabelFacts, read: LabelFacts): List<String> = differences(sent, read, kcal = 1.0, grams = 0.2, relative = 0.02)

    /**
     * Whether a reviewer left the person's numbers within label rounding (builder-program.md,
     * "Agreement"): 10 kcal or 2 g, or 10%, the same allowance as the moderation signals use.
     */
    fun reviewerKept(sent: LabelFacts, reviewed: LabelFacts): Boolean =
        differences(sent, reviewed, kcal = 10.0, grams = 2.0, relative = 0.10, missingDiffers = false).isEmpty()

    private fun differences(
        sent: LabelFacts,
        other: LabelFacts,
        kcal: Double,
        grams: Double,
        relative: Double,
        missingDiffers: Boolean = true,
    ): List<String> = buildList {
        fun close(a: Double, b: Double, absolute: Double) = abs(a - b) <= max(absolute, relative * max(abs(a), abs(b)))
        val sentSize = sent.servingSize
        val otherSize = other.servingSize
        if (sentSize != null) {
            if (otherSize == null) {
                if (missingDiffers) add(Headline.SERVING)
            } else if (!close(sentSize, otherSize, 0.0) || sent.servingUnit != other.servingUnit) {
                add(Headline.SERVING)
            }
        }
        for (key in Headline.NUTRIENTS) {
            val absolute = if (key == NutrientKey.Energy) kcal else grams
            // Per serving when both have it, so a serving-size slip shows once, on the serving.
            val pair = if (sent.perServing[key] != null && other.perServing[key] != null) {
                sent.perServing.getValue(key) to other.perServing.getValue(key)
            } else {
                sent.per100(key)?.let { a -> other.per100(key)?.let { b -> a to b } }
            }
            when {
                pair != null -> if (!close(pair.first, pair.second, absolute)) add(key.wire)
                sent.per100(key) != null && missingDiffers -> add(key.wire)
            }
        }
    }
}

/** The independent read of the label photo, and how it compared with what was sent. */
data class CloudCheck(
    val model: String,
    val readable: Boolean,
    val agrees: Boolean,
    val differs: List<String>,
    val latencyMs: Long,
)

/** What routing decided for one submission, kept on it as `routing` for review and the board. */
data class RoutingDecision(
    val lane: Lane,
    /** What the auto-approval lane would have done, in any mode: the shadow record. */
    val wouldAutoApprove: Boolean,
    /** Why it went where it went, as short codes: `not_trusted`, `not_grounded:fat_g`, `cloud_differs:energy_kcal`. */
    val reasons: List<String>,
    val confidence: Double?,
    val trust: TrustLevel,
    val mode: AutoApproveMode,
    val grounding: Grounding?,
    val cloud: CloudCheck?,
    val audit: Boolean = false,
)

/**
 * The deterministic half of routing (ai-options.md, "Routing"; builder-program.md, "Trust"):
 * pure, so every rule has a test. The auto-approval lane is deliberately narrow. A Trusted
 * Builder's pack photographed with the camera, every check passing, every headline number found
 * in the photo's text, and an independent read of the photo that agrees, or with no reader on
 * this server a phone confidence of [MIN_CONFIDENCE_WITHOUT_CLOUD]. Everything else goes to a
 * person, with the reasons first in the queue.
 */
object SubmissionRouter {
    /** The least confidence any auto-approval takes. */
    const val MIN_CONFIDENCE = 0.85

    /** Without a second read, only the phone's own measures speak for the numbers, so the bar is higher. */
    const val MIN_CONFIDENCE_WITHOUT_CLOUD = 0.95

    /** An ungrounded headline number caps confidence here: it may have been typed from anywhere. */
    private const val UNGROUNDED_CAP = 0.5
    private const val FAILED_CHECK_CAP = 0.6
    private const val BLOCKING_CAP = 0.1

    /**
     * The server's confidence: the phone's measured score taken as sent (its OCR confidence,
     * layout and image measures are things only the phone can see), capped by what the server
     * re-derived itself, the checks and the grounding.
     */
    fun confidence(capture: LabelCapture?, checks: List<LabelCheck>, grounding: Grounding?): Double? {
        val phone = capture?.score?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: return null
        var cap = 1.0
        if (grounding != null && !grounding.complete) cap = minOf(cap, UNGROUNDED_CAP)
        if (checks.isNotEmpty()) cap = minOf(cap, FAILED_CHECK_CAP)
        if (checks.any { it.code.blocking }) cap = minOf(cap, BLOCKING_CAP)
        return minOf(phone, cap)
    }

    @Suppress("LongParameterList")
    fun decide(
        trust: TrustLevel,
        mode: AutoApproveMode,
        capture: LabelCapture?,
        checks: List<LabelCheck>,
        grounding: Grounding?,
        cloud: CloudCheck?,
        cloudConfigured: Boolean,
    ): RoutingDecision {
        val confidence = confidence(capture, checks, grounding)
        val reasons = buildList {
            if (trust != TrustLevel.Trusted) add("not_trusted")
            if (capture == null) {
                add("no_capture")
            } else if (capture.source != CaptureSource.Camera) {
                add("not_from_camera")
            }
            checks.forEach { add("check:${it.code.wire}") }
            grounding?.missing?.forEach { add("not_grounded:$it") }
            if (grounding != null && grounding.groundable < Headline.KEYS.size) add("headline_incomplete")
            when {
                cloud != null && !cloud.readable -> add("cloud_unreadable")
                cloud != null -> cloud.differs.forEach { add("cloud_differs:$it") }
                cloudConfigured && capture != null -> add("no_cloud_read")
            }
            val bar = if (cloud != null) MIN_CONFIDENCE else MIN_CONFIDENCE_WITHOUT_CLOUD
            if (confidence == null || confidence < bar) add("low_confidence")
        }.distinct()
        val would = reasons.isEmpty()
        val lane = if (would && mode == AutoApproveMode.On) Lane.Auto else Lane.Review
        val shown = if (would && mode != AutoApproveMode.On) reasons + "mode:${mode.wire}" else reasons
        return RoutingDecision(lane, would, shown, confidence, trust, mode, grounding, cloud)
    }

    /** Whether an auto-approval is audited: all of a newly Trusted Builder's first ones, then [rate] at random. */
    fun audits(autoApprovedSinceTrusted: Int, rate: Double, draw: Double): Boolean =
        autoApprovedSinceTrusted < BuilderRules.AUDIT_ALL_FIRST || draw < rate
}
