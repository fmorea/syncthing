package com.fmorea.syncthing.syncthing

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmorea.syncthing.R
import com.fmorea.syncthing.model.Device
import com.fmorea.syncthing.service.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Simple in-memory cache for images to improve scrolling performance
val imageCacheShared = object : LruCache<String, android.graphics.Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt() // Use 1/8th of available memory
) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
        return value.byteCount / 1024
    }
}

/**
 * WYSIWYG-like Markdown component that renders the text with formatting
 * but hides the symbols.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val boldColor = MaterialTheme.colorScheme.primary
    val italicColor = MaterialTheme.colorScheme.secondary
    val codeColor = MaterialTheme.colorScheme.surfaceVariant

    val annotatedString = remember(text) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    // Bold **text**
                    text.startsWith("**", i) -> {
                        val end = text.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 2
                        } else {
                            append(text[i]); i++
                        }
                    }
                    // Italic *text*
                    text.startsWith("*", i) -> {
                        val end = text.indexOf("*", i + 1)
                        if (end != -1 && end != i + 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = italicColor)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i]); i++
                        }
                    }
                    // Code `text`
                    text.startsWith("`", i) -> {
                        val end = text.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(background = codeColor.copy(alpha = 0.2f), fontFamily = FontFamily.Monospace)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        } else {
                            append(text[i]); i++
                        }
                    }
                    else -> {
                        append(text[i]); i++
                    }
                }
            }
        }
    }
    Text(text = annotatedString, modifier = modifier, style = style)
}

@Composable
fun AsyncImage(file: File, modifier: Modifier, targetSize: Int = 400) {
    var bitmap by remember { mutableStateOf(imageCacheShared.get(file.absolutePath)) }

    LaunchedEffect(file.absolutePath) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    if (!file.exists()) return@withContext
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                
                    if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext

                    options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                    options.inJustDecodeBounds = false
                
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (decoded != null) {
                        imageCacheShared.put(file.absolutePath, decoded)
                        bitmap = decoded
                    }
                } catch (e: Exception) {
                    Log.e("AsyncImage", "Failed to decode ${file.name}", e)
                }
            }
        }
    }

    Crossfade(targetState = bitmap, animationSpec = tween(500), label = "imageFade") { targetBitmap ->
        if (targetBitmap != null) {
            Image(
                bitmap = targetBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

@Composable
fun Avatar(deviceId: String, profile: UserProfile? = null, size: Int = 32, onClick: () -> Unit = {}) {
    val initial = (profile?.firstName?.take(1) ?: deviceId.take(1)).uppercase()
    Surface(
        modifier = Modifier.size(size.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val rootDir = File(LocalContext.current.filesDir, com.fmorea.syncthing.service.Constants.LINKTHING_DIR_NAME)
            val photo = UserProfile.findPhoto(deviceId, profile?.discloserId ?: "", rootDir)
            if (photo != null) {
                AsyncImageAvatar(file = photo)
            } else {
                if (size > 40) {
                    Icon(
                        Icons.Default.Person, 
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size((size * 0.6).dp)
                    )
                } else {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
fun DiscoveredDeviceItem(
    deviceId: String,
    introducedBy: String?,
    deviceNames: Map<String, String>,
    onAdd: () -> Unit
) {
    ListItem(
        headlineContent = { Text("Nuovo Nodo: ${deviceId.take(8)}") },
        supportingContent = {
            Column {
                Text(deviceId.take(16) + "...", style = MaterialTheme.typography.bodySmall)
                if (!introducedBy.isNullOrEmpty()) {
                    val introducerName = deviceNames[introducedBy] ?: introducedBy.take(8)
                    Text(
                        "Segnalato da: $introducerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    Icons.Default.Devices, 
                    null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(28.dp)
                )
            }
        },
        trailingContent = {
            Button(onClick = onAdd) {
                Text("Aggiungi")
            }
        }
    )
}

@Composable
fun DeviceItem(
    device: Device,
    isMe: Boolean,
    profile: UserProfile?,
    introducedBy: String?,
    deviceNames: Map<String, String>,
    onDelete: () -> Unit,
    onEditProfile: () -> Unit,
    onViewIdentities: () -> Unit,
    onTogglePause: () -> Unit = {}
) {
    val isOnline = (device.numConnections ?: 0) > 0
    val isBootstrap = Constants.isBootstrapId(device.deviceID)
    val statusText = when {
        isMe -> "Tu"
        isBootstrap -> if (isOnline) "EtherMesh Bootstrapper (Online)" else "EtherMesh Bootstrapper (Offline)"
        device.paused -> "In pausa"
        isOnline -> "Online"
        else -> "Offline"
    }
    
    val statusColor = when {
        isMe || isOnline -> Color(0xFF4CAF50)
        device.paused -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }

    val displayName = profile?.getDisplayName() ?: device.getDisplayName()

    ListItem(
        headlineContent = { 
            Text(
                displayName,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
            ) 
        },
        supportingContent = { 
            Column {
                Text(device.deviceID.take(16) + "...", style = MaterialTheme.typography.bodySmall)
                
                val introducerId = introducedBy ?: device.introducedBy
                if (!introducerId.isNullOrEmpty() && introducerId != device.deviceID) {
                    val introducerName = deviceNames[introducerId] ?: introducerId.take(8)
                    Text(
                        "Introdotto da: $introducerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        leadingContent = { 
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(
                    deviceId = device.deviceID, 
                    profile = profile, 
                    size = 48,
                    onClick = onEditProfile
                )
                Surface(
                    modifier = Modifier
                        .size(20.dp) // Slightly larger to be more clickable
                        .offset(x = 4.dp, y = 4.dp)
                        .clickable(onClick = onEditProfile),
                    shape = CircleShape,
                    color = statusColor,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    shadowElevation = 4.dp
                ) {}
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onViewIdentities) {
                    Icon(Icons.Default.Badge, contentDescription = "Vedi Identità")
                }
                if (!isMe) {
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            if (device.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (device.paused) "Riprendi" else "Pausa",
                            tint = if (device.paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

@Composable
fun AsyncImageAvatar(file: File) {
    var bitmap by remember(file.absolutePath) { mutableStateOf(imageCacheShared.get(file.absolutePath)) }
    LaunchedEffect(file.absolutePath) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    options.inSampleSize = calculateInSampleSize(options, 256, 256)
                    options.inJustDecodeBounds = false
                    
                    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (decoded != null) {
                        imageCacheShared.put(file.absolutePath, decoded)
                        bitmap = decoded
                    }
                } catch (e: Exception) {
                    Log.e("Avatar", "Failed to decode", e)
                }
                Unit
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun EditorActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun FormattingToolbar(
    textValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    fun applyFormatting(prefix: String, suffix: String = prefix) {
        val selection = textValue.selection
        val text = textValue.text
        val selectedText = text.substring(selection.start, selection.end)
        val newText = text.replaceRange(selection.start, selection.end, "$prefix$selectedText$suffix")
        
        val newCursorPos = if (selection.collapsed) selection.start + prefix.length else selection.end + prefix.length + suffix.length
        onValueChange(textValue.copy(
            text = newText,
            selection = TextRange(newCursorPos)
        ))
    }

    fun moveCursor(delta: Int) {
        val newPos = (textValue.selection.start + delta).coerceIn(0, textValue.text.length)
        onValueChange(textValue.copy(selection = TextRange(newPos)))
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorActionButton(Icons.Default.FormatBold, stringResource(R.string.action_bold)) { applyFormatting("**") }
        EditorActionButton(Icons.Default.FormatItalic, stringResource(R.string.action_italic)) { applyFormatting("*") }
        EditorActionButton(Icons.Default.FormatQuote, stringResource(R.string.action_quote)) { applyFormatting("> ") }
        EditorActionButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.action_list)) { applyFormatting("- ") }
        EditorActionButton(Icons.Default.Code, stringResource(R.string.action_code)) { applyFormatting("`") }
        EditorActionButton(Icons.Default.Link, stringResource(R.string.action_link)) { applyFormatting("[", "](url)") }
        
        Spacer(modifier = Modifier.weight(1f))
        
        EditorActionButton(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") { moveCursor(-1) }
        EditorActionButton(Icons.AutoMirrored.Filled.ArrowForward, "Avanti") { moveCursor(1) }
    }
}

