package com.fmorea.syncthing.settings

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import com.fmorea.syncthing.R
import com.fmorea.syncthing.service.Constants
import com.fmorea.syncthing.util.LocalActivity
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsExperimentalEntry() {
    entry<SettingsRoute.Experimental> {
        SettingsExperimentalScreen()
    }
}


@Composable
fun SettingsExperimentalScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val useTor = rememberPreferenceState(Constants.PREF_USE_TOR, false)
    val socksProxy = rememberPreferenceState(Constants.PREF_SOCKS_PROXY_ADDRESS, "")
    val httpProxy = rememberPreferenceState(Constants.PREF_HTTP_PROXY_ADDRESS, "")

    var showRestartAlert by remember { mutableStateOf(false) }

    if (showRestartAlert) {
        androidx.compose.material3.AlertDialog(
            title = { Text(stringResource(R.string.dialog_settings_restart_app_title)) },
            text = { Text(stringResource(R.string.dialog_settings_restart_app_question)) },
            onDismissRequest = { showRestartAlert = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showRestartAlert = false
                        restartApp(activity)
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showRestartAlert = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.category_experimental),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.use_tor_title)) },
                summary = { Text(stringResource(R.string.use_tor_summary)) },
                value = useTor.value,
                onValueChange = {
                    useTor.value = it
                    showRestartAlert = true
                }
            )
        }
        item {
            val socksProxySummary = if (socksProxy.value.isBlank())
                "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.socks_proxy_address_example)}"
            else
                "${stringResource(R.string.use_proxy)} ${socksProxy.value}"
            TextFieldPreference(
                title = { Text(stringResource(R.string.socks_proxy_address_title)) },
                summary = { Text(socksProxySummary) },
                value = socksProxy.value,
                onValueChange = {
                    socksProxy.value = it
                    showRestartAlert = true
                },
                textToValue = {
                    validateProxy(
                        newValue = it,
                        regex = Regex("^socks5://.*:\\d{1,5}$"),
                        errorResId = R.string.toast_invalid_socks_proxy_address,
                        context = context,
                    )
                },
                enabled = !useTor.value,
            )
        }
        item {
            val httpProxySummary = if (httpProxy.value.isBlank())
                "${stringResource(R.string.do_not_use_proxy)} ${stringResource(R.string.generic_example)}: ${stringResource(R.string.http_proxy_address_example)}"
            else
                "${stringResource(R.string.use_proxy)} ${httpProxy.value}"
            TextFieldPreference(
                title = { Text(stringResource(R.string.http_proxy_address_title)) },
                summary = { Text(httpProxySummary) },
                value = httpProxy.value,
                onValueChange = {
                    httpProxy.value = it
                    showRestartAlert = true
                },
                textToValue = {
                    validateProxy(
                        newValue = it,
                        regex = Regex("^https?://.*:\\d{1,5}$"),
                        errorResId = R.string.toast_invalid_http_proxy_address,
                        context = context,
                    )
                },
                enabled = !useTor.value,
            )
        }
    }
}

private fun restartApp(activity: android.app.Activity?) {
    if (activity == null || activity.isFinishing) {
        return
    }

    val stopServiceIntent = android.content.Intent(activity, com.fmorea.syncthing.service.SyncthingService::class.java)
    activity.stopService(stopServiceIntent)

    val context = activity.applicationContext
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val mainIntent = android.content.Intent.makeRestartActivityTask(intent?.component)
    activity.finishAndRemoveTask()
    context.startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}

private fun validateProxy(
    newValue: String,
    regex: Regex,
    @StringRes errorResId: Int,
    context: Context
): String? {
    return when {
        newValue.isEmpty() -> newValue
        newValue.matches(regex) -> newValue
        else -> {
            Toast.makeText(context, errorResId, Toast.LENGTH_LONG).show()
            null
        }
    }
}
