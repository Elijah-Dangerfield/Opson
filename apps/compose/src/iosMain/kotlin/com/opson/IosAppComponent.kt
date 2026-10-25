package com.opson

import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.SecureSessionStorage
import com.opson.libraries.opson.PermissionManager
import com.opson.libraries.review.ReviewLauncher
import com.opson.libraries.ui.nativeviews.NativeViewFactory
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
    val nativeViewFactory: NativeViewFactory
) : AppComponent {

    @Provides
    fun providePermissionManager(): PermissionManager = permissionManager

    @Provides
    fun provideReviewLauncher(): ReviewLauncher = reviewLauncher

    @Provides
    fun provideAppleSignInCoordinator(): AppleSignInCoordinator = appleSignInCoordinator

    @Provides
    fun provideSecureSessionStorage(): SecureSessionStorage = secureSessionStorage
}


@MergeComponent.CreateComponent
expect fun create(
    permissionManager: PermissionManager,
    reviewLauncher: ReviewLauncher,
    appleSignInCoordinator: AppleSignInCoordinator,
    secureSessionStorage: SecureSessionStorage,
    nativeViewFactory: NativeViewFactory
): IosAppComponent
