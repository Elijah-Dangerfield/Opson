package com.opson.features.builder.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.foods.LabelRegion
import com.opson.libraries.foods.ShoppingFrequency
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

private val intro = BuilderPage.Intro(block = null, until = null, returning = false)

private val home = BuilderPage.Home(
    welcome = false,
    trusted = false,
    accepted = 7,
    needed = 10,
    proUntil = "November 28",
    proActive = true,
    foodsAdded = 12,
    checks = 5,
    acceptedPercent = 92,
    retention = false,
)

/** Not applied yet: one line on what it is, three steps, Apply, and the rules a row away. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderJoinPreview() = Page(BuilderProgramState(page = intro))

/** Opened offline for the first time: nothing to show yet, so the offline card and Try again, no spinner. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderFirstOpenOfflinePreview() = Page(BuilderProgramState(page = null, problem = BuilderProblem.Offline))

/** Someone who left, coming back: the same page with Apply again. In the dark. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderReturningDarkPreview() = Page(BuilderProgramState(page = intro.copy(returning = true)), dark = true)

/** A new account with no approved food yet: no Apply, and when it opens. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderTooNewPreview() = Page(BuilderProgramState(page = intro.copy(block = JoinBlock.TooNew, until = "November 3")))

/** The application is with Opson. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderAppliedPreview() = Page(BuilderProgramState(page = BuilderPage.Applied(sentOn = "October 29")))

/** Withdraw tapped: the confirmation. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderWithdrawConfirmPreview() =
    Page(BuilderProgramState(page = BuilderPage.Applied(sentOn = "October 29"), confirming = BuilderConfirm.Withdraw))

/** Declined with a note, and a wait before applying again. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderDeclinedPreview() = Page(
    BuilderProgramState(
        page = BuilderPage.Declined(note = "We have plenty of Builders near you for now. Try again in the new year.", againOn = "November 28"),
    ),
)

/** Declined a while ago, no note: Apply again is open. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderDeclinedReapplyPreview() = Page(BuilderProgramState(page = BuilderPage.Declined(note = null, againOn = null)))

/** Just accepted: the welcome over their new home. Tall, to show the whole page. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun BuilderWelcomePreview() = Page(
    BuilderProgramState(page = home.copy(welcome = true, accepted = 0, foodsAdded = 0, checks = 0, acceptedPercent = null)),
)

/** A Builder's home: Pro, the two things to do, what they've done. Tall, to show the whole page. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun BuilderMemberPreview() = Page(BuilderProgramState(page = home))

/** A Trusted Builder, in the dark. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun BuilderTrustedDarkPreview() = Page(
    BuilderProgramState(page = home.copy(trusted = true, accepted = 10, proUntil = "December 12", retention = true)),
    dark = true,
)

/** Builder Pro ran out after a quiet month. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun BuilderLapsedPreview() = Page(BuilderProgramState(page = home.copy(accepted = 2, proUntil = "October 2", proActive = false)))

/** Leave tapped: the confirmation. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderLeaveConfirmPreview() = Page(BuilderProgramState(page = home, confirming = BuilderConfirm.Leave))

/** Taken out of the program. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderRemovedPreview() = Page(BuilderProgramState(page = BuilderPage.Removed))

/** Offline on the way in. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderOfflinePreview() = Page(BuilderProgramState(page = intro, problem = BuilderProblem.Offline))

/** The application, nothing answered yet: Send waits. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun BuilderApplySheetPreview() = Sheet(BuilderApplyState())

/** The application filled in, with the rules opened in place. In the dark. */
@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun BuilderApplyReadyDarkPreview() = Sheet(
    BuilderApplyState(
        shopping = ShoppingFrequency.Weekly,
        region = LabelRegion.Uk,
        motivation = "The cereal aisle near me is missing half its packs.",
        agreed = true,
        rulesOpen = true,
    ),
    dark = true,
)

/** The application refused on its way: applied again too soon. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun BuilderApplyBlockedPreview() = Sheet(
    BuilderApplyState(shopping = ShoppingFrequency.Monthly, region = LabelRegion.Us, agreed = true, problem = ApplyProblem.Blocked(JoinBlock.Declined)),
)

/** The rules sheet, from Read the rules. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun BuilderRulesSheetPreview() = PreviewContent { BuilderRulesSheetContent(onTerms = {}, onClose = {}) }

@Composable
private fun Page(state: BuilderProgramState, dark: Boolean = false) =
    PreviewContent(darkTheme = dark) { BuilderProgramScreen(state = state, onAction = {}) }

@Composable
private fun Sheet(state: BuilderApplyState, dark: Boolean = false) =
    PreviewContent(darkTheme = dark) { BuilderApplySheetContent(state = state, onAction = {}, onClose = {}) }
