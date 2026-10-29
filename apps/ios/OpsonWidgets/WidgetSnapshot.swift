//
//  WidgetSnapshot.swift
//  OpsonWidgets
//
//  The snapshot the app writes for the widgets, decoded. It mirrors
//  libraries/widgets/.../WidgetSnapshot.kt field for field; change both
//  together.
//
//  Everything in it is already decided by the app's shared Kotlin, the
//  visibility policy included: a number the person's mode hides is not in
//  here at all. These widgets draw what they find and nothing else.
//
//  Fields added after the first release are optionals here. The extension
//  that ships with an update reads whatever the previous app wrote until the
//  new app runs, and that JSON doesn't have them. `version` only changes for
//  a rename or a retype, on both sides at once.
//  apps/ios/TestFixtures/widgets/ holds JSON the Kotlin side wrote;
//  scripts/check_widget_snapshot.sh decodes it with this file.
//
//  Foundation and SwiftUI only, so the check script can compile it for macOS.
//
//  Text the app can write is in here already in the person's language, from
//  libraries/resources. The extension's own words, for before there is a
//  snapshot (and the gallery), are in this target's Localizable.xcstrings.
//

import Foundation
import SwiftUI

struct WidgetSnapshot: Codable, Equatable {
    /// `WidgetSnapshotBuilder.VERSION` on the Kotlin side.
    static let supportedVersion = 1

    let version: Int
    let days: [WidgetDay]
    let logLabel: String
    let logLink: String
    let colors: WidgetColors
    let todayLink: String?
    let waterLink: String?
    let fasting: WidgetFasting?
    let labels: WidgetLabels?

    /// The entry for `date`: its own, else the last one (an empty day, right
    /// for any later day while the app hasn't run), else the first.
    func day(for date: Date, calendar: Calendar = .current) -> WidgetDay? {
        let iso = WidgetDates.iso(date, calendar: calendar)
        if let match = days.first(where: { $0.day == iso }) { return match }
        if let last = days.last, iso > last.day { return last }
        return days.first
    }

    var text: WidgetLabels { labels ?? .fallback }
}

struct WidgetDay: Codable, Equatable {
    let day: String
    let title: String
    let energy: WidgetEnergy?
    let meals: [WidgetMeal]
    let note: String?
    let left: WidgetFigure?
    let progress: WidgetProgress?
    let macros: [WidgetMacro]?
    let water: WidgetWater?
    let mealRows: [WidgetMealRow]?
}

struct WidgetEnergy: Codable, Equatable {
    let amount: String
    let unit: String
    let label: String
    let fraction: Double
    let rangeStart: Double
    let rangeEnd: Double
    let ring: Double?
    let ringRangeStart: Double?
    /// `amount` and `unit` on one line, in the language's order. Nil from an app before it.
    var line: String? = nil
}

struct WidgetFigure: Codable, Equatable {
    let amount: String
    let unit: String
    let label: String
    /// `amount` and `unit` on one line, "655 to range". Nil from an app before it.
    var line: String? = nil
}

struct WidgetProgress: Codable, Equatable {
    let done: Int
    let total: Int
    let label: String

    var fraction: Double { total > 0 ? Double(done) / Double(total) : 0 }
}

struct WidgetMacro: Codable, Equatable, Identifiable {
    let key: String
    let label: String
    let amount: String
    let unit: String
    let text: String
    let fraction: Double
    /// `amount` and `unit` on one line, "84 g". Nil from an app before it.
    var line: String? = nil
    /// What a screen reader says, "Protein, 84 / 120 g". Nil from an app before it.
    var description: String? = nil

    var id: String { key }
}

struct WidgetWater: Codable, Equatable {
    /// Nil when the mode softens water: the glasses alone, no amount.
    let text: String?
    let filled: Int
    let total: Int
    let label: String
    let glass: String
    /// What a screen reader says for the add button, "Add a glass, 250 ml". Nil from an app before it.
    var addLabel: String? = nil

    var fraction: Double { total > 0 ? min(1, Double(filled) / Double(total)) : 0 }
}

struct WidgetMealRow: Codable, Equatable, Identifiable {
    let slot: String
    let label: String
    let done: Bool
    let foods: String
    let calories: String?
    let link: String

    var id: String { slot }
}

struct WidgetFasting: Codable, Equatable {
    let plan: String
    let phases: [WidgetFastingPhase]

    /// The last phase that has started by `date`.
    func phase(at date: Date) -> WidgetFastingPhase? {
        let ms = Int64(date.timeIntervalSince1970 * 1000)
        return phases.last(where: { $0.fromMs <= ms }) ?? phases.first
    }
}

