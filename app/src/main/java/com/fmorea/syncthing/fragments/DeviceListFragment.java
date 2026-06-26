package com.fmorea.syncthing.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.fmorea.syncthing.R;
import com.fmorea.syncthing.SyncthingApp;
import com.fmorea.syncthing.activities.DeviceActivity;
import com.fmorea.syncthing.activities.MainActivity;
import com.fmorea.syncthing.activities.SyncthingActivity;
import com.fmorea.syncthing.model.Device;
import com.fmorea.syncthing.service.AppPrefs;
import com.fmorea.syncthing.service.Constants;
import com.fmorea.syncthing.service.RestApi;
import com.fmorea.syncthing.service.SyncthingService;
import com.fmorea.syncthing.util.ConfigRouter;
import com.fmorea.syncthing.util.ConfigXml.OpenConfigException;
import com.fmorea.syncthing.views.DevicesAdapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

/**
 * Displays a list of all existing devices.
 */
public class DeviceListFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener,
        ListView.OnItemClickListener {

    private final static String TAG = "DeviceListFragment";

    private Boolean ENABLE_VERBOSE_LOG = false;

    private ConfigRouter mConfigRouter = null;

    @Inject SharedPreferences mPreferences;

    /**
     * Compares devices by name, uses the device ID as fallback if the name is empty
     */
    private final static Comparator<Device> DEVICES_COMPARATOR = (lhs, rhs) -> {
        String lhsName = lhs.name != null && !lhs.name.isEmpty() ? lhs.name : lhs.deviceID;
        String rhsName = rhs.name != null && !rhs.name.isEmpty() ? rhs.name : rhs.deviceID;
        return lhsName.compareTo(rhsName);
    };

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mUpdateListHandler.postDelayed(this, Constants.REST_UPDATE_INTERVAL);
        }
    };

    private final Handler mUpdateListHandler = new Handler();
    private Boolean mLastVisibleToUser = false;
    private DevicesAdapter mAdapter;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getActivity().getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // User switched to the current tab, start handler.
            startUpdateListHandler();
        } else {
            // User switched away to another tab, stop handler.
            stopUpdateListHandler();
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        stopUpdateListHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLastVisibleToUser) {
            startUpdateListHandler();
        }
    }

    private void startUpdateListHandler() {
        LogV("startUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
        mUpdateListHandler.post(mUpdateListRunnable);
    }

    private void stopUpdateListHandler() {
        LogV("stopUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);
        setEmptyText(getString(R.string.no_devices_configured));
        getListView().setOnItemClickListener(this);
    }

    /**
     * Invokes updateList which polls the REST API for device status updates
     *  while the user is looking at the current tab.
     */
    private void onTimerEvent() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        LogV("Invoking updateList on UI thread");
        mainActivity.runOnUiThread(DeviceListFragment.this::updateList);
    }

    /**
     * Refreshes ListView by updating devices and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }
        if (mConfigRouter == null) {
            mConfigRouter = new ConfigRouter(activity);
        }
        List<Device> devices = mConfigRouter.getDevices(activity.getApi(), false);
        if (devices == null) {
            return;
        }
        RestApi restApi = activity.getApi();
        if (mServiceState == SyncthingService.State.ACTIVE &&
                restApi != null &&
                restApi.isConfigLoaded()) {
            /**
             * Syncthing is running and REST API is available.
             * Force a cache-miss to query status of all devices asynchronously.
            */
            restApi.getRemoteDeviceStatus("");
        }

        if (mAdapter == null) {
            mAdapter = new DevicesAdapter(activity);
            setListAdapter(mAdapter);
        }
        mAdapter.setRestApi(mConfigRouter, restApi);

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        Collections.sort(devices, DEVICES_COMPARATOR);
        mAdapter.addAll(devices);
        mAdapter.notifyDataSetChanged();
        setListShown(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), DeviceActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_IS_CREATE, false);
        intent.putExtra(DeviceActivity.EXTRA_DEVICE_ID, mAdapter.getItem(i).deviceID);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.device_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_device) {
            Intent intent = new Intent(getActivity(), DeviceActivity.class)
                    .putExtra(DeviceActivity.EXTRA_IS_CREATE, true);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
