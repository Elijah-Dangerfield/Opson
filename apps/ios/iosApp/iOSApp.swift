import SwiftUI
import UIKit
import ComposeApp

@main
struct iOSApp: App {
    
    let permissionManager = IOSPermissionManager()
    let reviewLauncher = IOSReviewLauncher()
    let appleSignInCoordinator = IOSAppleSignInCoordinator()
    let secureSessionStorage = IOSSecureSessionStorage()
    let fileSharePresenter = IOSFileSharePresenter()
    private let nativeViewFactory = IOSNativeViewFactory.shared
    private let iOSAppComponent: IosAppComponent

    init() {
        self.iOSAppComponent = create(
            permissionManager: permissionManager,
            reviewLauncher: reviewLauncher,
            appleSignInCoordinator: appleSignInCoordinator,
            secureSessionStorage: secureSessionStorage,
            fileSharePresenter: fileSharePresenter,
            nativeViewFactory: nativeViewFactory
        )
        iOSAppComponent.telemetry.initialize()
        // Construct every @AutoInit singleton up front — resolving the set is
        // what forces construction (AppEventDispatcher's lifecycle attach etc.).
        _ = iOSAppComponent.autoInits
    }
    
    var body: some Scene {
        WindowGroup {
            RootComposeView(
                appComponent: iOSAppComponent,
                nativeViewFactory: nativeViewFactory
            )
            .onOpenURL { url in
                // Forward URLs from custom-scheme links and Universal Links
                // into the Kotlin DeepLinkBridge — App.kt collects from it
                // and calls navController.handleDeepLink.
                iOSAppComponent.deepLinkBridge.emit(url: url.absoluteString)
            }
        }
    }
}

struct RootComposeView: View {
    @Environment(\.scenePhase) private var scenePhase
    let appComponent: IosAppComponent
    let nativeViewFactory: OpsonNativeViewFactory

    var body: some View {
        ComposeView(
            appComponent: appComponent,
            nativeViewFactory: nativeViewFactory
        )
        .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let appComponent: IosAppComponent
    let nativeViewFactory: OpsonNativeViewFactory

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            appComponent: appComponent
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

