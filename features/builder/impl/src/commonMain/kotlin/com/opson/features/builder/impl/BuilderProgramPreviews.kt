package com.opson.features.builder.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

private val joinPage = BuilderPage.Join(block = null, until = null, rejoin = false)

/** Not a Builder yet: what it is, what you get, the rules and Join. Tall, to show the whole page. */
@Preview(widthDp = 390, heightDp = 1900)
@Composable
private fun BuilderJoinPreview() = Page(BuilderProgramState(page = joinPage))

/** The rules ticked: Join is ready. */
@Preview(widthDp = 390, heightDp = 1900)
@Composable
private fun BuilderJoinReadyPreview() = Page(BuilderProgramState(page = joinPage, agreed = true, retention = true))

/** A week-old account with no approved food yet: no Join, and when it opens. */
@Preview(widthDp = 390, heightDp = 1900)
@Composable
private fun BuilderTooNewPreview() = Page(BuilderProgramState(page = joinPage.copy(block = JoinBlock.TooNew, until = "November 3")))

/** A Builder in their first month. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderMemberPreview() = Page(
    BuilderProgramState(page = BuilderPage.Member(trusted = false, accepted = 3, needed = 10, proUntil = "November 28", proActive = true, retention = false)),
)

/** A Trusted Builder, in the dark. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderTrustedDarkPreview() = Page(
    BuilderProgramState(page = BuilderPage.Member(trusted = true, accepted = 10, needed = 10, proUntil = "December 12", proActive = true, retention = true)),
    dark = true,
)

/** Builder Pro ran out after a quiet month. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderLapsedPreview() = Page(
    BuilderProgramState(page = BuilderPage.Member(trusted = false, accepted = 2, needed = 10, proUntil = "October 2", proActive = false, retention = false)),
)

/** Leave tapped: the confirmation. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderLeaveConfirmPreview() = Page(
    BuilderProgramState(
        page = BuilderPage.Member(trusted = false, accepted = 3, needed = 10, proUntil = "November 28", proActive = true, retention = false),
        confirmingLeave = true,
    ),
)

/** Taken out of the program. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderRemovedPreview() = Page(BuilderProgramState(page = BuilderPage.Removed))

/** Offline on the way in. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BuilderOfflinePreview() = Page(BuilderProgramState(page = joinPage, problem = BuilderProblem.Offline))

@Composable
private fun Page(state: BuilderProgramState, dark: Boolean = false) =
    PreviewContent(darkTheme = dark) { BuilderProgramScreen(state = state, onAction = {}) }
