package com.nutomic.syncthingandroid.test;

import android.app.Activity;
import android.content.Context;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MockRestApi extends RestApi {

    public MockRestApi(Context context, String url, String apiKey,
                       OnApiAvailableListener listener) {
        super(context, url, apiKey, listener, null);
    }

    @Override
    public void onWebGuiAvailable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Device> getDevices(boolean includeLocal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getSystemInfo(OnResultListener1<SystemInfo> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Folder> getFolders() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getModel(final String folderId, final OnReceiveModelListener listener) {
    }

    @Override
    public void editDevice(Device device, Activity activity, OnDeviceIdNormalizedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getModel(String folderId, OnResultListener2<String, Model> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean editFolder(Folder folder, boolean create, Activity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeDevice(String deviceId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void editFolder(Folder newFolder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeFolder(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void normalizeDeviceId(String id, OnResultListener1<String> listener, OnResultListener1<String> errorListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        throw new UnsupportedOperationException();
    }
}
