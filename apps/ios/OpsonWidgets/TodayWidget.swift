//
//  TodayWidget.swift
//  OpsonWidgets
//
//  The widget bundle, and the home screen Today widget (design v3
//  widgets.png): Today and a +, then the numbers card or the meal circles,
//  whichever the app's snapshot holds.
//

import SwiftUI
import WidgetKit

@main
struct OpsonWidgetsBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        CaloriesWidget()
        MacrosWidget()
        WaterWidget()
        MealsWidget()
        FastingWidget()
    }
}

/// Whether the gallery lists the fasting widget (a widget bundle can't hold an
/// `if`, so FastingWidget offers no sizes until this is true): only once this phone has had
/// the timer on in a mode that offers it. Someone who told setup that numbers
/// have been hard, or who never turned fasting on, never sees it offered. Once
/// seen it stays listed, so a placed fasting widget isn't pulled out from under
/// someone who turns the timer off; it says the timer is off instead.
enum FastingGate {
    static let key = "widgets.fastingSeen"

    static var offered: Bool {
        let defaults = UserDefaults(suiteName: WidgetStore.appGroup)
        if defaults?.bool(forKey: key) == true { return true }
        guard WidgetStore.load()?.fasting != nil else { return false }
        defaults?.set(true, forKey: key)
        return true
    }
}

struct TodayWidget: Widget {
    /// Placed widgets are found by kind: never rename it.
    static let kind = "OpsonToday"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SnapshotProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("Today")
        .description("Today at a glance, the way your mode shows it. Tap + to log.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
