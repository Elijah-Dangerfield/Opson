package com.opson.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

/**
 * Shared recipes (`/v1/admin/recipes`), in the Review area beside foods: the ones people reported
 * first, then hidden, shared and removed ones by filter. Three reports from different people hide
 * a recipe until someone looks. A recipe opens with what the public sees (numbers worked out by
 * the server from its ingredients) and every report. Remove takes it down for good and needs a
 * reason; Restore brings a hidden one back or lets a removed one be shared again; Dismiss closes
 * the reports and puts a hidden one back up.
 */
@Composable
internal fun RecipesView(ctx: AdminCtx, refresh: Int) {
    var filter by remember { mutableStateOf("reported") }
    var list by remember { mutableStateOf<AdminRecipeListDto?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(refresh, reload, filter) {
        list = null
        Catching { list = ctx.api.listRecipes(filter) }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load shared recipes")) }
    }

    H3 { Text("Shared recipes") }
    P(attrs = { classes("muted") }) {
        Text("Recipes people made public. Each one says it was made by a person and not checked by Opson; its numbers are worked out from the ingredients.")
    }
    Div {
        RecipeReview.FILTERS.forEach { (wire, label) ->
            Button(attrs = {
                classes(if (wire == filter) "btn" else "btn-ghost")
                onClick { filter = wire; selected = null }
            }) { Text(label) }
        }
    }
    val current = list
    when {
        current == null -> P(attrs = { classes("muted") }) { Text("Loading…") }
        current.recipes.isEmpty() -> P(attrs = { classes("muted") }) { Text("Nothing here.") }
        else -> {
            Table(attrs = { classes("flags") }) {
                Thead {
                    Tr {
                        Th { Text("Recipe") }
                        Th { Text("Reports") }
                        Th { Text("State") }
                        Th { Text("Shared") }
                    }
                }
                Tbody {
                    current.recipes.forEach { item ->
                        Tr(attrs = {
                            if (item.id == selected) classes("selected")
                            style { property("cursor", "pointer") }
                            onClick { selected = item.id }
                        }) {
                            Td {
                                Div { Text(item.name) }
                                Span(attrs = { classes("muted") }) {
                                    Text(listOfNotNull(item.code, item.ownerName, "${item.ingredientCount} ingredients").joinToString(" · "))
                                }
                            }
                            Td { Text(if (item.openReports == 0) "none" else RecipeReview.reasons(item.reasons)) }
                            Td { Text(RecipeReview.state(item.state)) }
                            Td { Text(item.publishedAtEpochMs?.let { relativeTime(it) } ?: "never") }
                        }
                    }
                }
            }
            current.recipes.firstOrNull { it.id == selected }?.let { item ->
                RecipePanel(ctx, item, onDecided = { selected = null; reload++ })
            }
        }
    }
}

@Composable
private fun RecipePanel(ctx: AdminCtx, item: AdminRecipeSummaryDto, onDecided: () -> Unit) {
    var detail by remember(item.id) { mutableStateOf<AdminRecipeDetailDto?>(null) }
    var reason by remember(item.id) { mutableStateOf("") }

    LaunchedEffect(item.id) {
        Catching { detail = ctx.api.recipe(item.id) }
            .onFailure { ctx.setStatus(Status(false, it.message ?: "Couldn't load the recipe")) }
    }

    fun act(title: String, after: String, success: String, call: suspend () -> RecipeModerationDto) {
        ctx.confirmWrite(title = title, flagPath = null, before = RecipeReview.state(item.state), after = after, success = success) {
            call()
            onDecided()
        }
    }

    Div(attrs = { classes("panel") }) {
        H3 { Text(item.name) }
        P(attrs = { classes("muted") }) {
            Text(
                listOfNotNull(
                    RecipeReview.state(item.state),
                    "owner ${item.ownerName ?: item.ownerId}" + if (item.showName) " (name shown)" else " (shown as an Opson user)",
                    "${item.saveCount} saves",
                ).joinToString(" · "),
            )
        }
        RecipeReview.link(item.code)?.let { link -> P { A(href = link) { Text(link) } } }
        item.removedReason?.let { P(attrs = { classes("err") }) { Text("Removed: $it") } }
        val d = detail
        if (d == null) {
            P(attrs = { classes("muted") }) { Text("Loading…") }
        } else {
            P { Text("Makes ${d.recipe.servings} · ${d.recipe.perServing.kcal?.let { "${it.toInt()} kcal a serving" } ?: "calories not known"} · ${d.recipe.checkedCount} of ${d.recipe.ingredients.size} from checked foods") }
            Ul {
                d.recipe.ingredients.forEach { ingredient ->
                    Li {
                        Text(
                            listOfNotNull(
                                ingredient.name,
                                ingredient.amountText,
                                ingredient.nutrients.kcal?.let { "${it.toInt()} kcal" },
                                "checked".takeIf { ingredient.checked },
                            ).joinToString(" · "),
                        )
                    }
                }
            }
            d.notes?.let { P { Text("Notes: $it") } }
            if (d.reports.isNotEmpty()) {
                H3 { Text("Reports") }
                Ul {
                    d.reports.forEach { report ->
                        Li {
                            Text(
                                listOfNotNull(
                                    RecipeReview.reason(report.reason),
                                    report.status,
                                    relativeTime(report.createdAtEpochMs),
                                    report.note,
                                ).joinToString(" · "),
                            )
                        }
                    }
                }
            }
        }
        Div {
            if (RecipeReview.canRemove(item.state)) {
                TextInput(reason) {
                    placeholder("Why it's coming down (the owner may ask)")
                    onInput { reason = it.value }
                }
                Button(attrs = {
                    classes("danger")
                    if (reason.isBlank()) attr("disabled", "")
                    onClick {
                        act("Remove this recipe", "removed: ${reason.trim()}", "Removed. The link shows nothing now.") {
                            ctx.api.removeRecipe(item.id, reason.trim())
                        }
                    }
                }) { Text("Remove") }
            }
            if (RecipeReview.canRestore(item.state)) {
                Button(attrs = {
                    onClick {
                        val after = if (item.state == "hidden") "shared again" else "not shared; the owner may share it again"
                        act("Restore this recipe", after, "Restored.") { ctx.api.restoreRecipe(item.id) }
                    }
                }) { Text("Restore") }
            }
            if (item.openReports > 0) {
                Button(attrs = {
                    onClick {
                        act("Dismiss the reports", "${item.openReports} reports dismissed", "Reports dismissed.") { ctx.api.dismissRecipeReports(item.id) }
                    }
                }) { Text("Dismiss reports") }
            }
        }
    }
}
