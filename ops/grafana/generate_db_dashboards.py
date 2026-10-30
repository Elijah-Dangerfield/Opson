#!/usr/bin/env python3
"""Writes the generated dashboards in this folder: the Postgres-backed product
boards (Food review among them), Pulse, and the Users and User detail boards.

The dashboards are generated rather than hand-edited so that every panel uses
the same datasource, environment toggle, time handling and thresholds, and so a
change to a view is one edit here instead of a hunt through JSON. Run it after
changing a view, then push the files (see README.md).

Two schemas, two rules. `analytics` views aggregate across users: the product
boards read only them. `support` views (V22) hold one person's account, builds,
counts and Settings choices, never what they logged or measured: only Users and
User detail read them, and only User detail selects an email
(DashboardQueryContractTest holds that).

One `env` dropdown drives every panel. Its value is the Postgres datasource
(`opson-prod-db` or `opson-db`) and its text is the Loki
`deployment_environment` (`prod` or `dev`), so SQL and Loki panels can never
show two environments side by side by accident.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent

DS = {"type": "grafana-postgresql-datasource", "uid": "${env}"}
LOKI = {"type": "loki", "uid": "grafanacloud-logs"}
ENV_VARIABLE = {
    "name": "env", "label": "Environment", "type": "custom",
    "query": "prod : opson-prod-db,dev : opson-db",
    "current": {"text": "prod", "value": "opson-prod-db", "selected": True},
    "options": [
        {"text": "prod", "value": "opson-prod-db", "selected": True},
        {"text": "dev", "value": "opson-db", "selected": False},
    ],
    "hide": 0, "includeAll": False, "multi": False, "skipUrlSync": False,
}

# Loki stream selectors. `${env:text}` is `prod` or `dev`.
CLIENT = '{service_name="opson-client", deployment_environment="${env:text}"}'
SERVER = '{service_name="opson-server", deployment_environment="${env:text}"}'
# One person's app events: every install `support.user_installs` has seen for the account.
THEIR = CLIENT + ' | install_id=~"${installs:regex}"'

LOKI_HORIZON = "Loki keeps 31 days, so this panel cannot look further back."
NO_TELEMETRY = "No client telemetry in this range"

GRAFANA_USER_DETAIL = "/d/opson-user/opson-user-detail?var-env=${env}&var-user=${__value.raw}"
SENTRY_USER = "https://elijah-dangerfield.sentry.io/issues/?query=user.id%3A${user}&statsPeriod=90d"


def sql(ref, query, fmt="time_series"):
    return {"refId": ref, "datasource": DS, "rawQuery": True, "editorMode": "code", "format": fmt, "rawSql": query}


def loki(ref, expr, legend="", instant=False):
    target = {"refId": ref, "datasource": LOKI, "expr": expr, "legendFormat": legend, "editorMode": "code",
              "queryType": "instant" if instant else "range"}
    if instant:
        target["instant"] = True
        target["range"] = False
    return target


def steps(*pairs):
    """Threshold steps. A descriptive number gets one neutral step; colour is kept for good and bad."""
    if not pairs:
        return {"mode": "absolute", "steps": [{"color": "text", "value": None}]}
    return {"mode": "absolute", "steps": [{"color": c, "value": v} for c, v in pairs]}


class Board:
    def __init__(self, uid, title, description, variables=(), links=(), time_from="now-30d"):
        self.uid, self.title, self.description = uid, title, description
        self.variables, self.links, self.time_from = list(variables), list(links), time_from
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

    def row(self, title):
        if self.x:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        self.add({"type": "row", "title": title, "collapsed": False, "panels": []}, 24, 1)
        self.x, self.y, self.row_h = 0, self.y + 1, 0

    def stat(self, title, target, description, unit="none", w=6, h=4, decimals=0, thresholds=None,
             no_value=None, text_mode="value", ds=DS, words=False):
        defaults = {"unit": unit, "decimals": decimals, "color": {"mode": "thresholds"},
                    "thresholds": thresholds or steps()}
        if no_value:
            defaults["noValue"] = no_value
        self.add({
            "type": "stat", "title": title, "description": description, "datasource": ds,
            "targets": [target] if isinstance(target, dict) else target,
            "fieldConfig": {"defaults": defaults, "overrides": []},
            # A stat shows numbers only unless told to read every field; [words] is for a stat that is text.
            "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "/.*/" if words else "",
                                          "values": False},
                        "colorMode": "value", "graphMode": "none", "textMode": text_mode},
        }, w, h)

    def sql_stat(self, title, query, description, **kw):
        self.stat(title, sql("A", query, "table"), description, **kw)

    def loki_stat(self, title, expr, description, **kw):
        kw.setdefault("no_value", "0")
        self.stat(title, loki("A", expr, instant=True), description + " " + LOKI_HORIZON, ds=LOKI, **kw)

    def series(self, title, targets, description, unit="none", w=12, h=8, stacked=False, bars=False, ds=DS,
               no_value=None, interval=None):
        custom = {"drawStyle": "bars" if bars else "line", "fillOpacity": 60 if bars else 10, "lineWidth": 2,
                  "stacking": {"mode": "normal" if stacked else "none", "group": "A"}, "spanNulls": False,
                  "showPoints": "auto"}
        defaults = {"unit": unit, "custom": custom, "color": {"mode": "palette-classic"}}
        if no_value:
            defaults["noValue"] = no_value
        panel = {
            "type": "timeseries", "title": title, "description": description, "datasource": ds, "targets": targets,
            "fieldConfig": {"defaults": defaults, "overrides": []},
            "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                        "tooltip": {"mode": "multi", "sort": "desc"}},
        }
        if interval:
            panel["interval"] = interval
        self.add(panel, w, h)

    def loki_series(self, title, targets, description, **kw):
        kw.setdefault("no_value", NO_TELEMETRY)
        self.series(title, targets, description + " " + LOKI_HORIZON, ds=LOKI, **kw)

    def table(self, title, target, description, w=12, h=8, no_value=None, overrides=(), ds=DS):
        defaults = {}
        if no_value:
            defaults["noValue"] = no_value
        self.add({
            "type": "table", "title": title, "description": description, "datasource": ds,
            "targets": [target] if isinstance(target, dict) else target,
            "fieldConfig": {"defaults": defaults, "overrides": list(overrides)},
            "options": {"showHeader": True, "cellHeight": "sm"},
        }, w, h)

    def sql_table(self, title, query, description, **kw):
        self.table(title, sql("A", query, "table"), description, **kw)

    def loki_table(self, title, expr, description, **kw):
        kw.setdefault("no_value", NO_TELEMETRY)
        target = loki("A", expr, instant=True)
        target["format"] = "table"
        self.table(title, target, description + " " + LOKI_HORIZON, ds=LOKI, **kw)
        # One row per label set, with the count as the last column.
        self.panels[-1]["transformations"] = [{"id": "labelsToFields", "options": {"mode": "columns"}},
                                              {"id": "merge", "options": {}},
                                              {"id": "organize", "options": {"excludeByName": {"Time": True}}}]

    def logs(self, title, expr, description, w=24, h=10):
        self.add({
            "type": "logs", "title": title, "description": description + " " + LOKI_HORIZON, "datasource": LOKI,
            "targets": [loki("A", expr)],
            "options": {"showTime": True, "wrapLogMessage": True, "enableLogDetails": True,
                        "sortOrder": "Descending", "dedupStrategy": "none"},
        }, w, h)

    def bars(self, title, query, description, w=12, h=8, unit="none", no_value=None):
        defaults = {"unit": unit, "color": {"mode": "palette-classic"}}
        if no_value:
            defaults["noValue"] = no_value
        self.add({
            "type": "barchart", "title": title, "description": description, "datasource": DS,
            "targets": [sql("A", query, "table")],
            "fieldConfig": {"defaults": defaults, "overrides": []},
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

    def text(self, title, markdown, w=24, h=3):
        self.add({"type": "text", "title": title, "options": {"mode": "markdown", "content": markdown}}, w, h)

    def write(self):
        doc = {
            "uid": self.uid, "title": self.title, "description": self.description,
            "tags": ["opson"], "timezone": "browser", "schemaVersion": 39, "editable": True,
            "time": {"from": self.time_from, "to": "now"}, "refresh": "1h",
            "templating": {"list": [ENV_VARIABLE] + self.variables}, "annotations": {"list": []},
            "links": self.links, "panels": self.panels,
        }
        (HERE / f"{self.uid.removeprefix('opson-')}.json").write_text(json.dumps(doc, indent=2) + "\n")


def link(title, url, tooltip=""):
    return {"title": title, "type": "link", "url": url, "tooltip": tooltip, "targetBlank": False,
            "keepTime": True, "includeVars": False, "icon": "dashboard"}


USERS_LINK = link("Users", "/d/opson-users/opson-c2b7-users?var-env=${env}")
PULSE_LINK = link("Pulse", "/d/opson-pulse/opson-pulse?var-env=${env}")


# ── Pulse ──────────────────────────────────────────────────────────────────
b = Board("opson-pulse", "Opson Pulse",
          "Is the app healthy, and is anyone using it? Start here. Row 1 is the glance: if it is all green or "
          "plain, close the tab.\n\nPostgres panels are durable at any range. Loki panels cannot see past 31 days, "
          "and read nothing at all while no build carries the Grafana OTLP credentials: the first stat says so.",
          links=[USERS_LINK])
b.row("The glance")
b.loki_stat("Client telemetry, last 24h", f"sum(count_over_time({CLIENT} [24h]))",
            "Every record the app shipped to Loki in the last day. 0 means no build is exporting: "
            "the GRAFANA_* secrets are missing from the release build, or nobody opened the app.",
            thresholds=steps(("red", None), ("green", 1)), w=6)
b.stat("Crash-free launches", [
    loki("A", f'sum(count_over_time({CLIENT} | event_name="app.launched" | previous_exit=~"crash|oom" [$__range]))', instant=True),
    loki("B", f'sum(count_over_time({CLIENT} | event_name="app.launched" [$__range]))', instant=True),
    {"refId": "C", "datasource": {"type": "__expr__", "uid": "__expr__"}, "type": "math",
     "expression": "1 - ($A / $B)"},
], "Launches whose previous run did not crash or run out of memory, from `previous_exit` on `app.launched`. "
   "Android only in practice: iOS reports `unknown` until MetricKit delivers, which flatters the number. "
   + LOKI_HORIZON, unit="percentunit", decimals=2, w=6,
   thresholds=steps(("red", None), ("orange", 0.98), ("green", 0.995)), no_value="no launches",
   ds={"type": "datasource", "uid": "-- Mixed --"})
b.panels[-1]["targets"][0]["hide"] = True
b.panels[-1]["targets"][1]["hide"] = True
b.sql_stat("Loggers today", "SELECT coalesce(max(active_loggers), 0) FROM analytics.daily_active_loggers WHERE day = current_date",
           "People with at least one log entry dated today.")
b.sql_stat("Loggers, 7-day average",
           "SELECT coalesce(avg(active_loggers), 0) FROM analytics.daily_active_loggers WHERE day > current_date - 7",
           "Average daily loggers over the last seven days.", decimals=1)
b.loki_stat("Installs active", f'count(sum by (install_id) (count_over_time({CLIENT} | event_name="app.foregrounded" [$__range])))',
            "Distinct installs that opened the app in the range.")
b.loki_stat("Sessions", f'count(sum by (session_id) (count_over_time({CLIENT} | event_name="app.foregrounded" [$__range])))',
            "Distinct sessions in the range. A session rolls over after 15 minutes in the background.")
b.loki_stat("Installs that could not reach us",
            f'count(sum by (install_id) (count_over_time({CLIENT} | event_name="net.backend_unreachable" [$__range])))',
            "Installs with at least one call that never reached the server (timeout, DNS, refused). One install "
            "is usually its own network; several at once is us.",
            thresholds=steps(("green", None), ("orange", 3), ("red", 10)))
b.loki_stat("App guard stops shown",
            f'sum(count_over_time({CLIENT} | event_name="guard.shown" | state=~"maintenance_blocking|upgrade_required" [$__range]))',
            "Maintenance or update screens people saw (0.2 and later). Non-zero while a kill switch is on is "
            "expected; non-zero with every switch off is a bug.",
            thresholds=steps(("text", None), ("orange", 1)))
b.row("Use")
b.series("Daily active loggers", [sql("A",
         "SELECT day::timestamp AS time, active_loggers AS \"loggers\" FROM analytics.daily_active_loggers "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "People with at least one entry on each day.")
b.series("Entries per day", [sql("A",
         "SELECT day::timestamp AS time, sum(entries) AS \"entries\" FROM analytics.entries_by_method_precision "
         "WHERE $__timeFilter(day::timestamp) GROUP BY 1 ORDER BY 1")],
         "Every food entry, by the day it was logged for.", bars=True)
b.sql_stat("Entries today",
           "SELECT coalesce(sum(entries), 0) FROM analytics.entries_by_method_precision WHERE day = current_date",
           "Food entries dated today, across everyone.")
b.sql_stat("Search zero-result rate, 7 days",
           "SELECT coalesce(sum(zero_result_searches)::float / nullif(sum(searches), 0), 0) "
           "FROM analytics.search_daily WHERE day > current_date - 7",
           "Share of searches that found nothing. High means the catalog is missing foods people eat.",
           unit="percentunit", decimals=1, thresholds=steps(("green", None), ("orange", 0.15), ("red", 0.3)))
b.sql_stat("Accounts", "SELECT count(*) FROM support.users",
           "Every account on this environment, guests included, all time.")
b.sql_stat("New accounts in range", "SELECT count(*) FROM support.users WHERE $__timeFilter(joined_at)",
           "Accounts created in the selected range.")
b.series("Days closed", [sql("A",
         "SELECT day::timestamp AS time, days_closed AS \"closed\" FROM analytics.days_closed "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Days someone closed with the check at the top of Today.", bars=True)
b.loki_series("App launches", [loki("A",
              f'sum(count_over_time({CLIENT} | event_name="app.launched" [$__interval]))', "launches")],
              "Client app.launched events.")
b.row("Health")
b.loki_series("Client warnings and errors", [
    loki("A", f'sum(count_over_time({CLIENT} | detected_level="warn" [$__interval]))', "warn"),
    loki("B", f'sum(count_over_time({CLIENT} | detected_level="error" [$__interval]))', "error"),
], "KLog lines at Warn and above forwarded from phones (not events). Read alongside Sentry.", stacked=True, bars=True)
b.loki_series("Server error logs", [loki("A",
              f'sum(count_over_time({SERVER} | detected_level="error" [$__interval]))', "errors")],
              "Error-level server log lines. Pair with Sentry for the stack traces. Empty while the server has "
              "no OTEL_EXPORTER_OTLP_ENDPOINT, which sends its logs to stdout instead.",
              no_value="No server logs in Loki in this range")
b.loki_series("Calls that never reached us", [loki("A",
              f'sum by (operation) (count_over_time({CLIENT} | event_name="net.backend_unreachable" [$__interval]))',
              "{{operation}}")],
              "By operation. A spike across operations is the server or the network in front of it.", stacked=True)
b.loki_series("App guard", [loki("A",
              f'sum by (state) (count_over_time({CLIENT} | event_name="guard.shown" [$__interval]))', "{{state}}")],
              "Which guard states people saw: maintenance stop or banner, maintenance shown offline, update "
              "required, update offered.", stacked=True, bars=True)
# The website waitlist (V16). A count per day only; the emails stay in the admin console.
b.series("Waitlist signups per day", [sql("A",
         "SELECT day::timestamp AS time, signups AS \"signups\" FROM analytics.waitlist_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "New addresses on the website waitlist, by UTC day. A repeat sign-up is not counted twice.", bars=True)
b.write()

# ── Users ──────────────────────────────────────────────────────────────────
b = Board("opson-users", "Opson · Users",
          "Who is using Opson, and how. Population, new and returning people, modes, platforms and builds, and "
          "the roster: click a user id to open everything about that person on User detail.\n\n"
          "Reads the `support` views (V22): one row per person, counts and choices only, never what anyone "
          "logged. No email appears on this board; User detail is the one board that shows it. Postgres panels "
          "are durable; the two Loki panels cannot see past 31 days.",
          links=[PULSE_LINK])
b.row("Population")
b.sql_stat("Accounts", "SELECT count(*) FROM support.users", "Every account, all time.")
b.sql_stat("Claimed", "SELECT count(*) FROM support.users WHERE NOT is_guest",
           "Accounts with an email, Google or Apple sign-in.", w=4)
b.sql_stat("Guests", "SELECT count(*) FROM support.users WHERE is_guest",
           "Accounts that live on one phone until claimed.", w=4)
b.sql_stat("New in range", "SELECT count(*) FROM support.users WHERE $__timeFilter(joined_at)",
           "Accounts created in the selected range.", w=4)
b.sql_stat("Logged in the last 7 days",
           "SELECT count(DISTINCT user_id) FROM support.user_daily_logging WHERE day > current_date - 7",
           "People with an entry dated in the last week.", w=3)
b.sql_stat("Never logged", "SELECT count(*) FROM support.users WHERE entries = 0",
           "Accounts with no entries. Onboarded and left, or just arrived.", w=3,
           thresholds=steps(("text", None)))
b.series("New and returning loggers", [sql("A",
         "SELECT d.day::timestamp AS time, "
         "count(*) FILTER (WHERE d.day = u.first_log_day) AS \"new\", "
         "count(*) FILTER (WHERE d.day > u.first_log_day) AS \"returning\" "
         "FROM support.user_daily_logging d JOIN support.users u USING (user_id) "
         "WHERE $__timeFilter(d.day::timestamp) GROUP BY 1 ORDER BY 1")],
         "People who logged each day: new is their first logged day ever, returning is any later one.",
         stacked=True, bars=True)
b.series("Accounts created per day", [sql("A",
         "SELECT date_trunc('day', joined_at) AS time, "
         "count(*) FILTER (WHERE is_guest) AS \"guest\", count(*) FILTER (WHERE NOT is_guest) AS \"claimed\" "
         "FROM support.users WHERE $__timeFilter(joined_at) GROUP BY 1 ORDER BY 1")],
         "New accounts by day, split by whether they are claimed now.", stacked=True, bars=True)
b.row("Modes, platforms and builds")
b.pie("Mode", "SELECT coalesce(preset, 'not synced yet') AS metric, count(*) AS value FROM support.users GROUP BY 1",
      "The visibility mode each account runs now.", w=8)
b.pie("Platform", "SELECT coalesce(platform, 'not seen by the server') AS metric, count(*) AS value "
      "FROM support.users GROUP BY 1",
      "The platform of each account's most recent install. 'Not seen' accounts have made no signed-in request "
      "since the 0.2 server began recording builds.", w=8)
b.sql_table("Builds in use",
            "SELECT platform, app_version AS version, build_number AS build, count(*) AS people, "
            "max(last_seen_at) AS last_seen FROM support.users WHERE app_version IS NOT NULL "
            "GROUP BY 1, 2, 3 ORDER BY people DESC",
            "The build each account's latest install runs. Read this before raising the version floor.", w=8,
            no_value="no builds recorded yet")
b.loki_series("Installs active by platform", [loki("A",
              f'count by (platform) (sum by (platform, install_id) (count_over_time({CLIENT} | event_name="app.foregrounded" [$__interval])))',
              "{{platform}}")],
              "Distinct installs that opened the app, per interval, by platform.", stacked=True, interval="1d")
b.loki_table("Builds seen in telemetry",
             f'count by (platform, service_version) (sum by (platform, service_version, install_id) (count_over_time({CLIENT} | event_name="app.foregrounded" [$__range])))',
             "Installs by platform and app version that opened the app in the range.")
b.row("Roster")
b.sql_table("People",
            "SELECT user_id::text AS user_id, coalesce(display_name, '') AS name, "
            "CASE WHEN is_guest THEN 'guest' ELSE 'claimed' END AS account, joined_at AS joined, "
            "last_seen_at AS last_seen, coalesce(platform, '') AS platform, coalesce(app_version, '') AS version, "
            "coalesce(preset, '') AS mode, days_logged, entries, last_log_day "
            "FROM support.users ORDER BY coalesce(last_seen_at, joined_at) DESC NULLS LAST LIMIT 500",
            "Everyone, most recently seen first. Click a user id for User detail. Counts only: this board cannot "
            "show what anyone logged.", w=24, h=14,
            overrides=[{"matcher": {"id": "byName", "options": "user_id"}, "properties": [
                {"id": "links", "value": [{"title": "User detail", "url": GRAFANA_USER_DETAIL}]},
                {"id": "custom.width", "value": 300}]}])
b.write()

# ── User detail ────────────────────────────────────────────────────────────
USER_VARIABLE = {
    "name": "user", "label": "Person", "type": "query", "datasource": DS,
    "query": "SELECT coalesce(display_name, email, 'guest') || ' · ' || left(user_id::text, 8) AS __text, "
             "user_id::text AS __value FROM support.users "
             "ORDER BY last_seen_at DESC NULLS LAST, joined_at DESC LIMIT 1000",
    "definition": "support.users", "refresh": 1, "includeAll": False, "multi": False, "hide": 0, "sort": 0,
}
# Every install id the server has seen for the person, for the Loki panels. 'none' when there is
# none: an empty regex would match every install on the stack.
INSTALLS_VARIABLE = {
    "name": "installs", "label": "Installs", "type": "query", "datasource": DS,
    "query": "SELECT install_id FROM support.user_installs WHERE user_id = '$user' AND install_id <> '' "
             "UNION SELECT 'none' WHERE NOT EXISTS (SELECT 1 FROM support.user_installs "
             "WHERE user_id = '$user' AND install_id <> '')",
    "definition": "support.user_installs", "refresh": 1, "includeAll": True, "multi": True, "hide": 2,
    "current": {"text": "All", "value": "$__all"},
}
b = Board("opson-user", "Opson · User detail",
          "Everything about one person: account, builds, settings, how they log, their sessions, sync and "
          "errors. Pick someone from the dropdown or click a user id on Users.\n\n"
          "Postgres panels (the `support` views) are durable and hold counts and choices, never what they "
          "logged. Loki panels find their app events by the install ids the server recorded for the account "
          "(from the 0.2 server on) and cannot see past 31 days.",
          variables=[USER_VARIABLE, INSTALLS_VARIABLE],
          links=[USERS_LINK, link("Sentry issues for this person", SENTRY_USER), PULSE_LINK],
          time_from="now-30d")
WHO = "WHERE user_id = '$user'"
b.row("Who this is")
b.sql_table("Account",
            "SELECT user_id::text AS user_id, coalesce(email, '') AS email, coalesce(display_name, '') AS name, "
            "CASE WHEN is_guest THEN 'guest' ELSE 'claimed' END AS account, joined_at AS joined, "
            "last_sign_in_at AS last_sign_in, last_seen_at AS last_seen_by_server, "
            f"(current_date - joined_at::date) AS days_since_joining FROM support.users {WHO}",
            "user_id is what every admin action and Sentry search takes. The email is here and in the admin "
            "console only; telemetry never has it.", w=24, h=4,
            overrides=[{"matcher": {"id": "byName", "options": "user_id"}, "properties": [
                {"id": "links", "value": [{"title": "Sentry issues for this person", "url": SENTRY_USER,
                                           "targetBlank": True}]},
                {"id": "custom.width", "value": 300}]}])
b.sql_stat("Mode", f"SELECT coalesce(preset, 'not synced') FROM support.users {WHO}", "Their visibility mode now.",
           w=4, words=True)
b.sql_stat("Runs", "SELECT coalesce(platform || ' ' || app_version || ' (' || build_number || ')', 'not seen') "
           f"FROM support.users {WHO}", "Their most recent install's platform and build.", w=5, words=True)
b.sql_stat("Entries, all time", f"SELECT entries FROM support.users {WHO}", "Every live entry.", w=3)
b.sql_stat("Days logged", f"SELECT days_logged FROM support.users {WHO}", "Distinct days with an entry.", w=3)
b.sql_stat("Entries, last 7 days",
           f"SELECT coalesce(sum(entries), 0) FROM support.user_daily_logging {WHO} AND day > current_date - 7",
           "Entries dated in the last week.", w=3)
b.sql_stat("Last logged", f"SELECT coalesce(last_log_day::text, 'never') FROM support.users {WHO}",
           "The latest day with an entry.", w=3, words=True)
b.sql_stat("Days closed", f"SELECT count(*) FILTER (WHERE closed) FROM support.user_daily_marks {WHO}",
           "Days they closed with the check on Today.", w=3)
b.row("Builds")
b.sql_table("Every build they ran",
            "SELECT CASE WHEN install_id = '' THEN '(no id yet)' ELSE install_id END AS install, platform, "
            "app_version AS version, build_number AS build, first_seen_at AS first_seen, last_seen_at AS last_seen "
            f"FROM support.user_installs {WHO} ORDER BY last_seen_at DESC",
            "One row per install and build, from the first and latest signed-in request it made. Recorded from "
            "the 0.2 server on; 0.1 phones are recorded too once they talk to it.", w=14,
            no_value="no signed-in request recorded yet")
b.loki_table("Builds seen in their telemetry",
             f'sum by (platform, service_version, release_channel) (count_over_time({THEIR} | event_name="app.foregrounded" [$__range]))',
             "Foregrounds by build from the app's own events.", w=10)
b.row("Settings")
b.sql_table("What they chose",
            "SELECT key AS setting, value FROM support.user_settings s, "
            f"jsonb_each_text(to_jsonb(s) - 'user_id') AS kv (key, value) {WHO.replace('user_id', 's.user_id')} "
            "ORDER BY key",
            "Settings from an allowlist: mode, visibility overrides, the pause, theme, tone, reminders and so on. "
            "numbers_changed_at is when they last changed the mode or a visibility; settings_synced_at is the "
            "last time any setting reached the server. Targets and onboarding answers are never shown.",
            w=10, h=12, no_value="no settings synced yet")
b.logs("Mode and visibility changes", f'{THEIR} | event_name="mode.changed"',
       "Each change of mode or visibility: from, to, whether it shows fewer numbers, and whether it came "
       "through the pause. Expand a line for the attributes.", w=14, h=6)
# By event only: SettingsNumbersChanger names `subject` and `applied` through constants, which the
# contract test's source scan cannot read, so a panel grouping on them could not be held to the code.
b.loki_series("The pause before showing more", [
    loki("A", f'sum(count_over_time({THEIR} | event_name="friction.shown" [$__interval]))', "pause shown"),
    loki("B", f'sum(count_over_time({THEIR} | event_name="friction.completed" [$__interval]))', "pause closed"),
], "Times the pause sheet opened and closed. Expand the mode changes above to see which ones went ahead.",
    w=14, h=6, bars=True)
b.row("Logging")
b.series("Entries per day by method", [sql("A",
         "SELECT day::timestamp AS time, search, barcode, describe, saved, copy, recent, favorite, custom, "
         "quick_number AS \"quick number\", ate_no_details AS \"ate, no details\" "
         f"FROM support.user_daily_logging {WHO} AND $__timeFilter(day::timestamp) ORDER BY 1")],
         "How they added food each day. Photo estimates are stored as describe.", w=16, stacked=True, bars=True,
         no_value="nothing logged in this range")
b.sql_table("Methods, all time",
            "SELECT method, entries FROM (SELECT sum(search) AS search, sum(barcode) AS barcode, "
            "sum(describe) AS describe, sum(saved) AS saved, sum(copy) AS copy, sum(recent) AS recent, "
            "sum(favorite) AS favorite, sum(custom) AS custom, sum(quick_number) AS quick_number, "
            f"sum(ate_no_details) AS ate_no_details FROM support.user_daily_logging {WHO}) t, "
            "LATERAL (VALUES ('search', search), ('barcode', barcode), ('describe', describe), ('saved', saved), "
            "('copy', copy), ('recent', recent), ('favorite', favorite), ('custom', custom), "
            "('quick number', quick_number), ('ate, no details', ate_no_details)) AS m (method, entries) "
            "WHERE entries > 0 ORDER BY entries DESC",
            "Which ways of logging they rely on.", w=8, no_value="nothing logged")
b.sql_table("Days",
            "SELECT coalesce(l.day, m.day) AS day, coalesce(l.entries, 0) AS entries, coalesce(l.meals, 0) AS meals, "
            "coalesce(l.weighed, 0) AS weighed, coalesce(l.estimated, 0) AS estimated, "
            "coalesce(l.named_only, 0) AS named_only, coalesce(l.from_label, 0) AS from_label, "
            "coalesce(m.closed, false) AS closed, "
            "coalesce(m.checked_in, false) AS checked_in, coalesce(m.meals_tagged, 0) AS meals_tagged, "
            "coalesce(m.weigh_ins, 0) AS weigh_ins, coalesce(m.fasted, false) AS fasted "
            f"FROM (SELECT * FROM support.user_daily_logging {WHO}) l "
            f"FULL JOIN (SELECT * FROM support.user_daily_marks {WHO}) m ON m.day = l.day "
            "ORDER BY 1 DESC LIMIT 60",
            "Their last 60 days with anything on them: entries and meals, how precise, and what else they did. "
            "Counts and yes or no only; weigh_ins counts weigh-ins, never a weight.", w=24, h=10,
            no_value="nothing logged")
b.loki_table("Features they reached for",
             f'sum by (method) (count_over_time({THEIR} | event_name="logger.method_picked" [$__range]))',
             "Each way of logging they picked in the app, photo and voice included.", w=12)
b.loki_series("Scanner, photo and voice", [
    loki("A", f'sum(count_over_time({THEIR} | event_name="barcode.scan_started" [$__interval]))', "barcode scans"),
    loki("B", f'sum(count_over_time({THEIR} | event_name="photo.started" [$__interval]))', "photo estimates"),
    loki("C", f'sum(count_over_time({THEIR} | event_name="voice.started" [$__interval]))', "voice"),
    loki("D", f'sum(count_over_time({THEIR} | event_name="search.sort_picked" [$__interval]))', "search sort changed"),
], "The 0.2 features, as they started them.", w=12, bars=True, stacked=True)
b.row("Sessions")
b.loki_series("Opens per day", [
    loki("A", f'sum by (cold_start) (count_over_time({THEIR} | event_name="app.foregrounded" [$__interval]))',
         "cold start {{cold_start}}"),
], "Each time they brought the app to the front.", w=12, bars=True, stacked=True, interval="1d")
b.loki_stat("Sessions in range",
            f'count(sum by (session_id) (count_over_time({THEIR} | event_name="app.foregrounded" [$__range])))',
            "Distinct sessions.", w=4)
b.loki_stat("Median session",
            f'quantile_over_time(0.5, {THEIR} | event_name="app.backgrounded" | unwrap session_duration_sec [$__range]) by (service_name)',
            "Half of their sessions were shorter than this.", unit="s", w=4, no_value="no sessions")
b.loki_stat("Launches after a crash",
            f'sum(count_over_time({THEIR} | event_name="app.launched" | previous_exit=~"crash|oom|anr" [$__range]))',
            "Cold starts whose previous run crashed, froze or ran out of memory. iOS mostly reports unknown.",
            w=4, thresholds=steps(("green", None), ("red", 1)))
b.loki_table("How their app last ended",
             f'sum by (previous_exit, platform) (count_over_time({THEIR} | event_name="app.launched" [$__range]))',
             "Launches by how the run before ended.", w=12, h=6)
b.loki_table("App guard they saw",
             f'sum by (state) (count_over_time({THEIR} | event_name="guard.shown" [$__range]))',
             "Maintenance or update screens and strips they were shown (0.2 and later).", w=12, h=6)
b.row("Sync health")
b.sql_stat("Last change synced",
           f"SELECT coalesce(to_char(last_synced_change_at, 'YYYY-MM-DD HH24:MI'), 'never') FROM support.users {WHO}",
           "The newest change from their phone the server holds: an entry or a setting.", w=6, words=True)
b.sql_stat("Last seen by the server",
           f"SELECT coalesce(to_char(last_seen_at, 'YYYY-MM-DD HH24:MI'), 'not recorded') FROM support.users {WHO}",
           "Their latest signed-in request of any kind. Recent here and old above means the phone talks to us "
           "but has nothing new, or cannot push.", w=6, words=True)
b.loki_table("Calls that never reached us",
             f'sum by (operation, error_kind) (count_over_time({THEIR} | event_name="net.backend_unreachable" [$__range]))',
             "Requests from their phone that failed before any answer.", w=12, h=6)
b.loki_series("Offline banner", [loki("A",
              f'sum by (visible) (count_over_time({THEIR} | event_name="net.offline_banner" [$__interval]))',
              "visible {{visible}}")],
              "Each time the offline banner came or went on their phone.", w=24, h=6, bars=True)
b.row("Errors and crashes")
b.text("Sentry", "Crashes, errors and feedback for this person are in Sentry under their account id: "
       "[open Sentry for this person](" + SENTRY_USER + "). Sentry keeps the stack traces; this board keeps "
       "the counts.", h=3)
b.logs("Their warnings and errors", f'{THEIR} | detected_level=~"warn|error"',
       "Warn-and-above log lines from their phone. Messages and exception messages never carry a URL query.",
       h=12)
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
b.loki_series("Mode changes and pauses", [
    loki("A", f'sum(count_over_time({CLIENT} | event_name="mode.changed" [$__interval]))', "mode changed"),
    loki("B", f'sum(count_over_time({CLIENT} | event_name="friction.shown" [$__interval]))', "pause shown"),
    loki("C", f'sum(count_over_time({CLIENT} | event_name="friction.completed" [$__interval]))', "pause completed"),
], "Client events. A pause shown but not completed is someone who stopped before showing themselves more numbers.",
    w=24)
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
b.sql_table("Top searches that found nothing",
            "SELECT * FROM analytics.top_zero_result_queries",
            "Last 30 days, queries searched at least three times. Add these foods or synonyms first.", w=12, h=10)
b.series("Barcode lookups by outcome", [sql("A",
         "SELECT day::timestamp AS time, outcome AS metric, lookups AS value FROM analytics.barcode_outcomes_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Found in our catalog, found on Open Food Facts, or not found.", h=10, stacked=True, bars=True)
# Scan coverage (V13). The hit rate decides whether a paid barcode fallback is worth turning on.
b.sql_stat("Barcode hit rate, 7 days",
           "SELECT coalesce(sum(hits)::float / nullif(sum(hits) + sum(misses), 0), 0) "
           "FROM analytics.barcode_hit_rate_daily WHERE day > current_date - 7",
           "Share of barcode lookups some source could answer. Lookups no source could reach are left out.",
           unit="percentunit", decimals=1, thresholds=steps(("red", None), ("orange", 0.7), ("green", 0.9)))
b.sql_stat("Barcode misses reported, 7 days",
           "SELECT coalesce(sum(misses), 0) FROM analytics.barcode_misses_by_country WHERE day > current_date - 7",
           "Misses the app reported after a scan found nothing.")
b.series("Barcode hit rate", [sql("A",
         "SELECT day::timestamp AS time, hit_rate AS \"hit rate\" FROM analytics.barcode_hit_rate_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Daily share of lookups answered by our catalog or an outside source.", unit="percentunit")
b.sql_table("Most-missed barcodes",
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
b.loki_series("Scanner", [
    loki("A", f'sum(count_over_time({CLIENT} | event_name="barcode.scan_started" [$__interval]))', "scans started"),
    loki("B", f'sum(count_over_time({CLIENT} | event_name="barcode.hit" [$__interval]))', "found"),
    loki("C", f'sum(count_over_time({CLIENT} | event_name="barcode.miss" [$__interval]))', "not found"),
    loki("D", f'sum(count_over_time({CLIENT} | event_name="barcode.permission_denied" [$__interval]))', "camera denied"),
    loki("E", f'sum(count_over_time({CLIENT} | event_name="barcode.camera_unavailable" [$__interval]))', "camera unavailable"),
], "Client events. Camera denied and unavailable count people who could not use the camera; they can still type the code.")
# Energy against the macros (V33). Each food is judged as it is served; the rule is in
# docs/food-data-sources.md. Ids and gaps only, no names or stated values.
b.row("Energy math: do the calories add up to the macros?")
b.sql_stat("Foods that don't add up",
           "SELECT coalesce(sum(inconsistent)::float / nullif(sum(consistent) + sum(inconsistent), 0), 0) "
           "FROM analytics.food_energy_by_source",
           "Of the foods that could be judged, the share whose calories are further from their macros' energy than a "
           "label's rounding allows (15 kcal or 12% a serving).",
           unit="percentunit", decimals=1, thresholds=steps(("green", None), ("orange", 0.02), ("red", 0.05)))
b.sql_stat("Not judged yet",
           "SELECT coalesce(sum(unchecked), 0) FROM analytics.food_energy_by_source",
           "Rows loaded before V33 that scripts/check_food_energy.py has not reached. 0 once it has run.",
           thresholds=steps(("green", None), ("orange", 1)))
b.sql_stat("Can't be judged",
           "SELECT coalesce(sum(unknown), 0) FROM analytics.food_energy_by_source",
           "No energy or a macro missing, or energy with no macros and no alcohol figure (spirits).", w=12)
b.bars("Judged foods by source",
       "SELECT source || ' (tier ' || tier || ')' AS source, consistent AS \"add up\", inconsistent AS \"don't\", "
       "unknown AS \"can't tell\", unchecked AS \"not judged\" FROM analytics.food_energy_by_source "
       "ORDER BY tier, source",
       "Every approved food per source. off_export is the monthly Open Food Facts table; off is the barcode cache in "
       "foods. Alcoholic drinks show as don't, since no source here carries an alcohol figure yet.",
       w=12, h=10)
b.sql_table("Furthest off",
            "SELECT food_id, source, tier, gap_kcal_per_100 AS \"stated minus macros, kcal per 100\", checked_at "
            "FROM analytics.food_energy_worst",
            "The 200 foods whose calories are furthest from their macros, per 100 g or ml. Positive: more calories than "
            "the macros give (alcohol, kJ read as kcal). Negative: fewer (a label's 0, sugar alcohols, a dropped digit). "
            "A reviewer's correction takes a food off this list.",
            w=12, h=10, no_value="Every food adds up")
b.write()

# ── Food review ────────────────────────────────────────────────────────────
# Foods people add from photos of a pack (V25, release.submitFood). The views carry ids, counts and
# ages only: no name, barcode or number from a label, and no account. A pending row links into the
# admin console, which fetches the photos with its token; Grafana never sees them.
ADMIN_REVIEW = "https://opson-server-${env:text}.fly.dev/admin/#review/${__value.raw}"
FOODS_LINK = link("Food catalog", "/d/opson-foods/opson-food-catalog?var-env=${env}")
b = Board("opson-food-review", "Opson · Food review",
          "Foods people added from photos of a pack, waiting for review. A reviewer approves them in the admin "
          "console's Review tab; each pending row here links there. Approved foods join search and barcode lookup "
          "for everyone.\n\nThe app half of the funnel (a miss, Add this food, the photos, the read, the submission) "
          "is Loki and cannot see past 31 days; the review half is Postgres.",
          links=[FOODS_LINK])
b.row("The queue")
b.sql_stat("Waiting for review", "SELECT count(*) FROM analytics.food_submission_queue",
           "Submissions nobody has approved or rejected yet.", thresholds=steps(("green", None), ("orange", 20), ("red", 100)))
b.sql_stat("Oldest waiting", "SELECT coalesce(max(age_hours), 0) FROM analytics.food_submission_queue",
           "Hours since the oldest pending submission was sent. Its submitter has it already; everyone else waits.",
           unit="h", decimals=1, thresholds=steps(("green", None), ("orange", 48), ("red", 168)))
b.sql_stat("Median wait",
           "SELECT coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY age_hours), 0) FROM analytics.food_submission_queue",
           "The middle pending submission's age.", unit="h", decimals=1)
b.sql_stat("Suggested barcodes waiting", "SELECT coalesce(max(suggestions), 0) FROM analytics.barcode_link_queue",
           "Barcodes someone missed and then found by searching, waiting to be attached or dismissed.")
b.row("Submitted and decided")
b.series("Submissions per day", [sql("A",
         "SELECT day::timestamp AS time, submitted AS \"submitted\" FROM analytics.food_submissions_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Foods people sent for review, by the day they sent them.", bars=True)
b.series("Decisions per day", [sql("A",
         "SELECT day::timestamp AS time, approved AS \"approved\", rejected AS \"rejected\" FROM analytics.food_submissions_daily "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "Approvals and rejections, by the day of the decision.", bars=True, stacked=True)
b.sql_stat("Approval rate",
           "SELECT coalesce(sum(approved)::float / nullif(sum(approved) + sum(rejected), 0), 0) "
           "FROM analytics.food_submissions_daily WHERE $__timeFilter(day::timestamp)",
           "Of the decisions in the range, the share approved. Low means the reading or the form needs work.",
           unit="percentunit", decimals=1, w=8)
b.sql_stat("Decided in range",
           "SELECT coalesce(sum(approved) + sum(rejected), 0) FROM analytics.food_submissions_daily "
           "WHERE $__timeFilter(day::timestamp)", "Approvals and rejections in the range.", w=8)
b.sql_stat("Failing a check, pending",
           "SELECT count(*) FILTER (WHERE failed_checks > 0) FROM analytics.food_submission_queue",
           "Pending submissions the label checks flag: energy against the macros, per serving against per 100, "
           "sodium, missing or impossible amounts. Review these first.", w=8)
b.row("Pending")
b.sql_table("Pending submissions",
            "SELECT submission_id, submitted_at, age_hours, origin, has_barcode, failed_checks "
            "FROM analytics.food_submission_queue",
            "Oldest first. The id opens the submission in this environment's admin console, which needs the admin "
            "token and shows the photos; nothing here does.", w=24, h=10, no_value="Nothing waiting",
            overrides=[{"matcher": {"id": "byName", "options": "submission_id"}, "properties": [
                {"id": "links", "value": [{"title": "Review in the admin console", "url": ADMIN_REVIEW, "targetBlank": True}]},
            ]}])
b.row("The app's funnel")
b.loki_series("Add this food, step by step", [
    loki("A", f'sum(count_over_time({CLIENT} | event_name="barcode.miss" [$__interval]))', "barcode missed"),
    loki("B", f'sum(count_over_time({CLIENT} | event_name="food_add.started" [$__interval]))', "add started"),
    loki("C", f'sum(count_over_time({CLIENT} | event_name="food_add.photo_taken" | side="label" [$__interval]))', "label photo taken"),
    loki("D", f'sum(count_over_time({CLIENT} | event_name="food_add.read" | outcome="read" [$__interval]))', "label read"),
    loki("E", f'sum(count_over_time({CLIENT} | event_name="food_add.submitted" | sent="true" [$__interval]))', "sent for review"),
], "Client events, no barcode or food in any of them. Approval is the Postgres half above.", w=24)
b.loki_table("Where people stop",
             f'sum by (stage) (count_over_time({CLIENT} | event_name="food_add.abandoned" [$__range]))',
             "Add this food left before anything was logged, by the step it was left at.")
b.loki_table("How reads end",
             f'sum by (outcome) (count_over_time({CLIENT} | event_name="food_add.read" [$__range]))',
             "Read: the form filled in. no_label: the second photo had no panel. unavailable: no model key on the "
             "server, so the form opened empty.")
b.write()

# ── Builders: contributions and abuse ──────────────────────────────────────
# V30: is anyone entering fake data, and who? Submissions, reports and corrections by state; the
# queues; one row per contributor with volume, approval, disagreement with the label reading,
# velocity, impossible values and reused photos; device checks; bans. `analytics` for the
# aggregates, `support` for the per-contributor rows (ids, counts and codes only: no food name,
# barcode, number from a label or photo). Each account id opens the admin console's Users tab,
# where Ban, Suspend, Restrict contributions, Lift and Roll back live.
ADMIN_USER = "https://opson-server-${env:text}.fly.dev/admin/#users/${__value.raw}"
USER_LINKS = [
    {"title": "Moderate in the admin console", "url": ADMIN_USER, "targetBlank": True},
    {"title": "User detail", "url": GRAFANA_USER_DETAIL},
]


def user_links(*columns):
    return [{"matcher": {"id": "byName", "options": c}, "properties": [{"id": "links", "value": USER_LINKS}]} for c in columns]


def by_state(kind):
    return [sql("A", "SELECT day::timestamp AS time, state AS metric, sum(contributions) AS value "
                     f"FROM analytics.contributions_daily WHERE kind = '{kind}' AND $__timeFilter(day::timestamp) "
                     "GROUP BY 1, 2 ORDER BY 1")]


# Contributors worth a look: flagged, reusing photos, or far above their own pace.
SUSPECT = ("(flagged_7d >= 3 OR photo_reuse_hits > 0 OR impossible_total > 0 "
           "OR (submitted_24h >= 5 AND submitted_24h > 3 * coalesce(median_daily_30d, 0)) "
           "OR (account_age_days < 3 AND submitted_total >= 10))")

b = Board("opson-builders", "Opson · Builders",
          "Are people entering fake data, and who? Start with the glance: if it is all green, close the tab.\n\n"
          "Every account id opens the admin console's Users tab, where an account can be banned, suspended or "
          "restricted from contributing, and its contributions rolled back. Nothing here shows a food's name, a "
          "barcode, a number from a label or a photo: look at the food in the console.\n\n"
          "Alert rules for the same signals are in ops/grafana/alerts/builders.json.",
          links=[USERS_LINK, link("Food review", "/d/opson-food-review/opson-food-review?var-env=${env}")])
b.row("The glance")
b.sql_stat("Contributors to look at", f"SELECT count(*) FROM support.contributors WHERE {SUSPECT} AND standing = 'ok'",
           "In good standing, and flagged three or more times this week, reusing a photo, sending impossible values, "
           "sending three times their usual day, or under three days old with ten or more foods.",
           thresholds=steps(("green", None), ("orange", 1), ("red", 5)), w=4)
b.sql_stat("Photos reused across accounts, 7 days",
           "SELECT count(*) FROM support.photo_reuse WHERE cross_account AND created_at > now() - interval '7 days'",
           "The same photo bytes sent by two accounts. Almost always one person with several accounts.",
           thresholds=steps(("green", None), ("red", 1)), w=4)
b.sql_stat("Impossible values, 7 days",
           "SELECT coalesce(sum(contributions), 0) FROM analytics.contribution_flags_daily "
           "WHERE flag IN ('impossible_value', 'kcal_zero_with_macros') AND day > current_date - 7",
           "Submissions with an amount no food can hold, or 0 kcal beside macros that add up to more.",
           thresholds=steps(("green", None), ("orange", 1), ("red", 10)), w=4)
b.sql_stat("Approval rate, 30 days",
           "SELECT coalesce(sum(contributions) FILTER (WHERE state = 'approved')::float "
           "/ nullif(sum(contributions) FILTER (WHERE state IN ('approved', 'rejected')), 0), 1) "
           "FROM analytics.contributions_daily WHERE kind = 'submission' AND day > current_date - 30",
           "Of submissions decided in the last 30 days, the share approved. A collapse means junk is arriving.",
           unit="percentunit", decimals=1, thresholds=steps(("red", None), ("orange", 0.6), ("green", 0.8)), w=4)
b.sql_stat("Waiting for review",
           "SELECT coalesce(sum(waiting), 0) + (SELECT coalesce(max(open_verifications), 0) FROM analytics.food_verification_queue) "
           "FROM analytics.contribution_queue",
           "Submissions pending, reports open and Builders' verifications waiting for a person.",
           thresholds=steps(("green", None), ("orange", 20), ("red", 100)), w=4)
b.sql_stat("Bans and restrictions in force", "SELECT count(*) FROM support.moderation_actions WHERE active",
           "Bans, suspensions and contribution restrictions not lifted or ended.", w=4)

b.row("Submissions, reports and corrections")
b.series("Submissions per day, by state", by_state("submission"),
         "submitted by the day sent; approved and rejected by the day decided; rolled_back when moderation took an "
         "approved food back out.", bars=True, stacked=True, w=8)
b.series("Reports per day, by state", by_state("report"),
         "Report a problem from the app: submitted, then accepted (a correction), rejected or duplicate. The server's "
         "own energy checks are left out.", bars=True, stacked=True, w=8)
b.series("Corrections per day", by_state("correction"),
         "Corrections laid over a food (applied), undone (reverted), and those rolled back with a banned reporter's "
         "contributions (in Reports, rolled_back).", bars=True, stacked=True, w=8)
b.sql_table("Queues", "SELECT kind, waiting, round(oldest_hours::numeric, 1) AS oldest_hours, "
                      "round(median_hours::numeric, 1) AS median_hours FROM analytics.contribution_queue",
            "What waits for a reviewer, how many, and how old in hours.", w=8, h=6)
# Database cleanup: reports and Builders' verifications both fix foods already in the catalog, and are
# reviewed in the same place (the admin console's Review), so they sit side by side, the same panels each.
b.row("Database cleanup: verifications and reports")
b.sql_stat("Verifications waiting", "SELECT coalesce(max(open_verifications), 0) FROM analytics.food_verification_queue",
           "Builders' checks against the pack that need a person: something was wrong, or the label's text did not "
           "show the food's numbers.", w=3, h=5, thresholds=steps(("green", None), ("orange", 20), ("red", 100)))
b.sql_stat("Foods with a waiting verification", "SELECT coalesce(max(foods), 0) FROM analytics.food_verification_queue",
           "Distinct foods those verifications are about.", w=3, h=5)
b.sql_stat("Said something was wrong", "SELECT coalesce(max(with_problem), 0) FROM analytics.food_verification_queue",
           "Of the waiting verifications, those where the Builder flagged fields as wrong or missing.", w=3, h=5)
b.sql_stat("Oldest waiting verification",
           "SELECT coalesce(extract(epoch FROM now() - max(oldest_at)) / 3600, 0) FROM analytics.food_verification_queue",
           "Hours since the oldest waiting verification was sent.", unit="h", decimals=1, w=3, h=5,
           thresholds=steps(("green", None), ("orange", 48), ("red", 168)))
b.sql_stat("Reports waiting", "SELECT coalesce(max(open_reports), 0) FROM analytics.food_report_queue",
           "Reports nobody has decided, the server's energy checks included.", w=3, h=5,
           thresholds=steps(("green", None), ("orange", 20), ("red", 100)))
b.sql_stat("Foods with a waiting report", "SELECT coalesce(max(foods), 0) FROM analytics.food_report_queue",
           "Distinct foods those reports are about.", w=3, h=5)
b.sql_stat("Energy checks waiting", "SELECT coalesce(max(energy_checks), 0) FROM analytics.food_report_queue",
           "Foods the server flagged itself: 0 kcal beside macros.", w=3, h=5)
b.sql_stat("Oldest waiting report",
           "SELECT coalesce(extract(epoch FROM now() - max(oldest_at)) / 3600, 0) FROM analytics.food_report_queue",
           "Hours since the oldest open report was sent.", unit="h", decimals=1, w=3, h=5,
           thresholds=steps(("green", None), ("orange", 48), ("red", 168)))
b.series("Verifications per day", [sql("A",
         "SELECT day::timestamp AS time, metric, value FROM analytics.food_verifications_daily, LATERAL (VALUES "
         "('confirmed on arrival', confirmed_on_arrival), ('sent for review', sent_for_review), "
         "('accepted', accepted), ('rejected', rejected)) AS v(metric, value) "
         "WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "By the day sent. Confirmed on arrival: the label's text showed the food's four numbers and the Builder said "
         "nothing was wrong, so the food reads Verified at once. Sent for review: waits for a person (accepted and "
         "rejected show how those ended).", bars=True, stacked=True, w=12)
b.series("Decisions per day, verifications and reports", [sql("A",
         "SELECT day::timestamp AS time, kind || ' ' || outcome AS metric, sum(n) AS value FROM analytics.food_review_decisions_daily, "
         "LATERAL (VALUES ('accepted', accepted), ('rejected', rejected), ('duplicate', duplicate)) AS v(outcome, n) "
         "WHERE $__timeFilter(day::timestamp) AND n > 0 GROUP BY 1, 2 ORDER BY 1")],
         "Every decision by the day it was made, on both queues.", bars=True, stacked=True, w=12)
b.sql_table("Decided per reviewer, 30 days",
            "SELECT reviewer, "
            "sum(accepted) FILTER (WHERE kind = 'verification') AS verifications_accepted, "
            "sum(rejected) FILTER (WHERE kind = 'verification') AS verifications_rejected, "
            "sum(accepted) FILTER (WHERE kind = 'report') AS reports_accepted, "
            "sum(rejected) FILTER (WHERE kind = 'report') AS reports_rejected, "
            "sum(duplicate) FILTER (WHERE kind = 'report') AS reports_duplicate, "
            "sum(accepted + rejected + duplicate) AS total "
            "FROM analytics.food_review_decisions_daily WHERE day > current_date - 30 "
            "GROUP BY reviewer ORDER BY total DESC",
            "Who is cleaning up the database: each reviewer's decisions, as the console's actor names them. The "
            "admin console's Review area has the same table.", w=24, h=6, no_value="Nothing decided in 30 days")

b.row("Contributors")
b.sql_table("Contributors to look at",
            "SELECT user_id, standing, submitted_30d, round(approval_rate_30d::numeric, 2) AS approval_30d, "
            "round(ocr_disagreement_rate::numeric, 2) AS ocr_disagreement, flagged_24h, flagged_7d, impossible_total, "
            "photo_reuse_hits, submitted_1h, submitted_24h, median_daily_30d, reported_30d, reports_accepted_30d, "
            "rolled_back, device_marked, account_age_days "
            f"FROM support.contributors WHERE {SUSPECT} "
            "ORDER BY photo_reuse_hits DESC, impossible_total DESC, flagged_7d DESC, submitted_24h DESC LIMIT 50",
            "Why each is here is in the columns. ocr_disagreement: of their last 50 submissions with a label reading, "
            "the share where what they sent differs from what the model read on kcal, protein, carbs or fat by more "
            "than label rounding. Open the id to moderate.",
            w=24, h=10, no_value="Nobody to look at", overrides=user_links("user_id"))
b.sql_table("Every contributor",
            "SELECT user_id, standing, submitted_total, submitted_30d, approved_30d, rejected_30d, pending, "
            "round(approval_rate_30d::numeric, 2) AS approval_30d, round(ocr_disagreement_rate::numeric, 2) AS ocr_disagreement, "
            "reported_30d, reports_accepted_30d, last_submitted_at "
            "FROM support.contributors ORDER BY submitted_30d DESC, submitted_total DESC LIMIT 200",
            "Volume and approval for everyone who has sent a food or a report.", w=24, h=10,
            no_value="No contributions yet", overrides=user_links("user_id"))
b.sql_table("Velocity outliers",
            "SELECT user_id, submitted_1h, submitted_24h, median_daily_30d, account_age_days, standing "
            "FROM support.contributors WHERE submitted_1h >= 10 "
            "OR (submitted_24h >= 5 AND submitted_24h > 3 * coalesce(median_daily_30d, 0)) "
            "ORDER BY submitted_24h DESC LIMIT 50",
            "Ten or more in the last hour, or a day three times their own median day. Scripts and Pro farming look like this.",
            no_value="Nobody unusually fast", overrides=user_links("user_id"))
b.sql_table("New accounts, many foods",
            "SELECT user_id, account_age_days, submitted_total, approval_rate_30d, standing FROM support.contributors "
            "WHERE account_age_days < 3 AND submitted_total >= 10 ORDER BY submitted_total DESC",
            "Under three days old with ten or more submissions.", no_value="None", overrides=user_links("user_id"))
b.bars("Disagreement with the label reading",
       "SELECT CASE WHEN ocr_disagreement_rate < 0.1 THEN 'under 10%' WHEN ocr_disagreement_rate < 0.25 THEN '10 to 25%' "
       "WHEN ocr_disagreement_rate < 0.5 THEN '25 to 50%' ELSE '50% or more' END AS disagreement, count(*) AS contributors "
       "FROM support.contributors WHERE ocr_disagreement_rate IS NOT NULL AND submitted_30d > 0 GROUP BY 1 ORDER BY 1",
       "Contributors by how often what they send differs from what the model read off their own photo. Honest "
       "corrections are rare; numbers typed from elsewhere disagree a lot.", w=12)

b.row("Signals")
b.series("Flags per day", [sql("A", "SELECT day::timestamp AS time, flag AS metric, contributions AS value "
                                    "FROM analytics.contribution_flags_daily WHERE $__timeFilter(day::timestamp) ORDER BY 1")],
         "atwater_mismatch: energy against 4/4/9 from the macros, or kJ read as kcal. impossible_value: an amount no "
         "food can hold. kcal_zero_with_macros: 0 kcal beside real macros. ocr_disagreement: sent differs from what was "
         "read. photo_reused: the same photo as an earlier submission.", bars=True, stacked=True, w=12)
b.sql_table("Duplicate photos, 7 days",
            "SELECT submission_id, other_submission_id, user_id, other_user_id, photo, cross_account, created_at "
            "FROM support.photo_reuse WHERE created_at > now() - interval '7 days' "
            "ORDER BY cross_account DESC, created_at DESC LIMIT 100",
            "Exact matches by SHA-256 of the photo, kept after the photos are deleted. cross_account first.",
            no_value="No reused photos", overrides=user_links("user_id", "other_user_id") + [
                {"matcher": {"id": "byName", "options": c}, "properties": [
                    {"id": "links", "value": [{"title": "Review in the admin console", "url": ADMIN_REVIEW, "targetBlank": True}]}]}
                for c in ("submission_id", "other_submission_id")])
b.sql_table("Top reported foods, 90 days",
            "SELECT food_id, reports, open_reports, reporters, last_reported_at FROM analytics.top_reported_foods",
            "Foods by how many people reported them. One food with many reporters is probably wrong; many reports from "
            "one reporter is something else.", no_value="No reports")
b.series("Device and account checks on contributions", [sql("A",
         "SELECT day::timestamp AS time, signal || ' ' || outcome AS metric, sum(checks) AS value "
         "FROM analytics.contribution_checks_daily WHERE $__timeFilter(day::timestamp) GROUP BY 1, 2 ORDER BY 1")],
         "Each contribution's device check (DeviceCheck, Play Integrity) by outcome, and refusals by account or install "
         "key. invalid: a token that didn't verify, which is counted and never blocks. flagged: Google wouldn't vouch "
         "for the device. Empty until DeviceCheck or Play Integrity is configured, apart from refusals.",
         bars=True, stacked=True, w=12)

b.row("Moderation")
b.series("Bans, suspensions and restrictions per day", [sql("A",
         "SELECT day::timestamp AS time, kind || ' ' || CASE WHEN lifted > 0 AND started = 0 THEN 'lifted' ELSE 'started' END AS metric, "
         "sum(started + lifted) AS value FROM analytics.moderation_daily WHERE $__timeFilter(day::timestamp) GROUP BY 1, 2 ORDER BY 1")],
         "Started by the day of the action, lifted by the day it was lifted.", bars=True, stacked=True, w=12)
b.sql_table("In force",
            "SELECT user_id, kind, scope, reason, created_by, created_at, until, mark_devices, appeal_state "
            "FROM support.moderation_actions WHERE active ORDER BY created_at DESC LIMIT 100",
            "Every ban, suspension and restriction not lifted or ended. The note is in the console.",
            no_value="Nothing in force", overrides=user_links("user_id"))
b.write()

# ── Builders alerts ────────────────────────────────────────────────────────
# Grafana-managed rules on the prod Postgres datasource, in Grafana's file-provisioning format.
# Each query returns one number; the rule fires when it crosses the threshold.


def rule(uid, title, query, op, threshold, summary, minutes=60, pending="0m"):
    return {
        "uid": uid, "title": title, "condition": "C",
        "data": [
            {"refId": "A", "relativeTimeRange": {"from": minutes * 60, "to": 0}, "datasourceUid": "opson-prod-db",
             "model": {"refId": "A", "rawSql": query, "rawQuery": True, "editorMode": "code", "format": "table"}},
            {"refId": "B", "relativeTimeRange": {"from": 0, "to": 0}, "datasourceUid": "__expr__",
             "model": {"refId": "B", "type": "reduce", "expression": "A", "reducer": "last", "settings": {"mode": "dropNN"}}},
            {"refId": "C", "relativeTimeRange": {"from": 0, "to": 0}, "datasourceUid": "__expr__",
             "model": {"refId": "C", "type": "threshold", "expression": "B",
                       "conditions": [{"evaluator": {"type": op, "params": [threshold]}}]}},
        ],
        "noDataState": "OK", "execErrState": "Error", "for": pending,
        "annotations": {"summary": summary, "__dashboardUid__": "opson-builders"},
        "labels": {"app": "opson", "board": "builders"}, "isPaused": False,
    }


ALERTS = {
    "apiVersion": 1,
    "groups": [{
        "orgId": 1, "name": "opson-builders", "folder": "Opson", "interval": "5m",
        "rules": [
            rule("opson-builders-burst", "One contributor, many submissions in an hour",
                 "SELECT count(*) AS value FROM support.contributors WHERE submitted_1h > 15",
                 "gt", 0, "Someone sent more than 15 foods in the last hour. Scripted, or farming for Pro: open Contributors to look at."),
            rule("opson-builders-flags", "One contributor, many flags in a day",
                 "SELECT count(*) AS value FROM support.contributors WHERE flagged_24h >= 5 AND standing = 'ok'",
                 "gt", 0, "A contributor in good standing had five or more flagged submissions in 24 hours."),
            rule("opson-builders-approval", "Approval rate collapsed",
                 "SELECT CASE WHEN coalesce(sum(contributions) FILTER (WHERE state IN ('approved', 'rejected')), 0) < 20 THEN 1 "
                 "ELSE sum(contributions) FILTER (WHERE state = 'approved')::float "
                 "/ sum(contributions) FILTER (WHERE state IN ('approved', 'rejected')) END AS value "
                 "FROM analytics.contributions_daily WHERE kind = 'submission' AND day > current_date - 7",
                 "lt", 0.5, "Under half of the last 7 days' submissions (20 or more decided) were approved.",
                 pending="1h"),
            rule("opson-builders-photo-reuse", "Photo reused across accounts",
                 "SELECT count(*) AS value FROM support.photo_reuse WHERE cross_account AND created_at > now() - interval '1 hour'",
                 "gt", 0, "Two accounts sent the same photo in the last hour. Almost always sock puppets."),
            rule("opson-builders-impossible", "Impossible values spiking",
                 "SELECT coalesce(sum(contributions), 0) AS value FROM analytics.contribution_flags_daily "
                 "WHERE flag IN ('impossible_value', 'kcal_zero_with_macros') AND day = current_date",
                 "gt", 10, "More than 10 submissions today hold an impossible amount or 0 kcal beside macros."),
            rule("opson-builders-queue-age", "Review queue ageing",
                 "SELECT coalesce(max(oldest_hours), 0) AS value FROM analytics.contribution_queue",
                 "gt", 72, "Something has waited for a reviewer more than 72 hours.", pending="1h"),
            rule("opson-builders-integrity", "Device checks failing",
                 "SELECT coalesce(sum(checks), 0) AS value FROM analytics.contribution_checks_daily "
                 "WHERE day = current_date AND outcome IN ('invalid', 'error')",
                 "gt", 20, "More than 20 device checks today didn't verify or couldn't reach Apple or Google: a key, "
                            "a config or a forged client."),
        ],
    }],
}
(HERE / "alerts").mkdir(exist_ok=True)
(HERE / "alerts" / "builders.json").write_text(json.dumps(ALERTS, indent=2) + "\n")

# ── Retention ──────────────────────────────────────────────────────────────
b = Board("opson-retention", "Opson · Retention",
          "Do people keep logging? Cohorts by the week of their first entry.")
b.sql_table("Weekly cohorts",
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
