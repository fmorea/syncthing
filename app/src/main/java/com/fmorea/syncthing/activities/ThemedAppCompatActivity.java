package com.fmorea.syncthing.activities;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.fmorea.syncthing.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 */
public abstract class ThemedAppCompatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Opt-in to edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Load theme.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String themeValue = sharedPreferences.getString(Constants.PREF_APP_THEME, "system");
        int prefAppTheme;
        switch (themeValue) {
            case "light":
                prefAppTheme = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case "dark":
                prefAppTheme = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case "system":
            default:
                prefAppTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);
        
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            boolean isDarkTheme = (prefAppTheme == AppCompatDelegate.MODE_NIGHT_YES);
            if (prefAppTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                isDarkTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            }
            controller.setAppearanceLightStatusBars(!isDarkTheme);
            controller.setAppearanceLightNavigationBars(!isDarkTheme);
        }
    }
}
