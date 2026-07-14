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
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.fmorea.syncthing.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.util.Log

enum class LinkThingTab {
    CHAT, FILE_VAULT, NETWORK
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LinkThingScreen(
    viewModel: LinkThingViewModel,
    scannedDeviceId: String = ""
) {
    val messages by viewModel.messages.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    
    // Map of deviceId to display name for chat bubbles
    val deviceNames = remember(friends, localDevice, userProfile, friendProfiles) {
        val map = friends.associate { it.deviceID to (friendProfiles[it.deviceID]?.getDisplayName() ?: it.getDisplayName()) }.toMutableMap()
        localDevice?.let { map[it.deviceID] = userProfile.getDisplayName() }
        map
    }

    var inputText by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf(LinkThingTab.CHAT) }
    var vaultTargetFile by remember { mutableStateOf<File?>(null) }
    var chatTargetMessageId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendAttachment(it) }
    }

    // Audio recording state
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

    if (editingProfileByDeviceId != null && croppingImageFile == null) {
        val profileToEdit = if (editingProfileByDeviceId == localDevice?.deviceID) userProfile 
                            else friendProfiles[editingProfileByDeviceId] ?: UserProfile(editingProfileByDeviceId!!)
        EditProfileDialog(
            profile = profileToEdit,
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

    // Keyboard back handler
    BackHandler(enabled = true) {
        if (selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        } else if (showMenu) {
            showMenu = false
        } else {
            focusManager.clearFocus()
        }
    }

    // Detect when scrolling to bottom of list (which is top of screen in reverseLayout)
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

    // Optimization: in reverseLayout, the list starts at index 0 (bottom)
    LaunchedEffect(messages.size, chatTargetMessageId) {
        if (chatTargetMessageId != null) {
            val index = messages.indexOfFirst { it.uniqueId == chatTargetMessageId }
            if (index != -1) {
                listState.animateScrollToItem(index)
                chatTargetMessageId = null
            } else {
                // If not found, it might be in an older page
                viewModel.loadMoreMessages()
            }
        } else if (messages.isNotEmpty()) {
            // Newest message is index 0. If we just sent/received, ensure it's visible.
            if (listState.firstVisibleItemIndex <= 1) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Periodic refresh for the mesh network
    LaunchedEffect(syncStatus) {
        if (syncStatus == "Attivo") {
            while (true) {
                kotlinx.coroutines.delay(60000) // 1 minute refresh to save battery
                viewModel.refreshFriends()
            }
        }
    }

    // Open dialog automatically if we have a scanned ID
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

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // ... selection mode TopAppBar
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
                                IconButton(onClick = {
                                    showMessageInfo = selectedMsg
                                }) {
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
            } else {
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
                                LinkThingTab.FILE_VAULT -> "File Vault"
                                LinkThingTab.NETWORK -> "Network"
                            }
                            
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
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
                    },
                    actions = {
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
                            DropdownMenuItem(
                                text = { Text("Gioca a Scacchi") },
                                onClick = {
                                    showMenu = false
                                    val gameFile = viewModel.shareChessGame()
                                    if (gameFile != null) {
                                        val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                                            action = Intent.ACTION_VIEW
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.provider", gameFile
                                            )
                                            data = uri
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Extension, null) }
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
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRecording) {
                                // ... existing recording UI
                                Icon(
                                    Icons.Default.Mic, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                Text(
                                    "Registrazione in corso...", 
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                IconButton(onClick = {
                                    isRecording = false
                                    recorder?.apply {
                                        try { stop() } catch(e: Exception) {}
                                        release()
                                    }
                                    recorder = null
                                    audioFile?.delete()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Annulla", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = {
                                    isRecording = false
                                    recorder?.apply {
                                        try {
                                            stop()
                                            release()
                                        } catch (e: Exception) {}
                                    }
                                    recorder = null
                                    audioFile?.let { viewModel.sendAudio(it) }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia Audio", tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        IconButton(onClick = {
                                            inputText = "$inputText**TestoBold**"
                                        }) { Icon(Icons.Default.FormatBold, "Bold") }
                                        IconButton(onClick = {
                                            inputText = "$inputText*TestoItalic*"
                                        }) { Icon(Icons.Default.FormatItalic, "Italic") }
                                        IconButton(onClick = {
                                            inputText = "$inputText`codice`"
                                        }) { Icon(Icons.Default.Code, "Codice") }
                                    }
                                    
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

                                        if (inputText.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.sendMessage(inputText, replyingTo)
                                                    inputText = ""
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
                                                            
                                                            val newRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                                                MediaRecorder(context)
                                                            } else {
                                                                @Suppress("DEPRECATION")
                                                                MediaRecorder()
                                                            }
                                                            
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
                                                        } catch (e: Exception) {
                                                            Log.e("AudioRec", "Failed to start recording", e)
                                                            isRecording = false
                                                            audioFile = null
                                                        }
                                                    } else {
                                                        recordPermissionState.launchPermissionRequest()
                                                    }
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

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(64.dp)
                    ) {
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                            label = { Text("Chat") },
                            selected = currentTab == LinkThingTab.CHAT,
                            onClick = { currentTab = LinkThingTab.CHAT }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text("File Vault") },
                            selected = currentTab == LinkThingTab.FILE_VAULT,
                            onClick = { currentTab = LinkThingTab.FILE_VAULT }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.People, contentDescription = null) },
                            label = { Text("Network") },
                            selected = currentTab == LinkThingTab.NETWORK,
                            onClick = { 
                                currentTab = LinkThingTab.NETWORK
                                viewModel.refreshFriends()
                            }
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            when (currentTab) {
                LinkThingTab.CHAT -> {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Nessun messaggio",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    "Inizia una conversazione con i tuoi amici",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.uniqueId }
                        ) { message ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem() 
                            ) {
                                if (message.dateHeader != null) {
                                    key(message.dateHeader) {
                                        DateDivider(message.dateHeader!!)
                                    }
                                }

                                var showMessageMenu by remember { mutableStateOf(false) }
                                val isSelected = selectedIds.contains(message.uniqueId)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                        .padding(vertical = 2.dp, horizontal = 4.dp),
                                    horizontalArrangement = if (message.isLocal) Arrangement.End else Arrangement.Start,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    if (!message.isLocal) {
                                        Avatar(
                                            deviceId = message.deviceId, 
                                            profile = friendProfiles[message.deviceId],
                                            onClick = { 
                                                if (isSelectionMode) {
                                                    selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId
                                                } else {
                                                    editingProfileByDeviceId = message.deviceId
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .pointerInput(isSelectionMode, isSelected) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (isSelectionMode) {
                                                            selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId
                                                        } else {
                                                            replyingTo = message
                                                        }
                                                    },
                                                    onLongPress = {
                                                        if (!isSelectionMode) {
                                                            showMessageMenu = true
                                                        } else {
                                                            selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        val senderProfile = if (message.isLocal) userProfile else friendProfiles[message.deviceId]
                MessageBubble(
                    message = message,
                    deviceNames = deviceNames,
                    profile = senderProfile,
                    allMessages = messages
                )
                                        DropdownMenu(
                                            expanded = showMessageMenu,
                                            onDismissRequest = { showMessageMenu = false }
                                        ) {
                                            if (message.file != null) {
                                                DropdownMenuItem(
                                                    text = { Text("Vedi nel Vault") },
                                                    onClick = {
                                                        showMessageMenu = false
                                                        vaultTargetFile = message.file
                                                        currentTab = LinkThingTab.FILE_VAULT
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Folder, null) }
                                                )
                                            }

                                            if (message.isAttachment && message.file != null) {
                                                val ext = message.file.extension.lowercase()
                                                if (ext == "chess") {
                                                    DropdownMenuItem(
                                                        text = { Text("Apri scacchi") },
                                                        onClick = {
                                                            showMessageMenu = false
                                                            val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                                                                action = Intent.ACTION_VIEW
                                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                    context, "${context.packageName}.provider", message.file
                                                                )
                                                                data = uri
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(intent)
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Extension, null) }
                                                    )
                                                } else {
                                                    DropdownMenuItem(
                                                        text = { Text("Apri file") },
                                                        onClick = {
                                                            showMessageMenu = false
                                                            FileUtils.openFile(context, message.file.absolutePath)
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.FileOpen, null) }
                                                    )
                                                }
                                            }
                                            
                                            DropdownMenuItem(
                                                text = { Text("Copia Testo") },
                                                onClick = {
                                                    showMessageMenu = false
                                                    clipboardManager.setText(AnnotatedString(message.content))
                                                    android.widget.Toast.makeText(context, "Copiato", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Condividi Esternamente") },
                                                onClick = {
                                                    showMessageMenu = false
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        if (message.isAttachment && message.file != null) {
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                context, "${context.packageName}.provider", message.file
                                                            )
                                                            putExtra(Intent.EXTRA_STREAM, uri)
                                                            type = context.contentResolver.getType(uri) ?: "*/*"
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        } else {
                                                            putExtra(Intent.EXTRA_TEXT, message.content)
                                                            type = "text/plain"
                                                        }
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                                },
                                                leadingIcon = { Icon(Icons.Default.Share, null) }
                                            )

                                            if (!message.isAttachment) {
                                                DropdownMenuItem(
                                                    text = { Text("Modifica") },
                                                    onClick = {
                                                        showMessageMenu = false
                                                        selectedMessage = message
                                                        editContent = message.content
                                                        showEditDialog = true
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Info") },
                                                onClick = {
                                                    showMessageMenu = false
                                                    showMessageInfo = message
                                                },
                                                leadingIcon = { Icon(Icons.Default.Info, null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Elimina") },
                                                onClick = {
                                                    showMessageMenu = false
                                                    viewModel.deleteMessage(message)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                                colors = MenuDefaults.itemColors(
                                                    textColor = MaterialTheme.colorScheme.error,
                                                    leadingIconColor = MaterialTheme.colorScheme.error
                                                )
                                            )
                                        }
                                    }

                                    if (message.isLocal) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Avatar(
                                            deviceId = message.deviceId,
                                            profile = userProfile,
                                            onClick = { 
                                                if (isSelectionMode) {
                                                    selectedIds = if (isSelected) selectedIds - message.uniqueId else selectedIds + message.uniqueId
                                                } else {
                                                    editingProfileByDeviceId = message.deviceId
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                LinkThingTab.FILE_VAULT -> FileVaultScreen(
                    viewModel = viewModel,
                    initialFile = vaultTargetFile,
                    onShowInChat = { msg ->
                        chatTargetMessageId = msg.uniqueId
                        currentTab = LinkThingTab.CHAT
                        vaultTargetFile = null
                    }
                )
                LinkThingTab.NETWORK -> NetworkView(
                    viewModel = viewModel,
                    onEditMyProfile = { editingProfileByDeviceId = localDevice?.deviceID },
                    onEditFriendProfile = { editingProfileByDeviceId = it }
                )
            }
        }
    }
}

@Composable
fun DateDivider(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp
        ) {
            Text(
                text = date,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: LinkThingMessage,
    deviceNames: Map<String, String>,
    profile: UserProfile? = null,
    allMessages: List<LinkThingMessage> = emptyList()
) {
    val date = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val bubbleColor = if (message.isLocal)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val shape = if (message.isLocal)
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    else
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)

    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape),
        color = bubbleColor,
        shadowElevation = 0.5.dp,
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (!message.isLocal) {
                val displayName = profile?.getDisplayName() ?: deviceNames[message.deviceId] ?: message.deviceId.take(8)
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            if (message.replyToTimestamp != null) {
                val repliedMsg = allMessages.find { it.timestamp == message.replyToTimestamp && it.deviceId == message.replyToDeviceId }
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()
                ) {
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
                MarkdownText(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            } else if (message.file != null) {
                val extension = remember(message.file.name) { message.file.extension.lowercase() }
                val isImage = remember(extension) { extension in listOf("jpg", "jpeg", "png", "webp", "gif") }
                val isAudio = remember(extension) { extension in listOf("m4a", "mp3", "wav", "ogg") }
                val isChess = remember(extension) { extension == "chess" }

                if (isImage) {
                    AsyncImage(
                        file = message.file,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (isAudio) {
                    AudioPlayer(message.file)
                } else if (isChess) {
                    val senderName = profile?.getDisplayName() ?: deviceNames[message.deviceId] ?: message.deviceId.take(8)
                    ChessChallengeView(message.file, senderName)
                } else if (!isImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (message.isLocal) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Text(
                text = date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )

            if (message.isLocal) {
                val ackCount = message.acknowledgments.size
                Row(modifier = Modifier.align(Alignment.End)) {
                    if (ackCount > 0) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "Letto da $ackCount",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = "Inviato",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(file.absolutePath) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                try {
                    if (mediaPlayer == null) {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            prepare()
                            setOnCompletionListener { isPlaying = false }
                        }
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Failed to play audio", e)
                }
            }
        }) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci"
            )
        }
        Text("Messaggio vocale", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChessChallengeView(file: File, senderName: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$senderName sta giocando a scacchi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        // Use FileProvider for modern Android compatibility
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ENTRA IN GIOCO")
            }
        }
    }
}
