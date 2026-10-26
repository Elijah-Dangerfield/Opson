package com.opson.libraries.ui.catalog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.BottomNavBar
import com.opson.libraries.ui.components.BottomNavItem
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.FillBar
import com.opson.libraries.ui.components.HeroBlock
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.MenuItem
import com.opson.libraries.ui.components.MenuPopover
import com.opson.libraries.ui.components.PagerDots
import com.opson.libraries.ui.components.ProgressRing
import com.opson.libraries.ui.components.RingSegment
import com.opson.libraries.ui.components.RingSegments
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.SegmentedBar
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.Stepper
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.chip.ActionChip
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.libraries.ui.components.chip.PillTag
import com.opson.libraries.ui.components.icon.AppIconGrid
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.color.toHexString
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import com.opson.system.color.Palette
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * Every token and component on one scrolling page, in four themes. Sample copy here is preview
 * only and never reaches a user, hence the VerifyStrings suppressions on the helpers below.
 */

@Preview(name = "Pine, light", widthDp = 390, heightDp = 4200)
@Composable
private fun GalleryPineLight() {
    DesignSystemGallery(palette = Palette.Pine, dark = false)
}

@Preview(name = "Pine, dark", widthDp = 390, heightDp = 4200)
@Composable
private fun GalleryPineDark() {
    DesignSystemGallery(palette = Palette.Pine, dark = true)
}

@Preview(name = "Espresso, light", widthDp = 390, heightDp = 4200)
@Composable
private fun GalleryEspressoLight() {
    DesignSystemGallery(palette = Palette.Espresso, dark = false)
}

@Preview(name = "Espresso, dark", widthDp = 390, heightDp = 4200)
@Composable
private fun GalleryEspressoDark() {
    DesignSystemGallery(palette = Palette.Espresso, dark = true)
}

@Preview(name = "Large text", widthDp = 390, heightDp = 1200)
@Composable
private fun GalleryLargeText() {
    PreviewContent(largeText = true) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) { TypeSection() }
    }
}

@Composable
internal fun DesignSystemGallery(palette: Palette, dark: Boolean) {
    PreviewContent(palette = palette, darkTheme = dark) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            HeroSection()
            Column(
                modifier = Modifier.padding(horizontal = AppTheme.spacing.page),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                ColorSection()
                TypeSection()
                ShapeSection()
                IconSection()
                CardsSection()
                ControlsSection()
                SheetSection()
            }
            BottomNavSection()
        }
    }
}

@Suppress("VerifyStrings")
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.overline,
        allCaps = true,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Suppress("VerifyStrings")
@Composable
private fun HeroSection() {
    var page by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(horizontal = AppTheme.spacing.page)) {
        HeroBlock(drawBehindStatusBar = false) {
            Text(
                text = "Today",
                typography = AppTheme.type.dayLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp),
            )
            Text(
                text = "ENERGY SUMMARY",
                typography = AppTheme.type.overline,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                val n = AppTheme.colors.nutrients
                RingWithLabel("Eaten") {
                    ProgressRing(
                        segments = RingSegments.split(
                            1f,
                            listOf(25f to n.protein.content, 55f to n.carbs.content, 20f to n.fat.content),
                        ),
                    ) { RingCenter("1,240", "kcal") }
                }
                RingWithLabel("Expended") {
                    ProgressRing(
                        segments = listOf(
                            RingSegment(0.6f, AppTheme.colors.onHero.withAlpha(0.92f)),
                            RingSegment(0.25f, AppTheme.colors.onHero.withAlpha(0.6f)),
                            RingSegment(0.15f, AppTheme.colors.onHero.withAlpha(0.36f)),
                        ),
                    ) { RingCenter("2,100", "kcal") }
                }
                RingWithLabel("Target") {
                    ProgressRing(fraction = 0.62f, color = AppTheme.colors.onHero) { RingCenter("2,000", "kcal") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Protein", typography = AppTheme.type.secondarySmall.Bold)
                    FillBar(0.55f, AppTheme.colors.nutrients.protein.content, trackColor = AppTheme.colors.heroBarTrack)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Carbs", typography = AppTheme.type.secondarySmall.Bold)
                    FillBar(0.8f, AppTheme.colors.nutrients.carbs.content, trackColor = AppTheme.colors.heroBarTrack)
                }
            }
            PagerDots(pageCount = 3, currentPage = page, onPageSelected = { page = it })
        }
    }
}

@Composable
private fun RingWithLabel(label: String, ring: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        ring()
        Text(text = label, typography = AppTheme.type.captionStrong)
    }
}

