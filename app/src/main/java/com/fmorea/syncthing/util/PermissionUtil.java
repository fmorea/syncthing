package com.fmorea.syncthing.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fmorea.syncthing.R;

public class PermissionUtil {

    private static final String TAG = "PermissionUtil";

    public static boolean haveStoragePermission(@NonNull Context context) {
        // App now uses internal private storage (/data/data/com...) which doesn't require runtime permissions.
        return true;
    }

    public static void requestStoragePermission(@NonNull Activity activity, final int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);
            return;
        }

        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        try {
            ComponentName componentName = intent.resolveActivity(activity.getPackageManager());
            if (componentName != null) {
                // Launch "Allow all files access?" dialog.
                activity.startActivity(intent);
                return;
            } else {
                Log.w(TAG, "Request all files access not supported");
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Request all files access not supported", e);
        }
        // Some devices don't support this request.
        Toast.makeText(activity, R.string.dialog_all_files_access_not_supported, Toast.LENGTH_LONG).show();
    }

    public static boolean haveLocationPermission(@NonNull Context context) {
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean backgroundLocationGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED;
        }

        return fineLocationGranted && backgroundLocationGranted;
    }

    public static boolean haveNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveCameraPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean haveIgnoreDozePermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean haveAllOnboardingPermissions(@NonNull Context context) {
        return haveNotificationPermission(context) &&
                haveCameraPermission(context) &&
                haveLocationPermission(context) &&
                haveIgnoreDozePermission(context);
    }

}
