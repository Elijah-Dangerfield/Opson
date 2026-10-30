package com.opson.features.trends.impl.recap

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.features.trends.impl.TrendsFixtures
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.PreviewLightDark
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every recap here comes from the real builder over RecapFixtures, the path the app and the leak
// test take: the week of Oct 19 to 25 read on the Monday after, with Wednesday skipped and
// Thursday empty.

private val full = TrendsFixtures.policy()

@Composable
private fun Phone(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark, content = content)
}

/** The page scrolled past the fold, to see the cards the 844 dp render cuts off. */
@Composable
private fun below(): Modifier = Modifier.verticalScroll(rememberScrollState(initial = BELOW_FOLD_PX))

private const val BELOW_FOLD_PX = 2_000

/** All numbers, Burned known each day, check-ins and celebrations on. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapWeekPreview() = Phone { RecapScreen(RecapFixtures.state(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapWeekBelowPreview() = Phone { RecapContent(RecapFixtures.state(full), onAction = {}, modifier = below()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapWeekDarkPreview() = Phone(dark = true) { RecapScreen(RecapFixtures.state(full), onAction = {}) }

/** A set deficit of 500: the days on plan or close, and eaten against burned. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapWeekDeficitBelowPreview() = Phone { RecapContent(RecapFixtures.state(full, deficit = true), onAction = {}, modifier = below()) }

/** Losing weight, showing up every day and three meals a day: each goal's days on track, from the goal engine. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapGoalsBelowPreview() = Phone { RecapContent(RecapFixtures.state(full, goals = true), onAction = {}, modifier = below()) }

/** September, a dot a day and a bar a day. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapMonthPreview() = Phone { RecapScreen(RecapFixtures.state(full, RecapFixtures.month), onAction = {}) }

/** The week still going, on its Sunday at noon: so far, and today still to come. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapInProgressPreview() = Phone { RecapScreen(RecapFixtures.inProgress(full), onAction = {}) }

/** No numbers: the days, how they felt, the run and the foods. Nothing about amounts. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapNoNumbersPreview() = Phone { RecapScreen(RecapFixtures.state(TrendsFixtures.policy(PresetId.NoNumbers)), onAction = {}) }

/** After numbers have been hard, in All numbers: showing up and how the days felt, never an amount. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapHardPreview() = Phone(dark = true) { RecapScreen(RecapFixtures.state(full, hard = true), onAction = {}) }

/** Macros only: no calories, so no eaten, burned or balance; the rest stays. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapMacrosOnlyPreview() = Phone { RecapScreen(RecapFixtures.state(TrendsFixtures.policy(PresetId.MacrosOnly)), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapEmptyPreview() = Phone { RecapScreen(RecapFixtures.empty(full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RecapLoadingPreview() = Phone { RecapScreen(RecapState(), onAction = {}) }

/** "Your week is ready" on Today, one of the first few times: the way to turn it off under the buttons. */
@Preview(widthDp = 390)
@Composable
private fun RecapBannerWeekPreview() = PreviewLightDark {
    RecapBannerCard(RecapFixtures.week, onOpen = {}, onNotNow = {}, onTurnOff = {})
}

/** A month's card, after the first few times: Not now and See it alone. */
@Preview(widthDp = 390)
@Composable
private fun RecapBannerMonthPreview() = PreviewLightDark {
    RecapBannerCard(RecapFixtures.month, onOpen = {}, onNotNow = {})
}
