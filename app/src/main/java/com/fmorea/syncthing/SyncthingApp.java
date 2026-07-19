package com.fmorea.syncthing;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.fmorea.syncthing.service.Constants;
import com.fmorea.syncthing.util.PreferenceMigration;
import androidx.emoji2.bundled.BundledEmojiCompatConfig;
import androidx.emoji2.text.EmojiCompat;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    private DaggerComponent mComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceMigration.migrate(sharedPreferences);
        
        // Apply theme early to avoid recreation
        String themeValue = sharedPreferences.getString(Constants.PREF_APP_THEME, "system");
        int prefAppTheme = switch (themeValue) {
            case "light" -> AppCompatDelegate.MODE_NIGHT_NO;
            case "dark" -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);

        mComponent = DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build();
        mComponent.inject(this);

        // Set VM policy to avoid crash when sending folder URI to file manager.
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(vmPolicy);

        // Initialize EmojiCompat with bundled font for compatibility on older Android versions
        EmojiCompat.init(new BundledEmojiCompatConfig(this));

        /*
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
            StrictMode.setThreadPolicy(threadPolicy);
        }
        */
    }

    public DaggerComponent component() {
        return mComponent;
    }
}
