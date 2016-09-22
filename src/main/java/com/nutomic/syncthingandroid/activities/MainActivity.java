package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.widget.TextView;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.DevicesFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/**
 * Shows {@link FolderListFragment} and
 * {@link com.nutomic.syncthingandroid.fragments.DevicesFragment} in different tabs, and
 * {@link com.nutomic.syncthingandroid.fragments.DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnApiChangeListener {

    private static final String TAG = "MainActivity";

    static final String EXTRA_FIRST_START = "com.nutomic.syncthing-android.MainActivity.FIRST_START";

    /**
     * Time after first start when usage reporting dialog should be shown.
     *
     * @see #showUsageReportingDialog()
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);

    private static final int REQUEST_WRITE_STORAGE = 142;

    public static final String ACTION_ADD_DEVICE = "add_device";
    public static final String EXTRA_DEVICE_ID   = "device_id";

    private AlertDialog mLoadingDialog;
    private AlertDialog mDisabledDialog;

    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
            case INIT:
                showLoadingDialog();
                break;
            case STARTING:
                showLoadingDialog();
                dismissDisabledDialog();
                break;
            case ACTIVE:
                dismissDisabledDialog();
                dismissLoadingDialog();
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                mDrawerFragment.requestGuiUpdate();
                if (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY &&
                        getApi().getUsageReportAccepted() == RestApi.USAGE_REPORTING_UNDECIDED) {
                    showUsageReportingDialog();
                }
                break;
            case ERROR:
                finish();
                break;
            case DISABLED:
                dismissLoadingDialog();
                if (!isFinishing()) {
                    showDisabledDialog();
                }
                break;
        }
    }

    private void dismissDisabledDialog() {
        if (mDisabledDialog != null) {
            mDisabledDialog.dismiss();
            mDisabledDialog = null;
        }
    }

    /**
     * Returns the unix timestamp at which the app was first installed.
     */
    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        long firstInstallTime = 0;
        try {
            // No info is available on Froyo.
            firstInstallTime = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
                    ? pm.getPackageInfo(getPackageName(), 0).firstInstallTime
                    : 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "This should never happen", e);
        }
        return firstInstallTime;
    }

    /**
     * Shows the loading dialog with the correct text ("creating keys" or "loading").
     */
    private void showLoadingDialog() {
        if (mLoadingDialog != null)
            return;

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams")
        View dialogLayout = inflater.inflate(R.layout.dialog_loading, null);
        TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
        loadingText.setText((getIntent().getBooleanExtra(EXTRA_FIRST_START, false))
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        mLoadingDialog = new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setView(dialogLayout)
                .show();
    }

    private void dismissLoadingDialog() {
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }

    private final FragmentPagerAdapter mSectionsPagerAdapter =
            new FragmentPagerAdapter(getSupportFragmentManager()) {

                @Override
                public Fragment getItem(int position) {
                    switch (position) {
                        case 0:
                            return mFolderFragment;
                        case 1:
                            return mDevicesFragment;
                        default:
                            return null;
                    }
                }

                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public CharSequence getPageTitle(int position) {
                    switch (position) {
                        case 0:
                            return getResources().getString(R.string.folders_fragment_title);
                        case 1:
                            return getResources().getString(R.string.devices_fragment_title);
                        default:
                            return String.valueOf(position);
                    }
                }
            };

    private FolderListFragment mFolderFragment;

    private DevicesFragment mDevicesFragment;

    private DrawerFragment mDrawerFragment;

    private ViewPager mViewPager;

    private ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;

    /**
     * Initializes tab navigation.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabContainer);
        tabLayout.setupWithViewPager(mViewPager);

        if (savedInstanceState != null) {
            FragmentManager fm = getSupportFragmentManager();
            mFolderFragment = (FolderListFragment) fm.getFragment(
                    savedInstanceState, FolderListFragment.class.getName());
            mDevicesFragment = (DevicesFragment) fm.getFragment(
                    savedInstanceState, DevicesFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
            mViewPager.setCurrentItem(savedInstanceState.getInt("currentTab"));
        } else {
            mFolderFragment = new FolderListFragment();
            mDevicesFragment = new DevicesFragment();
            mDrawerFragment = new DrawerFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.drawer, mDrawerFragment)
                .commit();
        mDrawerToggle = new Toggle(this, mDrawerLayout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));

        onNewIntent(getIntent());
    }

    /**
     * Shows dialog to add rejected devices.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // intent's action will be null if notification is selected
        if (intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case ACTION_ADD_DEVICE:
                final String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.device_rejected, deviceId))
                        .setPositiveButton(R.string.add_device, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                RestApi.Device device = new RestApi.Device();
                                device.deviceID = deviceId;
                                device.addresses = "";
                                getApi().editDevice(device, MainActivity.this, null);
                            }
                        })
                        .setNegativeButton(R.string.ignore, null)
                        .show();

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissDisabledDialog();
        dismissLoadingDialog();
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this);
            getService().unregisterOnApiChangeListener(mFolderFragment);
            getService().unregisterOnApiChangeListener(mDevicesFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(this);
        getService().registerOnApiChangeListener(mFolderFragment);
        getService().registerOnApiChangeListener(mDevicesFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Avoid crash if called during startup.
        if (mFolderFragment != null && mDevicesFragment != null && mDrawerFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.putFragment(outState, FolderListFragment.class.getName(), mFolderFragment);
            fm.putFragment(outState, DevicesFragment.class.getName(), mDevicesFragment);
            fm.putFragment(outState, DrawerFragment.class.getName(), mDrawerFragment);
            outState.putInt("currentTab", mViewPager.getCurrentItem());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mDrawerToggle.syncState();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void showDisabledDialog() {
        mDisabledDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                                Intent intent = new Intent(MainActivity.this, SettingsActivity.class)
                                        .setAction(SettingsActivity.ACTION_APP_SETTINGS);
                                startActivity(intent);
                            }
                        }
                )
                .setNegativeButton(R.string.exit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        }
                )
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                })
                .show();
        mDisabledDialog.setCanceledOnTouchOutside(false);
    }

    @Override
     public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Handles drawer opened and closed events, toggling option menu state.
     */
    private class Toggle extends ActionBarDrawerToggle {
        public Toggle(Activity activity, DrawerLayout drawerLayout) {
            super(activity, drawerLayout, R.string.app_name, R.string.app_name);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            mDrawerFragment.onDrawerOpened();
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            mDrawerFragment.onDrawerClosed();
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            super.onDrawerSlide(drawerView, 0);
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    /**
     * Toggles the drawer on menu button press.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START))
                mDrawerLayout.openDrawer(GravityCompat.START);
            else
                closeDrawer();

            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    /**
     * Close drawer on back button press.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
            closeDrawer();
        else
            super.onBackPressed();
    }

    /**
     * Calculating width based on
     * http://www.google.com/design/spec/patterns/navigation-drawer.html#navigation-drawer-specs.
     */
    private void setOptimalDrawerWidth(View drawerContainer) {
        int actionBarSize = 0;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarSize = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }

        ViewGroup.LayoutParams params = drawerContainer.getLayoutParams();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int minScreenWidth = min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        params.width = min(minScreenWidth - actionBarSize, 5 * actionBarSize);
        drawerContainer.requestLayout();
    }

    /**
     * Displays dialog asking user to accept/deny usage reporting.
     */
    private void showUsageReportingDialog() {
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        getApi().setUsageReportAccepted(RestApi.USAGE_REPORTING_ACCEPTED,
                                                        MainActivity.this);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        getApi().setUsageReportAccepted(RestApi.USAGE_REPORTING_DENIED,
                                                        MainActivity.this);
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        Uri uri = Uri.parse("https://data.syncthing.net");
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        break;
                }

            }
        };

        getApi().getUsageReport(new RestApi.OnReceiveUsageReportListener() {
            @Override
            public void onReceiveUsageReport(String report) {
                @SuppressLint("InflateParams")
                View v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.dialog_usage_reporting, null);
                TextView tv = (TextView) v.findViewById(R.id.example);
                tv.setText(report);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.usage_reporting_dialog_title)
                        .setView(v)
                        .setPositiveButton(R.string.yes, listener)
                        .setNegativeButton(R.string.no, listener)
                        .setNeutralButton(R.string.open_website, listener)
                        .show();
            }
        });
    }

}
