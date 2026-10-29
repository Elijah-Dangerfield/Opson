package com.opson

import com.opson.libraries.foods.IosDeviceSignals
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.SecureSessionStorage
import com.opson.libraries.navigation.FileSharePresenter
import com.opson.libraries.review.ReviewLauncher
import com.opson.libraries.scanner.TextRecognitionBridge
import com.opson.libraries.ui.nativeviews.NativeViewFactory
import com.opson.libraries.widgets.WidgetBridge
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent(
    private val reviewLauncher: ReviewLauncher,
    // The Swift `IOSAppleSignInCoordinator` (ASAuthorizationController flow),
    // passed in from `iOSApp.swift`. Android binds its own no-op via anvil.
    private val appleSignInCoordinator: AppleSignInCoordinator,
    // The Swift `IOSSecureSessionStorage` (Keychain-backed Supabase session
    // store), passed in from `iOSApp.swift`. Android binds
    // EncryptedSessionStorage via anvil.
    private val secureSessionStorage: SecureSessionStorage,
    // The Swift `IOSFileSharePresenter` (UIActivityViewController), passed in
    // from `iOSApp.swift` for Router.shareFile. Android shares through a
    // FileProvider bound via anvil.
    private val fileSharePresenter: FileSharePresenter,
    val nativeViewFactory: NativeViewFactory,
    // The Swift `IOSWidgetBridge` (App Group UserDefaults + WidgetCenter), passed in from
    // `iOSApp.swift` for the home screen widgets, whatever release.widgets says. Android
    // publishes through Glance, bound via anvil.
    private val widgetBridge: WidgetBridge,
    // The Swift `IOSTextRecognitionBridge` (Vision text request), passed in from
    // `iOSApp.swift` for reading a nutrition label on the phone. Android reads with
    // ML Kit, bound via anvil.
    private val textRecognitionBridge: TextRecognitionBridge,
    // The Swift `IOSDeviceSignals` (a Keychain install key and DeviceCheck tokens), passed in from
    // `iOSApp.swift`, sent only with contribution requests. Android uses Play Integrity via anvil.
    private val deviceSignals: IosDeviceSignals,
) : AppComponent {

    @Provides
    fun provideReviewLauncher(): ReviewLauncher = reviewLauncher

    @Provides
    fun provideAppleSignInCoordinator(): AppleSignInCoordinator = appleSignInCoordinator

    @Provides
    fun provideSecureSessionStorage(): SecureSessionStorage = secureSessionStorage

    @Provides
    fun provideFileSharePresenter(): FileSharePresenter = fileSharePresenter

    @Provides
    fun provideWidgetBridge(): WidgetBridge = widgetBridge

    @Provides
    fun provideTextRecognitionBridge(): TextRecognitionBridge = textRecognitionBridge

    @Provides
    fun provideDeviceSignals(): IosDeviceSignals = deviceSignals
}


@MergeComponent.CreateComponent
expect fun create(
    reviewLauncher: ReviewLauncher,
    appleSignInCoordinator: AppleSignInCoordinator,
    secureSessionStorage: SecureSessionStorage,
    fileSharePresenter: FileSharePresenter,
    nativeViewFactory: NativeViewFactory,
    widgetBridge: WidgetBridge,
    textRecognitionBridge: TextRecognitionBridge,
    deviceSignals: IosDeviceSignals,
): IosAppComponent
