package com.fmorea.syncthing.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.fmorea.syncthing.R
import com.fmorea.syncthing.service.SyncthingService
import me.zhanghai.compose.preference.Preference

fun EntryProviderScope<SettingsRoute>.settingsRootEntry() {
    entry<SettingsRoute.Root> {
        SettingsRootScreen()
    }
}

@Composable
fun SettingsRootScreen() {
    val navigator = LocalSettingsNavigator.current
    val stService = LocalSyncthingService.current
    val stServiceTick = LocalServiceUpdateTick.current

    val isSyncthingOptionsEnabled by remember(stService, stServiceTick) {
        derivedStateOf { stService != null && stService.currentState == SyncthingService.State.ACTIVE }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
    ) {
        item {
            Preference(
                title = { Text(stringResource(R.string.run_conditions_title)) },
                summary = { Text(stringResource(R.string.run_conditions_summary)) },
                icon = { Icon(Icons.Default.PowerSettingsNew, null) },
                onClick = { navigator.navigateTo(SettingsRoute.RunConditions) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_user_interface)) },
                icon = { Icon(Icons.Default.DisplaySettings, null) },
                onClick = { navigator.navigateTo(SettingsRoute.UserInterface) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_behaviour)) },
                icon = { Icon(Icons.Default.Tune, null) },
                onClick = { navigator.navigateTo(SettingsRoute.Behavior) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_syncthing_options)) },
                summary = { Text(stringResource(R.string.category_syncthing_options_summary)) },
                icon = { Icon(Icons.Default.Settings, null) },
                onClick = { navigator.navigateTo(SettingsRoute.SyncthingOptions) },
                enabled = isSyncthingOptionsEnabled,
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_backup)) },
                icon = { Icon(Icons.Default.Backup, null) },
                onClick = { navigator.navigateTo(SettingsRoute.ImportExport) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_debug)) },
                icon = { Icon(Icons.Default.BugReport, null) },
                onClick = { navigator.navigateTo(SettingsRoute.Troubleshooting) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.category_experimental)) },
                icon = { Icon(Icons.Default.Extension, null) },
                onClick = { navigator.navigateTo(SettingsRoute.Experimental) },
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
