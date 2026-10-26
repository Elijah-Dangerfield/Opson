//
//  WidgetSnapshot.swift
//  OpsonWidgets
//
//  The snapshot the app writes for the widget, decoded. It mirrors
//  libraries/widgets/.../WidgetSnapshot.kt field for field; change both
//  together and bump the version on both sides.
//
//  Everything in it is already decided by the app's shared Kotlin, the
//  visibility policy included: a number the person's mode hides is not in
//  here at all. This extension draws what it finds and nothing else.
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

    /// The entry for `date`: its own, else the last one (an empty day, right
    /// for any later day while the app hasn't run), else the first.
    func day(for date: Date, calendar: Calendar = .current) -> WidgetDay? {
        let iso = WidgetDates.iso(date, calendar: calendar)
        if let match = days.first(where: { $0.day == iso }) { return match }
        if let last = days.last, iso > last.day { return last }
        return days.first
    }
}

struct WidgetDay: Codable, Equatable {
    let day: String
    let title: String
    let energy: WidgetEnergy?
    let meals: [WidgetMeal]
    let note: String?
}

struct WidgetEnergy: Codable, Equatable {
    let amount: String
    let unit: String
    let label: String
    let fraction: Double
    let rangeStart: Double
    let rangeEnd: Double
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
}

struct WidgetTone: Codable, Equatable {
    let content: String
    let container: String
}

/// Where the app leaves the snapshot. The same two strings as
/// `IOSWidgetBridge` in the app target; change both together.
enum WidgetStore {
    static let appGroup = "group.com.opson.Opson"
    static let snapshotKey = "widgets.snapshot"

    static func load() -> WidgetSnapshot? {
        guard
            let json = UserDefaults(suiteName: appGroup)?.string(forKey: snapshotKey),
            let data = json.data(using: .utf8),
            let snapshot = try? JSONDecoder().decode(WidgetSnapshot.self, from: data),
            snapshot.version == WidgetSnapshot.supportedVersion
        else { return nil }
        return snapshot
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
