package com.fmorea.syncthing.syncthing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import android.graphics.BitmapFactory

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onPhotoSelected: (File) -> Unit
) {
    var firstName by remember { mutableStateOf(profile.firstName) }
    var lastName by remember { mutableStateOf(profile.lastName) }
    var country by remember { mutableStateOf(profile.country) }
    var address by remember { mutableStateOf(profile.address) }
    var gender by remember { mutableStateOf(profile.gender) }
    var height by remember { mutableStateOf(profile.height) }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val file = File(context.cacheDir, "temp_profile.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            onPhotoSelected(file)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica Profilo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") }
                ) {
                    val rootDir = File(android.os.Environment.getExternalStorageDirectory(), com.fmorea.syncthing.service.Constants.LINKTHING_DIR_NAME)
                    val photo = UserProfile.findPhoto(profile.deviceId, profile.discloserId, rootDir)
                    if (photo != null) {
                        val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                null,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                TextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Nome") })
                TextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Cognome") })
                TextField(value = country, onValueChange = { country = it }, label = { Text("Nazione") })
                TextField(value = address, onValueChange = { address = it }, label = { Text("Indirizzo") })
                TextField(value = gender, onValueChange = { gender = it }, label = { Text("Sesso") })
                TextField(value = height, onValueChange = { height = it }, label = { Text("Altezza") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(profile.copy(
                    firstName = firstName,
                    lastName = lastName,
                    country = country,
                    address = address,
                    gender = gender,
                    height = height
                ))
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
