//
//  CaloriesWidget.swift
//  OpsonWidgets
//
//  Calories as a ring, on the home screen and the Lock Screen. The person
//  picks the figure: what they've eaten, or what's left to their range. Both
//  come from the snapshot only when their mode shows that number; otherwise
//  the ring is the meals done today, which every mode shows, and never a 0
//  standing in for a hidden number.
//

import AppIntents
import SwiftUI
import WidgetKit

enum CaloriesFigure: String, AppEnum {
    case eaten
    case left

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Number"
    static var caseDisplayRepresentations: [CaloriesFigure: DisplayRepresentation] = [
        .eaten: "Eaten so far",
        .left: "Left to your range",
    ]
}

struct CaloriesConfiguration: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Calories"
    static var description = IntentDescription("Choose the number the ring shows. Your mode still decides whether it shows one.")

    @Parameter(title: "Show", default: .eaten)
    var figure: CaloriesFigure
}

struct CaloriesEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let figure: CaloriesFigure

    var day: WidgetDay? { snapshot?.day(for: date) }
}

struct CaloriesProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CaloriesEntry {
        CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .eaten)
    }

    func snapshot(for configuration: CaloriesConfiguration, in context: Context) async -> CaloriesEntry {
        CaloriesEntry(date: .now, snapshot: WidgetStore.load(), figure: configuration.figure)
    }

    func timeline(for configuration: CaloriesConfiguration, in context: Context) async -> Timeline<CaloriesEntry> {
        WidgetTimeline.timeline { CaloriesEntry(date: $0, snapshot: $1, figure: configuration.figure) }
    }
}

/// What the ring holds, decided from the snapshot alone.
enum CaloriesFace: Equatable {
    case figure(amount: String, unit: String, label: String, line: String, ring: Double, range: Double?)
    case meals(WidgetProgress)
    case note(String)

    static func of(_ day: WidgetDay?, figure: CaloriesFigure) -> CaloriesFace? {
        guard let day else { return nil }
        if let energy = day.energy {
            let ring = energy.ring ?? energy.fraction
            if figure == .left, let left = day.left {
                return .figure(
                    amount: left.amount, unit: left.unit, label: left.label,
                    line: left.line ?? "\(left.amount) \(left.unit)", ring: ring, range: energy.ringRangeStart
                )
            }
            return .figure(
                amount: energy.amount, unit: energy.unit, label: energy.label,
                line: energy.line ?? "\(energy.amount) \(energy.unit)", ring: ring, range: energy.ringRangeStart
            )
        }
        if let progress = day.progress { return .meals(progress) }
        if let note = day.note { return .note(note) }
        return nil
    }

    var inline: String {
        switch self {
        case let .figure(_, _, label, _, _, _): return label
        case let .meals(progress): return progress.label
        case let .note(text): return text
        }
    }
}

