package com.opson

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.opson.libraries.ui.nativeviews.LocalNativeViewFactory
import com.opson.libraries.ui.nativeviews.NativeViewFactory
import platform.UIKit.UIViewController

// TODO(review): the status bar follows the system's appearance, not a theme forced in Look and
// feel, so Dark on a light phone draws dark status text on the dark app (and Light the reverse).
// Android follows the pick (MainActivity.followThemeWithSystemBars); here the window needs
// overrideUserInterfaceStyle from Settings.theme, checked in the simulator.
fun MainViewController(
    appComponent: IosAppComponent,
): UIViewController {
    return ComposeUIViewController {
        CompositionLocalProvider(LocalNativeViewFactory provides appComponent.nativeViewFactory) {
            App(appComponent = appComponent)
        }
    }
}
