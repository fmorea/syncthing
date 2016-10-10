package com.nutomic.syncthingandroid.test.activities;

import android.support.test.rule.ActivityTestRule;

import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.fragments.DevicesFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockSyncthingService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class MainActivityTest {

    @Rule
    public final ActivityTestRule<MainActivity> mRule = new ActivityTestRule<>(MainActivity.class);

    private MockSyncthingService mService = new MockSyncthingService();

    @Test
    public void testOnServiceConnected() {
        getActivity().onServiceConnected(null, new SyncthingServiceBinder(mService));
        assertTrue(mService.containsListenerInstance(MainActivity.class));
        assertTrue(mService.containsListenerInstance(FolderListFragment.class));
        assertTrue(mService.containsListenerInstance(DevicesFragment.class));
    }

}