struct WidgetFastingPhase: Codable, Equatable {
    let fromMs: Int64
    /// `fasting`, `window` or `done`.
    let kind: String
    let title: String
    let caption: String
    let untilMs: Int64
    let sinceMs: Int64?
    /// The line's words before and after the time, so a language can put it anywhere. Nil
    /// from an app before them, which wrote only `caption`, the words before it.
    var captionBefore: String? = nil
    var captionAfter: String? = nil

    var before: String { captionBefore ?? caption + " " }
    var after: String { captionAfter ?? "" }

    var from: Date { Date(timeIntervalSince1970: Double(fromMs) / 1000) }
    var until: Date { Date(timeIntervalSince1970: Double(untilMs) / 1000) }
    var since: Date? { sinceMs.map { Date(timeIntervalSince1970: Double($0) / 1000) } }
    var isFasting: Bool { kind == "fasting" }
}

struct WidgetLabels: Codable, Equatable {
    let calories: String
    let macros: String
    let water: String
    let meals: String
    let fasting: String
    let addGlass: String
    let macrosHidden: String
    let waterHidden: String
    let fastingOff: String
    let nothingYet: String

    /// For JSON from an app that wrote no labels yet, and for previews: the same words as
    /// strings_widgets.xml's, from this target's Localizable.xcstrings.
    static var fallback: WidgetLabels {
        WidgetLabels(
            calories: String(localized: "Calories"),
            macros: String(localized: "Macros"),
            water: String(localized: "Water"),
            meals: String(localized: "Meals"),
            fasting: String(localized: "Fasting"),
            addGlass: String(localized: "Add a glass"),
            macrosHidden: String(localized: "Your mode keeps macros in the app."),
            waterHidden: String(localized: "Your mode keeps water in the app."),
            fastingOff: String(localized: "The fasting timer is off."),
            nothingYet: String(localized: "Nothing yet")
        )
    }
}

struct WidgetMeal: Codable, Equatable, Identifiable {
    let slot: String
    let label: String
    let done: Bool
    let description: String

    var id: String { slot }
}

struct WidgetColors: Codable, Equatable {
    let light: WidgetSwatch
    let dark: WidgetSwatch
}

struct WidgetSwatch: Codable, Equatable {
    let background: String
    let ink: String
    let muted: String
    let track: String
    let bar: String
    let accent: String
    let onAccent: String
    let meals: [String: WidgetTone]
    let nutrients: [String: WidgetTone]?
}

struct WidgetTone: Codable, Equatable {
    let content: String
    let container: String
}

/// Where the app leaves the snapshot: a JSON file in the App Group
/// container, written whole (atomically) by `IOSWidgetBridge` in the app
/// target. The same names are there; change both together.
///
/// A file rather than the group's UserDefaults, which is where the first
/// build kept it: a widget extension's preferences are cached in its own
/// process, and WidgetKit can ask for a timeline before that cache hears of
/// the app's write. With a timeline that never asked again, the widget kept
/// the old day ("0") until the next write lost the same race. The key is
/// still read when there's no file, for a phone whose app hasn't run since
/// the update.
enum WidgetStore {
    static let appGroup = "group.com.opson.Opson"
    static let fileName = "widget-snapshot.json"
    static let legacyKey = "widgets.snapshot"

    static func load() -> WidgetSnapshot? {
        if let url = fileURL, let data = try? Data(contentsOf: url) {
            return decode(data)
        }
        guard let json = UserDefaults(suiteName: appGroup)?.string(forKey: legacyKey) else { return nil }
        return decode(Data(json.utf8))
    }

    static func decode(_ data: Data) -> WidgetSnapshot? {
        guard
            let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data),
            snapshot.version == WidgetSnapshot.supportedVersion
        else { return nil }
        return snapshot
    }

    static var fileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(fileName, isDirectory: false)
    }
}

enum WidgetDates {
    /// `2026-10-25` in the device's own calendar and zone, as Kotlin's LocalDate prints it.
    static func iso(_ date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The start of the day an ISO date names, for the timeline.
    static func start(of iso: String, calendar: Calendar = .current) -> Date? {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }
}

extension Color {
    /// `#RRGGBB`, as the snapshot carries every colour.
    init(widgetHex hex: String) {
        let value = UInt64(hex.dropFirst(hex.hasPrefix("#") ? 1 : 0), radix: 16) ?? 0
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}
