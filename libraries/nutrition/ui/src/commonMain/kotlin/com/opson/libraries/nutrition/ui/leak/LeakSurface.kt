package com.opson.libraries.nutrition.ui.leak

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.VisibilityPolicy

/**
 * One screen, sheet or menu that `BlindModeLeakTest` renders in every hidden
 * preset and scans for calorie amounts. Each feature impl lists its own in
 * `commonMain` (`TodayLeakSurfaces.all` and so on) and `:apps:leaktest` joins
 * them. The contract a surface meets is in `docs/plans/screens/blind-leak-test.md`.
 *
 * [content] must build its state through the screen's real `XStateBuilder`
 * from realistic fixture inputs and [VisibilityPolicy] it is given, never from a
 * hand-written `Display`, or the test checks the fixture instead of the mapping.
 */
class LeakSurface(
    /** Stable, kebab case, and listed in `REQUIRED_SURFACES`: `today`, `add-sheet`. */
    val id: String,
    /** The prototype's label where it has one, for failure messages. */
    val label: String,
    /**
     * Whether this surface shows a calorie amount in Full. When true, the
     * control test renders it in Full and fails if it finds nothing, which is
     * what proves the scan can see this surface at all.
     */
    val showsCaloriesInFull: Boolean,
    val content: @Composable (policy: VisibilityPolicy) -> Unit,
) {
    // Parameterized test runners print the case with toString().
    override fun toString(): String = id
}
