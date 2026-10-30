//
//  ReviewLauncher.swift
//  iosApp
//
//  Wraps StoreKit's review prompt. The OS owns the throttling decision and never says whether
//  the sheet showed or whether the person rated. Two things can still be told: with no window
//  scene there is nothing to show it in (the coordinator then keeps the ask for a better
//  moment), and a sheet that does show arrives as a new window in the scene. That second one
//  is a best guess, reported as "likely".
//
import ComposeApp
import StoreKit
import UIKit

class IOSReviewLauncher: ReviewLauncher {

    /// Long enough for the sheet's window to be attached, short enough to stay in the moment.
    private static let settleNanoseconds: UInt64 = 1_500_000_000

    func __requestReview() async throws -> ReviewLaunchOutcome {
        let before: Int? = await MainActor.run { () -> Int? in
            guard let scene = Self.foregroundScene() else { return nil }
            let windows = scene.windows.count
            AppStore.requestReview(in: scene)
            return windows
        }
        guard let before else { return .noforegroundui }

        try? await Task.sleep(nanoseconds: Self.settleNanoseconds)
        let after = await MainActor.run { Self.foregroundScene()?.windows.count ?? before }
        return after > before ? .likelyshown : .likelynotshown
    }

    @MainActor
    private static func foregroundScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
    }
}
