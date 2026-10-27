//
//  IOSWidgetBridge.swift
//  iosApp
//
//  Hands the home screen widget its snapshot. Kotlin builds the snapshot
//  (the visibility policy already applied) and calls write(json:) on the main
//  thread; this stores it where the OpsonWidgets extension reads it and asks
//  WidgetKit to redraw. WidgetKit has no Objective-C API, which is why this
//  half lives in Swift.
//
//  Called whatever release.widgets says (that flag is only the More row): the
//  extension is in the widget gallery in every build. Both targets need the
//  App Group entitlement; without it UserDefaults(suiteName:) still answers,
//  it just isn't shared, and the widget says "Open Opson" for good.
//
import ComposeApp
import Foundation
import WidgetKit

final class IOSWidgetBridge: WidgetBridge {

    /// The same two strings as `WidgetStore` in apps/ios/OpsonWidgets. Change both together.
    static let appGroup = "group.com.opson.Opson"
    static let snapshotKey = "widgets.snapshot"

    func write(json: String?) {
        let defaults = UserDefaults(suiteName: Self.appGroup)
        if let json {
            defaults?.set(json, forKey: Self.snapshotKey)
        } else {
            defaults?.removeObject(forKey: Self.snapshotKey)
        }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
