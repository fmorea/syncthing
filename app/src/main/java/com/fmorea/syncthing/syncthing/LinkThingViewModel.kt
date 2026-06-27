package com.fmorea.syncthing.syncthing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fmorea.syncthing.service.Constants
import com.fmorea.syncthing.service.SyncthingService
import kotlinx.coroutines.flow.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import androidx.preference.PreferenceManager
import com.fmorea.syncthing.util.ConfigRouter
import com.fmorea.syncthing.model.Device
import com.fmorea.syncthing.model.Folder
import com.fmorea.syncthing.service.RestApi
import android.util.Log
import java.io.File
import kotlinx.coroutines.*

class LinkThingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val configRouter = ConfigRouter(application)
    private var restApi: RestApi? = null
    private val prefsLocalDeviceId: String
        get() = prefs.getString(Constants.PREF_LOCAL_DEVICE_ID, "") ?: ""
    
    private val repository = LinkThingRepository(application) { prefsLocalDeviceId }
    val messages: StateFlow<List<LinkThingMessage>> = repository.messages
    val meshTopology: StateFlow<Map<String, String>> = repository.meshTopology

    private val _friends = MutableStateFlow<List<Device>>(emptyList())
    val friends: StateFlow<List<Device>> = _friends

    private val _userProfile = MutableStateFlow(UserProfile(prefsLocalDeviceId))
    val userProfile: StateFlow<UserProfile> = _userProfile

    private val _friendProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    val friendProfiles: StateFlow<Map<String, UserProfile>> = _friendProfiles

    private val _allProfiles = MutableStateFlow<Map<String, List<UserProfile>>>(emptyMap())
    val allProfiles: StateFlow<Map<String, List<UserProfile>>> = _allProfiles

    private val _localDevice = MutableStateFlow<Device?>(null)
    val localDevice: StateFlow<Device?> = _localDevice

    private val _syncthingDiscoveredIds = MutableStateFlow<Set<String>>(emptySet())
    private val toastedDiscoveryIds = mutableSetOf<String>()

    val discoveredDevices: StateFlow<Set<String>> = combine(
        repository.beaconDeviceIds,
        _friends,
        localDevice,
        _syncthingDiscoveredIds
    ) { beaconIds, currentFriends, local, stDiscoveredIds ->
        val friendIds = currentFriends.map { it.deviceID }.toSet()
        val myId = local?.deviceID ?: prefsLocalDeviceId
        (beaconIds + stDiscoveredIds).filter { it != myId && it !in friendIds }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _syncStatus = MutableStateFlow("Offline")
    val syncStatus: StateFlow<String> = _syncStatus

    private val _uiEvents = MutableLiveData<UiEvent?>(null)
    val uiEvents: LiveData<UiEvent?> = _uiEvents

    sealed class UiEvent {
        object ShowMyId : UiEvent()
        object ScanQrCode : UiEvent()
        object ManageFriends : UiEvent()
        object OpenWebGui : UiEvent()
        object OpenSettings : UiEvent()
        object OpenChess : UiEvent()
        object EditProfile : UiEvent()
        data class DeviceDiscovered(val deviceId: String) : UiEvent()
    }

    init {
        _userProfile.value = UserProfile.load(prefsLocalDeviceId, prefsLocalDeviceId, repository.rootDir)
        
        viewModelScope.launch {
            repository.profilesVersion.collect {
                refreshFriends()
            }
        }
    }

    fun showMyId() { _uiEvents.value = UiEvent.ShowMyId }
    fun scanQrCode() { _uiEvents.value = UiEvent.ScanQrCode }
    fun manageFriends() { _uiEvents.value = UiEvent.ManageFriends }
    fun openWebGui() { _uiEvents.value = UiEvent.OpenWebGui }
    fun openSettings() { _uiEvents.value = UiEvent.OpenSettings }
    fun openChess() { _uiEvents.value = UiEvent.OpenChess }
    fun editProfile() { _uiEvents.value = UiEvent.EditProfile }

    fun shareChessGame(): File? {
        val id = prefsLocalDeviceId
        if (id.isBlank()) return null
        val timestamp = System.currentTimeMillis()
        val file = File(repository.rootDir, "${timestamp}_${id}.chess")
        try {
            file.writeText("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", Charsets.UTF_8)
            repository.refresh()
            return file
        } catch (e: Exception) { 
            Log.e("LinkThingVM", "Fail share chess", e)
            return null
        }
    }

    fun refreshFriends() {
        viewModelScope.launch(Dispatchers.IO) {
            val api = restApi ?: return@launch
            if (api.isConfigLoaded) {
                api.getRemoteDeviceStatus("")
                val allDevices = api.getDevices(true)
                val others = allDevices.filter { it.deviceID != prefsLocalDeviceId }
                others.forEach { device ->
                    val conn = api.getRemoteDeviceStatus(device.deviceID)
                    device.numConnections = if (conn.connected) 1 else 0
                }
                _friends.value = others
                _localDevice.value = allDevices.find { it.deviceID == prefsLocalDeviceId }
                
                // Fetch Syncthing local discovery
                api.getDiscoveredDevices { result ->
                    if (result != null) {
                        val discoveredIds = result.keys
                        _syncthingDiscoveredIds.value = discoveredIds
                        
                        val friendIds = others.map { it.deviceID }.toSet()
                        discoveredIds.forEach { id ->
                            if (id != prefsLocalDeviceId && id !in friendIds && id !in toastedDiscoveryIds) {
                                toastedDiscoveryIds.add(id)
                                viewModelScope.launch(Dispatchers.Main) {
                                    _uiEvents.value = UiEvent.DeviceDiscovered(id)
                                }
                            }
                        }
                    }
                }

                // Load profiles with new grammar
                val profiles = others.associate { it.deviceID to UserProfile.load(it.deviceID, prefsLocalDeviceId, repository.rootDir) }
                _friendProfiles.value = profiles
                _userProfile.value = UserProfile.load(prefsLocalDeviceId, prefsLocalDeviceId, repository.rootDir)

                // Load all multiple identities
                val allIdentities = (others.map { it.deviceID } + prefsLocalDeviceId).associateWith { 
                    UserProfile.loadAll(it, repository.rootDir)
                }
                _allProfiles.value = allIdentities

                repository.refreshBeacons() // Update topology and beacon data
                
                var configChanged = false
                others.forEach { device ->
                    if (!device.autoAcceptFolders) {
                        device.autoAcceptFolders = true
                        api.updateDevice(device)
                        configChanged = true
                    }
                }
                val folder = api.getFolderByID(Constants.LINKTHING_FOLDER_ID)
                if (folder != null) {
                    others.forEach { device ->
                        if (folder.getDevice(device.deviceID) == null) {
                            folder.addDevice(device)
                            configChanged = true
                        }
                    }
                    if (configChanged) api.updateFolder(folder)
                }
                if (configChanged) api.sendConfig()
            }
        }
    }

    fun updateMyProfile(profile: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            // By default, assigning to myself
            UserProfile.save(profile, prefsLocalDeviceId, repository.rootDir)
            _userProfile.value = profile
            refreshFriends()
        }
    }

    fun updateFriendProfile(friendDeviceId: String, profile: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            // Disclosure from ME about THEM
            UserProfile.save(profile, prefsLocalDeviceId, repository.rootDir)
            refreshFriends()
        }
    }

    fun updateMyPhoto(photoFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val destFile = File(repository.rootDir, "${prefsLocalDeviceId}_${prefsLocalDeviceId}.jpg")
            photoFile.copyTo(destFile, overwrite = true)
            refreshFriends()
        }
    }

    fun updateFriendPhoto(friendDeviceId: String, photoFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val destFile = File(repository.rootDir, "${friendDeviceId}_${prefsLocalDeviceId}.jpg")
            photoFile.copyTo(destFile, overwrite = true)
            refreshFriends()
        }
    }

    fun deleteIdentity(deviceId: String, discloserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            UserProfile.delete(deviceId, discloserId, repository.rootDir)
            refreshFriends()
        }
    }

    fun getFriends(): List<Device> = _friends.value
    fun getLocalDeviceId(): String = prefsLocalDeviceId

    fun removeFriend(deviceId: String) {
        val api = restApi ?: return
        val folder = api.getFolderByID(Constants.LINKTHING_FOLDER_ID)
        if (folder != null) {
            folder.removeDevice(deviceId)
            configRouter.updateFolder(api, folder)
        }
        configRouter.removeDevice(api, deviceId)
        refreshFriends()
    }

    fun addFriend(deviceId: String) {
        val api = restApi ?: return
        val currentFriends = getFriends()
        val hasIntroducer = currentFriends.any { it.introducer }
        
        val device = Device()
        device.deviceID = deviceId
        device.name = "Amico (${deviceId.take(7)})"
        device.addresses = listOf("dynamic")
        device.autoAcceptFolders = true
        
        // Stabilize: Only one device should be the introducer in the mesh to avoid endless gossip loops
        device.introducer = !hasIntroducer

        configRouter.updateDevice(api, device)
        ensureFolderExists(api)
        refreshFriends()
    }

    fun clearUiEvent() { _uiEvents.value = null }

    private var lastApiInstance: RestApi? = null

    fun updateSyncStatus(state: SyncthingService.State, completion: Int, api: RestApi?) {
        this.restApi = api
        if (state == SyncthingService.State.ACTIVE && api != null && api.isConfigLoaded()) {
            if (lastApiInstance != api) {
                lastApiInstance = api
                viewModelScope.launch(Dispatchers.IO) {
                    refreshFriends()
                    ensureFolderExists(api)
                    repository.refresh()
                }
            }
        } else if (state != SyncthingService.State.ACTIVE) {
            lastApiInstance = null
        }
        _syncStatus.value = when (state) {
            SyncthingService.State.ACTIVE -> {
                if (completion < 100 && completion >= 0) "Sincronizzazione in corso ($completion%)"
                else "Attivo"
            }
            SyncthingService.State.STARTING -> "Avvio di Syncthing..."
            SyncthingService.State.INIT -> "Inizializzazione..."
            SyncthingService.State.ERROR -> "Errore di sistema"
            SyncthingService.State.DISABLED -> "Servizio Disattivato"
            else -> "Offline"
        }
    }

    private fun ensureFolderExists(api: RestApi) {
        val folder = api.getFolderByID(Constants.LINKTHING_FOLDER_ID)
        val currentFriends = getFriends()
        val rootDir = File(android.os.Environment.getExternalStorageDirectory(), Constants.LINKTHING_DIR_NAME)
        if (!rootDir.exists()) rootDir.mkdirs()
        val marker = File(rootDir, ".stfolder")
        if (!marker.exists()) try { marker.mkdirs() } catch (e: Exception) {}
        if (folder == null) {
            val newFolder = Folder()
            newFolder.id = Constants.LINKTHING_FOLDER_ID
            newFolder.label = "LinkThing"
            newFolder.path = rootDir.absolutePath
            newFolder.type = Constants.FOLDER_TYPE_SEND_RECEIVE
            newFolder.fsWatcherEnabled = true
            newFolder.fsWatcherDelayS = 1.0f
            newFolder.rescanIntervalS = 60
            currentFriends.forEach { newFolder.addDevice(it) }
            configRouter.addFolder(api, newFolder)
        } else {
            var changed = false
            currentFriends.forEach { friend ->
                if (folder.getDevice(friend.deviceID) == null) {
                    folder.addDevice(friend)
                    changed = true
                }
            }
            if (folder.rescanIntervalS != 60) { folder.rescanIntervalS = 60; changed = true }
            if (changed) configRouter.updateFolder(api, folder)
        }
    }

    fun sendMessage(content: String, replyTo: LinkThingMessage? = null) { 
        if (content.isNotBlank()) repository.sendMessage(content, replyTo) 
    }
    fun sendAttachment(uri: android.net.Uri) { repository.sendAttachment(uri) }
    fun sendAudio(file: File) { repository.saveAudioRecording(file) }
    fun deleteMessage(message: LinkThingMessage) { repository.deleteMessage(message) }
    fun deleteMessages(messages: List<LinkThingMessage>) { repository.deleteMessages(messages) }
    fun editMessage(message: LinkThingMessage, newContent: String) { if (newContent.isNotBlank()) repository.editMessage(message, newContent) }
    fun loadMoreMessages() { repository.loadMore() }
    fun forceSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val api = restApi ?: return@launch
            if (api.isConfigLoaded) { api.rescanAll(); refreshFriends(); repository.refresh() }
        }
    }
    override fun onCleared() { super.onCleared(); repository.stop() }
}
