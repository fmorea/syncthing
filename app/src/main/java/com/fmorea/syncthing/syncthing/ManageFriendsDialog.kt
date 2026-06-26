package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fmorea.syncthing.model.Device

@Composable
fun ManageFriendsDialog(
    friends: List<Device>,
    onDismiss: () -> Unit,
    onRemoveFriend: (String) -> Unit,
    onSetAlias: (String, String) -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    var editingAliasByDeviceId by remember { mutableStateOf<String?>(null) }
    var aliasText by remember { mutableStateOf("") }

    if (showConfirmDelete != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = null },
            title = { Text("Rimuovi dal Network") },
            text = { Text("Sei sicuro di voler rimuovere questo dispositivo? Non potrai più scambiare messaggi con lui.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend(showConfirmDelete!!)
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

    if (editingAliasByDeviceId != null) {
        AlertDialog(
            onDismissRequest = { editingAliasByDeviceId = null },
            title = { Text("Imposta Alias") },
            text = {
                TextField(
                    value = aliasText,
                    onValueChange = { aliasText = it },
                    label = { Text("Nome Amico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetAlias(editingAliasByDeviceId!!, aliasText)
                    editingAliasByDeviceId = null
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { editingAliasByDeviceId = null }) { Text("Annulla") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gestisci Network") },
        text = {
            if (friends.isEmpty()) {
                Text("Nessun dispositivo rilevato nel network.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(friends, key = { it.deviceID }) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        editingAliasByDeviceId = friend.deviceID
                                        aliasText = friend.name ?: ""
                                    }, 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        friend.getDisplayName(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1
                                    )
                                    Text(
                                        friend.deviceID.take(8) + "...",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            IconButton(onClick = { showConfirmDelete = friend.deviceID }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}
