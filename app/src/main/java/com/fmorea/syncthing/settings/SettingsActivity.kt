package com.fmorea.syncthing.settings

import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.fmorea.syncthing.SyncthingApp
import com.fmorea.syncthing.activities.SyncthingActivity
import com.fmorea.syncthing.service.NotificationHandler
import com.fmorea.syncthing.service.SyncthingService
import com.fmorea.syncthing.theme.ApplicationTheme
import com.fmorea.syncthing.util.LocalActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals

val LocalSyncthingService = staticCompositionLocalOf<SyncthingService?> { null }
val LocalServiceUpdateTick = staticCompositionLocalOf { 0 }

class SettingsActivity : SyncthingActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    lateinit var prefFlow: MutableStateFlow<Preferences>
    @Inject
    lateinit var notificationHandler: NotificationHandler

    private var syncthingServiceState by mutableStateOf<SyncthingService?>(service)

    // The ticker will help update the ui whenever the state of syncthing service updates
    private var serviceUpdateTick by mutableIntStateOf(0)
    val stateChangeListener = SyncthingService.OnServiceStateChangeListener {
        serviceUpdateTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SyncthingApp).component().inject(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val activityScope = this.lifecycleScope
        prefFlow = createPreferenceFlow(sharedPreferences, activityScope)

        val routeStr = intent.getStringExtra(EXTRA_START_DESTINATION)
        val startDestination: SettingsRoute = SettingsRoute.fromString(routeStr)

        setContent {
            val backStack = rememberSettingsNavBackStack(startDestination)
            val navigator = remember(backStack) {
                object : Navigator<SettingsRoute> {
                    override fun navigateTo(route: SettingsRoute) {
                        backStack.add(route)
                    }
                    override fun navigateBack() {
                        if (backStack.size == 1) {
                            finish()
                        } else {
                            backStack.removeLastOrNull()
                        }
                    }
                    override fun navigateUp() {
                        finish()
                    }
                }
            }

            val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            ApplicationTheme {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
                    LocalActivityScope provides activityScope,
                    LocalSettingsNavigator provides navigator,
                    LocalSyncthingService provides syncthingServiceState,
                    LocalServiceUpdateTick provides serviceUpdateTick,
                ) {
                    ProvidePreferenceLocals(flow = prefFlow) {
                        SettingsNavDisplay(
                            backStack = backStack,
                        )
                    }
                }
            }
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        super.onServiceConnected(name, service)
        syncthingServiceState = this.service
        syncthingServiceState?.registerOnServiceStateChangeListener(stateChangeListener)
        serviceUpdateTick++
    }

    override fun onServiceDisconnected(name: ComponentName) {
        syncthingServiceState?.unregisterOnServiceStateChangeListener(stateChangeListener)
        super.onServiceDisconnected(name)
        syncthingServiceState = null
        serviceUpdateTick++
    }

    override fun onStop() {
        syncthingServiceState?.let {
            notificationHandler.updatePersistentNotification(it)
        }
        super.onStop()
    }

    override fun onDestroy() {
        syncthingServiceState?.unregisterOnServiceStateChangeListener(stateChangeListener)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_DESTINATION = "com.fmorea.syncthing.settings.EXTRA_START_DESTINATION"
    }
}
