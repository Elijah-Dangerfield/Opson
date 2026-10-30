@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts

import com.opson.libraries.navigation.Route
import com.opson.libraries.ui.components.icon.IconResource
import org.jetbrains.compose.resources.StringResource
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * One thing Opson might offer on Today: connect Apple Health, try a check-in, join the Builder
 * program. Contribute one with `@ContributesBinding(AppScope::class, multibinding = true)` and the
 * prompt processor in `:libraries:prompts:impl` decides when, if ever, it shows. The candidate
 * says only whether it would make sense right now ([isEligible]); everything about spam (one
 * prompt a session, a day between any two, a quiet first few days, how often this one may come
 * back) is the processor's, so no candidate re-derives it. The recipe and the rules are in
 * `docs/practices/prompts.md`.
 *
 * Never offer anything that nudges toward eating less, and keep [offerWhenNumbersHard] false
 * unless the prompt is gentle enough for someone who said numbers have been hard.
 *
 * Hidden from the iOS header: Swift never uses it, and [PromptContent] would drag Compose
 * resources into `ComposeApp.h` (AGENTS.md, Known landmines).
 */
@HiddenFromObjC
interface PromptCandidate {
    /**
     * Stable, unique, snake_case: `connect_health`. It keys the per-account counts on the phone
     * and is the `prompt` attribute of the app events, so renaming one shows it again to everyone
     * and splits its numbers in two.
     */
    val id: String

    /** Higher wins when several could show. See [PromptPriority]. */
    val priority: Int

    val presentation: PromptPresentation

    val content: PromptContent

    val frequency: PromptFrequency get() = PromptFrequency()

    /** Considered whenever Today settles with nothing over it. False for a prompt that only follows [events]. */
    val onTodayOpen: Boolean get() = true

    /**
     * Moments that can call it up straight away, as Today settles right after one. Such a showing
     * skips the day between prompts and the first few days, never its own [frequency].
     */
    val events: Set<PromptEvent> get() = emptySet()

    /** Shown to someone who said numbers have been hard. They get fewer prompts, and only gentle ones. */
    val offerWhenNumbersHard: Boolean get() = false

    /**
     * An offer someone can do without (connect Health, try a check-in): it shows only while
     * suggestions are on (`Settings.suggestions`), and its first few showings offer "I'd rather
     * not see these" ([OptOut.Suggestions]). Every dialog, sheet and card is one by default; a
     * prompt that answers something the person did (their Builder application) says false.
     */
    val optional: Boolean
        get() = presentation == PromptPresentation.Dialog || presentation == PromptPresentation.Sheet || presentation == PromptPresentation.Card

    /**
     * Whether it makes sense now: its release flag is on, the thing is not done already. Cheap
     * reads only; it runs while Today settles, and only once every global rule has passed. A throw
     * counts as false.
     */
    suspend fun isEligible(): Boolean

    /**
     * [isEligible] for one trigger: [moment] is the [PromptEvent] that called it up, or null when
     * Today simply opened. Override it for a prompt that both follows a moment and waits for
     * Today, with a different reason to show for each.
     */
    suspend fun isEligible(moment: PromptEvent?): Boolean = isEligible()

    /**
     * The main button was tapped. Do the work, if any, then say where the person goes. For
     * [PromptPresentation.System] this is the whole showing: ask the system, and return
     * [PromptAccept.Done].
     */
    suspend fun accept(): PromptAccept

    /**
     * For [PromptPresentation.Screen] only: the destination that draws the prompt itself, asked
     * once it has been picked. That screen owns its buttons, so [accept] and [content]'s words are
     * not used, and nothing counts as accepted or dismissed. Null shows nothing.
     */
    suspend fun screen(): Route? = null
}

/**
 * How a prompt is drawn. Dialog and sheet go over Today; a card sits in Today's list; a screen is
 * the candidate's own full-screen destination ([PromptCandidate.screen]), such as a celebration.
 * [System] is drawn by the operating system, the store's rating sheet: the host calls
 * [PromptCandidate.accept] and draws nothing, and nothing counts as accepted or dismissed, since
 * the system never says.
 */
enum class PromptPresentation(val wire: String) {
    Dialog("dialog"),
    Sheet("sheet"),
    Card("card"),
    Screen("screen"),
    System("system"),
}

/**
 * The words and the picture. [dismiss] is the quiet way out ("Not now"); a swipe, a tap outside
 * and Back count the same.
 */
@HiddenFromObjC
data class PromptContent(
    val icon: IconResource,
    val title: StringResource,
    val body: StringResource,
    val accept: StringResource,
    val dismiss: StringResource,
)

/**
 * How often one prompt may come back, on top of the global rules. The default is once, ever: shown
 * a single time whatever the person does with it. Accepting always ends it.
 *
 * @property maxShows times it may ever show to this account on this phone.
 * @property maxDismissals Not nows before it stops coming back.
 * @property minGap the least time between two of its showings.
 */
data class PromptFrequency(
    val maxShows: Int = 1,
    val maxDismissals: Int = 1,
    val minGap: Duration = 7.days,
)

/** What accepting leads to, after the prompt has gone. */
@HiddenFromObjC
sealed interface PromptAccept {
    /** Opens [route] where the prompt was, so Back lands on Today. */
    class Open(val route: Route) : PromptAccept

    class OpenLink(val url: String) : PromptAccept

    /** The work was done in [PromptCandidate.accept]; nothing to open. */
    data object Done : PromptAccept
}

/** Shared scale for [PromptCandidate.priority], so a new prompt can say where it sits. */
object PromptPriority {
    /** A milestone the person just reached. Above a moment's offer: it is about them, not something to try. */
    const val CELEBRATION = 500

    /** Follows a moment the person just had (a report sent). The moment does not come back. */
    const val MOMENT = 400

    /** Unlocks something most people would want. */
    const val HIGH = 300

    const val NORMAL = 200

    /** Nice to know, happy to wait. */
    const val LOW = 100
}
