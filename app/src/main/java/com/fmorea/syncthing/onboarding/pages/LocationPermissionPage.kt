package com.fmorea.syncthing.onboarding.pages

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
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
fun LocationPermissionPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    requestTvFocus: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onGrantLocationPermission: () -> Unit,
) {
    val actionFocusRequester = remember { FocusRequester() }
    val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        stringResource(R.string.location_permission_desc) + "\n\n" +
                stringResource(R.string.location_permission_desc_api_29)
    } else {
        stringResource(R.string.location_permission_desc)
    }

    OnboardingScaffold(
        icon = OnboardingIcon.Vector(Icons.Outlined.LocationOn),
        title = stringResource(R.string.location_permission_title),
        description = description,
        pageIndex = pageIndex,
        pageCount = uiState.pages.size,
        nextLabel = stringResource(R.string.cont),
        requestTvFocus = requestTvFocus,
        onBack = onBack,
        onNext = onContinue,
        actionFocusRequester = actionFocusRequester,
        action = {
            PermissionButton(
                granted = uiState.hasLocationPermission,
                onClick = onGrantLocationPermission,
                focusRequester = actionFocusRequester,
            )
        },
    )
}
