package com.fmorea.syncthing.onboarding

import androidx.compose.runtime.Composable
import com.fmorea.syncthing.onboarding.pages.IntroductionPage
import com.fmorea.syncthing.onboarding.pages.BatteryOptimizationPage
import com.fmorea.syncthing.onboarding.pages.CameraPermissionPage
import com.fmorea.syncthing.onboarding.pages.KeyGenerationPage
import com.fmorea.syncthing.onboarding.pages.LocationPermissionPage
import com.fmorea.syncthing.onboarding.pages.NotificationPermissionPage
import com.fmorea.syncthing.onboarding.pages.WelcomePage

/**
 * Specifies the type and content of each onboarding page.
 */
enum class OnboardingPage {
    WELCOME,
    INTRODUCTION,
    BATTERY_OPTIMIZATION,
    LOCATION_PERMISSION,
    NOTIFICATION_PERMISSION,
    CAMERA_PERMISSION,
    KEY_GENERATION,
}

/**
 * Renders the correct onboarding page for a given [OnboardingPage].
 */
@Composable
fun OnboardingPage(
    page: OnboardingPage,
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onFinishOnboarding: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onGrantNotificationPermission: () -> Unit,
    onGrantCameraPermission: () -> Unit,
) {
    when (page) {
        OnboardingPage.WELCOME -> WelcomePage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
        )
        OnboardingPage.INTRODUCTION -> IntroductionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
        )
        OnboardingPage.BATTERY_OPTIMIZATION -> BatteryOptimizationPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
        )
        OnboardingPage.LOCATION_PERMISSION -> LocationPermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
            onGrantLocationPermission = onGrantLocationPermission,
        )
        OnboardingPage.NOTIFICATION_PERMISSION -> NotificationPermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
            onGrantNotificationPermission = onGrantNotificationPermission,
        )
        OnboardingPage.CAMERA_PERMISSION -> CameraPermissionPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onContinue = onContinue,
            onGrantCameraPermission = onGrantCameraPermission,
        )
        OnboardingPage.KEY_GENERATION -> KeyGenerationPage(
            uiState = uiState,
            pageIndex = pageIndex,
            requestTvFocus = requestTvFocus,
            onBack = onBack,
            onFinishOnboarding = onFinishOnboarding,
        )
    }
}
