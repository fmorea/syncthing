package com.fmorea.syncthing.util;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import com.fmorea.syncthing.service.SyncTriggerJobService;

import static com.fmorea.syncthing.service.RunConditionMonitor.EXTRA_BEGIN_ACTIVE_TIME_WINDOW;

public class JobUtils {

    private static final String TAG = "JobUtils";

    public static void scheduleSyncTriggerServiceJob(Context context, int delayInSeconds, boolean startRun) {
        final int finalDelay = Math.max(0, delayInSeconds);
        final Context appContext = context.getApplicationContext();

        // Schedule the start of "SyncTriggerJobService" in "X" seconds.
        new Thread(() -> {
            try {
                // Use explicit package and class name strings to avoid component resolution errors
                ComponentName serviceComponent = new ComponentName(
                        appContext.getPackageName(), 
                        "com.fmorea.syncthing.service.SyncTriggerJobService"
                );
                JobInfo.Builder builder = new JobInfo.Builder(1001, serviceComponent);

                // Wait at least "delayInSeconds".
                builder.setMinimumLatency(finalDelay * 1000L);

                // Syncthing should start after the delay if startRun is true, and otherwise stop
                // The PersistableBundle is used to forward this information to the SyncTriggerJobService
                if (startRun) {
                    PersistableBundle extraBundle = new PersistableBundle();
                    extraBundle.putInt(EXTRA_BEGIN_ACTIVE_TIME_WINDOW, 1); // must be int, because boolean needs API 22
                    builder.setExtras(extraBundle);
                }

                JobScheduler jobScheduler = (JobScheduler) appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (jobScheduler != null) {
                    jobScheduler.schedule(builder.build());
                    Log.i(TAG, "Scheduled SyncTriggerJobService to run in " + finalDelay + " seconds.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to schedule SyncTriggerJobService safely", e);
            }
        }).start();
    }

    public static void cancelAllScheduledJobs(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }
}
