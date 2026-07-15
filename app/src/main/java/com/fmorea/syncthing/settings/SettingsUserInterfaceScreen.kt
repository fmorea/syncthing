package com.fmorea.syncthing.settings

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation3.runtime.EntryProviderScope
import com.fmorea.syncthing.R
import com.fmorea.syncthing.service.Constants
import com.fmorea.syncthing.util.ConfigRouter
import com.fmorea.syncthing.util.LocalActivityScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.rememberPreferenceState


private const val TAG = "SettingsUserInterfaceScreen"

fun EntryProviderScope<SettingsRoute>.settingsUserInterfaceEntry() {
    entry<SettingsRoute.UserInterface> {
        SettingsUserInterfaceScreen()
    }
}


@Composable
fun SettingsUserInterfaceScreen() {
    val context = LocalContext.current
    val scope = LocalActivityScope.current
    val stService = LocalSyncthingService.current

    val themeNames = stringArrayResource(R.array.app_theme_names)
    val themeValues = stringArrayResource(R.array.app_theme_values)

    var theme by rememberPreferenceState(Constants.PREF_APP_THEME, "system")

    SettingsScaffold(
        title = stringResource(R.string.category_user_interface),
    ) {
        item {
            val themeIndex = themeValues.indexOf(theme).coerceAtLeast(0)
            ListPreference(
                title = { Text(stringResource(R.string.preference_app_theme_title)) },
                summary = { Text(themeNames[themeIndex]) },
                value = theme,
                onValueChange = { newTheme ->
                    if (newTheme != theme) {
                        theme = newTheme
                        val mode = when (newTheme) {
                            "light" -> AppCompatDelegate.MODE_NIGHT_NO
                            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(mode)
                        
                        // Force a small delay to allow SharedPreferences to persist before service reload if needed
                        scope.launch(Dispatchers.IO) {
                            withContext(NonCancellable) {
                                try {
                                    val restApi = stService?.api
                                    if (restApi != null && restApi.isConfigLoaded) {
                                        val config = ConfigRouter(context)
                                        val gui = config.getGui(restApi)
                                        gui.theme = if (newTheme == "system") "default" else newTheme
                                        config.updateGui(restApi, gui)
                                        restApi.sendConfig()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error updating theme with config router", e)
                                }
                            }
                        }
                    }
                },
                values = themeValues.toList(),
                valueToText = { value -> 
                    val index = themeValues.indexOf(value).coerceAtLeast(0)
                    AnnotatedString(themeNames[index]) 
                }
            )
        }
    }
}
