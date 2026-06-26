package com.fmorea.syncthing.model;

import android.text.TextUtils;

import com.fmorea.syncthing.service.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sources:
 * - https://github.com/syncthing/syncthing/tree/master/lib/config
 * - https://github.com/syncthing/syncthing/blob/master/lib/config/folderconfiguration.go
 */
public class Folder {

    // Folder Configuration
    public String group = "";
    public String id;
    public String label = "";
    public String filesystemType = "basic";
    public String path;
    public String type = Constants.FOLDER_TYPE_SEND_RECEIVE;
    public boolean fsWatcherEnabled = true;
    public float fsWatcherDelayS = 10;
    private List<SharedWithDevice> devices = new ArrayList<>();
    /**
     * Folder rescan interval defaults to 3600s as it is the default in
     * syncthing when the file watcher is enabled and a new folder is created.
     */
    public int rescanIntervalS = 3600;
    public boolean ignorePerms = true;
    public boolean autoNormalize = true;
    public MinDiskFree minDiskFree;
    public Versioning versioning;
    public int copiers = 0;
    public int pullerMaxPendingKiB;
    public int hashers = 0;
    public String order = "random";
    public boolean ignoreDelete = false;
    public int scanProgressIntervalS = 0;
    public int pullerPauseS = 0;
    public int maxConflicts = 10;
    public boolean disableSparseFiles = false;
    public boolean paused = false;
    public String markerName = Constants.FILENAME_STFOLDER;

    // Since v1.9.0
    public Boolean copyOwnershipFromParent = false;

    // Since v1.11.0
    public int modTimeWindowS = 0;

    // Since v1.15.0
    public String blockPullOrder = "standard";
    public Boolean disableFsync = false;
    public int maxConcurrentWrites = 0;

    // Since v1.21.0
    public String copyRangeMethod = "standard";

    // Since v1.23.1
    public Boolean caseSensitiveFS = false;

    // Since v1.25.0
    public Boolean syncOwnership = false;
    public Boolean sendOwnership = false;

    // Since v1.26.0
    public Boolean syncXattrs = false;
    public Boolean sendXattrs = false;

    // Since v1.27.0
    public Boolean blockIndexing = true;

    public String invalid;

    public static class Versioning {
        public String type;
        public int cleanupIntervalS;
        public Map<String, String> params = new HashMap<>();
        // Since v1.14.0
        public String fsPath;
        public String fsType;           // default: "basic"
    }

    public static class MinDiskFree {
        public float value = 1;
        public String unit = "%";
    }

    public void addDevice(String deviceId) {
        if (getDevice(deviceId) != null) return;
        SharedWithDevice d = new SharedWithDevice();
        d.deviceID = deviceId;
        devices.add(d);
    }

    public List<String> getSharedWithDeviceIDs() {
        List<String> ids = new ArrayList<>();
        if (devices != null) {
            for (SharedWithDevice d : devices) {
                ids.add(d.deviceID);
            }
        }
        return ids;
    }

    public void addDevice(final Device device) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(device.deviceID);

        SharedWithDevice d = new SharedWithDevice();
        d.deviceID = device.deviceID;
        d.introducedBy = device.introducedBy;
        devices.add(d);
    }

    public void addDevice(final SharedWithDevice sharedWithDevice) {
        // Avoid {@link ConfigXml#updateDevice} creating two list entries with the same device ID.
        removeDevice(sharedWithDevice.deviceID);

        SharedWithDevice d = new SharedWithDevice();
        d.deviceID = sharedWithDevice.deviceID;
        d.encryptionPassword = sharedWithDevice.encryptionPassword;
        d.introducedBy = sharedWithDevice.introducedBy;
        devices.add(d);
    }

    public List<SharedWithDevice> getSharedWithDevices() {
        return devices;
    }

    /**
     * Returns the number of devices this folder is shared with.
     */
    public int getDeviceCount() {
        int count = 0;
        if (devices != null) {
            for (SharedWithDevice d : devices) {
                if (d.deviceID != null) {
                    count++;
                }
            }
        }
        return count;
    }

    public SharedWithDevice getDevice(String deviceId) {
        if (devices == null) return null;
        for (SharedWithDevice d : devices) {
            if (d.deviceID != null && d.deviceID.equals(deviceId)) {
                return d;
            }
        }
        return null;
    }

    public void removeDevice(String deviceId) {
        if (devices == null) return;
        for (Iterator<SharedWithDevice> it = devices.iterator(); it.hasNext();) {
            String currentId = it.next().deviceID;
            if (currentId != null && currentId.equals(deviceId)) {
                it.remove();
            }
        }
    }

    @Override
    public String toString() {
        return (TextUtils.isEmpty(label))
                ? id
                : label;
    }
}
