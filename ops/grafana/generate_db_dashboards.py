#!/usr/bin/env python3
"""Writes the Postgres-backed dashboards in this folder.

The dashboards are generated rather than hand-edited so that every panel uses
the same datasource, time handling and thresholds, and so a change to an
`analytics` view is one edit here instead of a hunt through JSON. Run it after
changing a view, then push the files (see README.md).

Every query reads the `analytics` schema only. The Grafana role cannot read
anything else, and that is deliberate: these dashboards must never be able to
show one person's food log.
"""
import json
import pathlib

# Every Postgres panel reads the dashboard's `db` variable, so one dropdown
# switches all of them between prod (`opson-prod-db`, the default) and dev
# (`opson-db`). The regex keeps other apps' databases out of the list.
DS = {"type": "grafana-postgresql-datasource", "uid": "${db}"}
DB_VARIABLE = {
    "name": "db", "label": "Database", "type": "datasource", "query": "grafana-postgresql-datasource",
    "regex": "/^opson-(prod-)?db$/", "current": {"text": "opson-prod-db", "value": "opson-prod-db"},
    "hide": 0, "refresh": 1, "includeAll": False, "multi": False,
}
LOKI = {"type": "loki", "uid": "grafanacloud-logs"}
HERE = pathlib.Path(__file__).parent


def sql(ref, query, fmt="time_series"):
    return {"refId": ref, "datasource": DS, "rawQuery": True, "editorMode": "code", "format": fmt, "rawSql": query}


def loki(ref, expr, legend=""):
    return {"refId": ref, "datasource": LOKI, "expr": expr, "legendFormat": legend, "queryType": "range"}


class Board:
    def __init__(self, uid, title, description):
        self.uid, self.title, self.description = uid, title, description
        self.panels, self.x, self.y, self.row_h = [], 0, 0, 0

    def add(self, panel, w, h):
        if self.x + w > 24:
            self.x, self.y = 0, self.y + self.row_h
            self.row_h = 0
        panel["id"] = len(self.panels) + 1
        panel["gridPos"] = {"x": self.x, "y": self.y, "w": w, "h": h}
        self.panels.append(panel)
        self.x += w
        self.row_h = max(self.row_h, h)

    def stat(self, title, query, description, unit="none", w=6, h=4, decimals=0):
        self.add({
            "type": "stat", "title": title, "description": description, "datasource": DS,
            "targets": [sql("A", query, "table")],
            "fieldConfig": {"defaults": {"unit": unit, "decimals": decimals, "color": {"mode": "thresholds"},
                                         "thresholds": {"mode": "absolute", "steps": [{"color": "blue", "value": None}]}},
                            "overrides": []},
            "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                        "colorMode": "value", "graphMode": "none", "textMode": "value"},
        }, w, h)

    def series(self, title, targets, description, unit="none", w=12, h=8, stacked=False, bars=False, ds=DS):
        custom = {"drawStyle": "bars" if bars else "line", "fillOpacity": 60 if bars else 10, "lineWidth": 2,
                  "stacking": {"mode": "normal" if stacked else "none", "group": "A"}, "spanNulls": False,
                  "showPoints": "auto"}
        self.add({
            "type": "timeseries", "title": title, "description": description, "datasource": ds, "targets": targets,
            "fieldConfig": {"defaults": {"unit": unit, "custom": custom, "color": {"mode": "palette-classic"}},
                            "overrides": []},
            "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                        "tooltip": {"mode": "multi", "sort": "desc"}},
        }, w, h)

    def table(self, title, query, description, w=12, h=8):
        self.add({
            "type": "table", "title": title, "description": description, "datasource": DS,
            "targets": [sql("A", query, "table")],
            "fieldConfig": {"defaults": {}, "overrides": []}, "options": {"showHeader": True},
        }, w, h)

    def bars(self, title, query, description, w=12, h=8, unit="none"):
        self.add({
            "type": "barchart", "title": title, "description": description, "datasource": DS,
            "targets": [sql("A", query, "table")],
            "fieldConfig": {"defaults": {"unit": unit, "color": {"mode": "palette-classic"}}, "overrides": []},
            "options": {"orientation": "horizontal", "showValue": "auto", "legend": {"showLegend": False}},
        }, w, h)

    def pie(self, title, query, description, w=12, h=8):
        self.add({
            "type": "piechart", "title": title, "description": description, "datasource": DS,
            "targets": [sql("A", query, "table")],
            "fieldConfig": {"defaults": {"color": {"mode": "palette-classic"}}, "overrides": []},
            "options": {"pieType": "donut", "reduceOptions": {"calcs": ["lastNotNull"], "values": True},
                        "legend": {"displayMode": "table", "placement": "right", "values": ["value", "percent"]}},
        }, w, h)

    def write(self):
        doc = {
            "uid": self.uid, "title": self.title, "description": self.description,
            "tags": ["opson"], "timezone": "browser", "schemaVersion": 39, "editable": True,
            "time": {"from": "now-30d", "to": "now"}, "refresh": "1h",
            "templating": {"list": [DB_VARIABLE]}, "annotations": {"list": []}, "panels": self.panels,
        }
        (HERE / f"{self.uid.removeprefix('opson-')}.json").write_text(json.dumps(doc, indent=2) + "\n")


