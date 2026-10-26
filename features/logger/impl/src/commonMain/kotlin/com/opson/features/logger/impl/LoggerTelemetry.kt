package com.opson.features.logger.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** How something was logged, as `logger.method_picked` names it. */
enum class LogVia(val wire: String) {
    Search("search"),
    Favorite("favorite"),
    Usual("usual"),
    Recent("recent"),
    Saved("saved"),
    Foods("foods"),
    Describe("describe"),
    Yesterday("yesterday"),
    AteNoDetails("ate_no_details"),
    QuickNumber("quick_number"),
    NameOnly("name_only"),
    Note("note"),
    ;

    companion object {
        fun of(source: PickSource): LogVia = when (source) {
            PickSource.Search -> Search
            PickSource.Favorite -> Favorite
            PickSource.Usual -> Usual
            PickSource.Recent -> Recent
            PickSource.Saved -> Saved
            PickSource.Foods -> Foods
            PickSource.Describe -> Describe
            PickSource.Name -> NameOnly
        }
    }
}

/**
 * The logger's app events (`docs/practices/app-events.md`). Typed so a call
 * site cannot pass a food name or an amount: a method and a count is all
 * there is room for.
 */
interface LoggerTelemetry {
    fun methodPicked(via: LogVia, items: Int)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsLoggerTelemetry : LoggerTelemetry {
    private val logger = KLog.withTag("Logger")

    override fun methodPicked(via: LogVia, items: Int) {
        logger.logEvent("logger.method_picked", "method" to via.wire, "items" to items)
    }
}
