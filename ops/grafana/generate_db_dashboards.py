#!/usr/bin/env python3
"""Writes the generated dashboards in this folder: the Postgres-backed product
boards, Pulse, and the Users and User detail boards.

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
b.write()

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
