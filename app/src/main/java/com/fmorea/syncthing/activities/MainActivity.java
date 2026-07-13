package com.fmorea.syncthing.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;

import androidx.activity.OnBackPressedCallback;
import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.ViewModelProvider;

import com.fmorea.syncthing.R;
import com.fmorea.syncthing.SyncthingApp;
import com.fmorea.syncthing.fragments.DeviceIdDialogFragment;
import com.fmorea.syncthing.syncthing.LinkThingBridge;
import com.fmorea.syncthing.syncthing.LinkThingViewModel;
import com.fmorea.syncthing.webgui.WebGuiActivity;
import com.fmorea.syncthing.model.Device;
import com.fmorea.syncthing.service.Constants;
import com.fmorea.syncthing.service.RestApi;
import com.fmorea.syncthing.service.SyncthingService;
import com.fmorea.syncthing.service.SyncthingServiceBinder;
import com.fmorea.syncthing.util.ConfigRouter;
import com.fmorea.syncthing.util.PermissionUtil;
import com.fmorea.syncthing.util.Util;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Main activity of the application.
 */
public class MainActivity extends SyncthingActivity implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "MainActivity";

    public static final String ACTION_EXIT = ".MainActivity.EXIT";

    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(1);
    private static final Boolean DEBUG_FORCE_USAGE_REPORTING_DIALOG = false;

    private static final int QR_SCAN_REQUEST_CODE = 403;

    private AlertDialog mUsageReportingDialog;
    private SyncthingService.State mSyncthingServiceState = SyncthingService.State.INIT;
    private LinkThingViewModel mLinkThingViewModel;
    private NfcAdapter mNfcAdapter;
    private Intent mLastIntent;

    @Inject
    public SharedPreferences mPreferences;

    private OnBackPressedCallback mBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            doExit();
        }
    };

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mSyncthingServiceState = currentState;
        
        if (mLinkThingViewModel != null) {
            RestApi api = getApi();
            int completion = (api != null && api.isConfigLoaded()) ? api.getTotalSyncCompletion() : 100;
            mLinkThingViewModel.updateSyncStatus(currentState, completion, api);
        }

        if (currentState == SyncthingService.State.ACTIVE) {
            RestApi restApi = getApi();
            if (restApi != null && restApi.isConfigLoaded() && (
                    DEBUG_FORCE_USAGE_REPORTING_DIALOG ||
                    System.currentTimeMillis() - getFirstStartTime() > USAGE_REPORTING_DIALOG_DELAY &&
                    !restApi.isUsageReportingDecided()
            )) {
                showUsageReportingDialog(restApi);
            }
        } else if (currentState == SyncthingService.State.ERROR) {
            Toast.makeText(this, "Errore durante l'avvio di Syncthing. Controlla i log.", Toast.LENGTH_LONG).show();
        }
    }

    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        try {
            return pm.getPackageInfo(getPackageName(), 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to get first install time", e);
            return 0;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);
        
        mLinkThingViewModel = new ViewModelProvider(this).get(LinkThingViewModel.class);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        observeViewModelEvents();

        ComposeView composeView = new ComposeView(this);
        composeView.setTag("LinkThingCompose");
        LinkThingBridge.setContent(composeView, mLinkThingViewModel, "");
        setContentView(composeView);

        Intent serviceIntent = new Intent(this, SyncthingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        onNewIntent(getIntent());
        getOnBackPressedDispatcher().addCallback(this, mBackPressedCallback);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        mLastIntent = intent;
        super.onNewIntent(intent);
        
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                Ndef ndef = Ndef.get(tag);
                if (ndef != null) {
                    try {
                        ndef.connect();
                        NdefMessage msg = ndef.getCachedNdefMessage();
                        if (msg != null) {
                            for (NdefRecord record : msg.getRecords()) {
                                String payload = new String(record.getPayload());
                                // Often NDEF text starts with a language code (e.g. "en") and some control chars
                                // We'll just look for a valid device ID length
                                if (payload.length() > 10) {
                                    Toast.makeText(this, "NFC: Identità rilevata!", Toast.LENGTH_SHORT).show();
                                    mLinkThingViewModel.addFriend(payload.trim());
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "NFC read failed", e);
                    } finally {
                        try { ndef.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNfcAdapter != null) {
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? android.app.PendingIntent.FLAG_MUTABLE : 0);
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
        if (!PermissionUtil.haveAllOnboardingPermissions(this) || !Constants.getConfigFile(this).exists()) {
            startActivity(new Intent(this, com.fmorea.syncthing.onboarding.OnboardingActivity.class));
            finish();
            return;
        }

        SyncthingService service = getService();
        if (service != null) {
            service.evaluateRunConditions();
        }

        if (mLastIntent != null && ACTION_EXIT.equals(mLastIntent.getAction())) {
            doExit();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService service = getService();
        if (service != null) {
            service.unregisterOnServiceStateChangeListener(this);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        SyncthingServiceBinder binder = (SyncthingServiceBinder) iBinder;
        SyncthingService service = binder.getService();
        service.registerOnServiceStateChangeListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Util.dismissDialogSafe(mUsageReportingDialog, this);
    }

    private void showUsageReportingDialog(RestApi restApi) {
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            try {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        restApi.setUsageReporting(true);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        restApi.setUsageReporting(false);
                        restApi.sendConfig();
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        final Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                        intent.putExtra(WebViewActivity.EXTRA_WEB_URL, getString(R.string.syncthing_usage_stats_url));
                        startActivity(intent);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in usage reporting dialog listener", e);
            }
        };

        restApi.getUsageReport(report -> {
            @SuppressLint("InflateParams")
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.dialog_usage_reporting, null);
            TextView tv = v.findViewById(R.id.example);
            tv.setText(report);
            Util.dismissDialogSafe(mUsageReportingDialog, MainActivity.this);
            mUsageReportingDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.usage_reporting_dialog_title)
                    .setMessage(R.string.usage_reporting_dialog_description)
                    .setView(v)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(R.string.open_website, listener)
                    .show();
        });
    }

    private void observeViewModelEvents() {
        mLinkThingViewModel.getUiEvents().observe(this, event -> {
            if (event == null) return;

            if (event instanceof LinkThingViewModel.UiEvent.ShowMyId) {
                showQrCodeDialog();
            } else if (event instanceof LinkThingViewModel.UiEvent.ScanQrCode) {
                startActivityForResult(QRScannerActivity.intent(this), QR_SCAN_REQUEST_CODE);
            } else if (event instanceof LinkThingViewModel.UiEvent.OpenWebGui) {
                startActivity(new Intent(this, WebGuiActivity.class));
            } else if (event instanceof LinkThingViewModel.UiEvent.OpenSettings) {
                startActivity(new Intent(this, com.fmorea.syncthing.settings.SettingsActivity.class));
            } else if (event instanceof LinkThingViewModel.UiEvent.ManageFriends) {
                startActivity(new Intent(this, com.fmorea.syncthing.syncthing.NetworkManagementActivity.class));
            } else if (event instanceof LinkThingViewModel.UiEvent.OpenChess) {
                startActivity(new Intent(this, com.fmorea.syncthing.chess.ChessActivity.class));
            } else if (event instanceof LinkThingViewModel.UiEvent.DeviceDiscovered) {
                String id = ((LinkThingViewModel.UiEvent.DeviceDiscovered) event).getDeviceId();
                Toast.makeText(this, "Dispositivo scoperto nelle vicinanze: " + id.substring(0, 8), Toast.LENGTH_LONG).show();
            }
            mLinkThingViewModel.clearUiEvent();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String scannedId = data.getStringExtra(QRScannerActivity.QR_RESULT_ARG);
            if (scannedId != null) {
                ComposeView composeView = findViewById(android.R.id.content).findViewWithTag("LinkThingCompose");
                if (composeView == null) {
                    View root = findViewById(android.R.id.content);
                    if (root instanceof ViewGroup) {
                        for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                            View child = ((ViewGroup) root).getChildAt(i);
                            if (child instanceof ComposeView) {
                                composeView = (ComposeView) child;
                                break;
                            }
                        }
                    }
                }
                if (composeView != null) {
                    LinkThingBridge.setContent(composeView, mLinkThingViewModel, scannedId);
                }
            }
        }
    }

    public void showQrCodeDialog() {
        String deviceId = mPreferences.getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(deviceId)) {
            Toast.makeText(this, R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show();
            return;
        }
        
        ConfigRouter config = new ConfigRouter(this);
        List<Device> devices = config.getDevices(getApi(), true);
        String deviceName = "";

        for (Device d : devices) {
            if (d.deviceID.equals(deviceId)) {
                deviceName = d.getDisplayName();
                break;
            }
        }

        DeviceIdDialogFragment.Companion.show(
                getSupportFragmentManager(),
                deviceName.trim(),
                deviceId,
                true
        );
    }

    public void doExit() {
        if (isFinishing()) return;
        Log.i(TAG, "Exiting app on user request");
        stopService(new Intent(this, SyncthingService.class));
        finishAndRemoveTask();
    }
}
