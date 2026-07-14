package com.fmorea.syncthing.syncthing

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.fmorea.syncthing.activities.SyncthingActivity
import com.fmorea.syncthing.model.Device
import com.fmorea.syncthing.service.SyncthingService
import com.fmorea.syncthing.theme.ApplicationTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fmorea.syncthing.fragments.DeviceIdDialogFragment
import com.fmorea.syncthing.activities.QRScannerActivity
import android.app.Activity
import android.text.TextUtils
import android.widget.Toast
import com.fmorea.syncthing.util.ConfigRouter

class NetworkManagementActivity : SyncthingActivity(), SyncthingService.OnServiceStateChangeListener {
    
    private lateinit var viewModel: LinkThingViewModel
    private var scannedDeviceId = mutableStateOf("")
    private val QR_SCAN_REQUEST_CODE = 403

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this).get(LinkThingViewModel::class.java)
        
        observeViewModelEvents()
        
        setContent {
            ApplicationTheme {
                NetworkManagementScreen(
                    viewModel = viewModel,
                    scannedDeviceId = scannedDeviceId.value,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun observeViewModelEvents() {
        viewModel.uiEvents.observe(this) { event ->
            if (event == null) return@observe

            when (event) {
                is LinkThingViewModel.UiEvent.ShowMyId -> showQrCodeDialog()
                is LinkThingViewModel.UiEvent.ScanQrCode -> {
                    startActivityForResult(QRScannerActivity.intent(this), QR_SCAN_REQUEST_CODE)
                }
                else -> {}
            }
            viewModel.clearUiEvent()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCAN_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val code = data.getStringExtra(QRScannerActivity.QR_RESULT_ARG)
            if (code != null) {
                scannedDeviceId.value = code
            }
        }
    }

    private fun showQrCodeDialog() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val deviceId = prefs.getString(com.fmorea.syncthing.service.Constants.PREF_LOCAL_DEVICE_ID, "")
        if (TextUtils.isEmpty(deviceId)) {
            Toast.makeText(this, com.fmorea.syncthing.R.string.could_not_access_deviceid, Toast.LENGTH_SHORT).show()
            return
        }
      
        val config = ConfigRouter(this)
        val devices = config.getDevices(getApi(), true)
        var deviceName = ""

        for (d in devices) {
            if (d.deviceID == deviceId) {
                deviceName = d.displayName
                break
            }
        }

        DeviceIdDialogFragment.show(
                supportFragmentManager,
                deviceName.trim(),
                deviceId!!,
                true
        )
    }

    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
        super.onServiceConnected(name, service)
        viewModel.updateSyncStatus(SyncthingService.State.ACTIVE, 100, getApi())
        viewModel.refreshFriends()
    }

    override fun onServiceStateChange(currentState: SyncthingService.State) {
        viewModel.updateSyncStatus(currentState, 100, getApi())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkManagementScreen(
    viewModel: LinkThingViewModel,
    scannedDeviceId: String = "",
    onBack: () -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val topology by viewModel.meshTopology.collectAsState()
    val discoveredIds by viewModel.discoveredDevices.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    
    // Map of deviceId -> display name for the whole network
    val deviceNames = remember(friends, localDevice, userProfile, friendProfiles) {
        val map = friends.associate { it.deviceID to (friendProfiles[it.deviceID]?.getDisplayName() ?: it.getDisplayName()) }.toMutableMap()
        localDevice?.let { map[it.deviceID] = userProfile.getDisplayName() }
        map
    }

    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var viewingIdentitiesForDeviceId by remember { mutableStateOf<String?>(null) }
    var editingProfileForDeviceId by remember { mutableStateOf<String?>(null) }

    // Open dialog automatically if we have a scanned ID
    LaunchedEffect(scannedDeviceId) {
        if (scannedDeviceId.isNotBlank()) {
            showAddFriendDialog = true
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            profile = userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { viewModel.updateMyProfile(it) },
            onPhotoSelected = { viewModel.updateMyPhoto(it) }
        )
    }

    if (editingProfileForDeviceId != null) {
        val targetId = editingProfileForDeviceId!!
        val profileToEdit = friendProfiles[targetId] ?: UserProfile(targetId)
        EditProfileDialog(
            profile = profileToEdit,
            onDismiss = { editingProfileForDeviceId = null },
            onSave = { viewModel.updateFriendProfile(targetId, it) },
            onPhotoSelected = { viewModel.updateFriendPhoto(targetId, it) }
        )
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

    if (showConfirmDelete != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = null },
            title = { Text("Rimuovi dal Network") },
            text = { Text("Sei sicuro di voler rimuovere questo dispositivo? Non potrai più scambiare messaggi con lui.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFriend(showConfirmDelete!!)
                        showConfirmDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = null }) { Text("Annulla") }
            }
        )
    }

    if (viewingIdentitiesForDeviceId != null) {
        val targetDeviceId = viewingIdentitiesForDeviceId!!
        val profiles = allProfiles[targetDeviceId] ?: emptyList()
        AlertDialog(
            onDismissRequest = { viewingIdentitiesForDeviceId = null },
            title = { Text("Gestione Identità") },
            text = {
                LazyColumn {
                    items(profiles) { profile ->
                        val isOwner = profile.discloserId == targetDeviceId
                        val isVerifiedByMe = profile.discloserId == localDevice?.deviceID
                        
                        val identityType = when {
                            isOwner -> "Identità Autodeterminata"
                            isVerifiedByMe -> "Identità Verificata (da te)"
                            else -> "Identità Segnalata da: ${deviceNames[profile.discloserId] ?: profile.discloserId.take(8)}"
                        }

                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.getDisplayName())
                                    if (isVerifiedByMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            supportingContent = { Text(identityType) },
                            trailingContent = {
                                if (!isOwner) {
                                    IconButton(onClick = { viewModel.deleteIdentity(targetDeviceId, profile.discloserId) }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingIdentitiesForDeviceId = null }) { Text("Chiudi") }
            }
        )
    }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Network") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddFriendDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Aggiungi Amico")
                    }
                    IconButton(onClick = { viewModel.showMyId() }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Il Mio QR")
                    }
                    IconButton(onClick = { showEditProfileDialog = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Modifica Profilo")
                    }
                    IconButton(onClick = { viewModel.refreshFriends() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Aggiorna")
                    }
                    IconButton(onClick = {
                        val intent = Intent(context, com.fmorea.syncthing.settings.SettingsActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SectionHeader("IL MIO PROFILO")
            }

            if (localDevice != null) {
                item {
                    DeviceItem(
                        device = localDevice!!,
                        isMe = true,
                        profile = userProfile,
                        introducedBy = null,
                        deviceNames = deviceNames,
                        onDelete = {},
                        onEditProfile = { showEditProfileDialog = true },
                        onViewIdentities = { viewingIdentitiesForDeviceId = localDevice!!.deviceID }
                    )
                }
            } else {
                item {
                    ListItem(headlineContent = { Text("Caricamento info profilo...") })
                }
            }

            item {
                SectionHeader("DISPOSITIVI CONNESSI")
            }

            if (friends.isEmpty()) {
                item {
                    Text(
                        "Nessun altro dispositivo nel network mesh.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(friends, key = { it.deviceID }) { device ->
                    DeviceItem(
                        device = device,
                        isMe = false,
                        profile = friendProfiles[device.deviceID],
                        introducedBy = topology[device.deviceID],
                        deviceNames = deviceNames,
                        onDelete = { showConfirmDelete = device.deviceID },
                        onEditProfile = { editingProfileForDeviceId = device.deviceID },
                        onViewIdentities = { viewingIdentitiesForDeviceId = device.deviceID }
                    )
                }
            }

            if (discoveredIds.isNotEmpty()) {
                item {
                    SectionHeader("DISPOSITIVI SCOPERTI (MESH)")
                }
                items(discoveredIds.toList()) { deviceId ->
                    DiscoveredDeviceItem(
                        deviceId = deviceId,
                        introducedBy = topology[deviceId],
                        deviceNames = deviceNames,
                        onAdd = { viewModel.addFriend(deviceId) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Info Network",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tutti i dispositivi in questa lista partecipano al network mesh. I profili (nome e foto) sono distribuiti in modo decentralizzato.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
