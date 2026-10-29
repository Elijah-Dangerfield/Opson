//
//  MacrosWidget.swift
//  OpsonWidgets
//
//  The macros the person picks, against their targets. Only the ones their
//  mode shows in full are in the snapshot; a picked macro the mode keeps in
//  the app is simply not drawn, and with none left the widget says so.
//

import AppIntents
import SwiftUI
import WidgetKit

struct MacrosConfiguration: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Macros"
    static var description = IntentDescription("Pick the macros to show. Your mode decides which ones appear.")

    @Parameter(title: "Protein", default: true) var protein: Bool
    @Parameter(title: "Carbs", default: true) var carbs: Bool
    @Parameter(title: "Fat", default: true) var fat: Bool
    @Parameter(title: "Fiber", default: false) var fiber: Bool

    /// The keys picked, in the app's order. Nothing picked reads as all of them.
    var keys: [String] {
        let picked = [("protein", protein), ("carbs", carbs), ("fat", fat), ("fiber", fiber)].filter(\.1).map(\.0)
        return picked.isEmpty ? ["protein", "carbs", "fat", "fiber"] : picked
    }
}

struct MacrosEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let keys: [String]

    var day: WidgetDay? { snapshot?.day(for: date) }

    /// The picked macros the mode shows.
    var macros: [WidgetMacro] { (day?.macros ?? []).filter { keys.contains($0.key) } }
}

struct MacrosProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MacrosEntry {
        MacrosEntry(date: .now, snapshot: .sample(numbers: true), keys: ["protein", "carbs", "fat"])
    }

    func snapshot(for configuration: MacrosConfiguration, in context: Context) async -> MacrosEntry {
        MacrosEntry(date: .now, snapshot: WidgetStore.load(), keys: configuration.keys)
    }

    func timeline(for configuration: MacrosConfiguration, in context: Context) async -> Timeline<MacrosEntry> {
        WidgetTimeline.timeline { MacrosEntry(date: $0, snapshot: $1, keys: configuration.keys) }
    }
}

struct MacrosWidget: Widget {
    static let kind = "OpsonMacros"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: MacrosConfiguration.self, provider: MacrosProvider()) { entry in
            MacrosWidgetView(entry: entry)
        }
        .configurationDisplayName("Macros")
        .description("The macros you pick, the way your mode shows them.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}

struct MacrosWidgetView: View {
    let entry: MacrosEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var theme: WidgetTheme { WidgetTheme(entry.snapshot, colorScheme) }
    private var labels: WidgetLabels { entry.snapshot?.text ?? .fallback }
    private var link: URL { URL(widgetLink: entry.snapshot?.todayLink, fallback: WidgetURLs.today) }

    /// Why there is nothing to draw, when there isn't.
    private var emptyText: String? {
        if entry.snapshot == nil { return WidgetText.openApp }
        // An untracked day has no rows at all, and its note says so.
        if entry.day?.mealRows?.isEmpty == true, let note = entry.day?.note { return note }
        return entry.macros.isEmpty ? labels.macrosHidden : nil
    }

    var body: some View {
        Group {
            switch family {
            case .accessoryRectangular: rectangular.accessoryBackground()
            case .systemMedium: medium.opsonBackground(theme)
            default: small.opsonBackground(theme)
            }
        }
        .widgetURL(link)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetTitle(text: labels.macros, theme: theme)
            if let emptyText {
                Spacer(minLength: 0)
                WidgetNote(text: emptyText, theme: theme)
            } else {
                Spacer(minLength: 0)
                ForEach(entry.macros.prefix(4)) { macro in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(macro.label)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(theme.ink)
                            Spacer(minLength: 4)
                            Text(macro.line ?? "\(macro.amount) \(macro.unit)")
                                .font(.system(size: 12, weight: .bold))
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                        }
                        .lineLimit(1)
                        bar(macro)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(macro.description ?? "\(macro.label), \(macro.text)")
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func bar(_ macro: WidgetMacro) -> some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(theme.track)
                Capsule().fill(theme.nutrient(macro.key)).frame(width: geometry.size.width * min(1, max(0, macro.fraction)))
            }
        }
        .frame(height: 6)
    }

    private var medium: some View {
        VStack(alignment: .leading, spacing: 10) {
            WidgetTitle(text: labels.macros, theme: theme)
            if let emptyText {
                Spacer(minLength: 0)
                WidgetNote(text: emptyText, theme: theme)
                Spacer(minLength: 0)
            } else {
                HStack(spacing: 8) {
                    ForEach(entry.macros) { macro in
                        VStack(spacing: 6) {
                            ZStack {
                                WidgetRing(fraction: macro.fraction, tint: theme.nutrient(macro.key), track: theme.track, lineWidth: 7)
                                VStack(spacing: 0) {
                                    Text(macro.amount)
                                        .font(.system(size: 16, weight: .bold))
                                        .monospacedDigit()
                                        .foregroundStyle(theme.ink)
                                        .minimumScaleFactor(0.6)
                                    Text(macro.unit)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(theme.muted)
                                }
                                .lineLimit(1)
                                .padding(.horizontal, 6)
                            }
                            .frame(width: 58, height: 58)
                            Text(macro.label)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(theme.ink)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(macro.description ?? "\(macro.label), \(macro.text)")
                    }
                }
                .frame(maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let emptyText {
                Text(labels.macros).font(.headline).widgetAccentable()
                Text(emptyText).lineLimit(2)
            } else {
                ForEach(entry.macros.prefix(3)) { macro in
                    HStack {
                        Text(macro.label).widgetAccentable()
                        Spacer(minLength: 4)
                        Text(macro.text).monospacedDigit().privacySensitive()
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview("Small", as: .systemSmall) {
    MacrosWidget()
} timeline: {
    MacrosEntry(date: .now, snapshot: .sample(numbers: true), keys: ["protein", "carbs", "fat"])
}

#Preview("Medium, all four", as: .systemMedium) {
    MacrosWidget()
} timeline: {
    MacrosEntry(date: .now, snapshot: .sample(numbers: true), keys: ["protein", "carbs", "fat", "fiber"])
}

#Preview("A mode that keeps macros in the app", as: .systemMedium) {
    MacrosWidget()
} timeline: {
    MacrosEntry(date: .now, snapshot: .sample(numbers: false), keys: ["protein", "carbs", "fat"])
}

#Preview("Lock Screen", as: .accessoryRectangular) {
    MacrosWidget()
} timeline: {
    MacrosEntry(date: .now, snapshot: .sample(numbers: true), keys: ["protein", "carbs", "fat"])
}
