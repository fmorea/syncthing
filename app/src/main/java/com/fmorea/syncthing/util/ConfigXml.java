package com.fmorea.syncthing.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.fmorea.syncthing.R;
import com.fmorea.syncthing.model.Device;
import com.fmorea.syncthing.model.Folder;
import com.fmorea.syncthing.model.FolderIgnoreList;
import com.fmorea.syncthing.model.Gui;
import com.fmorea.syncthing.model.IgnoredFolder;
import com.fmorea.syncthing.model.Options;
import com.fmorea.syncthing.model.SharedWithDevice;
import com.fmorea.syncthing.service.AppPrefs;
import com.fmorea.syncthing.service.Constants;
import com.fmorea.syncthing.service.SyncthingRunnable;

import org.mindrot.jbcrypt.BCrypt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Provides access to syncthing's config.xml.
 *
 * This class is not thread-safe.
 */
public class ConfigXml {

    private static final String TAG = "ConfigXml";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Thrown if the config file can't be opened.
     */
    public static class OpenConfigException extends Exception {
    }

    private final Context mContext;

    private final File mConfigFile;

    /**
     * The parsed config.
     */
    private Document mConfig;

    public ConfigXml(Context context) {
        mContext = context;
        mConfigFile = Constants.getConfigFile(context);
    }

    public void loadConfig() throws OpenConfigException {
        if (!mConfigFile.exists()) {
            throw new OpenConfigException();
        }

        parseConfig();
    }

    /**
     * This should run within an AsyncTask as it can cause a full CPU load
     * for more than 30 seconds on older phone hardware.
     */
    public void generateConfig() throws OpenConfigException, SyncthingRunnable.ExecutableNotFoundException {
        // Create new secret keys and config.
        Log.i(TAG, "(Re)Generating keys and config.");
        new SyncthingRunnable(mContext, SyncthingRunnable.Command.generate).run(true);
        parseConfig();
        Boolean changed = false;

        // Set local device name.
        Log.i(TAG, "Starting syncthing to retrieve local device id.");
        String localDeviceID = getLocalDeviceIDandStoreToPref();
        if (!TextUtils.isEmpty(localDeviceID)) {
            changed = changeLocalDeviceName(localDeviceID) || changed;
        }

        // Add mandatory LinkThing folder
        changed = ensureLinkThingFolder() || changed;

        // Change default folder section.
        Element elementDefaults = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("defaults").item(0);
        if (elementDefaults != null) {
            Element elementDefaultFolder = (Element) elementDefaults
                    .getElementsByTagName("folder").item(0);
            if (elementDefaultFolder != null) {
                Element elementVersioning = (Element) elementDefaultFolder.getElementsByTagName("versioning").item(0);
                if (elementVersioning != null) {
                    elementVersioning.setAttribute("type", "trashcan");
                    Node nodeParam = mConfig.createElement("param");
                    elementVersioning.appendChild(nodeParam);
                    Element elementParam = (Element) nodeParam;
                    elementParam.setAttribute("key", "cleanoutDays");
                    elementParam.setAttribute("val", "14");
                    changed = true;
                }
            }
        }

        /* Section - GUI */
        Element gui = getGuiElement();
        if (gui == null) {
            throw new OpenConfigException();
        }
        
        // Force TLS for the local GUI to match RestApi expectations
        gui.setAttribute("tls", "true");

        // Set user to "syncthing"
        changed = setConfigElement(gui, "user", "syncthing") || changed;

        // Initialiaze password to the API key
        changed = setConfigElement(gui, "password",  BCrypt.hashpw(getApiKey(), BCrypt.gensalt(4))) || changed;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_WEBUI_PASSWORD, getApiKey())
                .apply();