@Composable
private fun RingCenter(value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, typography = AppTheme.type.ringValue, color = AppTheme.colors.onHero)
        Text(text = unit, typography = AppTheme.type.ringUnit, color = AppTheme.colors.onHeroMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("VerifyStrings")
@Composable
private fun ColorSection() {
    val c = AppTheme.colors
    SectionTitle("Base")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("bg", c.bg)
        Swatch("surface", c.surface)
        Swatch("ink", c.ink)
        Swatch("muted", c.muted)
        Swatch("line", c.line)
        Swatch("track", c.track)
        Swatch("soft", c.soft)
        Swatch("on-accent", c.onAccent)
    }
    SectionTitle("Brand, ${c.palette.name}")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("hero", c.hero)
        Swatch("accent", c.accent)
        Swatch("tint", c.accentTint)
        Swatch("scrim", c.scrim)
    }
    SectionTitle("Nutrients")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("protein", c.nutrients.protein.content)
        Swatch("tint", c.nutrients.protein.container)
        Swatch("carbs", c.nutrients.carbs.content)
        Swatch("tint", c.nutrients.carbs.container)
        Swatch("fat/water", c.nutrients.fat.content)
        Swatch("tint", c.nutrients.fat.container)
        Swatch("fiber", c.nutrients.fiber.content)
        Swatch("tint", c.nutrients.fiber.container)
    }
    SectionTitle("Meal tiles")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MealTone.entries.forEach { meal ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconTile(icon = AppIcons.meal(meal), tone = c.meal(meal))
                Text(meal.name, typography = AppTheme.type.caption, color = c.muted)
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: ColorResource) {
    Column(Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color, AppTheme.shapes.input)
                .border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.input)
        )
        Text(name, typography = AppTheme.type.captionStrong, color = AppTheme.colors.ink, maxLines = 1)
        Text(color.toHexString(), typography = AppTheme.type.caption.Tabular, color = AppTheme.colors.muted, maxLines = 1)
    }
}

@Suppress("VerifyStrings")
@Composable
private fun TypeSection() {
    val t = AppTheme.type
    SectionTitle("Type, base ${t.baseSize.toInt()} sp")
    val samples: List<Pair<String, TypographyResource>> = listOf(
        "h1 · Foods" to t.h1,
        "sub-h1 · Nutrients" to t.subH1,
        "day label · Today" to t.dayLabel,
        "sheet title · Trends settings" to t.sheetTitle,
        "card title · Breakfast" to t.cardTitle,
        "body · Usual at lunch" to t.body,
        "body strong · Full numbers" to t.bodyStrong,
        "secondary · Built from vitamins" to t.secondary,
        "secondary small · 1 cup · Estimate" to t.secondarySmall,
        "caption · Only you see this" to t.caption,
        "overline · ENERGY SUMMARY" to t.overline,
        "numeric · 1,234 0123456789" to t.numeric,
        "numeric large · 1.5 cup" to t.numericLarge,
    )
    samples.forEach { (label, style) ->
        Text(text = label, typography = style, color = AppTheme.colors.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("VerifyStrings")
@Composable
private fun ShapeSection() {
    val s = AppTheme.shapes
    SectionTitle("Shapes and spacing")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShapeSample("card 22", s.card)
        ShapeSample("sheet 28 top", s.sheet)
        ShapeSample("hero 24 bottom", s.heroBlock)
        ShapeSample("popover 18", s.popover)
        ShapeSample("input 12", s.input)
        ShapeSample("input 14", s.inputLarge)
        ShapeSample("pill", s.pill)
    }
    Text(
        "page ${AppTheme.spacing.page.value.toInt()} · section ${AppTheme.spacing.section.value.toInt()} · touch ${AppTheme.spacing.touchTarget.value.toInt()}",
        typography = AppTheme.type.secondarySmall.Tabular,
        color = AppTheme.colors.muted,
    )
}

@Composable
private fun ShapeSample(name: String, shape: Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 96.dp, height = 56.dp).background(AppTheme.colors.accentTint, shape))
        Text(name, typography = AppTheme.type.caption, color = AppTheme.colors.muted)
    }
}

