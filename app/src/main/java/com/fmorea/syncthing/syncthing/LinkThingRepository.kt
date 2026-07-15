package com.fmorea.syncthing.syncthing

import android.content.Context
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import com.fmorea.syncthing.service.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Ultra-optimized Repository for Global Network.
 * Handles high message volume with minimal I/O and CPU usage.
 */
class LinkThingRepository(
    private val context: Context,
    private val getLocalDeviceId: () -> String
) {
    private val TAG = "LinkThingRepo"
    val rootDir = File(context.filesDir, Constants.LINKTHING_DIR_NAME)
    
    private var currentLimit = 50 
    private val PAGE_SIZE = 50

    private val _messages = MutableStateFlow<List<LinkThingMessage>>(emptyList())
    val messages: StateFlow<List<LinkThingMessage>> = _messages

    private val _profilesVersion = MutableStateFlow(0)
    val profilesVersion: StateFlow<Int> = _profilesVersion

    private val _beaconDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconDeviceIds: StateFlow<Set<String>> = _beaconDeviceIds

    // Map of DeviceID -> IntroducerID (parsed from .net files)
    private val _meshTopology = MutableStateFlow<Map<String, String>>(emptyMap())
    val meshTopology: StateFlow<Map<String, String>> = _meshTopology

    private val _meshEdges = MutableStateFlow<Set<Triple<String, String, String>>>(emptySet())
    val meshEdges: StateFlow<Set<Triple<String, String, String>>> = _meshEdges

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _bannedDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val bannedDeviceIds: StateFlow<Set<String>> = _bannedDeviceIds
    
    val isLocalUserBanned: StateFlow<Boolean> = _bannedDeviceIds.map { banned ->
        banned.contains(getLocalDeviceId())
    }.stateIn(scope, SharingStarted.Eagerly, false)
    private val messageCache = ConcurrentHashMap<String, LinkThingMessage>()
    private val fileTimestampCache = ConcurrentHashMap<String, Long>()

    // Map of messageId (timestamp_deviceId) -> Map of receiverId to ackTimestamp
    private val ackCache = ConcurrentHashMap<String, MutableMap<String, Long>>()

    private var lastTimestamp = 0L
    @Synchronized
    private fun getUniqueTimestamp(): Long {
        var now = System.currentTimeMillis()
        if (now <= lastTimestamp) {
            now = lastTimestamp + 1
        }
        lastTimestamp = now
        return now
    }

    private var refreshJob: Job? = null
    private val refreshDelayMs = 500L // Increased debounce to avoid thrashing during sync

    private val observers = listOf(
        object : FileObserver(rootDir.absolutePath, CREATE or MODIFY or MOVED_TO or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || path.startsWith(".") || path.contains(".syncthing.")) return
                if (path.endsWith(".ack")) {
                    triggerRefresh()
                    return
                }
                if (path.endsWith(".net")) {
                    refreshBeacons()
                    return
                }
                if (path.endsWith(".ban")) {
                    refreshBans()
                    triggerRefresh()
                    return
                }
                if (path.endsWith(".INFO") || isImagePath(path)) {
                    _profilesVersion.value++
                }
                triggerRefresh()
            }
        }
    )

    private fun isImagePath(path: String): Boolean {
        val ext = path.substringAfterLast(".", "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp")
    }

    init {
        ensureDir()
        observers.forEach { it.startWatching() }
        refreshBans()
        triggerRefresh()
        refreshBeacons()
    }

    private fun ensureDir() {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    fun refresh() {
        ensureDir()
        observers.forEach { it.stopWatching(); it.startWatching() }
        refreshBans()
        triggerRefresh()
        refreshBeacons()
    }

    fun loadMore() {
        currentLimit += PAGE_SIZE
        triggerRefresh()
    }

    private fun triggerRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(refreshDelayMs)
            refreshMessagesInternal()
        }
    }

    private suspend fun refreshMessagesInternal() {
        withContext(Dispatchers.IO) {
            val allFiles = rootDir.listFiles() ?: return@withContext
            val banned = _bannedDeviceIds.value
            
            // Fast scan for ACKs first
            val ackFiles = rootDir.listFiles { _, name -> name.endsWith(".ack") } ?: emptyArray()
            ackCache.clear()
            ackFiles.forEach { file ->
                val parts = file.name.removeSuffix(".ack").split("_")
                if (parts.size >= 3) {
                    val msgKey = "${parts[0]}_${parts[1]}"
                    val receiverId = parts[2]
                    val ackTime = file.lastModified()
                    ackCache.getOrPut(msgKey) { ConcurrentHashMap<String, Long>() }[receiverId] = ackTime
                }
            }

            // Filter as efficiently as possible
            val validFiles = allFiles.filter { file ->
                val name = file.name
                val isBanned = banned.any { name.contains(it) }
                !isBanned && file.isFile && !name.startsWith(".") && !name.contains(".syncthing.") && 
                (name.endsWith(".msg") || name.endsWith(".chess") || name.contains("_")) &&
                !name.endsWith(".ack") && !name.endsWith(".INFO") && !name.endsWith(".net") && !name.endsWith(".ban")
            }
            
            if (validFiles.isEmpty()) {
                _messages.value = emptyList()
                return@withContext
            }

            // Fast sort by name (unix timestamp is at start)
            val newestBatch = validFiles.sortedByDescending { it.name }.take(currentLimit)
            
            val deviceId = getLocalDeviceId()
            val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

            // Incremental parsing with double cache
            val updatedMessages = newestBatch.map { file ->
                val fileName = file.name
                val lastMod = file.lastModified()
                val cached = messageCache[fileName]
                
                val msg = if (cached != null && fileTimestampCache[fileName] == lastMod) {
                    cached
                } else {
                    LinkThingMessage.fromFile(file, deviceId)?.also {
                        messageCache[fileName] = it
                        fileTimestampCache[fileName] = lastMod
                    }
                }
                
                // Attach ACKs
                msg?.let {
                    val acks = ackCache[it.msgId] ?: emptyMap<String, Long>()
                    if (it.acknowledgments != acks) it.copy(acknowledgments = acks) else it
                }
            }.filterNotNull()
            .sortedByDescending { it.timestamp } // Assicura ordine cronologico decrescente

            // Auto-send ACKs for new non-local messages
            updatedMessages.forEach { msg ->
                if (!msg.isLocal && !msg.acknowledgments.contains(deviceId)) {
                    sendAcknowledgment(msg)
                }
            }

            // Virtual Date Headers: aggiunti sul messaggio più vecchio di ogni giorno
            // per apparire correttamente in cima al gruppo nel LazyColumn invertito.
            var lastDate = ""
            val messagesWithHeaders = updatedMessages.asReversed().map { msg ->
                val dateStr = dateFormat.format(Date(msg.timestamp))
                val header = if (dateStr != lastDate) {
                    lastDate = dateStr
                    dateStr
                } else null
                
                if (msg.dateHeader != header) msg.copy(dateHeader = header) else msg
            }.asReversed()

            // Emit update to UI
            _messages.value = messagesWithHeaders
            
            // Background cleanup of cache
            if (messageCache.size > currentLimit * 2) {
                val currentNames = newestBatch.map { it.name }.toSet()
                messageCache.keys.retainAll(currentNames)
                fileTimestampCache.keys.retainAll(currentNames)
            }
        }
    }

    fun sendMessage(content: String, replyTo: LinkThingMessage? = null) {
        scope.launch {
            val id = getLocalDeviceId()
            if (id.isBlank()) return@launch
            val timestamp = getUniqueTimestamp()
            val fileName = if (replyTo != null) {
                "${timestamp}_${id}_${replyTo.timestamp}_${replyTo.deviceId}.msg"
            } else {
                "${timestamp}_${id}.msg"
            }
            val file = File(rootDir, fileName)
            try {
                file.writeText(content, Charsets.UTF_8)
                triggerRefresh()
            } catch (e: Exception) { Log.e(TAG, "Fail send", e) }
        }
    }

    fun sendAttachment(uri: android.net.Uri) {
        scope.launch {
            val id = getLocalDeviceId()
            if (id.isBlank()) return@launch
            val timestamp = getUniqueTimestamp()
            val originalName = getFileName(uri) ?: "file_${timestamp}"
            val file = File(rootDir, "${timestamp}_${id}_${originalName}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                triggerRefresh()
            } catch (e: Exception) { Log.e(TAG, "Fail attach", e) }
        }
    }

    fun saveAudioRecording(tempFile: File) {
        scope.launch {
            val id = getLocalDeviceId()
            if (id.isBlank()) return@launch
            val timestamp = getUniqueTimestamp()
            val destFile = File(rootDir, "${timestamp}_${id}_audio.m4a")
            try {
                tempFile.inputStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
                tempFile.delete()
                triggerRefresh()
            } catch (e: Exception) { Log.e(TAG, "Fail audio", e) }
        }
    }

    private fun getFileName(uri: android.net.Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) return it.getString(index)
            }
        }
        return null
    }

    fun refreshBans() {
        scope.launch {
            val banFiles = rootDir.listFiles { _, name -> name.endsWith(".ban") } ?: emptyArray()
            val bannedIds = banFiles.map { it.name.removeSuffix(".ban") }.toSet()
            _bannedDeviceIds.value = bannedIds
        }
    }

    fun refreshBeacons() {
        scope.launch {
            val allNetFiles = rootDir.listFiles()?.filter { it.isFile && it.name.endsWith(".net") } ?: emptyList()
            val banned = _bannedDeviceIds.value
            
            val foundIds = mutableSetOf<String>()
            val topology = mutableMapOf<String, String>()
            val edges = mutableSetOf<Triple<String, String, String>>()
            
            allNetFiles.forEach { file ->
                val netName = file.name.removeSuffix(".net")
                val nameParts = netName.split("_")
                if (nameParts.size >= 2) {
                    val nodeA = nameParts[0]
                    val nodeB = nameParts[1]
                    
                    if (!banned.contains(nodeA) && !banned.contains(nodeB)) {
                        foundIds.add(nodeA)
                        foundIds.add(nodeB)
                        edges.add(Triple(nodeA, nodeB, netName))
                        
                        try {
                            val content = file.readText().trim()
                            if (content.length > 10 && !banned.contains(content)) {
                                topology[nodeB] = content
                                edges.add(Triple(content, nodeB, "topology"))
                            } else if (!banned.contains(nodeA)) {
                                topology[nodeB] = nodeA 
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
            
            _beaconDeviceIds.value = foundIds
            _meshTopology.value = topology
            _meshEdges.value = edges
        }
    }

    fun updateBeacons(friends: List<com.fmorea.syncthing.model.Device>) {
        scope.launch {
            val myId = getLocalDeviceId()
            if (myId.isBlank()) return@launch
            friends.forEach { friend ->
                val file = File(rootDir, "${myId}_${friend.deviceID}.net")
                if (!file.exists()) {
                    try { 
                        // Write the introducer ID if known, otherwise our own
                        file.writeText(friend.introducedBy.ifBlank { myId })
                    } catch (e: Exception) {}
                }
            }
        }
    }

    fun deleteMessage(message: LinkThingMessage) {
        deleteMessages(listOf(message))
    }

    fun deleteMessages(messages: List<LinkThingMessage>) {
        scope.launch {
            messages.forEach { msg ->
                val file = File(rootDir, msg.fileName)
                if (file.exists()) {
                    file.delete()
                    messageCache.remove(msg.fileName)
                    fileTimestampCache.remove(msg.fileName)
                }
            }
            triggerRefresh()
        }
    }

    fun editMessage(message: LinkThingMessage, newContent: String) {
        if (message.isAttachment) return
        scope.launch {
            val file = File(rootDir, message.fileName)
            if (file.exists()) {
                file.writeText(newContent)
                triggerRefresh()
            }
        }
    }

    fun sendAcknowledgment(message: LinkThingMessage) {
        val myId = getLocalDeviceId()
        if (myId.isBlank() || message.deviceId == myId) return
        scope.launch {
            val ackFile = File(rootDir, "${message.timestamp}_${message.deviceId}_${myId}.ack")
            if (!ackFile.exists()) {
                try {
                    ackFile.writeText("OK")
                } catch (e: Exception) { Log.e(TAG, "Fail ack", e) }
            }
        }
    }

    fun stop() { observers.forEach { it.stopWatching() }; scope.cancel() }
}
