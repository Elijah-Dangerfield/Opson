package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import kotlinx.datetime.LocalDate

/**
 * Explorer's food bridge (v3 2.8): a gentle next step from a food someone eats often to one
 * they have had only once or twice. [from] and [to] are short names ("Rice", "Rice pudding");
 * [shared] is the word they have in common, lower case ("rice"). [foodId] or [customFoodId]
 * lets "I tried it" open the logger with that food already picked.
 */
data class FoodBridge(
    val from: String,
    val to: String,
    val shared: String,
    val foodId: String? = null,
    val customFoodId: String? = null,
)

/**
 * Finds a [FoodBridge] in the person's own diary, and nowhere else: no catalog, no pairing
 * table, nothing they have not eaten. Pure, so it is tested on its own.
 *
 * The step is from a food they keep coming back to (the safe foods, most logged first) to a
 * food they have logged at most [RARE_MAX] times that shares a word with it. Among those, the
 * least logged wins, then the most recent, so the nudge is towards something barely tried but
 * still remembered. A food already eaten [today], a quick number, or a second spelling of the
 * same food is never the step. No pair, no card: the bridge never invents one.
 */
object FoodBridges {
    /** Logged this many times or fewer still counts as barely tried. */
    const val RARE_MAX = 2

    private const val MIN_WORD = 3

    /** Words that say how a food was made or served, not what it is. */
    private val ignored = setOf(
        "with", "and", "the", "for", "from", "plain", "raw", "cooked", "fresh", "homemade", "home", "made", "style",
        "small", "medium", "large", "slice", "slices", "piece", "pieces", "cup", "cups", "bowl", "plate", "whole", "low", "fat",
    )

    /**
     * [frequent] is `DiaryRepository.frequentFoods`, most logged first. [days] is the window to
     * look in (Explorer reads the last six months) up to [today].
     */
    fun suggest(frequent: List<LogItem>, days: List<Day>, today: LocalDate): FoodBridge? {
        val entries = days.flatMap { it.allItems }.filter { it.counts() }
        val anchors = frequent.filter { it.counts() }.distinctBy { it.foodKey() }
        if (anchors.isEmpty()) return null
        val anchorKeys = anchors.mapTo(HashSet()) { it.foodKey() }
        val anchorNames = anchors.mapTo(HashSet()) { nameKey(it.name) }
        val eatenToday = entries.filter { it.day == today }.mapTo(HashSet()) { it.foodKey() }
        val candidates = entries.groupBy { it.foodKey() }
            .filter { (key, items) -> key !in anchorKeys && key !in eatenToday && items.size <= RARE_MAX }
            .map { (_, items) -> Candidate(latest = items.maxBy { it.loggedAt }, times = items.size) }
            .filter { nameKey(it.latest.name) !in anchorNames }

        for (anchor in anchors) {
            val anchorWords = words(anchor.name)
            if (anchorWords.isEmpty()) continue
            val pick = candidates
                .mapNotNull { candidate -> words(candidate.latest.name).firstOrNull { it in anchorWords }?.let { candidate to it } }
                .sortedWith(
                    compareBy<Pair<Candidate, String>> { it.first.times }
                        .thenByDescending { it.first.latest.loggedAt }
                        .thenBy { it.first.latest.name },
                )
                .firstOrNull() ?: continue
            val (candidate, shared) = pick
            return FoodBridge(
                from = ExplorerStatsBuilder.shortName(anchor.name),
                to = ExplorerStatsBuilder.shortName(candidate.latest.name),
                shared = shared,
                foodId = candidate.latest.foodId,
                customFoodId = candidate.latest.customFoodId,
            )
        }
        return null
    }

    private data class Candidate(val latest: LogItem, val times: Int)

    /** A quick number has no food in it, and a blank name has nothing to bridge from. */
    private fun LogItem.counts(): Boolean = method != LogMethod.QuickNumber && name.isNotBlank()

    /** The same food however it was logged: its catalog or custom id, else its name. */
    private fun LogItem.foodKey(): String = foodId?.let { "f:$it" } ?: customFoodId?.let { "c:$it" } ?: "n:${nameKey(name)}"

    private fun nameKey(name: String): String = ExplorerStatsBuilder.shortName(name).lowercase()

    /** "Greek yogurt, plain" is {greek, yogurt}; "Apples" is {apple}. In the name's order. */
    internal fun words(name: String): List<String> =
        ExplorerStatsBuilder.shortName(name).lowercase()
            .split(notLetters)
            .filter { it.length >= MIN_WORD && it !in ignored }
            .map { if (it.length > MIN_WORD && it.endsWith('s') && pluralEndings.none(it::endsWith)) it.dropLast(1) else it }
            .distinct()

    /** Compiled once: [words] runs for every candidate against every anchor on each rebuild. */
    private val notLetters = Regex("[^\\p{L}]+")

    /** Endings that look plural and are not: "glass", "hummus", "couscous", "tahini" never loses an s anyway. */
    private val pluralEndings = listOf("ss", "us", "is")
}
