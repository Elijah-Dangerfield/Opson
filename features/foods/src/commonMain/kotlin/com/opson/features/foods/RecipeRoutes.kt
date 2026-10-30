package com.opson.features.foods

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/*
 * Recipes (`release.recipes`, docs/plans/recipes.md). Foods registers every one.
 */

/** Make a recipe, or change [recipeId]. */
@Serializable
data class RecipeEditRoute(val recipeId: String? = null) : Route()

/** One of the person's recipes: its numbers per serving, Log a serving, Share, Edit. */
@Serializable
data class RecipeRoute(val recipeId: String) : Route()

/**
 * Someone's shared recipe, by its share code. A recipe link (`www.opsontracker.com/r/<code>`,
 * `opson://recipe/<code>`), a scanned QR code and search all open this.
 */
@Serializable
data class PublicRecipeRoute(val code: String) : Route()

/** A sheet: what sharing [recipeId] makes public, then its link and QR code once it is shared. */
@Serializable
data class RecipeShareRoute(val recipeId: String) : Route()

/** A sheet: log a serving of one of the person's recipes ([recipeId]) or of a shared one ([code]). */
@Serializable
data class LogRecipeRoute(val recipeId: String? = null, val code: String? = null) : Route()

/** A sheet: report the shared recipe [code], called [name]. */
@Serializable
data class ReportRecipeRoute(val code: String, val name: String) : Route()

/** The camera, reading QR codes only, for a recipe's code. */
@Serializable
class ScanRecipeRoute : Route()
