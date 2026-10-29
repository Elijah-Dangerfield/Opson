//
//  main.swift
//  The widget snapshot decode check (scripts/check_widget_snapshot.sh).
//
//  Decodes the JSON the Kotlin side wrote (WidgetSnapshotFixtureTest) with
//  the extension's own WidgetSnapshot.swift, and checks what each widget
//  would draw. Exits non-zero on the first thing that doesn't hold.
//

import Foundation

var failures = 0

func expect(_ condition: Bool, _ message: String) {
    if !condition {
        failures += 1
        FileHandle.standardError.write(Data("FAIL: \(message)\n".utf8))
    }
}

func load(_ name: String) -> WidgetSnapshot? {
    let dir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
    let url = URL(fileURLWithPath: dir).appendingPathComponent(name)
    guard let data = try? Data(contentsOf: url) else {
        expect(false, "\(name) is missing at \(url.path)")
        return nil
    }
    do {
        let snapshot = try JSONDecoder().decode(WidgetSnapshot.self, from: data)
        expect(snapshot.version == WidgetSnapshot.supportedVersion, "\(name) is version \(snapshot.version)")
        expect(WidgetStore.decode(data) != nil, "\(name) is refused by WidgetStore")
        return snapshot
    } catch {
        expect(false, "\(name) doesn't decode: \(error)")
        return nil
    }
}

// WidgetFixtures' noon on its Sunday, in its zone (UTC), whatever this Mac's zone is.
var utc = Calendar(identifier: .gregorian)
utc.timeZone = TimeZone(identifier: "UTC")!
let fixtureDay = WidgetDates.start(of: "2026-10-25", calendar: utc)!.addingTimeInterval(12 * 3600)

if let full = load("snapshot-full.json") {
    let today = full.day(for: fixtureDay, calendar: utc)
    expect(today?.day == "2026-10-25", "full: today's entry")
    expect(today?.energy?.amount == "1,245", "full: the calories amount")
    expect(today?.energy?.ring != nil, "full: the ring")
    expect(today?.left?.label == "655 left", "full: what is left")
    expect(today?.progress?.label == "3 of 4 meals", "full: meals done")
    expect(today?.macros?.map(\.key) == ["protein", "carbs", "fat", "fiber"], "full: the macros")
    expect(today?.water?.text == "1,250 of 2,000 ml", "full: the water amount")
    expect(today?.mealRows?.first?.calories == "445 kcal", "full: a meal's energy")
    expect(full.todayLink == "opson://widget/today", "full: the Today link")
    expect(full.waterLink == "opson://widget/water/add", "full: the water link")
    expect(full.fasting?.phase(at: fixtureDay)?.kind == "window", "full: the fasting phase at noon")
    expect(full.colors.light.nutrients?["water"] != nil, "full: the water colour")
    // Words the app writes so the extension never composes them in English.
    expect(today?.energy?.line == "1,245 kcal", "full: the calories line")
    expect(today?.left?.line == "655 to range", "full: what is left, on one line")
    expect(today?.macros?.first?.line == "84 g", "full: a macro's line")
    expect(today?.macros?.first?.description == "Protein, 84 / 120 g", "full: a macro for a screen reader")
    expect(today?.water?.addLabel == "Add a glass, 250 ml", "full: the add button for a screen reader")
    let window = full.fasting?.phase(at: fixtureDay)
    expect(window?.before == "Until " && window?.after == "", "full: the words around the fasting time")
    let tomorrow = full.day(for: fixtureDay.addingTimeInterval(24 * 3600), calendar: utc)
    expect(tomorrow?.day == "2026-10-26" && tomorrow?.energy?.amount == "0", "full: tomorrow is an empty day")
}

if let blind = load("snapshot-no-numbers.json") {
    let today = blind.day(for: fixtureDay, calendar: utc)
    expect(today?.energy == nil, "no numbers: no calories")
    expect(today?.left == nil, "no numbers: nothing left")
    expect(today?.macros?.isEmpty == true, "no numbers: no macros")
    expect(today?.mealRows?.allSatisfy { $0.calories == nil } == true, "no numbers: no meal energy")
    expect(today?.meals.count == 4, "no numbers: the meal circles")
    expect(today?.progress?.done == 3, "no numbers: meals done")
    expect(blind.fasting == nil, "no numbers: no fasting timer")
}

// JSON the first release wrote, without any of the newer fields, still reads.
let legacy = """
{"version":1,"days":[{"day":"2026-10-25","title":"Today","energy":{"amount":"0","unit":"kcal","label":"0 kcal","fraction":0.0,"rangeStart":0.68,"rangeEnd":0.83},"meals":[],"note":null}],
"logLabel":"Log food","logLink":"opson://widget/log","colors":{"light":{"background":"#EEF1F6","ink":"#18202E","muted":"#5A6478","track":"#E0E5ED","bar":"#18202E","accent":"#A3500A","onAccent":"#FFFFFF","meals":{}},"dark":{"background":"#161B27","ink":"#E6EAF2","muted":"#98A2B4","track":"#323B4D","bar":"#E6EAF2","accent":"#FA9E5C","onAccent":"#1F120A","meals":{}}}}
"""
let old = WidgetStore.decode(Data(legacy.utf8))
expect(old?.days.first?.energy?.amount == "0", "the first release's JSON still decodes")
expect(old?.text == WidgetLabels.fallback, "and falls back to the extension's own labels")
expect(old?.days.first?.energy?.line == nil, "and has no lines, which the widgets compose themselves")

if failures > 0 {
    FileHandle.standardError.write(Data("\(failures) widget snapshot check(s) failed\n".utf8))
    exit(1)
}
print("Widget snapshot fixtures decode as the extension reads them.")
