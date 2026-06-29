package com.fmorea.syncthing.util;

import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.fmorea.syncthing.service.Constants;
import android.util.Log;

import java.util.Map;

public class PreferenceMigration {
    private static final String TAG = "PreferenceMigration";

    public static void migrate(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        Map<String, ?> allEntries = prefs.getAll();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.equals(Constants.PREF_APP_THEME) && value instanceof Integer) {
                int oldVal = (Integer) value;
                String newVal = switch (oldVal) {
                    case AppCompatDelegate.MODE_NIGHT_NO -> "light";
                    case AppCompatDelegate.MODE_NIGHT_YES -> "dark";
                    default -> "system";
                };
                editor.putString(key, newVal);
                Log.d(TAG, "Migrated " + key + " from " + oldVal + " to " + newVal);
            } else if (key.equals(Constants.PREF_POWER_SOURCE) && value instanceof Integer) {
                // Example migration for power source if it was int
                editor.putString(key, Constants.PowerSource.CHARGER_BATTERY);
            } else if ((key.equals(Constants.PREF_SYNC_DURATION_MINUTES) || 
                        key.equals(Constants.PREF_SLEEP_INTERVAL_MINUTES)) && value instanceof Integer) {
                editor.putString(key, String.valueOf(value));
                Log.d(TAG, "Migrated " + key + " from int to string");
            }
        }
        editor.apply();
    }
}
