package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hand-rolled scroll in a sheet is always reported. A lazy list is reported unless the sheet
 * pins its height with `heightFraction`, which is the logger's shape: pinned header, `LazyColumn`,
 * pinned footer.
 */
class ScrollInsideBottomSheetTest {

    private val rule get() = ScrollInsideBottomSheet(Config.empty)

    @Test
    fun `reports verticalScroll in the content lambda`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { Text("Long") }
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports verticalScroll even with heightFraction, the sheet owns that scroll`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}, heightFraction = 0.93f) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { Text("Long") }
                }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a lazy list in a sheet whose height comes from its content`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}) {
                    LazyColumn { items(foods) { FoodRow(it) } }
                }
                BottomSheet(onDismissRequest = {}, scrollableContent = true, content = {
                    LazyVerticalGrid(columns = GridCells.Fixed(2)) { }
                })
            }
            """
        )

        assertEquals(2, findings.size)
        assertTrue(findings.all { "heightFraction" in it.message })
    }

    @Test
    fun `allows a lazy list when heightFraction pins the sheet`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Logger() {
                BottomSheet(onDismissRequest = {}, heightFraction = BottomSheetDefaults.FullHeightFraction) {
                    Header()
                    LazyColumn(Modifier.weight(1f)) { items(foods) { FoodRow(it) } }
                    Footer()
                }
                BottomSheet(onDismissRequest = {}, heightFraction = 0.6f) {
                    LazyColumn { }
                }
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `an explicit null heightFraction pins nothing`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Sheet() {
                BottomSheet(onDismissRequest = {}, heightFraction = null) { LazyColumn { } }
            }
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `ignores horizontal lists and lists outside a sheet`() {
        val findings = rule.findingsOn(
            """
            @Composable
            fun Screen() {
                LazyColumn { }
                Column(Modifier.verticalScroll(rememberScrollState())) { }
                BottomSheet(onDismissRequest = {}) {
                    LazyRow { items(chips) { Chip(it) } }
                }
            }
            """
        )

        assertEquals(0, findings.size)
    }
}
