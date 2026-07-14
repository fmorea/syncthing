package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fmorea.syncthing.model.Device
import java.io.File

@Composable
fun NetworkView(
    viewModel: LinkThingViewModel,
    onEditMyProfile: () -> Unit,
    onEditFriendProfile: (String) -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val topology by viewModel.meshTopology.collectAsState()
    val discoveredIds by viewModel.discoveredDevices.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    
    val deviceNames = remember(friends, localDevice, userProfile, friendProfiles) {
        val map = friends.associate { it.deviceID to (friendProfiles[it.deviceID]?.getDisplayName() ?: it.getDisplayName()) }.toMutableMap()
        localDevice?.let { map[it.deviceID] = userProfile.getDisplayName() }
        map
    }

    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    var viewingIdentitiesForDeviceId by remember { mutableStateOf<String?>(null) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    if (showManualAddDialog) {
        AddFriendDialog(
            onDismiss = { showManualAddDialog = false },
            onAddFriend = { deviceId ->
                viewModel.addFriend(deviceId)
                showManualAddDialog = false
            },
            onScanQrCode = {
                showManualAddDialog = false
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
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                                    Row {
                                        if (!isVerifiedByMe) {
                                            IconButton(onClick = { viewModel.updateFriendProfile(targetDeviceId, profile) }) {
                                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        IconButton(onClick = { viewModel.deleteIdentity(targetDeviceId, profile.discloserId) }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
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

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    onEditProfile = { onEditMyProfile() },
                    onViewIdentities = { viewingIdentitiesForDeviceId = localDevice!!.deviceID },
                    onTogglePause = {}
                )
            }
            item {
                Row(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    TextButton(onClick = { viewModel.showMyId() }) {
                        Icon(Icons.Default.QrCode, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Il mio ID QR")
                    }
                }
            }
        }

        item {
            SectionHeader("DISPOSITIVI CONNESSI")
        }

        item {
            OutlinedButton(
                onClick = { showManualAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aggiungi Dispositivo")
            }
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
                    onEditProfile = { onEditFriendProfile(device.deviceID) },
                    onViewIdentities = { viewingIdentitiesForDeviceId = device.deviceID },
                    onTogglePause = { viewModel.toggleDevicePause(device.deviceID) }
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
        }
    }
}
