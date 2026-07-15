package com.fmorea.syncthing.syncthing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fmorea.syncthing.fragments.DeviceIdDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onPhotoSelected: (File) -> Unit
) {
    var firstName by remember(profile) { mutableStateOf(profile.firstName) }
    var lastName by remember(profile) { mutableStateOf(profile.lastName) }
    var country by remember(profile) { mutableStateOf(profile.country) }
    var address by remember(profile) { mutableStateOf(profile.address) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var height by remember(profile) { mutableStateOf(profile.height) }

    var showQrCode by remember { mutableStateOf(false) }

    val isFormValid = remember(firstName, lastName) {
        firstName.isNotBlank() || lastName.isNotBlank()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val tempFile = File(context.cacheDir, "crop_input_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(it)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            onPhotoSelected(tempFile)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMe) "Modifica Il Tuo Profilo" else "Profilo di ${profile.getDisplayName()}") },
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
                        .then(if (isMe) Modifier.clickable { imagePickerLauncher.launch("image/*") } else Modifier)
                ) {
                    val rootDir = File(context.filesDir, com.fmorea.syncthing.service.Constants.LINKTHING_DIR_NAME)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(profile.deviceId))
                        Toast.makeText(context, "ID Dispositivo copiato", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copia ID")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(onClick = { showQrCode = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Mostra QR Code")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = firstName, 
                    onValueChange = { if (isMe) firstName = it }, 
                    label = { Text("Nome") },
                    placeholder = { Text("Esempio: Mario") },
                    readOnly = !isMe,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = lastName, 
                    onValueChange = { if (isMe) lastName = it }, 
                    label = { Text("Cognome") },
                    placeholder = { Text("Esempio: Rossi") },
                    readOnly = !isMe,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = country, onValueChange = { if (isMe) country = it }, label = { Text("Nazione") }, readOnly = !isMe, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = address, onValueChange = { if (isMe) address = it }, label = { Text("Indirizzo") }, readOnly = !isMe, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = gender, onValueChange = { if (isMe) gender = it }, label = { Text("Sesso") }, readOnly = !isMe, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = height, onValueChange = { if (isMe) height = it }, label = { Text("Altezza") }, readOnly = !isMe, modifier = Modifier.fillMaxWidth())
                
                if (!isMe) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val discloserName = if (profile.discloserId == profile.deviceId) "Lui stesso" else profile.discloserId.take(8)
                    Text(
                        "Identità dichiarata da: $discloserName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            if (isMe) {
                Button(
                    onClick = {
                        onSave(profile.copy(
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            country = country.trim(),
                            address = address.trim(),
                            gender = gender.trim(),
                            height = height.trim()
                        ))
                        onDismiss()
                    }
                ) { Text("Salva") }
            } else {
                Button(
                    onClick = {
                        onSave(profile) // This "verifies" it by saving my disclosure
                        onDismiss()
                    }
                ) { 
                    Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verifica Identità") 
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )

    if (showQrCode) {
        val qrBitmap = remember(profile.deviceId) { generateQrCode(profile.deviceId) }
        DeviceIdDialog(
            onDismiss = { showQrCode = false },
            deviceName = profile.getDisplayName(),
            deviceId = profile.deviceId,
            qrCode = qrBitmap,
            onCopy = {
                clipboardManager.setText(AnnotatedString(profile.deviceId))
                Toast.makeText(context, "ID Dispositivo copiato", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, profile.deviceId)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            },
            isCurrentDevice = isMe
        )
    }
}

private fun generateQrCode(deviceId: String): Bitmap {
    val qrSize = 232
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()

    val bitMatrix = MultiFormatWriter()
        .encode(deviceId, BarcodeFormat.QR_CODE, qrSize, qrSize)
    val bitMap = createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)

    for (x in 0 until qrSize) {
        for (y in 0 until qrSize) {
            val pixel = if (bitMatrix[x, y]) black else white
            bitMap.setPixel(x, y, pixel)
        }
    }

    return bitMap
}
