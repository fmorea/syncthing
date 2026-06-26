package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fmorea.syncthing.model.Device

@Composable
fun AddFriendDialog(
    initialDeviceId: String = "",
    onDismiss: () -> Unit,
    onAddFriend: (String) -> Unit,
    onScanQrCode: () -> Unit
) {
    var deviceId by remember { mutableStateOf(initialDeviceId) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialDeviceId) {
        if (initialDeviceId.isNotBlank()) {
            deviceId = initialDeviceId
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi Amico") },
        text = {
            Column {
                Text("Inserisci l'ID del dispositivo del tuo amico o scansiona il suo codice QR.")
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = deviceId,
                        onValueChange = { 
                            deviceId = it
                            error = null
                        },
                        placeholder = { Text("ID Dispositivo") },
                        modifier = Modifier.weight(1f),
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onScanQrCode) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scansiona QR")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val device = Device()
                    device.deviceID = deviceId
                    if (device.checkDeviceID()) {
                        onAddFriend(deviceId)
                    } else {
                        error = "ID Dispositivo non valido"
                    }
                }
            ) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
