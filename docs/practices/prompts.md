# Prompts on Today

Opson sometimes offers one thing on Today: connect Apple Health, choose meal reminders, try a
check-in. Every such offer is a **prompt candidate**, and one processor decides whether Today shows
one and which. Nothing else in the app should open an unasked-for dialog or sheet over Today. The
design is ported from Cards' Home arbiter, where five surfaces that each raced their own "seen"
flag were folded into one pure pass, and from its review coordinator (a quiet start and a floor
between asks).

Code: the contract in `:libraries:prompts`, the processor, its host, the dialog and sheet
destinations, the card slot on Today and the three shipped prompts in `:libraries:prompts:impl`.

## The rules

Checked in this order by `PromptRules.check`; the QA menu shows the first one holding each prompt
back.

1. **Signed in, onboarded, settings known.** Sign-in and onboarding are their own routes, so the
   host never asks there; this covers the edges around them, and waits for the account's own
   "numbers have been hard" answer rather than a default.
2. **The prompt's own record** (`PromptFrequency`): accepted once ends it; `maxDismissals` Not
   nows end it (default 1); `maxShows` showings end it (default 1, "never more than once"); two
   showings are at least `minGap` apart. Nothing skips these.
3. **Asked for this trigger.** `onTodayOpen` for Today settling; `events` for moments.
4. **Numbers have been hard** means only prompts with `offerWhenNumbersHard = true`.
5. **One prompt a session** (`SessionTracker`: a cold start, or 15 minutes in the background).
   One exception: a moment that is someone's own answer to the prompt just shown
   (`PromptEvent.answersPrompt`, today only a celebration's "I can do this") may call up one more
   prompt in that session, once.
6. **On Today opening only:** none in an account's first two days on this phone, and a day between
   any two prompts (three days after numbers have been hard).

A **moment** (`PromptEvent`: a report sent, a food reported, the day closed, a celebration's "I
can do this", today becoming a day someone showed up, a food they sent approved) lets a prompt that lists it show as
Today next settles, within ten minutes. It skips rule 6, because the moment does not come back,
but never rules 1 to 5. A moment nobody could use is dropped.

Among the candidates that pass, the highest `priority` whose own `isEligible(moment)` says yes shows
(`PromptPriority`; a tie goes by id). Eligibility is asked only after the rules pass, so it may read
the database. `moment` is the event that called the prompt up, or null for Today opening; it
defaults to `isEligible()`, and a prompt with a different reason to show for each overrides it
(`join_builder` follows a food report, or Today once a food was approved, and shows once either way).

**What a prompt may say.** Never anything that nudges toward eating less, and nothing that asks
someone to log more. Respect the modes: a prompt about energy or calories checks that calories are
on show. Keep `offerWhenNumbersHard` false unless the offer is gentle without any number in it.

## When it asks, and where it draws

`PromptHost.Host` sits beside the nav host in `App.kt`. While Today is the top of the back stack
(sheets and dialogs are back stack entries, so nothing is open over it) and the app is resumed, it
waits two seconds, asks `PromptProcessor.next()`, and asks again whenever a moment arrives. A
dialog or sheet then opens as its own destination (`PromptDialogRoute`, `PromptSheetRoute`), so it
can never stack on another sheet, and Today is no longer on top until it closes. A card is drawn in
Today's list through `TodayCardContribution`, on today only, until it is answered or the session
ends.

A showing is written down before it is drawn, and takes one answer: Accept, or Not now (a swipe, a
tap outside and Back count as Not now). Counts live on the phone, per account, in the
`prompt_ledger` cache. They survive signing out and back in, and are never synced.

## The shipped prompts

| id | Presentation | Shows when | Opens | Flag |
|---|---|---|---|---|
| `connect_health` | sheet | the phone has a health store, it is not connected, calories are on show; not after numbers have been hard | Watch and health apps | `release.health` |
| `meal_reminders` | dialog | no reminder level was ever chosen and notifications were not refused; not after numbers have been hard | Reminders and celebrations | `release.reminders` |
| `try_check_in` | card, up to 3 times, 3 days apart | 3 logged days in the last 14 and no check-in in them | Today's check-in | `release.checkIn` |
| `join_builder` | sheet, once ever | right after a food report is sent (`FoodReportSent`), or as Today opens once a food they added was approved; never to a Builder, someone with an application waiting, or someone who can't apply | More › Builders | `release.builders` |
| `builder_accepted` | sheet, once ever | Opson accepted their application in the last 14 days, they're still a Builder, and this phone hasn't drawn the welcome on the Builders page | More › Builders | `release.builders` |
| `builder_declined` | dialog, once ever | Opson declined their application in the last 14 days and this phone hasn't shown it on the Builders page | More › Builders | `release.builders` |
| `celebration` | screen, once per milestone | a milestone for showing up was reached in the last 7 days and not celebrated on this phone; right after wrapping up the day (`DayClosed`) or as Today opens; offered after numbers have been hard, gently | A milestone's celebration (`CelebrationRoute`) | `release.celebrations` |
| `set_up_goal` | card, up to 3 times, 3 days apart | the account never answered the weight goal, the mode shows weight, not pregnant or breastfeeding; not after numbers have been hard | the goal flow (`GoalSetupRoute`) | `release.onboardingSetup` |
| `review` | system (the store's rating sheet) | right after this install's happy moment: a celebration's "I can do this", today wrapped up as Good, the first thing of the day from someone who showed up on 10 of the last 14 days, or a food they sent approved. Never as Today opens, never after numbers have been hard; the review coordinator adds a quiet first week, 122 days between asks and none offline or after trouble | the store's own sheet | `release.reviewAsk` |
| `goal_recalculated` | screen, any number of times, a day apart | the goal setting the target has a recalculation to offer (`GoalRepository.recalculation`): something its typical day is worked out from changed enough to move the target, and the person hasn't kept their target through these inputs | its own dialog over Today (`GoalRecalculationRoute`): what changed, the target now and with the update, Use the new target or Keep my current target | `release.onboardingSetup` |

A **system** prompt (`PromptPresentation.System`) is drawn by the operating system: the host
calls the candidate's `accept()`, which asks the store for its rating sheet, and draws nothing.
It counts as shown, never as accepted or dismissed, because the store never says. Which moment
each install asks at, the pacing and how to read the results are in
[`review-asks.md`](review-asks.md).

A **screen** prompt (`PromptPresentation.Screen`) draws itself: the host opens the route its
`screen()` names, and that screen owns its buttons, so nothing is counted as accepted or
dismissed. `goal_recalculated` is one too, because each recalculation is its own question and a
dialog prompt ends for good once accepted. `celebration` is the other: its own counts are no limit (each milestone celebrates once,
in `CelebrationStore`), but it answers to every global rule, so it never lands on another prompt
and takes the session's one showing. Its priority, `PromptPriority.CELEBRATION`, puts it above a
moment's offer.

## First-run tips

A tip is not a prompt: it offers nothing, it points at something already on screen ("Tap a
number with the arrows to see it another way"). They have their own, smaller rules, in
`TipMemory` (`:libraries:prompts:impl`): at most one a screen visit, never before onboarding is
done, and each once per account on this phone. When a screen has several on it, the first in
`Tips.all` not yet seen wins, so the registry's order is the priority.

To add one: a `Tip` in `Tips.kt` with its line in `strings_tips.xml`, then wrap what it points at
in `CoachMarkAnchor` (Today has `TipAnchor`) inside the screen's `CoachMarkScreen`. Previews and
tests never show a tip: `LocalCoachMarks` defaults to none and nothing is asked in inspection
mode. QA menu › Prompts › Reset tips forgets them for the signed-in account.

| id | Where | Points at |
|---|---|---|
| `today_flip_figure` | Today | the main number's ⇄ |
| `today_food_details` | Today | the first plain food row |
| `today_pull_refresh` | Today | the header, while pull to refresh is on |
| `targets_weight_goal` | Targets | "Set from your weight goal" |

## Adding a prompt (the Builder program, for example)

One file in the feature that owns what the prompt offers, plus a dependency and the copy.

1. In that feature's `impl/build.gradle.kts`: `implementation(projects.libraries.prompts)`.
2. The copy in `libraries/resources/.../values/`, in the app's voice. `prompt_not_now` is there to
   reuse.
3. The candidate:

```kotlin
@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.builder.impl

@HiddenFromObjC
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class JoinBuilderPrompt(
    private val flag: BuilderProgramEnabled, // the program's release flag
    private val builder: BuilderRepository, // whatever knows whether they joined
) : PromptCandidate {
    override val id = "join_builder" // never rename: it keys the counts and the app events
    override val priority = PromptPriority.MOMENT
    override val presentation = PromptPresentation.Sheet
    override val content = PromptContent(
        icon = AppIcons.Science,
        title = Res.string.builder_prompt_title,
        body = Res.string.builder_prompt_body,
        accept = Res.string.builder_prompt_accept,
        dismiss = Res.string.prompt_not_now,
    )

    // Right after someone sends a report, and only then.
    override val onTodayOpen = false
    override val events = setOf(PromptEvent.ReportSent)

    // Twice at most, a month apart, until they say no once.
    override val frequency = PromptFrequency(maxShows = 2, maxDismissals = 1, minGap = 30.days)

    override suspend fun isEligible(): Boolean = flag() && !builder.isMember()

    override suspend fun accept(): PromptAccept = PromptAccept.Open(BuilderProgramRoute())
}
```

That is all: the multibinding puts it in front of the processor, the QA menu lists it, and the app
events carry its id. `PromptAccept.OpenLink(url)` opens a page instead, and `PromptAccept.Done`
closes after `accept()` did the work itself (a join call, say).

**Emitting a moment.** Inject `PromptEvents` and call `emit(PromptEvent.X)` where the moment
happens, after it succeeded. `FeedbackRepositoryImpl` emits `ReportSent` for bug reports and
feedback, `FoodReportViewModel` for a food's "Report a problem" (with `FoodReportSent` too, which only that report emits), and Close the day's Done emits
`DayClosed`, a celebration's "I can do this" emits `CelebrationCheered`, and `MomentWatcher`
(`:libraries:prompts:impl`) emits `ShowedUp` and `FoodApproved` when it sees them change while the
app runs. A new kind of report emits `ReportSent` too; a new kind of moment is a new `PromptEvent` value. A constructor that must
not grow a required argument can default it to `PromptEvents.None`.

## Testing a prompt

QA menu › Prompts lists every candidate with the rule holding it back, its own answer and its
counts. **Show next** shows it the next time Today settles, past every rule but being signed in and onboarded; **Reset** forgets one
prompt's counts, and **Reset all prompts** forgets them all and ends the waits, including the first
two days. The rules themselves are pinned in `PromptProcessorTest`.
