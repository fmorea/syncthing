package com.fmorea.syncthing.onboarding.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.fmorea.syncthing.R
import com.fmorea.syncthing.onboarding.OnboardingIcon
import com.fmorea.syncthing.onboarding.OnboardingScaffold
import com.fmorea.syncthing.onboarding.OnboardingUiState
import com.fmorea.syncthing.onboarding.PermissionButton

@Composable
fun CameraPermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantCameraPermission: () -> Unit,
) {
    val actionFocusRequester = remember { FocusRequester() }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.CameraAlt),
        title = stringResource(R.string.camera_permission_title),
        description = stringResource(R.string.camera_permission_desc),
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        nextEnabled = uiState.hasCameraPermission,
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = onContinue,
        actionFocusRequester = actionFocusRequester,
        action = {
            PermissionButton(
                granted = uiState.hasCameraPermission,
                onClick = onGrantCameraPermission,
                focusRequester = actionFocusRequester,
            )
        },
    )
}
