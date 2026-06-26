package com.fmorea.syncthing;

import com.fmorea.syncthing.activities.DeviceActivity;
import com.fmorea.syncthing.activities.FolderActivity;
import com.fmorea.syncthing.activities.MainActivity;
import com.fmorea.syncthing.activities.PhotoShootActivity;
import com.fmorea.syncthing.activities.RecentChangesActivity;
import com.fmorea.syncthing.activities.ShareActivity;
import com.fmorea.syncthing.activities.SyncConditionsActivity;
import com.fmorea.syncthing.fragments.DeviceListFragment;
import com.fmorea.syncthing.fragments.FolderListFragment;
import com.fmorea.syncthing.fragments.StatusFragment;
import com.fmorea.syncthing.onboarding.OnboardingActivity;
import com.fmorea.syncthing.receiver.AppConfigReceiver;
import com.fmorea.syncthing.service.RunConditionMonitor;
import com.fmorea.syncthing.service.EventProcessor;
import com.fmorea.syncthing.service.RestApi;
import com.fmorea.syncthing.service.SyncthingRunnable;
import com.fmorea.syncthing.service.SyncthingService;
import com.fmorea.syncthing.settings.SettingsActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {SyncthingModule.class})
public interface DaggerComponent {
    void inject(AppConfigReceiver appConfigReceiver);
    void inject(DeviceActivity activity);
    void inject(DeviceListFragment fragment);
    void inject(EventProcessor eventProcessor);
    void inject(FolderActivity activity);
    void inject(FolderListFragment fragment);
    void inject(MainActivity activity);
    void inject(OnboardingActivity onboardingActivity);
    void inject(PhotoShootActivity photoShootActivity);
    void inject(RestApi restApi);
    void inject(RecentChangesActivity recentChangesActivity);
    void inject(RunConditionMonitor runConditionMonitor);
    void inject(SettingsActivity settingsActivity);
    void inject(ShareActivity activity);
    void inject(StatusFragment fragment);
    void inject(SyncConditionsActivity activity);
    void inject(SyncthingApp app);
    void inject(SyncthingRunnable syncthingRunnable);
    void inject(SyncthingService service);
}
