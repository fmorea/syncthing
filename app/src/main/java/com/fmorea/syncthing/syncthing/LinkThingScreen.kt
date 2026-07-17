package com.fmorea.syncthing.syncthing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.fmorea.syncthing.R
import androidx.compose.ui.text.input.TextFieldValue
import android.content.Intent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.permissions.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.fmorea.syncthing.util.FileUtils
import com.fmorea.syncthing.service.Constants
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

enum class LinkThingTab {
    CHAT, FILE_DRIVE, NETWORK, NETWORK_GRAPH, APPLICATIONS, CALENDAR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LinkThingScreen(
    viewModel: LinkThingViewModel,
    scannedDeviceId: String = ""
) {
    val messages by viewModel.messages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    val isLocalUserBanned by viewModel.isLocalUserBanned.collectAsState()
    
    if (isLocalUserBanned) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.Gavel, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Text("SEI STATO BANNATO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Il tuo ID è stato inserito in una lista di ban da un amministratore. Non puoi più partecipare al network EtherMesh con questa identità.", textAlign = TextAlign.Center)
            }
        }
        return
    }

    val deviceNames = remember(friends, localDevice, userProfile, friendProfiles) {
        val map = friends.associate { it.deviceID to (friendProfiles[it.deviceID]?.getDisplayName() ?: it.getDisplayName()) }.toMutableMap()
        localDevice?.let { map[it.deviceID] = userProfile.getDisplayName() }
        map
    }

    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isSearching by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(LinkThingTab.CHAT) }
    var vaultTargetFile by remember { mutableStateOf<File?>(null) }
    var vaultTargetCategory by remember { mutableStateOf<String?>(null) }
    var resetVaultTrigger by remember { mutableStateOf(0) }
    var chatTargetMessageId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showConnectedDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendAttachment(it) }
    }

    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val recordPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    DisposableEffect(Unit) {
        onDispose {
            recorder?.apply {
                try { stop() } catch(e: Exception) {}
                release()
            }
        }
    }

    var selectedMessage by remember { mutableStateOf<LinkThingMessage?>(null) }
    var replyingTo by remember { mutableStateOf<LinkThingMessage?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editContent by remember { mutableStateOf("") }
    
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var editingProfileByDeviceId by remember { mutableStateOf<String?>(null) }
    var croppingImageFile by remember { mutableStateOf<File?>(null) }

    if (croppingImageFile != null) {
        ImageCropper(
            imageFile = croppingImageFile!!,
            onCropDone = { cropped ->
                if (editingProfileByDeviceId == localDevice?.deviceID) viewModel.updateMyPhoto(cropped)
                else viewModel.updateFriendPhoto(editingProfileByDeviceId!!, cropped)
                croppingImageFile = null
            },
            onDismiss = { croppingImageFile = null }
        )
    }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMessageInfo by remember { mutableStateOf<LinkThingMessage?>(null) }
    val isSelectionMode = selectedIds.isNotEmpty()

    if (editingProfileByDeviceId != null) {
        val profileToEdit = if (editingProfileByDeviceId == localDevice?.deviceID) userProfile 
                            else friendProfiles[editingProfileByDeviceId] ?: UserProfile(editingProfileByDeviceId!!)
        EditProfileDialog(
            profile = profileToEdit,
            isMe = editingProfileByDeviceId == localDevice?.deviceID,
            onDismiss = { editingProfileByDeviceId = null },
            onSave = { 
                if (editingProfileByDeviceId == localDevice?.deviceID) viewModel.updateMyProfile(it)
                else viewModel.updateFriendProfile(editingProfileByDeviceId!!, it)
            },
            onPhotoSelected = { 
                croppingImageFile = it
            }
        )
    }

    BackHandler(enabled = true) {
        if (selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        } else if (showMenu) {
            showMenu = false
        } else if (currentTab == LinkThingTab.NETWORK_GRAPH) {
            currentTab = LinkThingTab.NETWORK
        } else if (currentTab != LinkThingTab.CHAT) {
            currentTab = LinkThingTab.CHAT
        } else {
            focusManager.clearFocus()
        }
    }

    val isAtEnd by remember {
        derivedStateOf {
            val lastItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastItem != null && lastItem.index >= messages.size - 1
        }
    }

    LaunchedEffect(isAtEnd) {
        if (isAtEnd && messages.size >= 20) {
            viewModel.loadMoreMessages()
        }
    }

    LaunchedEffect(messages.size, chatTargetMessageId) {
        if (chatTargetMessageId != null) {
            val index = messages.indexOfFirst { it.uniqueId == chatTargetMessageId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                chatTargetMessageId = null
            } else {
                viewModel.loadMoreMessages()
            }
        } else if (messages.isNotEmpty()) {
            if (listState.firstVisibleItemIndex <= 1) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LaunchedEffect(syncStatus) {
        if (syncStatus == "Attivo") {
            while (true) {
                viewModel.refreshFriends()
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    LaunchedEffect(scannedDeviceId) {
        if (scannedDeviceId.isNotBlank()) {
            showAddFriendDialog = true
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            initialDeviceId = scannedDeviceId,
            onDismiss = { showAddFriendDialog = false },
            onAddFriend = { deviceId ->
                viewModel.addFriend(deviceId)
                showAddFriendDialog = false
            },
            onScanQrCode = {
                viewModel.scanQrCode()
            }
        )
    }

    if (showEditDialog && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Modifica messaggio") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = { editContent = "$editContent**TestoBold**" }) {
                            Icon(Icons.Default.FormatBold, "Bold")
                        }
                        IconButton(onClick = { editContent = "$editContent*TestoItalic*" }) {
                            Icon(Icons.Default.FormatItalic, "Italic")
                        }
                        IconButton(onClick = { editContent = "$editContent`codice`" }) {
                            Icon(Icons.Default.Code, "Codice")
                        }
                    }
                    TextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editMessage(selectedMessage!!, editContent)
                    showEditDialog = false
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Annulla") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Elimina messaggi") },
            text = { Text("Vuoi eliminare ${selectedIds.size} messaggi selezionati?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = messages.filter { it.uniqueId in selectedIds }
                        viewModel.deleteMessages(toDelete)
                        selectedIds = emptySet()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            }
        )
    }

    if (showMessageInfo != null) {
        AlertDialog(
            onDismissRequest = { showMessageInfo = null },
            title = { Text("Info Messaggio") },
            text = {
                Column {
                    Text("Visualizzato da:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showMessageInfo!!.acknowledgments.isEmpty()) {
                        Text("Nessuno ancora", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        val ackList = showMessageInfo!!.acknowledgments.toList().sortedBy { it.second }
                        val infoDateFormat = remember { SimpleDateFormat("HH:mm, dd/MM", Locale.getDefault()) }
                        ackList.forEach { (readerId, ackTime) ->
                            val readerName = deviceNames[readerId] ?: readerId.take(8)
                            val timeStr = infoDateFormat.format(Date(ackTime))
                            ListItem(
                                headlineContent = { Text(readerName) },
                                supportingContent = { Text("Visualizzato il $timeStr") },
                                leadingContent = { Avatar(deviceId = readerId, profile = friendProfiles[readerId]) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMessageInfo = null }) { Text("Chiudi") }
            }
        )
    }

    if (showConnectedDialog) {
        val connectedFriends = friends.filter { it.numConnections > 0 }
        AlertDialog(
            onDismissRequest = { showConnectedDialog = false },
            title = { Text("Utenti Online") },
            text = {
                Column {
                    if (connectedFriends.isEmpty()) {
                        Text("Nessun utente connesso al momento.", color = MaterialTheme.colorScheme.outline)
                    } else {
                        connectedFriends.forEach { friend ->
                            val name = friendProfiles[friend.deviceID]?.getDisplayName() ?: friend.getDisplayName()
                            ListItem(
                                headlineContent = { Text(name) },
                                leadingContent = { Avatar(deviceId = friend.deviceID, profile = friendProfiles[friend.deviceID]) },
                                supportingContent = { Text("Connesso • ${friend.deviceID.take(8)}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectedDialog = false }) { Text("Chiudi") }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selezionati") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annulla")
                        }
                    },
                    actions = {
                        if (selectedIds.size == 1) {
                            val selectedMsg = messages.find { it.uniqueId == selectedIds.first() }
                            if (selectedMsg != null) {
                                IconButton(onClick = { showMessageInfo = selectedMsg }) {
                                    Icon(Icons.Default.Info, contentDescription = "Info")
                                }
                                if (!selectedMsg.isAttachment && selectedMsg.isLocal) {
                                    IconButton(onClick = {
                                        selectedMessage = selectedMsg
                                        editContent = selectedMsg.content
                                        showEditDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Modifica")
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else if (isSearching && currentTab == LinkThingTab.CHAT) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, "Cancella")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearching = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                )
            } else if (currentTab != LinkThingTab.FILE_DRIVE) {
                TopAppBar(
                    title = {
                        Column {
                            val alias = localDevice?.name ?: "Io"
                            val title = when (currentTab) {
                                LinkThingTab.CHAT -> {
                                    val id = localDevice?.deviceID ?: ""
                                    val shortId = id.take(6)
                                    val endId = id.takeLast(6)
                                    "$alias ($shortId..$endId)"
                                }
                                LinkThingTab.NETWORK -> "Network"
                                LinkThingTab.NETWORK_GRAPH -> "Network Graph"
                                LinkThingTab.APPLICATIONS -> "Applicazioni"
                                else -> ""
                            }
                            
                            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiveConnectionBadge(friends = friends, onClick = { showConnectedDialog = true })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    syncStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        syncStatus == "Attivo" -> MaterialTheme.colorScheme.primary
                                        syncStatus.startsWith("Errore") -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentTab == LinkThingTab.CHAT) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Cerca in Chat")
                            }
                        }
                        if (currentTab == LinkThingTab.NETWORK) {
                            IconButton(onClick = { showAddFriendDialog = true }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Aggiungi Amico")
                            }
                            IconButton(onClick = { viewModel.showMyId() }) {
                                Icon(Icons.Default.QrCode, contentDescription = "Il Mio QR")
                            }
                            IconButton(onClick = { editingProfileByDeviceId = localDevice?.deviceID }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Modifica Profilo")
                            }
                        }
                        IconButton(onClick = { viewModel.forceSync() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronizza")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Il mio ID (QR)") },
                                onClick = {
                                    showMenu = false
                                    viewModel.showMyId()
                                },
                                leadingIcon = { Icon(Icons.Default.QrCode, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Impostazioni App") },
                                onClick = {
                                    showMenu = false
                                    viewModel.openSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Console Avanzata (Web)") },
                                onClick = {
                                    showMenu = false
                                    viewModel.openWebGui()
                                },
                                leadingIcon = { Icon(Icons.Default.Public, null) }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (currentTab == LinkThingTab.CHAT) {
                        if (replyingTo != null) {
                            val replierName = deviceNames[replyingTo!!.deviceId] ?: replyingTo!!.deviceId.take(8)
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.width(4.dp).height(40.dp).background(MaterialTheme.colorScheme.primary))
                                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                                        Text(replierName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text(replyingTo!!.content, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { replyingTo = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Annulla")
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRecording) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
                                Text("Registrazione in corso...", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                IconButton(onClick = {
                                    isRecording = false
                                    recorder?.apply { try { stop() } catch(e: Exception) {}; release() }
                                    recorder = null
                                    audioFile?.delete()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Annulla", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = {
                                    isRecording = false
                                    recorder?.apply { try { stop(); release() } catch (e: Exception) {} }
                                    recorder = null
                                    audioFile?.let { viewModel.sendAudio(it) }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia Audio", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    FormattingToolbar(textValue = inputText, onValueChange = { inputText = it }, modifier = Modifier.fillMaxWidth())
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                            Icon(Icons.Default.AttachFile, contentDescription = "Allega", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        TextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("Messaggio...") },
                                            maxLines = 4,
                                            shape = RoundedCornerShape(24.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                disabledIndicatorColor = Color.Transparent,
                                                errorIndicatorColor = Color.Transparent
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        if (inputText.text.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.sendMessage(inputText.text, replyingTo)
                                                    inputText = TextFieldValue("")
                                                    replyingTo = null
                                                    focusManager.clearFocus()
                                                },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia", tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    if (recordPermissionState.status.isGranted) {
                                                        try {
                                                            val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                                                            audioFile = file
                                                            val newRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                                            newRecorder.apply {
                                                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                                                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                                                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                                                setAudioSamplingRate(44100)
                                                                setAudioEncodingBitRate(128000)
                                                                setOutputFile(file.absolutePath)
                                                                prepare()
                                                                start()
                                                            }
                                                            recorder = newRecorder
                                                            isRecording = true
                                                        } catch (e: Exception) { isRecording = false; audioFile = null }
                                                    } else { recordPermissionState.launchPermissionRequest() }
                                                },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = "Registra", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, modifier = Modifier.height(64.dp)) {
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                            label = { Text("Chat") },
                            selected = currentTab == LinkThingTab.CHAT,
                            onClick = { currentTab = LinkThingTab.CHAT }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("Drive") },
                            selected = currentTab == LinkThingTab.FILE_DRIVE,
                            onClick = { 
                                if (currentTab == LinkThingTab.FILE_DRIVE) {
                                    resetVaultTrigger++
                                }
                                vaultTargetFile = null
                                vaultTargetCategory = null
                                currentTab = LinkThingTab.FILE_DRIVE 
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.People, contentDescription = null) },
                            label = { Text("Network") },
                            selected = currentTab == LinkThingTab.NETWORK || currentTab == LinkThingTab.NETWORK_GRAPH,
                            onClick = { 
                                currentTab = if (currentTab == LinkThingTab.NETWORK) LinkThingTab.NETWORK_GRAPH else LinkThingTab.NETWORK
                                viewModel.refreshFriends()
                            }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                            label = { Text("App") },
                            selected = currentTab == LinkThingTab.APPLICATIONS,
                            onClick = { currentTab = LinkThingTab.APPLICATIONS }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    val initialIndex = when (initialState) {
                        LinkThingTab.CHAT -> 0
                        LinkThingTab.FILE_DRIVE -> 1
                        LinkThingTab.NETWORK, LinkThingTab.NETWORK_GRAPH -> 2
                        LinkThingTab.APPLICATIONS -> 3
                        LinkThingTab.CALENDAR -> 4
                    }
                    val targetIndex = when (targetState) {
                        LinkThingTab.CHAT -> 0
                        LinkThingTab.FILE_DRIVE -> 1
                        LinkThingTab.NETWORK, LinkThingTab.NETWORK_GRAPH -> 2
                        LinkThingTab.APPLICATIONS -> 3
                        LinkThingTab.CALENDAR -> 4
                    }
                    
                    val animationSpec = tween<Float>(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 300, easing = androidx.compose.animation.core.FastOutSlowInEasing)

                    if (targetIndex > initialIndex) {
                        (slideInHorizontally(animationSpec = slideSpec) { width -> width } + fadeIn(animationSpec = animationSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> -width } + fadeOut(animationSpec = animationSpec))
                    } else if (targetIndex < initialIndex) {
                        (slideInHorizontally(animationSpec = slideSpec) { width -> -width } + fadeIn(animationSpec = animationSpec))
                            .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> width } + fadeOut(animationSpec = animationSpec))
                    } else {
                        fadeIn(animationSpec = animationSpec).togetherWith(fadeOut(animationSpec = animationSpec))
                    }
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    LinkThingTab.CHAT -> {
                        if (messages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Nessun messaggio", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                    Text("Inizia una conversazione con i tuoi amici", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                                }
                            }
                        }
                        LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(items = messages, key = { it.uniqueId }) { message ->
                                Column(modifier = Modifier.fillMaxWidth().animateItem()) {
                                    if (message.dateHeader != null) { key(message.dateHeader) { DateDivider(message.dateHeader!!) } }
                                    var showMessageMenu by remember { mutableStateOf(false) }
                                    val isSelected = selectedIds.contains(message.uniqueId)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent).padding(vertical = 2.dp, horizontal = 4.dp),
                                        horizontalArrangement = if (message.isLocal) Arrangement.End else Arrangement.Start,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        if (!message.isLocal) {
                                            Avatar(deviceId = message.deviceId, profile = friendProfiles[message.deviceId], onClick = { if (isSelectionMode) { selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId } else { editingProfileByDeviceId = message.deviceId } })
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Box(modifier = Modifier.weight(1f, fill = false).pointerInput(isSelectionMode, isSelected) {
                                            detectTapGestures(onTap = { if (isSelectionMode) { selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId } else { replyingTo = message } }, onLongPress = { if (!isSelectionMode) { showMessageMenu = true } else { selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId } })
                                        }) {
                                            val senderProfile = if (message.isLocal) userProfile else friendProfiles[message.deviceId]
                                            MessageBubble(
                                                message = message, 
                                                deviceNames = deviceNames, 
                                                profile = senderProfile, 
                                                allMessages = messages,
                                                onOpenCalendar = { currentTab = LinkThingTab.CALENDAR }
                                            )
                                            DropdownMenu(expanded = showMessageMenu, onDismissRequest = { showMessageMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Seleziona") },
                                                    onClick = { 
                                                        showMessageMenu = false
                                                        selectedIds = selectedIds + message.uniqueId 
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                                                )
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                if (message.file != null) {
                                                    DropdownMenuItem(text = { Text("Vedi nel Drive") }, onClick = { showMessageMenu = false; vaultTargetFile = message.file; currentTab = LinkThingTab.FILE_DRIVE }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                                                }
                                                if (message.isAttachment && message.file != null) {
                                                    val ext = message.file.extension.lowercase()
                                                    if (ext == "chess") {
                                                        DropdownMenuItem(text = { Text("Apri scacchi") }, onClick = { showMessageMenu = false; val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply { action = Intent.ACTION_VIEW; val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", message.file); data = uri; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(intent) }, leadingIcon = { Icon(Icons.Default.Extension, null) })
                                                    } else {
                                                        DropdownMenuItem(text = { Text("Apri file") }, onClick = { showMessageMenu = false; FileUtils.openFile(context, message.file.absolutePath) }, leadingIcon = { Icon(Icons.Default.FileOpen, null) })
                                                    }
                                                }
                                                DropdownMenuItem(text = { Text("Copia Testo") }, onClick = { showMessageMenu = false; clipboardManager.setText(AnnotatedString(message.content)); android.widget.Toast.makeText(context, "Copiato", android.widget.Toast.LENGTH_SHORT).show() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                                DropdownMenuItem(text = { Text("Condividi Esternamente") }, onClick = { showMessageMenu = false; val sendIntent = Intent().apply { action = Intent.ACTION_SEND; if (message.isAttachment && message.file != null) { val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", message.file); putExtra(Intent.EXTRA_STREAM, uri); type = context.contentResolver.getType(uri) ?: "*/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) } else { putExtra(Intent.EXTRA_TEXT, message.content); type = "text/plain" } }; context.startActivity(Intent.createChooser(sendIntent, null)) }, leadingIcon = { Icon(Icons.Default.Share, null) })
                                                if (!message.isAttachment) {
                                                    DropdownMenuItem(text = { Text("Modifica") }, onClick = { showMessageMenu = false; selectedMessage = message; editContent = message.content; showEditDialog = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                                }
                                                DropdownMenuItem(text = { Text("Info") }, onClick = { showMessageMenu = false; showMessageInfo = message }, leadingIcon = { Icon(Icons.Default.Info, null) })
                                                DropdownMenuItem(text = { Text("Elimina") }, onClick = { showMessageMenu = false; viewModel.deleteMessage(message) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error))
                                            }
                                        }
                                        if (message.isLocal) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Avatar(deviceId = message.deviceId, profile = userProfile, onClick = { if (isSelectionMode) { selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId } else { editingProfileByDeviceId = message.deviceId } })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    LinkThingTab.FILE_DRIVE -> FileVaultScreen(
                        viewModel = viewModel, 
                        initialFile = vaultTargetFile, 
                        initialCategory = vaultTargetCategory,
                        resetTrigger = resetVaultTrigger,
                        onShowInChat = { msg -> 
                            chatTargetMessageId = msg.uniqueId
                            currentTab = LinkThingTab.CHAT
                            vaultTargetFile = null
                            vaultTargetCategory = null 
                        }
                    )
                    LinkThingTab.NETWORK -> NetworkView(viewModel = viewModel, onEditMyProfile = { editingProfileByDeviceId = localDevice?.deviceID }, onEditFriendProfile = { editingProfileByDeviceId = it }, onShowGraph = { currentTab = LinkThingTab.NETWORK_GRAPH })
                    LinkThingTab.NETWORK_GRAPH -> NetworkGraphView(viewModel = viewModel, onNodeClick = { editingProfileByDeviceId = it })
                    LinkThingTab.APPLICATIONS -> {
                        val labelRubrica = stringResource(R.string.category_profiles)
                        val labelNetwork = stringResource(R.string.category_network)
                        ApplicationsTabContent(
                            viewModel = viewModel,
                            onPlayChess = {
                                val gameFile = viewModel.shareChessGame()
                                if (gameFile != null) {
                                    val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", gameFile)
                                        data = uri
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onOpenRubrica = {
                                vaultTargetCategory = labelRubrica
                                currentTab = LinkThingTab.FILE_DRIVE
                            },
                            onShowGraph = {
                                vaultTargetCategory = labelNetwork
                                currentTab = LinkThingTab.FILE_DRIVE
                            },
                            onOpenCalendar = {
                                currentTab = LinkThingTab.CALENDAR
                            }
                        )
                    }
                    LinkThingTab.CALENDAR -> {
                        CalendarView(viewModel = viewModel, onBack = { currentTab = LinkThingTab.APPLICATIONS })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarView(viewModel: LinkThingViewModel, onBack: () -> Unit) {
    var showAddEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var displayedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var viewMode by remember { mutableStateOf("MONTH") } // MONTH, WEEK, LIST
    
    val messagesState by viewModel.messages.collectAsState()
    val allEvents = remember(messagesState) { viewModel.getCalendarEvents() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val sdf = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
                    Text(
                        text = sdf.format(displayedDate.time).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewMode = when(viewMode) {
                            "MONTH" -> "WEEK"
                            "WEEK" -> "LIST"
                            else -> "MONTH"
                        }
                    }) {
                        Icon(
                            when(viewMode) {
                                "MONTH" -> Icons.Default.CalendarViewMonth
                                "WEEK" -> Icons.Default.CalendarViewWeek
                                else -> Icons.Default.ViewList
                            }, 
                            contentDescription = "Cambia vista"
                        )
                    }
                    IconButton(onClick = { 
                        val today = Calendar.getInstance()
                        selectedDate = today
                        displayedDate = today
                    }) {
                        Icon(Icons.Default.Today, "Oggi")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingEvent = null
                    showAddEventDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Nuovo Evento")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Calendar Navigation Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newDate = displayedDate.clone() as Calendar
                        if (viewMode == "MONTH") newDate.add(Calendar.MONTH, -1)
                        else newDate.add(Calendar.WEEK_OF_YEAR, -1)
                        displayedDate = newDate
                    }) { Icon(Icons.Default.ChevronLeft, "Precedente") }
                    
                    val currentLocale = Locale.getDefault()
                    val dayMonthSdf = remember(currentLocale) { SimpleDateFormat("d MMM", currentLocale) }

                    Text(
                        text = if (viewMode == "MONTH") {
                            val monthYearSdf = remember(currentLocale) { SimpleDateFormat("MMMM yyyy", currentLocale) }
                            monthYearSdf.format(displayedDate.time).replaceFirstChar { it.uppercase() }
                        } else {
                            val start = displayedDate.clone() as Calendar
                            start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                            val end = start.clone() as Calendar
                            end.add(Calendar.DAY_OF_WEEK, 6)
                            "${dayMonthSdf.format(start.time)} - ${dayMonthSdf.format(end.time)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(onClick = {
                        val newDate = displayedDate.clone() as Calendar
                        if (viewMode == "MONTH") newDate.add(Calendar.MONTH, 1)
                        else newDate.add(Calendar.WEEK_OF_YEAR, 1)
                        displayedDate = newDate
                    }) { Icon(Icons.Default.ChevronRight, "Successivo") }
                }
            }

            val currentLocale = Locale.getDefault()
            when (viewMode) {
                "MONTH" -> MonthGridView(
                    displayedMonth = displayedDate,
                    selectedDate = selectedDate,
                    events = allEvents,
                    onDateClick = { 
                        selectedDate = it
                        displayedDate = it
                        viewMode = "WEEK" 
                    }
                )
                "WEEK" -> WeekView(displayedDate, allEvents, viewModel, onEditEvent = { editingEvent = it; showAddEventDialog = true })
                "LIST" -> EventListView(allEvents, viewModel, onEditEvent = { editingEvent = it; showAddEventDialog = true })
            }
        }
    }

    if (showAddEventDialog) {
        AddEventDialog(
            initialDate = selectedDate.timeInMillis,
            onDismiss = { showAddEventDialog = false },
            onConfirm = { event ->
                viewModel.addCalendarEvent(event)
                showAddEventDialog = false
            },
            viewModel = viewModel,
            eventToEdit = editingEvent
        )
    }
}

@Composable
fun MonthGridView(
    displayedMonth: Calendar,
    selectedDate: Calendar,
    events: List<CalendarEvent>,
    onDateClick: (Calendar) -> Unit
) {
    val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfMonth = (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val startOffset = (firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - firstDayOfMonth.firstDayOfWeek + 7) % 7
    
    val totalCells = ((daysInMonth + startOffset + 6) / 7) * 7
    
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Day Names
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val dayNames = listOf("L", "M", "M", "G", "V", "S", "D")
            dayNames.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Grid
        var cellIndex = 0
        while (cellIndex < totalCells) {
            Row(modifier = Modifier.weight(1f)) {
                for (i in 0 until 7) {
                    val dayNum = cellIndex - startOffset + 1
                    val isDayInMonth = dayNum in 1..daysInMonth
                    
                    val cellDate = if (isDayInMonth) {
                        (displayedMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayNum) }
                    } else null

                    val isSelected = cellDate != null && 
                        cellDate.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                        cellDate.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR)

                    val isToday = cellDate != null && Calendar.getInstance().let {
                        it.get(Calendar.YEAR) == cellDate.get(Calendar.YEAR) &&
                        it.get(Calendar.DAY_OF_YEAR) == cellDate.get(Calendar.DAY_OF_YEAR)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isDayInMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable(enabled = isDayInMonth) { onDateClick(cellDate!!) },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (isDayInMonth) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                
                                // Event dots
                                val dayEvents = events.filter {
                                    val eventCal = Calendar.getInstance().apply { timeInMillis = it.date }
                                    eventCal.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR) &&
                                    eventCal.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH) &&
                                    eventCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                }
                                
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    dayEvents.take(3).forEach { event ->
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .padding(1.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    cellIndex++
                }
            }
        }
    }
}

@Composable
fun WeekView(currentWeek: Calendar, events: List<CalendarEvent>, viewModel: LinkThingViewModel, onEditEvent: (CalendarEvent) -> Unit) {
    val weekStart = currentWeek.clone() as Calendar
    weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
    
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
        val currentLocale = Locale.getDefault()
        val sdf = remember(currentLocale) { SimpleDateFormat("EEE d", currentLocale) }
        
        for (i in 0 until 7) {
            val day = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_WEEK, i) }
            val dayEvents = events.filter {
                val eventCal = Calendar.getInstance().apply { timeInMillis = it.date }
                eventCal.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
                eventCal.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Column(
                    modifier = Modifier.width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isToday = day.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                    Text(
                        text = sdf.format(day.time).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
                
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    if (dayEvents.isEmpty()) {
                        Text(
                            text = "Nessun impegno",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        dayEvents.forEach { event ->
                            CalendarEventItem(event, viewModel, friendProfiles[event.creatorId], onEdit = { onEditEvent(event) })
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun EventListView(allEvents: List<CalendarEvent>, viewModel: LinkThingViewModel, onEditEvent: (CalendarEvent) -> Unit) {
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    
    if (allEvents.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ancora niente in programma", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        val currentLocale = Locale.getDefault()
        val sdf = remember(currentLocale) { SimpleDateFormat("EEEE d MMMM", currentLocale) }
        val eventsByDate = allEvents.groupBy { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
            sdf.format(cal.time)
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            eventsByDate.forEach { (dateStr, dayEvents) ->
                item {
                    Text(
                        text = dateStr.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(dayEvents) { event ->
                    CalendarEventItem(event, viewModel, friendProfiles[event.creatorId], onEdit = { onEditEvent(event) })
                }
            }
        }
    }
}

@Composable
fun CalendarEventItem(event: CalendarEvent, viewModel: LinkThingViewModel, profile: UserProfile?, onEdit: () -> Unit) {
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    val modifierProfile = friendProfiles[event.lastModifierId] ?: if (event.lastModifierId == viewModel.getLocalDeviceId()) viewModel.userProfile.collectAsState().value else null
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(deviceId = event.creatorId, profile = profile, size = 32)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${event.startTime ?: "00:00"} - ${event.endTime ?: "23:59"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { 
                        val file = File(viewModel.getRootDir(), "${event.createdAt}_${event.creatorId}_${event.id.take(8)}.cal")
                        if (file.exists()) file.delete()
                        viewModel.forceSync()
                    }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(4.dp))
                val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val creatorName = profile?.getDisplayName() ?: event.creatorId.take(8)
                val modifierName = modifierProfile?.getDisplayName() ?: event.lastModifierId.take(8)
                
                Text(
                    text = "Creato da $creatorName • Modificato da $modifierName (${sdf.format(Date(event.updatedAt))})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 9.sp
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (CalendarEvent) -> Unit,
    viewModel: LinkThingViewModel,
    eventToEdit: CalendarEvent? = null
) {
    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var desc by remember { mutableStateOf(eventToEdit?.description ?: "") }
    
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventToEdit?.date ?: initialDate)
    var showDatePicker by remember { mutableStateOf(false) }
    
    val startTimeState = rememberTimePickerState(
        initialHour = eventToEdit?.startTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = eventToEdit?.startTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true
    )
    val endTimeState = rememberTimePickerState(
        initialHour = eventToEdit?.endTime?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 10,
        initialMinute = eventToEdit?.endTime?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true
    )

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annulla") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showStartTimePicker) {
        Dialog(onDismissRequest = { showStartTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Seleziona ora inizio", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = startTimeState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showStartTimePicker = false }) { Text("OK") }
                    }
                }
            }
        }
    }

    if (showEndTimePicker) {
        Dialog(onDismissRequest = { showEndTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Seleziona ora fine", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = endTimeState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEndTimePicker = false }) { Text("OK") }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (eventToEdit == null) "Nuovo Evento Condiviso" else "Modifica Evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text("Titolo") }, 
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                TextField(
                    value = desc, 
                    onValueChange = { desc = it }, 
                    label = { Text("Descrizione (opzionale)") }, 
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = dateFormatter.format(Date(datePickerState.selectedDateMillis ?: initialDate)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedCard(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Inizio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(String.format("%02d:%02d", startTimeState.hour, startTimeState.minute))
                        }
                    }
                    OutlinedCard(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Fine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(String.format("%02d:%02d", endTimeState.hour, endTimeState.minute))
                        }
                    }
                }
                
                Text(
                    "Questo evento è sincronizzato P2P tra tutti i partecipanti alla rete EtherMesh.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                
                if (eventToEdit != null) {
                    Text(
                        "Versione originale di: ${eventToEdit.creatorId.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(CalendarEvent(
                        id = eventToEdit?.id ?: UUID.randomUUID().toString(),
                        title = title,
                        description = desc,
                        date = datePickerState.selectedDateMillis ?: initialDate,
                        startTime = String.format("%02d:%02d", startTimeState.hour, startTimeState.minute),
                        endTime = String.format("%02d:%02d", endTimeState.hour, endTimeState.minute),
                        creatorId = eventToEdit?.creatorId ?: viewModel.getLocalDeviceId(),
                        lastModifierId = viewModel.getLocalDeviceId(),
                        createdAt = eventToEdit?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    ))
                },
                enabled = title.isNotBlank()
            ) { Text(if (eventToEdit == null) "Crea" else "Aggiorna") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}




@Composable
fun ApplicationsTabContent(
    viewModel: LinkThingViewModel,
    onPlayChess: () -> Unit,
    onOpenRubrica: () -> Unit,
    onShowGraph: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Applicazioni EtherMesh", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
        
        AppCard(
            title = "Rubrica",
            description = "Gestisci i contatti e le identità verificate.",
            icon = Icons.Default.ContactPage,
            onClick = onOpenRubrica
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        AppCard(
            title = "Network Graph",
            description = "Visualizza la topologia e i file di scoperta rete.",
            icon = Icons.Default.Hub,
            onClick = onShowGraph
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppCard(
            title = "Calendario",
            description = "Pianifica eventi e appuntamenti condivisi.",
            icon = Icons.Default.CalendarToday,
            onClick = onOpenCalendar
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppCard(
            title = "Scacchi",
            description = "Sfida i tuoi amici in una partita decentralizzata.",
            icon = Icons.Default.Extension,
            onClick = onPlayChess
        )
    }
}

@Composable
fun AppCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LiveConnectionBadge(friends: List<com.fmorea.syncthing.model.Device>, onClick: () -> Unit = {}) {
    val connectedCount = remember(friends) { friends.count { it.numConnections > 0 } }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(20.dp).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(12.dp), tint = if (connectedCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = connectedCount.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (connectedCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun DateDivider(date: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
            Text(text = date, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MessageBubble(
    message: LinkThingMessage, 
    deviceNames: Map<String, String>, 
    profile: UserProfile? = null, 
    allMessages: List<LinkThingMessage> = emptyList(),
    onOpenCalendar: () -> Unit = {}
) {
    val date = remember(message.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)) }
    val bubbleColor = if (message.isLocal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val shape = if (message.isLocal) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    Surface(modifier = Modifier.widthIn(max = 280.dp).clip(shape), color = bubbleColor, shadowElevation = 0.5.dp, shape = shape) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (!message.isLocal) {
                val displayName = profile?.getDisplayName() ?: deviceNames[message.deviceId] ?: message.deviceId.take(8)
                Text(text = displayName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (message.replyToTimestamp != null) {
                val repliedMsg = allMessages.find { it.timestamp == message.replyToTimestamp && it.deviceId == message.replyToDeviceId }
                Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            val replierName = deviceNames[message.replyToDeviceId] ?: message.replyToDeviceId?.take(8) ?: "Sconosciuto"
                            Text(replierName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(repliedMsg?.content ?: "Messaggio originale non trovato", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!message.isAttachment) {
                MarkdownText(text = message.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 2.dp))
            } else if (message.file != null) {
                val extension = remember(message.file.name) { message.file.extension.lowercase() }
                val isImage = remember(extension) { extension in listOf("jpg", "jpeg", "png", "webp", "gif") }
                val isAudio = remember(extension) { extension in listOf("m4a", "mp3", "wav", "ogg") }
                val isChess = remember(extension) { extension == "chess" }
                val isCal = remember(extension) { extension == "cal" }
                if (isImage) { AsyncImage(file = message.file, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(8.dp))); Spacer(modifier = Modifier.height(4.dp)) }
                if (isAudio) { AudioPlayer(message.file) } 
                else if (isChess) { val senderName = profile?.getDisplayName() ?: deviceNames[message.deviceId] ?: message.deviceId.take(8); ChessChallengeView(message.file, senderName) } 
                else if (isCal) { 
                    val senderName = profile?.getDisplayName() ?: deviceNames[message.deviceId] ?: message.deviceId.take(8)
                    CalendarCardView(message.file, senderName, onOpenCalendar = onOpenCalendar)
                }
                else if (!isImage) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(18.dp), tint = if (message.isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)); Text(text = message.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) } }
            } else { Text(text = message.content, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            Text(text = date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
            if (message.isLocal) {
                val ackCount = message.acknowledgments.size
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (Constants.isBootstrapId(message.deviceId)) {
                        Text(
                            "EtherMesh Bootstrapper", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    if (ackCount > 0) {
                        Icon(Icons.Default.DoneAll, "Letto da $ackCount", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.Done, "Inviato", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            } else if (Constants.isBootstrapId(message.deviceId)) {
                Text(
                    "EtherMesh Bootstrapper", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPlayer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(file.absolutePath) { onDispose { mediaPlayer?.release() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (isPlaying) { mediaPlayer?.pause(); isPlaying = false } else { try { if (mediaPlayer == null) { mediaPlayer = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { isPlaying = false } } } ; mediaPlayer?.start(); isPlaying = true } catch (e: Exception) { Log.e("AudioPlayer", "Failed to play audio", e) } } }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pausa" else "Riproduci") }
        Text("Messaggio vocale", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChessChallengeView(file: File, senderName: String) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Extension, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "$senderName sta giocando a scacchi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply { action = Intent.ACTION_VIEW; val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file); data = uri; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(intent) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(modifier = Modifier.width(8.dp)); Text("ENTRA IN GIOCO") }
        }
    }
}

@Composable
fun CalendarCardView(file: File, senderName: String, onOpenCalendar: () -> Unit) {
    val event = remember(file) { CalendarEvent.fromFile(file) }
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$senderName ha condiviso un evento",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = event?.title ?: "Evento Calendario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (event != null) {
                val locale = Locale.getDefault()
                val sdf = remember(locale) { SimpleDateFormat("dd/MM/yyyy", locale) }
                Text(
                    text = "${sdf.format(Date(event.date))} | ${event.startTime ?: "00:00"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenCalendar,
                colors = ButtonDefaults.filledTonalButtonColors(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("APRI CALENDARIO")
            }
        }
    }
}