# ── Pulse ──────────────────────────────────────────────────────────────────
b = Board("opson-pulse", "Opson Pulse",
          "Is anyone using it today, and is the pipeline healthy? Start here.")
b.stat("Loggers today", "SELECT coalesce(max(active_loggers), 0) FROM analytics.daily_active_loggers WHERE day = current_date",
       "People with at least one log entry dated today.")
b.stat("Loggers, 7-day average",
       "SELECT coalesce(avg(active_loggers), 0) FROM analytics.daily_active_loggers WHERE day > current_date - 7",
       "Average daily loggers over the last seven days.", decimals=1)
b.stat("Entries today",
       "SELECT coalesce(sum(entries), 0) FROM analytics.entries_by_method_precision WHERE day = current_date",
       "Food entries dated today, across everyone.")
b.stat("Search zero-result rate, 7 days",
       "SELECT coalesce(sum(zero_result_searches)::float / nullif(sum(searches), 0), 0) "
       "FROM analytics.search_daily WHERE day > current_date - 7",
       "Share of searches that found nothing. High means the catalog is missing foods people eat.",
       unit="percentunit", decimals=1)
b.series("Daily active loggers", [sql("A",
         "SELECT day::timestamp AS time, active_loggers AS \"loggers\" FROM analytics.daily_active_loggers "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "People with at least one entry on each day.")
b.series("Entries per day", [sql("A",
         "SELECT day::timestamp AS time, sum(entries) AS \"entries\" FROM analytics.entries_by_method_precision "
         "WHERE $__timeFilter(day::timestamp) GROUP BY 1 ORDER BY 1")],
         "Every food entry, by the day it was logged for.", bars=True)
b.series("Days closed", [sql("A",
         "SELECT day::timestamp AS time, days_closed AS \"closed\" FROM analytics.days_closed "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Days someone closed with the check at the top of Today.", bars=True)
b.series("App launches", [loki("A",
         'sum(count_over_time({service_name="opson-client"} | event_name="app.launched" [$__interval]))', "launches")],
         "Client app.launched events. Loki keeps 31 days, so this panel cannot look further back.", ds=LOKI)
b.series("Server error logs", [loki("A",
         'sum(count_over_time({service_name="opson-server"} | detected_level="error" [$__interval]))', "errors")],
         "Error-level server log lines. Pair with Sentry for the stack traces.", ds=LOKI)
b.write()

# ── Logging ────────────────────────────────────────────────────────────────
b = Board("opson-logging", "Opson · Logging",
          "How people add food: which methods, how precise, how many meals. Aggregates only.")
b.series("Entries by method", [sql("A",
         "SELECT day::timestamp AS time, method AS metric, sum(entries) AS value "
         "FROM analytics.entries_by_method_precision WHERE $__timeFilter(day::timestamp) GROUP BY 1, 2 ORDER BY 1")],
         "Search, saved meals, copy, describe, ate with no details, and so on.", w=24, stacked=True, bars=True)
b.pie("Precision mix",
      "SELECT precision AS metric, sum(entries) AS value FROM analytics.entries_by_method_precision "
      "WHERE $__timeFilter(day::timestamp) GROUP BY 1",
      "Weighed, estimated, or just the name. A healthy mix leans on estimates; it is not a goal to push toward weighing.")
b.pie("Method mix",
      "SELECT method AS metric, sum(entries) AS value FROM analytics.entries_by_method_precision "
      "WHERE $__timeFilter(day::timestamp) GROUP BY 1",
      "Share of entries by how they were added, over the selected range.")
b.series("'Ate, no details' share", [sql("A",
         "SELECT day::timestamp AS time, sum(entries) FILTER (WHERE method = 'ate_no_details')::float "
         "/ nullif(sum(entries), 0) AS \"share\" FROM analytics.entries_by_method_precision "
         "WHERE $__timeFilter(day::timestamp) GROUP BY 1 ORDER BY 1")],
         "Meals marked eaten without details. Rising means logging feels like too much work, or people are using the gentle path as intended.",
         unit="percentunit")
b.bars("Meals per logged day",
       "SELECT meals_per_day::text AS meals, sum(person_days) AS days FROM analytics.meals_per_day_distribution "
       "WHERE $__timeFilter(day::timestamp) GROUP BY meals_per_day ORDER BY meals_per_day",
       "How many meal slots have something in them on a day with any logging.")
b.write()

# ── Modes ──────────────────────────────────────────────────────────────────
b = Board("opson-modes", "Opson · Modes",
          "Which visibility modes people choose. This is the product's core bet, so watch it.")
b.bars("People by mode",
       "SELECT preset, users FROM analytics.preset_distribution ORDER BY users DESC",
       "Current mode for every account with synced settings.", w=12, h=10)
b.pie("Mode share",
      "SELECT preset AS metric, users AS value FROM analytics.preset_distribution",
      "Same data as a share.", w=12, h=10)
b.series("Mode changes and pauses", [
    loki("A", 'sum(count_over_time({service_name="opson-client"} | event_name="mode.changed" [$__interval]))', "mode changed"),
    loki("B", 'sum(count_over_time({service_name="opson-client"} | event_name="friction.shown" [$__interval]))', "pause shown"),
    loki("C", 'sum(count_over_time({service_name="opson-client"} | event_name="friction.completed" [$__interval]))', "pause completed"),
], "Client events. A pause shown but not completed is someone who stopped before showing themselves more numbers.",
    w=24, ds=LOKI)
b.write()

# ── Food catalog ───────────────────────────────────────────────────────────
b = Board("opson-foods", "Opson · Food catalog",
          "Is the food database good enough? Zero-result searches are the to-do list.")
b.series("Searches per day", [sql("A",
         "SELECT day::timestamp AS time, searches, zero_result_searches AS \"found nothing\" FROM analytics.search_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Searches, and the ones that returned no foods.")
b.series("Zero-result rate", [sql("A",
         "SELECT day::timestamp AS time, zero_result_rate AS \"rate\" FROM analytics.search_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Share of searches that found nothing.", unit="percentunit")
b.table("Top searches that found nothing",
        "SELECT * FROM analytics.top_zero_result_queries",
        "Last 30 days, queries searched at least three times. Add these foods or synonyms first.", w=12, h=10)
b.series("Barcode lookups by outcome", [sql("A",
         "SELECT day::timestamp AS time, outcome AS metric, lookups AS value FROM analytics.barcode_outcomes_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Found in our catalog, found on Open Food Facts, or not found.", h=10, stacked=True, bars=True)
# Scan coverage (V13). The hit rate decides whether a paid barcode fallback is worth turning on.
b.stat("Barcode hit rate, 7 days",
       "SELECT coalesce(sum(hits)::float / nullif(sum(hits) + sum(misses), 0), 0) "
       "FROM analytics.barcode_hit_rate_daily WHERE day > current_date - 7",
       "Share of barcode lookups some source could answer. Lookups no source could reach are left out.",
       unit="percentunit", decimals=1)
b.stat("Barcode misses reported, 7 days",
       "SELECT coalesce(sum(misses), 0) FROM analytics.barcode_misses_by_country WHERE day > current_date - 7",
       "Misses the app reported after a scan found nothing.")
b.series("Barcode hit rate", [sql("A",
         "SELECT day::timestamp AS time, hit_rate AS \"hit rate\" FROM analytics.barcode_hit_rate_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Daily share of lookups answered by our catalog or an outside source.", unit="percentunit")
b.table("Most-missed barcodes",
        "SELECT code AS barcode, misses, countries, last_missed_at FROM analytics.top_missed_barcodes",
        "Last 30 days, grouped by GTIN so a UPC-A and its EAN-13 count once. Add these products first.",
        w=12, h=10)
b.bars("Barcode misses by country",
       "SELECT country, sum(misses) AS misses FROM analytics.barcode_misses_by_country "
       "WHERE $__timeFilter(day::timestamp) GROUP BY country ORDER BY misses DESC LIMIT 20",
       "The phone's region setting at the time of the miss. Tells us which country's data to add next.",
       w=12, h=10)
b.series("Barcode misses per day", [sql("A",
         "SELECT day::timestamp AS time, sum(misses) AS \"misses\" FROM analytics.barcode_misses_by_country "
         "WHERE $__timeFilter(day::timestamp) GROUP BY 1 ORDER BY 1")],
         "Misses the app reported, by day.", bars=True)
b.series("Scanner", [
    loki("A", 'sum(count_over_time({service_name="opson-client"} | event_name="barcode.scan_started" [$__interval]))', "scans started"),
    loki("B", 'sum(count_over_time({service_name="opson-client"} | event_name="barcode.hit" [$__interval]))', "found"),
    loki("C", 'sum(count_over_time({service_name="opson-client"} | event_name="barcode.miss" [$__interval]))', "not found"),
    loki("D", 'sum(count_over_time({service_name="opson-client"} | event_name="barcode.permission_denied" [$__interval]))', "camera denied"),
    loki("E", 'sum(count_over_time({service_name="opson-client"} | event_name="barcode.camera_unavailable" [$__interval]))', "camera unavailable"),
], "Client events. Loki keeps 31 days. Camera denied and unavailable count people who could not use the camera; they can still type the code.", ds=LOKI)
b.write()

# ── Retention ──────────────────────────────────────────────────────────────
b = Board("opson-retention", "Opson · Retention",
          "Do people keep logging? Cohorts by the week of their first entry.")
b.table("Weekly cohorts",
        "SELECT cohort_week, cohort_size, week_offset, active_users, round(retention::numeric, 3) AS retention "
        "FROM analytics.weekly_retention_cohorts ORDER BY cohort_week DESC, week_offset",
        "Share of each first-log week still logging N weeks later.", w=24, h=10)
b.series("Day 1, 7 and 30 retention by cohort day", [sql("A",
         "SELECT cohort_day::timestamp AS time, d1::float / nullif(cohort_size, 0) AS \"day 1\", "
         "d7::float / nullif(cohort_size, 0) AS \"day 7\", d30::float / nullif(cohort_size, 0) AS \"day 30\" "
         "FROM analytics.retention_d1_d7_d30 "
         "WHERE $__timeFilter(cohort_day::timestamp) ORDER BY 1")],
         "Of the people whose first entry was that day, the share who logged again on day 1, 7 and 30.",
         w=24, unit="percentunit")
b.write()