        //  Allow debug and release to run in parallel for testing purposes.
        if (Constants.isDebuggable(mContext)) {
            // Set alternative gui listen port.
            changed = setConfigElement(gui, "address", "127.0.0.1:8385") || changed;

            // Set alternative data listen port.
            Element elementOptions = (Element) mConfig.getDocumentElement().getElementsByTagName("options").item(0);
            if (elementOptions != null) {
                changed = setConfigElement(elementOptions, "listenAddress", new String[]{
                                "tcp://:22001",
                                "dynamic+https://relays.syncthing.net/endpoint"
                        }
                ) || changed;
            }
        }

        // Enable autodiscovery and NAT traversal by default
        Element elementOptions = (Element) mConfig.getDocumentElement().getElementsByTagName("options").item(0);
        if (elementOptions != null) {
            changed = setConfigElement(elementOptions, "localAnnounceEnabled", true) || changed;
            changed = setConfigElement(elementOptions, "globalAnnounceEnabled", true) || changed;
            changed = setConfigElement(elementOptions, "natEnabled", true) || changed;
            changed = setConfigElement(elementOptions, "relaysEnabled", true) || changed;
            changed = setConfigElement(elementOptions, "urAccepted", "-1") || changed; // Opt-out of usage reporting by default
        }

        // Save changes if we made any.
        if (changed) {
            saveChanges();
        }
    }

    private String getLocalDeviceIDfromPref() {
        String localDeviceID = PreferenceManager.getDefaultSharedPreferences(mContext).getString(Constants.PREF_LOCAL_DEVICE_ID, "");
        if (TextUtils.isEmpty(localDeviceID)) {
            Log.d(TAG, "getLocalDeviceIDfromPref: Local device ID unavailable, trying to retrieve it from syncthing ...");
            try {
                localDeviceID = getLocalDeviceIDandStoreToPref();
            } catch (SyncthingRunnable.ExecutableNotFoundException e) {
                Log.e(TAG, "getLocalDeviceIDfromPref: getLocalDeviceIDandStoreToPref failed", e);
            }
        }
        return localDeviceID;
    }

    /**
     * Reads the local device ID from the syncthing binary, and stores it in SharedPreferences.
     */
    private String getLocalDeviceIDandStoreToPref() throws SyncthingRunnable.ExecutableNotFoundException {
        String output = new SyncthingRunnable(mContext, SyncthingRunnable.Command.deviceid).run(true);
        if (output == null) {
            return null;
        }

        String deviceID = output.trim();
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_LOCAL_DEVICE_ID, deviceID)
                .apply();
        return deviceID;
    }

    /**
     * Returns the API key.
     */
    public String getApiKey() {
        NodeList nodeGui = mConfig.getDocumentElement().getElementsByTagName("gui");
        if (nodeGui.getLength() > 0) {
            NodeList nodeApiKey = ((Element) nodeGui.item(0)).getElementsByTagName("apikey");
            if (nodeApiKey.getLength() > 0) {
                return nodeApiKey.item(0).getTextContent();
            }
        }
        return null;
    }

    /**
     * Returns the web GUI URL.
     */
    public String getWebGuiUrl() {
        NodeList nodeGui = mConfig.getDocumentElement().getElementsByTagName("gui");
        if (nodeGui.getLength() > 0) {
            Element guiElement = (Element) nodeGui.item(0);
            NodeList nodeAddress = guiElement.getElementsByTagName("address");
            if (nodeAddress.getLength() > 0) {
                String address = nodeAddress.item(0).getTextContent();
                boolean useTls = Boolean.parseBoolean(guiElement.getAttribute("tls"));
                String protocol = useTls ? "https://" : "http://";
                address = address.replace("https://", "");
                address = address.replace("http://", "");
                return protocol + address;
            }
        }
        return null;
    }

    /**
     * Returns a list of all folders in the config.
     */
    public List<Folder> getFolders() {
        List<Folder> folders = new ArrayList<>();
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            Folder folder = new Folder();
            folder.id = getAttributeOrDefault(r, "id", "");
            folder.label = getAttributeOrDefault(r, "label", "");
            folder.path = getAttributeOrDefault(r, "path", "");
            folder.type = getAttributeOrDefault(r, "type", Constants.FOLDER_TYPE_SEND_RECEIVE);
            folder.autoNormalize = Boolean.parseBoolean(getAttributeOrDefault(r, "autoNormalize", "true"));
            folder.fsWatcherDelayS = Float.parseFloat(getAttributeOrDefault(r, "fsWatcherDelayS", "10"));
            folder.fsWatcherEnabled = Boolean.parseBoolean(getAttributeOrDefault(r, "fsWatcherEnabled", "true"));
            folder.ignorePerms = Boolean.parseBoolean(getAttributeOrDefault(r, "ignorePerms", "true"));
            folder.rescanIntervalS = Integer.parseInt(getAttributeOrDefault(r, "rescanIntervalS", "3600"));

            folder.copiers = Integer.parseInt(getConfigElementValue(r, "copiers", "0"));
            folder.hashers = Integer.parseInt(getConfigElementValue(r, "hashers", "0"));
            folder.order = getConfigElementValue(r, "order", "random");
            folder.paused = Boolean.parseBoolean(getConfigElementValue(r, "paused", "false"));
            folder.ignoreDelete = Boolean.parseBoolean(getConfigElementValue(r, "ignoreDelete", "false"));
            folder.copyOwnershipFromParent = Boolean.parseBoolean(getConfigElementValue(r, "copyOwnershipFromParent", "false"));
            folder.modTimeWindowS = Integer.parseInt(getConfigElementValue(r, "modTimeWindowS", "0"));
            folder.blockPullOrder = getConfigElementValue(r, "blockPullOrder", "standard");
            folder.disableFsync = Boolean.parseBoolean(getConfigElementValue(r, "disableFsync", "false"));
            folder.maxConcurrentWrites = Integer.parseInt(getConfigElementValue(r, "maxConcurrentWrites", "2"));
            folder.maxConflicts = Integer.parseInt(getConfigElementValue(r, "maxConflicts", "10"));
            folder.copyRangeMethod = getConfigElementValue(r, "copyRangeMethod", "standard");
            folder.caseSensitiveFS = Boolean.parseBoolean(getConfigElementValue(r, "caseSensitiveFS", "false"));

            // Get shared devices.
            NodeList nodeDevices = r.getElementsByTagName("device");
            for (int j = 0; j < nodeDevices.getLength(); j++) {
                Element deviceElement = (Element) nodeDevices.item(j);
                SharedWithDevice swd = new SharedWithDevice();
                swd.deviceID = deviceElement.getAttribute("id");
                folder.addDevice(swd);
            }

            folders.add(folder);
        }
        return folders;
    }

    /**
     * Adds a new folder to the config.
     */
    public void addFolder(Folder folder) {
        Element root = mConfig.getDocumentElement();
        Element folderElement = mConfig.createElement("folder");
        root.appendChild(folderElement);
        folderElement.setAttribute("id", folder.id);
        updateFolder(folder);
    }

    /**
     * Updates an existing folder in the config.
     */
    public void updateFolder(final Folder folder) {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (folder.id.equals(getAttributeOrDefault(r, "id", ""))) {
                // Found folder node to update.
                r.setAttribute("group", folder.group);
                r.setAttribute("label", folder.label);
                r.setAttribute("path", folder.path);
                r.setAttribute("type", folder.type);
                r.setAttribute("autoNormalize", Boolean.toString(folder.autoNormalize));
                r.setAttribute("fsWatcherDelayS", Float.toString(folder.fsWatcherDelayS));
                r.setAttribute("fsWatcherEnabled", Boolean.toString(folder.fsWatcherEnabled));
                r.setAttribute("ignorePerms", Boolean.toString(folder.ignorePerms));
                r.setAttribute("rescanIntervalS", Integer.toString(folder.rescanIntervalS));

                setConfigElement(r, "copiers", Integer.toString(folder.copiers));
                setConfigElement(r, "hashers", Integer.toString(folder.hashers));
                setConfigElement(r, "order", folder.order);
                setConfigElement(r, "paused", folder.paused);
                setConfigElement(r, "ignoreDelete", folder.ignoreDelete);
                setConfigElement(r, "copyOwnershipFromParent", folder.copyOwnershipFromParent);
                setConfigElement(r, "modTimeWindowS", Integer.toString(folder.modTimeWindowS));
                setConfigElement(r, "blockPullOrder", folder.blockPullOrder);
                setConfigElement(r, "disableFsync", folder.disableFsync);
                setConfigElement(r, "maxConcurrentWrites", Integer.toString(folder.maxConcurrentWrites));
                setConfigElement(r, "maxConflicts", Integer.toString(folder.maxConflicts));
                setConfigElement(r, "copyRangeMethod", folder.copyRangeMethod);
                setConfigElement(r, "caseSensitiveFS", folder.caseSensitiveFS);

                // Update shared devices.
                NodeList existingDevices = r.getElementsByTagName("device");
                List<Node> toRemove = new ArrayList<>();
                for (int j = 0; j < existingDevices.getLength(); j++) {
                    toRemove.add(existingDevices.item(j));
                }
                for (Node node : toRemove) {
                    r.removeChild(node);
                }
                for (SharedWithDevice swd : folder.getSharedWithDevices()) {
                    Element deviceElement = mConfig.createElement("device");
                    deviceElement.setAttribute("id", swd.deviceID);
                    r.appendChild(deviceElement);
                }
                break;
            }
        }
    }

    /**
     * Removes a folder from the config.
     */
    public void removeFolder(String folderID) {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (folderID.equals(getAttributeOrDefault(r, "id", ""))) {
                removeChildElementFromTextNode((Element) r.getParentNode(), r);
                break;
            }
        }
    }

    /**
     * Returns a list of all devices in the config.
     */
    public List<Device> getDevices() {
        List<Device> devices = new ArrayList<>();
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                Device device = new Device();
                device.deviceID = getAttributeOrDefault(r, "id", "");
                device.name = getAttributeOrDefault(r, "name", "");
                device.compression = getAttributeOrDefault(r, "compression", "metadata");
                device.introducer = Boolean.parseBoolean(getAttributeOrDefault(r, "introducer", "false"));
                device.introducedBy = getAttributeOrDefault(r, "introducedBy", "");
                device.paused = Boolean.parseBoolean(getConfigElementValue(r, "paused", "false"));
                device.autoAcceptFolders = Boolean.parseBoolean(getConfigElementValue(r, "autoAcceptFolders", "false"));
                device.maxSendKbps = Integer.parseInt(getConfigElementValue(r, "maxSendKbps", "0"));
                device.maxRecvKbps = Integer.parseInt(getConfigElementValue(r, "maxRecvKbps", "0"));
                device.untrusted = Boolean.parseBoolean(getConfigElementValue(r, "untrusted", "false"));
                device.numConnections = Integer.parseInt(getConfigElementValue(r, "numConnections", "0"));

                // Get addresses.
                NodeList nodeAddresses = r.getElementsByTagName("address");
                List<String> addresses = new ArrayList<>();
                for (int j = 0; j < nodeAddresses.getLength(); j++) {
                    addresses.add(nodeAddresses.item(j).getTextContent());
                }
                device.addresses = addresses;

                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Adds or updates a device identified by its device ID.
     */
    public void updateDevice(final Device device) {
        NodeList childNodes;
        boolean deviceExists = false;

        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (device.deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    deviceExists = true;
                    break;
                }
            }
        }

        // If the device does not exist in config, add it.
        if (!deviceExists) {
            Log.d(TAG, "updateDevice: [addDevice] Adding deviceID='" + device.deviceID + "' to config ...");
            Node nodeConfig = mConfig.getDocumentElement();
            Node nodeDevice = mConfig.createElement("device");
            nodeConfig.appendChild(nodeDevice);
            Element elementDevice = (Element) nodeDevice;
            elementDevice.setAttribute("id", device.deviceID);
        }

        // Now update all attributes and elements of the device node.
        childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (device.deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    r.setAttribute("name", device.name);
                    r.setAttribute("compression", device.compression);
                    r.setAttribute("introducer", Boolean.toString(device.introducer));
                    r.setAttribute("introducedBy", device.introducedBy);

                    setConfigElement(r, "paused", device.paused);
                    setConfigElement(r, "autoAcceptFolders", device.autoAcceptFolders);
                    setConfigElement(r, "maxSendKbps", Integer.toString(device.maxSendKbps));
                    setConfigElement(r, "maxRecvKbps", Integer.toString(device.maxRecvKbps));
                    setConfigElement(r, "untrusted", device.untrusted);
                    setConfigElement(r, "numConnections", Integer.toString(device.numConnections));

                    // Update addresses.
                    setConfigElement(r, "address", device.addresses.toArray(new String[0]));
                    break;
                }
            }
        }
    }

    /**
     * Removes a device from the config.
     */
    public void removeDevice(String deviceID) {
        // Prevent enumerating "<device>" tags below "<folder>" nodes by enumerating child nodes manually.
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (deviceID.equals(getAttributeOrDefault(r, "id", ""))) {
                    // Found device to remove.
                    Log.d(TAG, "removeDevice: Removing device node, deviceID=" + deviceID);
                    removeChildElementFromTextNode((Element) r.getParentNode(), r);
                    break;
                }
            }
        }
    }

    /**
     * Returns the options in the config.
     */
    public Options getOptions() {
        Options options = new Options();
        NodeList nodeOptions = mConfig.getDocumentElement().getElementsByTagName("options");
        if (nodeOptions.getLength() > 0) {
            Element r = (Element) nodeOptions.item(0);
            options.localAnnounceEnabled = Boolean.parseBoolean(getConfigElementValue(r, "localAnnounceEnabled", "true"));
            options.globalAnnounceEnabled = Boolean.parseBoolean(getConfigElementValue(r, "globalAnnounceEnabled", "true"));
            options.natEnabled = Boolean.parseBoolean(getConfigElementValue(r, "natEnabled", "true"));
            options.relaysEnabled = Boolean.parseBoolean(getConfigElementValue(r, "relaysEnabled", "true"));
            options.urAccepted = Integer.parseInt(getConfigElementValue(r, "urAccepted", "0"));
        }
        return options;
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, java.lang.reflect.Type type) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    public Gui getGui() {
        NodeList nodeGui = mConfig.getDocumentElement().getElementsByTagName("gui");
        if (nodeGui.getLength() > 0) {
            Element r = (Element) nodeGui.item(0);
            Gui gui = new Gui();
            gui.enabled = Boolean.parseBoolean(getAttributeOrDefault(r, "enabled", "true"));
            gui.useTLS = Boolean.parseBoolean(getAttributeOrDefault(r, "tls", "false"));
            gui.address = getConfigElementValue(r, "address", "127.0.0.1:8384");
            gui.user = getConfigElementValue(r, "user", "");
            gui.password = getConfigElementValue(r, "password", "");
            gui.apiKey = getConfigElementValue(r, "apikey", "");
            gui.theme = getConfigElementValue(r, "theme", "default");
            gui.insecureAdminAccess = Boolean.parseBoolean(getConfigElementValue(r, "insecureAdminAccess", "false"));
            gui.insecureAllowFrameLoading = Boolean.parseBoolean(getConfigElementValue(r, "insecureAllowFrameLoading", "false"));
            gui.insecureSkipHostCheck = Boolean.parseBoolean(getConfigElementValue(r, "insecureSkipHostCheck", "false"));
            return gui;
        }
        return null;
    }

    public void updateGui(Gui gui) {
        NodeList nodeGui = mConfig.getDocumentElement().getElementsByTagName("gui");
        if (nodeGui.getLength() > 0) {
            Element r = (Element) nodeGui.item(0);
            r.setAttribute("enabled", Boolean.toString(gui.enabled));
            r.setAttribute("tls", Boolean.toString(gui.useTLS));
            setConfigElement(r, "address", gui.address);
            setConfigElement(r, "user", gui.user);
            setConfigElement(r, "password", gui.password);
            setConfigElement(r, "apikey", gui.apiKey);
            setConfigElement(r, "theme", gui.theme);
            setConfigElement(r, "insecureAdminAccess", gui.insecureAdminAccess);
            setConfigElement(r, "insecureAllowFrameLoading", gui.insecureAllowFrameLoading);
            setConfigElement(r, "insecureSkipHostCheck", gui.insecureSkipHostCheck);
        }
    }

    public Integer getWebGuiBindPort() {
        Gui gui = getGui();
        if (gui == null || gui.address == null) return Constants.DEFAULT_WEBGUI_TCP_PORT;
        String[] parts = gui.address.split(":");
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return Constants.DEFAULT_WEBGUI_TCP_PORT;
        }
    }

    public void setFolderPause(String folderId, boolean paused) {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (folderId.equals(getAttributeOrDefault(r, "id", ""))) {
                setConfigElement(r, "paused", paused);
                break;
            }
        }
    }

    public void setDevicePause(String deviceId, boolean paused) {
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                Element r = (Element) node;
                if (deviceId.equals(getAttributeOrDefault(r, "id", ""))) {
                    setConfigElement(r, "paused", paused);
                    break;
                }
            }
        }
    }

    public void getFolderIgnoreList(Folder folder, ConfigRouter.OnResultListener1<FolderIgnoreList> listener) {
        // XML config doesn't store ignore list, it's in .stignore files.
        // For simplicity, return empty if not running.
        listener.onResult(new FolderIgnoreList());
    }

    public void postFolderIgnoreList(Folder folder, String[] ignore) {
        // Same as above.
    }

    public java.util.List<Device> getDevices(Boolean includeLocal) {
        return getDevices(); // Already filters for non-nested device tags.
    }

    private void parseConfig() throws OpenConfigException {
        try {
            FileInputStream fileInputStream = new FileInputStream(mConfigFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
            InputSource inputSource = new InputSource(inputStreamReader);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            mConfig = builder.parse(inputSource);
            fileInputStream.close();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.w(TAG, "Failed to parse config file", e);
            throw new OpenConfigException();
        }
    }

    private String getAttributeOrDefault(Element element, String attributeName, String defaultValue) {
        String value = element.getAttribute(attributeName);
        return (TextUtils.isEmpty(value)) ? defaultValue : value;
    }

    private String getConfigElementValue(Element parent, String tagName, String defaultValue) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return defaultValue;
    }

    private boolean setConfigElement(Element parent, String tagName, Boolean newValue) {
        return setConfigElement(parent, tagName, Boolean.toString(newValue));
    }

    private boolean setConfigElement(Element parent, String tagName, String textContent) {
        Node element = parent.getElementsByTagName(tagName).item(0);
        if (element == null) {
            element = mConfig.createElement(tagName);
            parent.appendChild(element);
        }
        if (!textContent.equals(element.getTextContent())) {
            element.setTextContent(textContent);
            return true;
        }
        return false;
    }

    private boolean setConfigElement(Element parent, String tagName, String[] textArray) {
        NodeList existingNodes = parent.getElementsByTagName(tagName);
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < existingNodes.getLength(); i++) {
            Node node = existingNodes.item(i);
            if (node.getParentNode() == parent) {
                toRemove.add(node);
            }
        }
        for (Node node : toRemove) {
            parent.removeChild(node);
        }
        for (String text : textArray) {
            Element newElement = mConfig.createElement(tagName);
            newElement.setTextContent(text);
            parent.appendChild(newElement);
        }
        return (!toRemove.isEmpty() || textArray.length > 0);
    }

    private Element getGuiElement() {
        return (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
    }

    /**
     * Ensures the LinkThing mandatory folder exists in configuration.
     */
    private boolean ensureLinkThingFolder() {
        NodeList nodeFolders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < nodeFolders.getLength(); i++) {
            Element r = (Element) nodeFolders.item(i);
            if (Constants.LINKTHING_FOLDER_ID.equals(getAttributeOrDefault(r, "id", ""))) {
                return false;
            }
        }

        File linkThingDir = new File(Environment.getExternalStorageDirectory(), Constants.LINKTHING_DIR_NAME);
        if (!linkThingDir.exists()) {
            linkThingDir.mkdirs();
        }
        
        Folder folder = new Folder();
        folder.id = Constants.LINKTHING_FOLDER_ID;
        folder.label = "LinkThing";
        folder.path = linkThingDir.getAbsolutePath();
        folder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
        folder.fsWatcherEnabled = true;
        folder.fsWatcherDelayS = 1.0f;

        addFolder(folder);
        return true;
    }

    /**
     * Set device model name as device name for Syncthing.
     * We need to iterate through XML nodes manually, as mConfig.getDocumentElement() will also
     * return nested elements inside folder element. We have to check that we only rename the
     * device corresponding to the local device ID.
     * Returns if changes to the config have been made.
     */
    private boolean changeLocalDeviceName(String localDeviceID) {
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                if (((Element) node).getAttribute("id").equals(localDeviceID)) {
                    Log.i(TAG, "changeLocalDeviceName: Rename device ID " + localDeviceID + " to " + Build.MODEL);
                    ((Element) node).setAttribute("name", Build.MODEL);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Writes updated mConfig back to file.
     */
    public void saveChanges() {
        if (!mConfigFile.canWrite()) {
            Log.w(TAG, "Failed to save updated config. Cannot change the owner of the config file.");
            return;
        }

        Log.i(TAG, "Saving config file");
        File mConfigTempFile = Constants.getConfigTempFile(mContext);
        try {
            // Write XML header.
            FileOutputStream fileOutputStream = new FileOutputStream(mConfigTempFile);
            fileOutputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8));

            // Prepare Object-to-XML transform.
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-16");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            // Output XML body.
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            StreamResult streamResult = new StreamResult(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8));
            transformer.transform(new DOMSource(mConfig), streamResult);
            byte[] outputBytes = byteArrayOutputStream.toByteArray();
            fileOutputStream.write(outputBytes);
            fileOutputStream.close();
        } catch (TransformerException e) {
            Log.w(TAG, "Failed to transform object to xml and save temporary config file", e);
            return;
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Failed to save temporary config file, FileNotFoundException", e);
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Failed to save temporary config file, UnsupportedEncodingException", e);
        } catch (IOException e) {
            Log.w(TAG, "Failed to save temporary config file, IOException", e);
        }
        try {
            mConfigTempFile.renameTo(mConfigFile);
        } catch (Exception e) {
            Log.w(TAG, "Failed to rename temporary config file to original file");
        }
    }

    /**
     * If an indented child element is removed, whitespace and line break will be left by
     * Element.removeChild().
     * See https://stackoverflow.com/questions/14255064/removechild-how-to-remove-indent-too
     */
    private void removeChildElementFromTextNode(Element parentElement, Element childElement) {
        Node prev = childElement.getPreviousSibling();
        if (prev != null &&
                prev.getNodeType() == Node.TEXT_NODE &&
                prev.getNodeValue().trim().length() == 0) {
            parentElement.removeChild(prev);
        }
        parentElement.removeChild(childElement);
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
