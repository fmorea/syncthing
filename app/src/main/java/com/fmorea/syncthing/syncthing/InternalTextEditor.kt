package com.fmorea.syncthing.syncthing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fmorea.syncthing.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FileMetadata(
    val timestamp: Long? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val introducerId: String? = null,
    val introducedId: String? = null,
    val originalTimestamp: Long? = null,
    val originalSender: String? = null,
    val type: String = "Sconosciuto",
    val profile: UserProfile? = null
)

/**
 * A Markdown VisualTransformation to highlight Bold, Italic and Code.
 */
class MarkdownVisualTransformation(
    private val boldColor: androidx.compose.ui.graphics.Color,
    private val italicColor: androidx.compose.ui.graphics.Color,
    private val codeColor: androidx.compose.ui.graphics.Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightMarkdown(text.text),
            OffsetMapping.Identity
        )
    }

    private fun highlightMarkdown(content: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < content.length) {
                when {
                    content.startsWith("**", i) -> {
                        val end = content.indexOf("**", i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                                append(content.substring(i, end + 2))
                            }
                            i = end + 2
                        } else {
                            append(content[i]); i++
                        }
                    }
                    content.startsWith("*", i) -> {
                        val end = content.indexOf("*", i + 1)
                        if (end != -1 && end != i + 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = italicColor)) {
                                append(content.substring(i, end + 1))
                            }
                            i = end + 1
                        } else {
                            append(content[i]); i++
                        }
                    }
                    content.startsWith("`", i) -> {
                        val end = content.indexOf("`", i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(background = codeColor.copy(alpha = 0.2f), fontFamily = FontFamily.Monospace)) {
                                append(content.substring(i, end + 1))
                            }
                            i = end + 1
                        } else {
                            append(content[i]); i++
                        }
                    }
                    else -> {
                        append(content[i]); i++
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalTextEditor(
    file: File,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onShowInChat: ((LinkThingMessage) -> Unit)? = null
) {
    val context = LocalContext.current
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(true) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(true) }

    val metadata = remember(file) {
        val name = file.name
        val ext = file.extension.lowercase()
        val parts = name.removeSuffix(".$ext").split("_")
        
        when {
            ext == "msg" -> {
                if (parts.size >= 4) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        originalTimestamp = parts[2].toLongOrNull(),
                        originalSender = parts[3],
                        type = context.getString(R.string.type_msg_reply)
                    )
                } else if (parts.size >= 2) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        type = context.getString(R.string.type_msg)
                    )
                } else FileMetadata(type = context.getString(R.string.type_msg_unknown))
            }
            ext == "ack" -> {
                if (parts.size >= 3) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        originalSender = parts[1],
                        receiverId = parts[2],
                        type = context.getString(R.string.type_ack)
                    )
                } else FileMetadata(type = context.getString(R.string.type_ack_unknown))
            }
            ext == "net" -> {
                if (parts.size >= 2) {
                    FileMetadata(
                        introducerId = parts[0],
                        introducedId = parts[1],
                        type = context.getString(R.string.type_net)
                    )
                } else FileMetadata(type = context.getString(R.string.type_net_unknown))
            }
            ext == "chess" -> {
                if (parts.size >= 2) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        type = context.getString(R.string.type_chess)
                    )
                } else FileMetadata(type = context.getString(R.string.type_chess))
            }
            ext == "info" -> {
                val profile = try { UserProfile.loadFromFile(file) } catch (e: Exception) { null }
                val partsInfo = file.name.removeSuffix(".INFO").split("_")
                val deviceIdFromPath = partsInfo.getOrNull(0) ?: ""
                val discloserIdFromPath = partsInfo.getOrNull(1) ?: ""
                
                FileMetadata(
                    type = context.getString(R.string.metadata_user_profile),
                    profile = profile?.copy(
                        deviceId = if (profile.deviceId.isBlank()) deviceIdFromPath else profile.deviceId,
                        discloserId = if (profile.discloserId.isBlank()) discloserIdFromPath else profile.discloserId
                    )
                )
            }
            else -> {
                // Try attachment format: timestamp_senderId_filename
                if (parts.size >= 3) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        type = context.getString(R.string.type_attachment, ext)
                    )
                } else FileMetadata(type = context.getString(R.string.type_generic))
            }
        }
    }

    val markdownTransformation = remember {
        MarkdownVisualTransformation(
            boldColor = androidx.compose.ui.graphics.Color.Unspecified,
            italicColor = androidx.compose.ui.graphics.Color.Unspecified,
            codeColor = androidx.compose.ui.graphics.Color.Gray
        )
    }

    LaunchedEffect(file) {
        isLoading = true
        try {
            val content = file.readText()
            textValue = TextFieldValue(content)
        } catch (e: Exception) {
            textValue = TextFieldValue(context.getString(R.string.error_loading_file, e.message))
        }
        isLoading = false
    }

    fun applyFormatting(prefix: String, suffix: String = prefix) {
        val selection = textValue.selection
        val text = textValue.text
        val selectedText = text.substring(selection.start, selection.end)
        val newText = text.replaceRange(selection.start, selection.end, "$prefix$selectedText$suffix")
        
        val newCursorPos = if (selection.collapsed) selection.start + prefix.length else selection.end + prefix.length + suffix.length
        textValue = textValue.copy(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            file.name, 
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            file.parent ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (file.name.endsWith(".msg") && onShowInChat != null) {
                        val msg = remember(file) { LinkThingMessage.fromFile(file, "") }
                        if (msg != null) {
                            IconButton(onClick = { onShowInChat.invoke(msg) }) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.action_show_in_chat))
                            }
                        }
                    }
                    IconButton(onClick = { showMetadata = !showMetadata }) {
                        Icon(
                            if (showMetadata) Icons.Default.Info else Icons.Default.Info,
                            contentDescription = stringResource(R.string.action_metadata),
                            tint = if (showMetadata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Icon(
                            if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (isPreviewMode) stringResource(R.string.action_edit) else stringResource(R.string.action_preview)
                        )
                    }
                    IconButton(onClick = { 
                        onSave(textValue.text)
                        Toast.makeText(context, context.getString(R.string.toast_file_saved), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_title))
                    }
                }
            )
        },
        bottomBar = {
            if (!isLoading && !isPreviewMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EditorActionButton(Icons.Default.FormatBold, stringResource(R.string.action_bold)) { applyFormatting("**") }
                            EditorActionButton(Icons.Default.FormatItalic, stringResource(R.string.action_italic)) { applyFormatting("*") }
                            EditorActionButton(Icons.Default.FormatQuote, stringResource(R.string.action_quote)) { applyFormatting("> ") }
                            EditorActionButton(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.action_list)) { applyFormatting("- ") }
                            EditorActionButton(Icons.Default.Code, stringResource(R.string.action_code)) { applyFormatting("`") }
                            EditorActionButton(Icons.Default.Link, stringResource(R.string.action_link)) { applyFormatting("[", "](url)") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showMetadata) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${stringResource(R.string.metadata_interpretation)}: ${metadata.type}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.getDefault()) }
                        
                        metadata.timestamp?.let {
                            MetadataRow(stringResource(R.string.metadata_creation_date), dateFormat.format(Date(it)))
                        }
                        metadata.senderId?.let {
                            MetadataRow(stringResource(R.string.metadata_sender_id), it)
                        }
                        metadata.receiverId?.let {
                            MetadataRow(stringResource(R.string.metadata_receiver_id), it)
                        }
                        metadata.introducerId?.let {
                            MetadataRow(stringResource(R.string.metadata_introducer_id), it)
                        }
                        metadata.introducedId?.let {
                            MetadataRow(stringResource(R.string.metadata_introduced_node_id), it)
                        }
                        metadata.originalTimestamp?.let {
                            MetadataRow(stringResource(R.string.metadata_original_message_ref), dateFormat.format(Date(it)))
                        }
                        metadata.originalSender?.let {
                            MetadataRow(stringResource(R.string.metadata_original_author_ref), it)
                        }
                        
                        metadata.profile?.let { p ->
                            MetadataRow(stringResource(R.string.profile_first_name), p.firstName.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_last_name), p.lastName.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_address), p.address.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_country), p.country.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_gender), p.gender.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_height), p.height.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_device_id), p.deviceId.ifBlank { "-" })
                            MetadataRow(stringResource(R.string.profile_declared_by), p.discloserId.ifBlank { "-" })
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (isPreviewMode) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        MarkdownText(
                            text = textValue.text,
                            style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    val lines = textValue.text.split("\n")
                    val lineCount = lines.size.coerceAtLeast(1)

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Line Numbers
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            repeat(lineCount) { index ->
                                Text(
                                    text = (index + 1).toString(),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.height(20.dp)
                                )
                            }
                        }

                        // Editor
                        BasicTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = markdownTransformation,
                            decorationBox = { innerTextField ->
                                innerTextField()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun EditorActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}
