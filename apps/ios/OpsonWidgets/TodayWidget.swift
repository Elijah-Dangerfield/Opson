//
//  TodayWidget.swift
//  OpsonWidgets
//
//  The home screen widget (design v3 widgets.png): Today and a +, then the
//  numbers card or the meal circles, whichever the app's snapshot holds.
//  The app reloads it whenever the snapshot changes; the timeline carries an
//  entry for tomorrow so it turns over at midnight with the app closed.
//

import SwiftUI
import WidgetKit

@main
struct OpsonWidgetsBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
    }
}

struct TodayWidget: Widget {
    static let kind = "OpsonToday"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("Today")
        .description("Today at a glance, the way your mode shows it. Tap + to log.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TodayEntry: TimelineEntry {
    let date: Date
    /// Nil before the app has written one, or while nobody is signed in.
    let snapshot: WidgetSnapshot?

    var day: WidgetDay? { snapshot?.day(for: date) }
}

/// Reads what the app wrote. The gallery preview reads it too, so it never
/// shows a number the person's mode hides.
struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: .now, snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(TodayEntry(date: .now, snapshot: WidgetStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let snapshot = WidgetStore.load()
        let now = Date.now
        // One entry per day the snapshot holds, each from the start of its day.
        var entries = (snapshot?.days ?? [])
            .compactMap { WidgetDates.start(of: $0.day) }
            .filter { $0 > now }
            .map { TodayEntry(date: $0, snapshot: snapshot) }
        entries.insert(TodayEntry(date: now, snapshot: snapshot), at: 0)
        // The app reloads on every change, so nothing to poll for.
        completion(Timeline(entries: entries, policy: .never))
    }
}
