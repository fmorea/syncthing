package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Folder implements Serializable {

    public String id;
    public String label;
    public String path;
    public String type;
    private transient List<Map<String, String>> devices = new ArrayList<>();
    public int rescanIntervalS;
    public final boolean ignorePerms = true;
    public boolean autoNormalize = true;
    public MinDiskFree minDiskFree;
    public Versioning versioning;
    public int copiers;
    public int pullers;
    public int hashers;
    public String order;
    public boolean ignoreDelete;
    public int scanProgressIntervalS;
    public int pullerSleepS;
    public int pullerPauseS;
    public int maxConflicts = 10;
    public boolean disableSparseFiles;
    public boolean disableTempIndexes;
    public String invalid;
    public boolean fsync = true;

    public static class Versioning implements Serializable {
        public String type;
        public Map<String, String> params = new HashMap<>();
    }

    public List<String> getDevices() {
        if (devices == null)
            return new ArrayList<>();

        List<String> devicesList = new ArrayList<>();
        for (Map<String, String> map : devices) {
            devicesList.addAll(map.values());
        }
        return devicesList;
    }

    public void setDevices(List<String> newDevices) {
        devices.clear();
        for (String d : newDevices) {
            Map<String, String> map = new HashMap<>();
            map.put("deviceID", d);
            devices.add(map);
        }
    }

    @Override
    public String toString() {
        return !TextUtils.isEmpty(label) ? label : id;
    }
}
