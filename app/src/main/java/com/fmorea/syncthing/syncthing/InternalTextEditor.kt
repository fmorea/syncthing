package com.fmorea.syncthing.syncthing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * A Markdown and Code VisualTransformation to highlight syntax.
 */
class MarkdownVisualTransformation(
    private val boldColor: Color,
    private val italicColor: Color,
    private val codeColor: Color,
    private val searchHighlightColor: Color = Color.Yellow,
    private val searchQuery: String = "",
    private val extension: String = ""
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = if (extension in listOf("msg", "ack", "net", "info", "chess", "md", "txt", "markdown")) {
            highlightMarkdown(text.text)
        } else {
            highlightCode(text.text, extension)
        }
        
        val withSearch = if (searchQuery.isNotBlank()) {
            buildAnnotatedString {
                append(highlighted)
                var start = 0
                while (true) {
                    val index = highlighted.text.indexOf(searchQuery, start, ignoreCase = true)
                    if (index == -1) break
                    addStyle(
                        SpanStyle(background = searchHighlightColor.copy(alpha = 0.5f), color = Color.Black),
                        index,
                        index + searchQuery.length
                    )
                    start = index + searchQuery.length
                }
            }
        } else highlighted

        return TransformedText(
            withSearch,
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
                            withStyle(SpanStyle(background = codeColor.copy(alpha = 0.1f), fontFamily = FontFamily.Monospace, color = codeColor)) {
                                append(content.substring(i, end + 1))
                            }
                            i = end + 1
                        } else {
                            append(content[i]); i++
                        }
                    }
                    content.startsWith("#", i) -> {
                        val end = content.indexOf("\n", i)
                        val headerEnd = if (end != -1) end else content.length
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor, fontSize = 18.sp)) {
                            append(content.substring(i, headerEnd))
                        }
                        i = headerEnd
                    }
                    else -> {
                        append(content[i]); i++
                    }
                }
            }
        }
    }

    private fun highlightCode(content: String, ext: String): AnnotatedString {
        val keywordColor = Color(0xFF2196F3)
        val stringColor = Color(0xFF4CAF50)
        val commentColor = Color.Gray
        val attrColor = Color(0xFF9C27B0)
        val numberColor = Color(0xFFF44336)

        return buildAnnotatedString {
            var i = 0
            while (i < content.length) {
                val char = content[i]
                when {
                    char == '\"' || char == '\'' -> {
                        val quote = char
                        val end = content.indexOf(quote, i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(color = stringColor)) {
                                append(content.substring(i, end + 1))
                            }
                            i = end + 1
                        } else { append(char); i++ }
                    }
                    content.startsWith("//", i) -> {
                        val end = content.indexOf('\n', i)
                        val comment = if (end != -1) content.substring(i, end) else content.substring(i)
                        withStyle(SpanStyle(color = commentColor)) {
                            append(comment)
                        }
                        i += comment.length
                    }
                    content.startsWith("/*", i) -> {
                        val end = content.indexOf("*/", i + 2)
                        val comment = if (end != -1) content.substring(i, end + 2) else content.substring(i)
                        withStyle(SpanStyle(color = commentColor)) {
                            append(comment)
                        }
                        i += comment.length
                    }
                    ext in listOf("xml", "html") && char == '<' -> {
                        val end = content.indexOf('>', i + 1)
                        if (end != -1) {
                            withStyle(SpanStyle(color = keywordColor)) {
                                append(content.substring(i, end + 1))
                            }
                            i = end + 1
                        } else { append(char); i++ }
                    }
                    char.isDigit() -> {
                        var j = i
                        while (j < content.length && content[j].isDigit()) j++
                        withStyle(SpanStyle(color = numberColor)) {
                            append(content.substring(i, j))
                        }
                        i = j
                    }
                    char.isLetter() -> {
                        var j = i
                        while (j < content.length && (content[j].isLetterOrDigit() || content[j] == '_')) j++
                        val word = content.substring(i, j)
                        val keywords = listOf("val", "var", "fun", "class", "import", "package", "if", "else", "for", "while", "return", "true", "false", "null", "public", "private", "protected", "static", "void", "String", "int", "boolean")
                        if (word in keywords) {
                            withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                                append(word)
                            }
                        } else if (ext == "json" && i > 0 && content.getOrNull(i-1) == '\"') {
                            val k = content.indexOf(':', j)
                            if (k != -1 && content.substring(j, k).isBlank()) {
                                withStyle(SpanStyle(color = attrColor)) {
                                    append(word)
                                }
                            } else append(word)
                        } else {
                            append(word)
                        }
                        i = j
                    }
                    else -> {
                        append(char)
                        i++
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
    onShowInChat: ((LinkThingMessage) -> Unit)? = null,
    searchQuery: String = "",
    isPreviewMode: Boolean = false,
    showMetadata: Boolean = false,
    onPreviewModeChange: (Boolean) -> Unit = {},
    onMetadataToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Simple Undo/Redo Stack
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    
    fun pushUndo(content: String) {
        if (undoStack.isEmpty() || undoStack.last() != content) {
            undoStack.add(content)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
    }

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
            ext == "mail" -> {
                if (parts.size >= 3) {
                    FileMetadata(
                        timestamp = parts[0].toLongOrNull(),
                        senderId = parts[1],
                        receiverId = parts[2],
                        type = context.getString(R.string.type_mail)
                    )
                } else FileMetadata(type = context.getString(R.string.type_mail))
            }
            ext == "info" -> {
                val profile = try { UserProfile.loadFromFile(file) } catch (_: Exception) { null }
                val partsInfo = file.name.removeSuffix(".INFO").split("_")
                val deviceIdFromPath = partsInfo.getOrNull(0) ?: ""
                val discloserIdFromPath = partsInfo.getOrNull(1) ?: ""
                
                FileMetadata(
                    type = context.getString(R.string.metadata_user_profile),
                    profile = profile?.copy(
                        deviceId = profile.deviceId.ifBlank { deviceIdFromPath },
                        discloserId = profile.discloserId.ifBlank { discloserIdFromPath }
                    )
                )
            }
            else -> {
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

    val keywordColor = Color(0xFF2196F3)
    val boldColor = Color(0xFFFF9800)
    val italicColor = Color(0xFF9C27B0)
    val codeColor = Color(0xFFE91E63)

    val markdownTransformation = remember(searchQuery, file.extension, keywordColor, boldColor, italicColor, codeColor) {
        MarkdownVisualTransformation(
            boldColor = boldColor,
            italicColor = italicColor,
            codeColor = codeColor,
            searchQuery = searchQuery,
            extension = file.extension.lowercase()
        )
    }

    LaunchedEffect(file) {
        isLoading = true
        try {
            val content = file.readText()
            textValue = TextFieldValue(content)
            undoStack.clear()
            redoStack.clear()
            undoStack.add(content)
        } catch (e: Exception) {
            textValue = TextFieldValue(context.getString(R.string.error_loading_file, e.message))
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isLoading && !isPreviewMode) {
            Surface(tonalElevation = 4.dp, shadowElevation = 2.dp) {
                Column {
                    // Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp))
                        }

                        IconButton(onClick = { /* Files switch logic */ }) {
                            Icon(Icons.Default.FilterNone, "Files", modifier = Modifier.size(20.dp))
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("edit", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        
                        IconButton(onClick = { 
                            onSave(textValue.text)
                            Toast.makeText(context, R.string.toast_file_saved, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Save, "Save", modifier = Modifier.size(20.dp))
                        }

                        if (file.extension.lowercase() in listOf("msg", "ack") && onShowInChat != null) {
                            IconButton(onClick = { 
                                // Simplified LinkThingMessage creation for jumping to chat
                                val msg = LinkThingMessage(
                                    fileName = file.name,
                                    timestamp = metadata.timestamp ?: 0L,
                                    deviceId = metadata.senderId ?: "",
                                    content = textValue.text,
                                    file = file
                                )
                                onShowInChat(msg)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Chat, "Chat", modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Jump to Start/End
                        IconButton(onClick = { textValue = textValue.copy(selection = TextRange(0)) }) {
                            Icon(Icons.Default.VerticalAlignTop, "Start", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { textValue = textValue.copy(selection = TextRange(textValue.text.length)) }) {
                            Icon(Icons.Default.VerticalAlignBottom, "End", modifier = Modifier.size(20.dp))
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                        IconButton(onClick = {
                            if (undoStack.size > 1) {
                                val current = undoStack.removeAt(undoStack.size - 1)
                                redoStack.add(current)
                                val previous = undoStack.last()
                                textValue = textValue.copy(text = previous)
                            }
                        }, enabled = undoStack.size > 1) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeAt(redoStack.size - 1)
                                undoStack.add(next)
                                textValue = textValue.copy(text = next)
                            }
                        }, enabled = redoStack.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
                        }
                    }
                    
                    // Filename Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${file.name} | UTF-8",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(Modifier.weight(1f))
                        
                        var showEditMenu by remember { mutableStateOf(false) }
                        Text(
                            "MODIFICA", 
                            modifier = Modifier.clickable { showEditMenu = true },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_metadata)) },
                                onClick = {
                                    showEditMenu = false
                                    onMetadataToggle()
                                },
                                leadingIcon = { Icon(Icons.Default.Fingerprint, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_preview)) },
                                onClick = {
                                    showEditMenu = false
                                    onPreviewModeChange(!isPreviewMode)
                                },
                                leadingIcon = { Icon(Icons.Default.Visibility, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Cerca/Sostituisci") },
                                onClick = {
                                    showEditMenu = false
                                    Toast.makeText(context, "Usa la barra di ricerca in alto", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                        }
                    }
                    
                    FormattingToolbar(
                        textValue = textValue,
                        onValueChange = { 
                            pushUndo(it.text)
                            textValue = it 
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
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
                    val lineHeightDp = with(density) { 20.sp.toDp() }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val minHeight = maxHeight
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .heightIn(min = minHeight)
                            ) {
                                // Line Numbers
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(40.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    repeat(lineCount) { index ->
                                        Text(
                                            text = (index + 1).toString(),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.height(lineHeightDp)
                                        )
                                    }
                                }

                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )

                                Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
                                    BasicTextField(
                                        value = textValue,
                                        onValueChange = { 
                                            pushUndo(it.text)
                                            textValue = it 
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 12.dp, top = 16.dp, end = 16.dp, bottom = 64.dp),
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
                                    
                                    // Character and Line Count Overlay
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(8.dp),
                                        tonalElevation = 2.dp
                                    ) {
                                        Text(
                                            text = "Lines: $lineCount | Chars: ${textValue.text.length}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
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