struct CaloriesWidget: Widget {
    static let kind = "OpsonCalories"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: Self.kind, intent: CaloriesConfiguration.self, provider: CaloriesProvider()) { entry in
            CaloriesWidgetView(entry: entry)
        }
        .configurationDisplayName("Calories")
        .description("A ring for today, the way your mode shows it.")
        .supportedFamilies([.systemSmall, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

struct CaloriesWidgetView: View {
    let entry: CaloriesEntry

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var theme: WidgetTheme { WidgetTheme(entry.snapshot, colorScheme) }
    private var face: CaloriesFace? { CaloriesFace.of(entry.day, figure: entry.figure) }
    private var labels: WidgetLabels { entry.snapshot?.text ?? .fallback }
    private var link: URL { URL(widgetLink: entry.snapshot?.todayLink, fallback: WidgetURLs.today) }

    var body: some View {
        Group {
            switch family {
            case .accessoryCircular: circular.accessoryBackground()
            case .accessoryRectangular: rectangular.accessoryBackground()
            case .accessoryInline: inline.accessoryBackground()
            default: small.opsonBackground(theme)
            }
        }
        .widgetURL(link)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetTitle(text: labels.calories, theme: theme)
            switch face {
            case let .figure(amount, unit, label, _, ring, range):
                ringed(fraction: ring, range: range) {
                    VStack(spacing: 0) {
                        Text(amount)
                            .font(.system(size: 22, weight: .bold))
                            .monospacedDigit()
                            .foregroundStyle(theme.ink)
                            .minimumScaleFactor(0.6)
                        Text(unit)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(theme.muted)
                            .minimumScaleFactor(0.7)
                    }
                    .lineLimit(1)
                    .padding(.horizontal, 10)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(label)
                }
            case let .meals(progress):
                ringed(fraction: progress.fraction, range: nil) {
                    Image(systemName: "fork.knife")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(theme.ink)
                }
                Text(progress.label)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            case let .note(text):
                Spacer(minLength: 0)
                WidgetNote(text: text, theme: theme)
            case nil:
                Spacer(minLength: 0)
                WidgetNote(text: WidgetText.openApp, theme: theme)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func ringed<Center: View>(fraction: Double, range: Double?, @ViewBuilder center: () -> Center) -> some View {
        ZStack {
            WidgetRing(fraction: fraction, range: range, tint: theme.accent, track: theme.track, lineWidth: 9)
            center()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @ViewBuilder
    private var circular: some View {
        switch face {
        case let .figure(amount, _, label, _, ring, _):
            Gauge(value: min(1, max(0, ring))) {
                Image(systemName: "flame")
            } currentValueLabel: {
                Text(amount).monospacedDigit().privacySensitive()
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .accessibilityLabel(label)
        case let .meals(progress):
            Gauge(value: progress.fraction) {
                Image(systemName: "fork.knife")
            } currentValueLabel: {
                Image(systemName: "fork.knife")
            }
            .gaugeStyle(.accessoryCircularCapacity)
            .accessibilityLabel(progress.label)
        default:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "fork.knife")
            }
        }
    }

    @ViewBuilder
    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(labels.calories).font(.headline).widgetAccentable().lineLimit(1)
            switch face {
            case let .figure(_, _, label, line, ring, _):
                Text(line)
                    .font(.system(.body, design: .rounded).weight(.semibold))
                    .monospacedDigit()
                    .lineLimit(1)
                    .privacySensitive()
                    .accessibilityLabel(label)
                Gauge(value: min(1, max(0, ring))) { EmptyView() }
                    .gaugeStyle(.accessoryLinearCapacity)
            case let .meals(progress):
                Text(progress.label).lineLimit(1)
                Gauge(value: progress.fraction) { EmptyView() }
                    .gaugeStyle(.accessoryLinearCapacity)
            case let .note(text):
                Text(text).lineLimit(2)
            case nil:
                Text(WidgetText.openApp).lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inline: some View {
        Text(face?.inline ?? "Opson").privacySensitive()
    }
}

#Preview("Eaten", as: .systemSmall) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .eaten)
}

#Preview("Left", as: .systemSmall) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .left)
}

#Preview("No numbers", as: .systemSmall) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: false), figure: .eaten)
}

#Preview("Before the app has run", as: .systemSmall) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: nil, figure: .eaten)
}

#Preview("Lock Screen, circular", as: .accessoryCircular) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .eaten)
    CaloriesEntry(date: .now, snapshot: .sample(numbers: false), figure: .eaten)
}

#Preview("Lock Screen, rectangular", as: .accessoryRectangular) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .left)
    CaloriesEntry(date: .now, snapshot: .sample(numbers: false), figure: .eaten)
}

#Preview("Lock Screen, inline", as: .accessoryInline) {
    CaloriesWidget()
} timeline: {
    CaloriesEntry(date: .now, snapshot: .sample(numbers: true), figure: .eaten)
}
