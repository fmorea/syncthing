package com.fmorea.syncthing.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.fmorea.syncthing.R
import me.zhanghai.compose.preference.Preference

fun EntryProviderScope<SettingsRoute>.settingsRootEntry() {
    entry<SettingsRoute.Root> {
        SettingsRootScreen()
    }
}

@Composable
fun SettingsRootScreen() {
    val navigator = LocalSettingsNavigator.current

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
    ) {
        item {
            Preference(
                title = { Text(stringResource(R.string.category_user_interface)) },
                icon = { Icon(Icons.Default.DisplaySettings, null) },
                onClick = { navigator.navigateTo(SettingsRoute.UserInterface) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_about)) },
                icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
                onClick = { navigator.navigateTo(SettingsRoute.About) },
            )
        }
    }
}
