package com.opson

import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.SecureSessionStorage
import com.opson.libraries.navigation.FileSharePresenter
import com.opson.libraries.opson.PermissionManager
import com.opson.libraries.review.ReviewLauncher
import com.opson.libraries.ui.nativeviews.NativeViewFactory
import com.opson.libraries.widgets.WidgetBridge
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent(
    private val permissionManager: PermissionManager,
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
    // `iOSApp.swift` for the home screen widgets. Only called with release.widgets on. Android
    // publishes through Glance, bound via anvil.
    private val widgetBridge: WidgetBridge,
) : AppComponent {

    @Provides
    fun providePermissionManager(): PermissionManager = permissionManager

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
}


@MergeComponent.CreateComponent
expect fun create(
    permissionManager: PermissionManager,
    reviewLauncher: ReviewLauncher,
    appleSignInCoordinator: AppleSignInCoordinator,
    secureSessionStorage: SecureSessionStorage,
    fileSharePresenter: FileSharePresenter,
    nativeViewFactory: NativeViewFactory,
    widgetBridge: WidgetBridge,
): IosAppComponent
