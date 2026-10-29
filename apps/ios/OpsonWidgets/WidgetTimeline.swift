//
//  WidgetTimeline.swift
//  OpsonWidgets
//
//  The timeline every Opson widget runs on. The app asks WidgetKit to reload
//  whenever the snapshot changes; between those, a timeline carries an entry
//  for each midnight the snapshot covers (so the day turns over with the app
//  closed) and each fasting phase, and asks again within the hour, so a
//  reload the system dropped mends itself instead of leaving yesterday up.
//

import Foundation
import WidgetKit

/// One moment of a widget: the snapshot as stored, read for `date`.
struct SnapshotEntry: TimelineEntry {
    let date: Date
    /// Nil before the app has written one, or while nobody is signed in.
    let snapshot: WidgetSnapshot?

    var day: WidgetDay? { snapshot?.day(for: date) }
    var labels: WidgetLabels { snapshot?.text ?? .english }
}

enum WidgetTimeline {
    /// How long a timeline stands before WidgetKit asks again whatever the app does.
    /// Hourly is well inside the daily reload budget.
    static let refresh: TimeInterval = 60 * 60

    /// Fasting phases further out than this wait for the next timeline.
    static let horizon: TimeInterval = 36 * 60 * 60

    /// Now, each later midnight the snapshot covers, and each later fasting phase.
    static func dates(for snapshot: WidgetSnapshot?, now: Date = .now) -> [Date] {
        guard let snapshot else { return [now] }
        let midnights = snapshot.days.compactMap { WidgetDates.start(of: $0.day) }
        let phases = snapshot.fasting?.phases.map(\.from) ?? []
        let later = (midnights + phases).filter { $0 > now && $0 < now.addingTimeInterval(horizon) }
        return [now] + Array(Set(later)).sorted()
    }

    static func timeline<Entry: TimelineEntry>(now: Date = .now, entry: (Date, WidgetSnapshot?) -> Entry) -> Timeline<Entry> {
        let snapshot = WidgetStore.load()
        let entries = dates(for: snapshot, now: now).map { entry($0, snapshot) }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(refresh)))
    }
}

/// For the widgets with nothing to choose: Today, water, meals, fasting.
struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        // WidgetKit draws a placeholder redacted, so this sample is only ever shapes.
        SnapshotEntry(date: .now, snapshot: .sample(numbers: true))
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        // The gallery reads what the app wrote too, never the sample: someone in a mode with
        // no numbers should not meet a calorie figure while picking a widget.
        completion(SnapshotEntry(date: .now, snapshot: WidgetStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        completion(WidgetTimeline.timeline { SnapshotEntry(date: $0, snapshot: $1) })
    }
}