@Suppress("VerifyStrings")
@Composable
private fun IconSection() {
    SectionTitle("Icons")
    AppIconGrid()
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("VerifyStrings")
@Composable
private fun CardsSection() {
    SectionTitle("Cards and rows")
    EdgeCard(contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Water", typography = AppTheme.type.cardTitle)
                    Text("3 of 8 glasses", typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
                }
                SegmentedBar(filled = 3, total = 8)
            }
            RoundIconButton(icon = AppIcons.Add, onClick = {})
        }
    }
    EdgeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon = AppIcons.meal(MealTone.Breakfast), tone = AppTheme.colors.meal(MealTone.Breakfast))
            Column(Modifier.weight(1f)) {
                Text("Breakfast", typography = AppTheme.type.cardTitle)
                Text("8:10 am", typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
            }
            RoundIconButton(icon = AppIcons.Add, onClick = {})
        }
        ListRow(title = "Oatmeal", subtitle = "1 bowl · Estimate", minHeight = 44.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillTag("Peckish")
            PillTag("Calm")
        }
    }
    EdgeCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        ListRow(
            title = "Create a meal",
            subtitle = "Group foods you often eat together",
            icon = AppIcons.BookmarkAdd,
            iconTone = AppTheme.colors.nutrients.fat,
            showDivider = true,
            onClick = {},
        ) {
            Icon(AppIcons.ChevronRight, size = 22.dp, color = AppTheme.colors.muted)
        }
        ListRow(
            title = "Greek yogurt",
            subtitle = "1 cup",
            icon = AppIcons.Restaurant,
            iconTone = AppTheme.colors.nutrients.protein,
            trailingText = "Weighed",
        ) {
            RoundIconButton(icon = AppIcons.Check, onClick = {}, selected = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("VerifyStrings")
@Composable
private fun ControlsSection() {
    SectionTitle("Controls")
    var meal by remember { mutableStateOf("Lunch") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Breakfast", "Lunch", "Snack", "Dinner").forEach {
            PillChip(label = it, selected = it == meal, onClick = { meal = it })
        }
        PillChip(label = "g", selected = false, onClick = {}, size = PillChipSize.Compact)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ActionChip("Photo", AppIcons.PhotoCamera, AppTheme.colors.nutrients.protein, onClick = {})
        ActionChip("Describe it", AppIcons.Mic, AppTheme.colors.nutrients.fiber, onClick = {})
    }
    var range by remember { mutableStateOf("Day") }
    SegmentedControl(options = listOf("Day", "Week average"), selected = range, onSelect = { range = it }, label = { it })
    var on by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Health on Today", typography = AppTheme.type.bodyStrong, modifier = Modifier.weight(1f))
        AppSwitch(checked = on, onCheckedChange = { on = it })
    }
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
        RadioRow(title = "Full numbers", subtitle = "Everything, as numbers", selected = true, onClick = {})
        RadioRow(title = "Nutrient focus", subtitle = "Protein and fiber, no calories", selected = false, onClick = {}, showDivider = false)
    }
    Stepper(amount = "1.5 cup", onDecrease = {}, onIncrease = {})
    ButtonPrimary(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Add to lunch") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ButtonSecondary(onClick = {}) { Text("Today") }
        ButtonGhost(onClick = {}) { Text("Stop asking these") }
    }
}

@Suppress("VerifyStrings")
@Composable
private fun SheetSection() {
    SectionTitle("Sheet and menu")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.bg, AppTheme.shapes.sheet)
            .border(1.dp, AppTheme.colors.line.color, AppTheme.shapes.sheet)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHandle()
        SheetHeader(title = "Greek yogurt", subtitle = "Fage, 2%", onClose = {}, closeButtonColor = AppTheme.colors.soft)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        MenuPopover(modifier = Modifier.padding(bottom = 30.dp)) {
            MenuItem("Food", onClick = {}, icon = AppIcons.Restaurant)
            MenuItem("Scan a barcode", onClick = {}, icon = AppIcons.BarcodeScanner)
            MenuItem("Weigh-in", onClick = {}, icon = AppIcons.MonitorWeight)
            MenuItem("Note", onClick = {}, icon = AppIcons.EditNote, showDivider = false)
        }
    }
}

@Suppress("VerifyStrings")
@Composable
private fun BottomNavSection() {
    Spacer(Modifier.height(20.dp))
    BottomNavBar(
        items = listOf(
            BottomNavItem("Today", AppIcons.CalendarToday, selected = true),
            BottomNavItem("Trends", AppIcons.Insights, selected = false),
            BottomNavItem("Foods", AppIcons.Restaurant, selected = false),
            BottomNavItem("More", AppIcons.MoreHoriz, selected = false),
        ),
        onItemClick = {},
        onAddClick = {},
        addIcon = AppIcons.Add,
    )
}
