package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.ShareActionProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.support.v4.view.MenuItemCompat;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.List;

/**
 * Shows the log information from Syncthing.
 */
public class LogActivity extends SyncthingActivity {

    private final static String TAG = "LogActivity";

    private TextView mLog;
    private boolean mSyncthingLog = true;
    private AsyncTask mFetchLogTask = null;
    private ScrollView mScrollView;
    private Intent mShareIntent;

    /**
     * Initialize Log.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);

        if (savedInstanceState != null) {
            mSyncthingLog = savedInstanceState.getBoolean("syncthingLog");
            ActivityCompat.invalidateOptionsMenu(this);
        }

        mLog = (TextView) findViewById(R.id.log);
        mScrollView = (ScrollView) findViewById(R.id.scroller);

        updateLog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("syncthingLog", mSyncthingLog);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_list, menu);

        MenuItem switchLog = menu.findItem(R.id.switch_logs);
        switchLog.setTitle(mSyncthingLog ? R.string.log_android_title : R.string.log_syncthing_title);

        // Add the share button
        MenuItem shareItem = menu.findItem(R.id.menu_share);
        ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        mShareIntent = new Intent();
        mShareIntent.setAction(Intent.ACTION_SEND);
        mShareIntent.setType("text/plain");
        mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, mLog.getText());
        actionProvider.setShareIntent(mShareIntent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_logs:
                mSyncthingLog = !mSyncthingLog;
                item.setTitle(mSyncthingLog ? R.string.log_android_title : R.string.log_syncthing_title);
                updateLog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void scrollToBottom() {
        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.scrollTo(0, mLog.getBottom());
            }
        });
    }

    private void updateLog() {
        if (mFetchLogTask != null) {
            mFetchLogTask.cancel(true);
        mLog.setText("Retrieving logs...");
        mFetchLogTask = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                return getLog(mSyncthingLog);
            }
            @Override
            protected void onPostExecute(String log) {
                mLog.setText(log);
                if (mShareIntent != null)
                    mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, log);
                scrollToBottom();
            }
        }.execute();
    }

    /**
     * Queries logcat to obtain a log.
     *
     * @param syncthingLog Filter on Syncthing's native messages.
     */
    private String getLog(final boolean syncthingLog) {
        Process process = null;
        try {
            ProcessBuilder pb;
            if (syncthingLog) {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "-v", "time", "-s", "SyncthingNativeCode");
            } else {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "-v", "time", "'*'");
            }
            pb.redirectErrorStream(true);
            process = pb.start();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"), 8192);
            StringBuilder log = new StringBuilder();
            String line = "";
            String sep = System.getProperty("line.separator");
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
                log.append(sep);
            }
            return log.toString();
        } catch (IOException e) {
            Log.w(TAG, "Error reading Android log", e);
        } finally {
            if (process != null) {
                process.destroy();
            }

            // Filter Android log.
            output = output.replaceAll("I/SyncthingNativeCode", "");
            // Remove PID.
            output = output.replaceAll("\\(\\s?[0-9]+\\):", "");
            String[] lines = output.split("\n");
            List<String> list = new ArrayList<String>(Arrays.asList(lines));
            ListIterator<String> it = list.listIterator();
            while (it.hasNext()) {
                String logline = it.next();
                if (
                            logline.contains("--- beginning of ") ||
                            logline.contains("W/ActionBarDrawerToggle") ||
                            logline.contains("W/ActivityThread") ||
                            logline.contains("I/Adreno") ||
                            logline.contains("I/chatty") ||
                            logline.contains("/Choreographer") ||
                            logline.contains("W/chmod") ||
                            logline.contains("/chromium") ||
                            logline.contains("/ContentCatcher") ||
                            logline.contains("/cr_AwContents") ||
                            logline.contains("/cr_base") ||
                            logline.contains("/cr_BrowserStartup") ||
                            logline.contains("/cr_ChildProcessConn") ||
                            logline.contains("/cr_ChildProcLH") ||
                            logline.contains("/cr_CrashFileManager") ||
                            logline.contains("/cr_LibraryLoader") ||
                            logline.contains("/cr_media") ||
                            logline.contains("/cr_MediaCodecUtil") ||
                            logline.contains("I/ConfigStore") ||
                            logline.contains("/eglCodecCommon") ||
                            logline.contains("/InputEventReceiver") ||
                            logline.contains("/ngandroid.debu") ||
                            logline.contains("/OpenGLRenderer") ||
                            logline.contains("/PacProxySelector") ||
                            logline.contains("I/Perf") ||
                            logline.contains("/RenderThread") ||
                            logline.contains("W/sh") ||
                            logline.contains("/StrictMode") ||
                            logline.contains("I/Timeline") ||
                            logline.contains("/VideoCapabilities") ||
                            logline.contains("I/WebViewFactory") ||
                            logline.contains("I/X509Util") ||
                            logline.contains("/zygote64")
                        ) {
                    it.remove();
                    continue;
                }
                // Remove date.
                logline = logline.replaceFirst("^[0-9]{2}-[0-9]{2}\\s", "");
                // Remove milliseconds.
                logline = logline.replaceFirst("^([0-9]{2}:[0-9]{2}:[0-9]{2})\\.[0-9]{3}\\s", "$1");
                it.set(logline);
            }
            return TextUtils.join("\n", list.toArray(new String[0]));
        }
    }

}
