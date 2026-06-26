package com.fmorea.syncthing;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.fmorea.syncthing.service.NotificationHandler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SyncthingModule {

    private final SyncthingApp mApp;

    public SyncthingModule(SyncthingApp app) {
        mApp = app;
    }

    @Provides
    @Singleton
    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mApp);
    }

    @Provides
    @Singleton
    public NotificationHandler getNotificationHandler(SharedPreferences preferences) {
        return new NotificationHandler(mApp, preferences);
    }
}
