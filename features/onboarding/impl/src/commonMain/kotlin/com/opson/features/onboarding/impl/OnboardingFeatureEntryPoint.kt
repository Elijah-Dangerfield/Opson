package com.opson.features.onboarding.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.opson.features.today.TodayRoute
import com.opson.features.onboarding.ForgotPasswordRoute
import com.opson.features.onboarding.OnboardingRoute
import com.opson.features.onboarding.SignInRoute
import com.opson.features.onboarding.SignUpRoute
import com.opson.features.onboarding.VerifyEmailRoute
import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.FindSupportRoute
import com.opson.features.settings.SupportRoute
import com.opson.libraries.flowroutines.ObserveEvents
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.routeDeepLink
import com.opson.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class OnboardingFeatureEntryPoint(
    private val onboardingViewModelFactory: () -> OnboardingViewModel,
    private val signInViewModelFactory: () -> SignInViewModel,
    private val signUpViewModelFactory: () -> SignUpViewModel,
    private val verifyEmailViewModelFactory: (email: String?, guestLink: Boolean) -> VerifyEmailViewModel,
    private val forgotPasswordViewModelFactory: () -> ForgotPasswordViewModel,
    private val findSupportEnabled: FindSupportEnabled,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<OnboardingRoute> {
            val viewModel: OnboardingViewModel = viewModel { onboardingViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    OnboardingEvent.NavigateToToday -> router.enterTab(TodayRoute())
                    OnboardingEvent.NavigateToSignIn -> router.navigate(SignInRoute())
                    OnboardingEvent.NavigateToSignUp -> router.navigate(SignUpRoute())
                    // Find support once it's released; Support has the helplines until then.
                    OnboardingEvent.NavigateToSupport ->
                        router.navigate(if (findSupportEnabled()) FindSupportRoute() else SupportRoute())
                }
            }

            OnboardingScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }

        screen<SignInRoute> {
            val viewModel: SignInViewModel = viewModel { signInViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    SignInEvent.NavigateToToday -> router.enterTab(TodayRoute())
                    is SignInEvent.NavigateToVerifyEmail -> router.navigate(
                        VerifyEmailRoute(event.email),
                    )
                }
            }

            SignInScreen(
                state = state,
                onAction = viewModel::takeAction,
                onBack = { router.goBack() },
                onCreateAccount = { router.navigate(SignUpRoute()) },
                onForgotPassword = { router.navigate(ForgotPasswordRoute()) },
            )
        }

        screen<ForgotPasswordRoute> {
            val viewModel: ForgotPasswordViewModel = viewModel { forgotPasswordViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            ForgotPasswordScreen(
                state = state,
                onAction = viewModel::takeAction,
                onBack = { router.goBack() },
                onBackToSignIn = { router.goBack() },
            )
        }

        screen<SignUpRoute> {
            val viewModel: SignUpViewModel = viewModel { signUpViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    is SignUpEvent.NavigateToVerifyEmail -> router.navigate(
                        VerifyEmailRoute(event.email, guestLink = event.guestLink),
                    )
                }
            }

            SignUpScreen(
                state = state,
                onAction = viewModel::takeAction,
                onBack = { router.goBack() },
                onSignIn = {
                    router.popBackTo(SignUpRoute(), inclusive = true)
                    router.navigate(SignInRoute(), NavigationOptions(launchSingleTop = true))
                },
            )
        }

        screen<VerifyEmailRoute>(
            deepLinks = listOf(
                routeDeepLink<VerifyEmailRoute>(basePath = "opson://auth/confirmed"),
            ),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<VerifyEmailRoute>()
            val viewModel: VerifyEmailViewModel =
                viewModel { verifyEmailViewModelFactory(route.email, route.guestLink) }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    VerifyEmailEvent.NavigateToToday -> router.enterTab(TodayRoute())
                    VerifyEmailEvent.NavigateBackToSignIn -> router.navigate(
                        SignInRoute(),
                        NavigationOptions(launchSingleTop = true, clearBackStack = true),
                    )
                    // Brand-new signup: drop into onboarding at the first question
                    // (the VM detects the authenticated-but-not-onboarded session).
                    // Clear the auth back stack so Back can't return to verify/signup.
                    VerifyEmailEvent.NavigateToOnboarding -> router.navigate(
                        OnboardingRoute(),
                        NavigationOptions(launchSingleTop = true, clearBackStack = true),
                    )
                    // Anon guest linked email: their account and progress are
                    // intact, so clear the auth/claim stack and land on Today.
                    VerifyEmailEvent.NavigateToAccountSaved -> router.enterTab(TodayRoute())
                }
            }

            VerifyEmailScreen(
                state = state,
                onAction = viewModel::takeAction,
                onBack = { router.goBack() },
            )
        }
    }
}
