package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmorea.syncthing.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class FileViewMode {
    GRID, LIST, DASHBOARD
}

enum class FileSortMode {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, TYPE
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileVaultScreen(
    viewModel: LinkThingViewModel,
    modifier: Modifier = Modifier,
    initialFile: File? = null,
    onShowInChat: (LinkThingMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val friends by viewModel.friends.collectAsState()
    var viewMode by remember { mutableStateOf(FileViewMode.DASHBOARD) }
    var sortMode by remember { mutableStateOf(FileSortMode.TYPE) }
    
    var searchQuery by remember { mutableStateOf("") }
    var activeCategoryLabel by remember { mutableStateOf<String?>(null) }
    var showConnectedDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isRegexSearch by remember { mutableStateOf(false) }
    
    var currentPath by remember { mutableStateOf(viewModel.getRootDir()) }
    var highlightedFile by remember { mutableStateOf<File?>(null) }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    val isSelectionMode = selectedFiles.isNotEmpty()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(initialFile) {
        initialFile?.let {
            if (it.exists()) {
                currentPath = it.parentFile ?: viewModel.getRootDir()
                highlightedFile = it
                viewMode = FileViewMode.GRID
                // Clear search to show context
                searchQuery = ""
            }
        }
    }

    if (searchQuery.isNotEmpty() && viewMode == FileViewMode.DASHBOARD) {
        viewMode = FileViewMode.GRID
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var editingFile by remember { mutableStateOf<File?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val allFilesOnDisk = remember(currentPath) { currentPath.listFiles() ?: emptyArray<File>() }
    
    val filteredFiles = remember(allFilesOnDisk, sortMode, searchQuery, isRegexSearch) {
        val filtered = allFilesOnDisk.filter { file ->
            if (searchQuery.isBlank()) true
            else {
                val queries = searchQuery.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
                if (queries.size > 1 && !isRegexSearch) {
                    queries.any { q -> file.name.contains(q, ignoreCase = true) }
                } else if (isRegexSearch) {
                    try { Regex(searchQuery, RegexOption.IGNORE_CASE).containsMatchIn(file.name) } catch (e: Exception) { file.name.contains(searchQuery, ignoreCase = true) }
                } else {
                    file.name.contains(searchQuery, ignoreCase = true)
                }
            }
        }
        
        filtered.sortedWith { f1, f2 ->
            // Always keep directories at the top, except when sorting specifically by something else that might override it?
            // Actually, most file managers keep folders first.
            if (f1.isDirectory && !f2.isDirectory) return@sortedWith -1
            if (!f1.isDirectory && f2.isDirectory) return@sortedWith 1
            
            when (sortMode) {
                FileSortMode.TYPE -> compareBy<File>({ it.extension.lowercase() }, { it.name.lowercase() }).compare(f1, f2)
                FileSortMode.NAME_ASC -> f1.name.lowercase().compareTo(f2.name.lowercase())
                FileSortMode.NAME_DESC -> f2.name.lowercase().compareTo(f1.name.lowercase())
                FileSortMode.DATE_ASC -> f1.lastModified().compareTo(f2.lastModified())
                FileSortMode.DATE_DESC -> f2.lastModified().compareTo(f1.lastModified())
                FileSortMode.SIZE_ASC -> f1.length().compareTo(f2.length())
                FileSortMode.SIZE_DESC -> f2.length().compareTo(f1.length())
            }
        }
    }

    LaunchedEffect(highlightedFile, filteredFiles) {
        if (highlightedFile != null) {
            val index = filteredFiles.indexOfFirst { it.absolutePath == highlightedFile?.absolutePath }
            if (index >= 0) {
                if (viewMode == FileViewMode.GRID) {
                    gridState.animateScrollToItem(index)
                } else if (viewMode == FileViewMode.LIST) {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    if (editingFile != null) {
        InternalTextEditor(
            file = editingFile!!,
            onDismiss = { 
                if (highlightedFile != null) {
                    highlightedFile = null
                }
                editingFile = null 
            },
            onSave = { newContent ->
                try {
                    editingFile!!.writeText(newContent)
                    currentPath = File(currentPath.absolutePath)
                } catch (e: Exception) {
                    val msg = context.getString(R.string.error_saving)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onShowInChat = { msg ->
                editingFile = null
                onShowInChat(msg)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles = emptySet() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            selectedFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                            selectedFiles = emptySet()
                            currentPath = File(currentPath.absolutePath)
                        }) {
                            Icon(Icons.Default.Delete, null)
                        }
                    }
                )
            } else {
                FileVaultTopBar(
                    currentPath = currentPath,
                    rootDir = viewModel.getRootDir(),
                    viewMode = viewMode,
                    searchQuery = searchQuery,
                    isSearchExpanded = isSearchExpanded,
                    onSearchExpandedChange = { isSearchExpanded = it },
                    isRegexSearch = isRegexSearch,
                    onSearchQueryChange = { 
                        searchQuery = it
                        if (it.isEmpty()) activeCategoryLabel = null
                    },
                    onToggleRegex = { isRegexSearch = !isRegexSearch },
                    onBack = { 
                        if (isSearchExpanded) { 
                            isSearchExpanded = false
                            searchQuery = "" 
                            activeCategoryLabel = null
                        } else if (highlightedFile != null) {
                            highlightedFile = null
                        } else if (viewMode == FileViewMode.DASHBOARD) { 
                            // Already home
                        } else if (currentPath == viewModel.getRootDir()) { 
                            viewMode = FileViewMode.DASHBOARD 
                        } else { 
                            currentPath = currentPath.parentFile ?: viewModel.getRootDir() 
                        }
                    },
                    onHomeClick = { 
                        viewMode = FileViewMode.DASHBOARD 
                        searchQuery = ""
                        activeCategoryLabel = null
                        isSearchExpanded = false
                        currentPath = viewModel.getRootDir()
                    },
                    activeCategoryLabel = activeCategoryLabel,
                    onClearCategory = { 
                        activeCategoryLabel = null
                        searchQuery = ""
                    },
                    onPathEdit = { newPath ->
                        val f = File(newPath)
                        if (f.exists() && f.isDirectory) {
                            currentPath = f
                            viewMode = FileViewMode.GRID
                        } else {
                            val msg = context.getString(R.string.invalid_path)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateTo = { folder ->
                        currentPath = folder
                        viewMode = FileViewMode.GRID
                    },
                    friends = friends,
                    onConnectedClick = { showConnectedDialog = true }
                )
            }
        },
        bottomBar = {
            // Bottom bar removed as controls are now context-aware in headers or FAB
        },
        floatingActionButton = {
            if (viewMode != FileViewMode.DASHBOARD && !isSelectionMode) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewMode == FileViewMode.DASHBOARD) {
                FileVaultDashboard(
                    rootDir = viewModel.getRootDir(),
                    viewModel = viewModel,
                    onCategoryClick = { extList, label ->
                        searchQuery = extList.joinToString(" ")
                        activeCategoryLabel = label
                        viewMode = FileViewMode.GRID
                    },
                    onOpenPath = {
                        currentPath = it
                        viewMode = FileViewMode.GRID
                    },
                    onEditFile = { editingFile = it },
                    viewMode = viewMode,
                    onToggleView = { viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID },
                    sortMode = sortMode,
                    onSort = { sortMode = it }
                )
            } else {
                FileVaultListHeader(
                    currentPath = currentPath,
                    rootDir = viewModel.getRootDir(),
                    viewMode = viewMode,
                    onToggleView = { viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID },
                    sortMode = sortMode,
                    onSort = { sortMode = it },
                    onGoUp = {
                        if (currentPath == viewModel.getRootDir()) {
                            viewMode = FileViewMode.DASHBOARD
                        } else {
                            currentPath = currentPath.parentFile ?: viewModel.getRootDir()
                        }
                    }
                )
                
                val labelNet = stringResource(R.string.category_network)
                if (activeCategoryLabel == labelNet) {
                    NetworkGraphView(viewModel = viewModel, modifier = Modifier.weight(1f))
                } else if (filteredFiles.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            Text(stringResource(R.string.no_files_found), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    if (viewMode == FileViewMode.GRID) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 90.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(filteredFiles, key = { it.absolutePath }) { file ->
                                val isSelected = selectedFiles.contains(file)
                                val isHighlighted = highlightedFile?.absolutePath == file.absolutePath
                                FileVaultItem(
                                    file = file,
                                    viewMode = FileViewMode.GRID,
                                    highlighted = isHighlighted,
                                    selected = isSelected,
                                    onTap = { 
                                        if (isSelectionMode) {
                                            selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                        } else {
                                            highlightedFile = null
                                            handleFileClick(file, context, viewModel, { currentPath = it }, { editingFile = it }) 
                                        }
                                    },
                                    onLongClick = {
                                        selectedFiles = selectedFiles + file
                                    },
                                    onRename = { fileToRename = it; renameValue = it.name },
                                    onDelete = { fileToDelete = it },
                                    onShowInChat = { target ->
                                        val msg = viewModel.findMessageForFile(target)
                                        if (msg != null) onShowInChat(msg)
                                        else {
                                            val errorMsg = context.getString(R.string.message_not_found)
                                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            items(filteredFiles) { file ->
                                val isSelected = selectedFiles.contains(file)
                                val isHighlighted = highlightedFile?.absolutePath == file.absolutePath
                                FileVaultItem(
                                    file = file,
                                    viewMode = FileViewMode.LIST,
                                    highlighted = isHighlighted,
                                    selected = isSelected,
                                    onTap = { 
                                        if (isSelectionMode) {
                                            selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                        } else {
                                            highlightedFile = null
                                            handleFileClick(file, context, viewModel, { currentPath = it }, { editingFile = it }) 
                                        }
                                    },
                                    onLongClick = {
                                        selectedFiles = selectedFiles + file
                                    },
                                    onRename = { fileToRename = it; renameValue = it.name },
                                    onDelete = { fileToDelete = it },
                                    onShowInChat = { target ->
                                        val msg = viewModel.findMessageForFile(target)
                                        if (msg != null) onShowInChat(msg)
                                        else {
                                            val errorMsg = context.getString(R.string.message_not_found)
                                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { TextField(value = newFolderName, onValueChange = { newFolderName = it }, placeholder = { Text(stringResource(R.string.folder_name_hint)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        File(currentPath, newFolderName).mkdirs()
                        currentPath = File(currentPath.absolutePath) 
                        showCreateFolderDialog = false
                        newFolderName = ""
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { TextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val dest = File(fileToRename!!.parentFile, renameValue)
                    if (fileToRename!!.renameTo(dest)) {
                        currentPath = File(currentPath.absolutePath)
                        fileToRename = null
                    }
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = { TextButton(onClick = { fileToRename = null }) { Text(stringResource(R.string.cancel_title)) } }
        )
    }

    if (showConnectedDialog) {
        val connectedFriends = friends.filter { it.numConnections > 0 }
        AlertDialog(
            onDismissRequest = { showConnectedDialog = false },
            title = { Text("Utenti Online") },
            text = {
                Column {
                    if (connectedFriends.isEmpty()) {
                        Text("Nessun utente connesso al momento.", color = MaterialTheme.colorScheme.outline)
                    } else {
                        connectedFriends.forEach { friend ->
                            ListItem(
                                headlineContent = { Text(friend.getDisplayName()) },
                                leadingContent = { Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("Connesso • ${friend.deviceID.take(8)}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectedDialog = false }) { Text("Chiudi") }
            }
        )
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm_msg, fileToDelete!!.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fileToDelete!!.isDirectory) fileToDelete!!.deleteRecursively() else fileToDelete!!.delete()
                        currentPath = File(currentPath.absolutePath)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text(stringResource(R.string.cancel_title)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVaultTopBar(
    currentPath: File,
    rootDir: File,
    viewMode: FileViewMode,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    isRegexSearch: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleRegex: () -> Unit,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    onPathEdit: (String) -> Unit,
    onNavigateTo: (File) -> Unit,
    activeCategoryLabel: String? = null,
    onClearCategory: () -> Unit = {},
    friends: List<com.fmorea.syncthing.model.Device> = emptyList(),
    onConnectedClick: () -> Unit = {}
) {
    var isPathEditing by remember { mutableStateOf(false) }
    var editedPath by remember { mutableStateOf(currentPath.absolutePath) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            if (isSearchExpanded) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = onToggleRegex) {
                                        Icon(Icons.Default.Code, null, tint = if (isRegexSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(onClick = { onSearchExpandedChange(false); onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onSearchExpandedChange(false); onSearchQueryChange("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    }
                )
            } else if (isPathEditing) {
                TopAppBar(
                    title = {
                        TextField(
                            value = editedPath,
                            onValueChange = { editedPath = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    onPathEdit(editedPath)
                                    isPathEditing = false 
                                }) {
                                    Icon(Icons.Default.Check, null)
                                }
                            },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isPathEditing = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onHomeClick)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Home, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LiveConnectionBadge(
                            friends = friends,
                            onClick = onConnectedClick
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pathParts = remember(currentPath, rootDir) {
                            val rootPath = rootDir.absolutePath
                            val current = currentPath.absolutePath
                            if (current == rootPath) emptyList()
                            else if (current.startsWith(rootPath)) {
                                current.removePrefix(rootPath).split(File.separator).filter { it.isNotBlank() }
                            } else {
                                // Outside root? Show full path or just parts
                                current.split(File.separator).filter { it.isNotBlank() }
                            }
                        }
                        
                        InputChip(
                            selected = currentPath == rootDir,
                            onClick = { onNavigateTo(rootDir) },
                            label = { Text("Vault") },
                            leadingIcon = { Icon(Icons.Default.FolderZip, null, modifier = Modifier.size(16.dp)) },
                            border = null,
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        
                        var accumulatedPath = rootDir
                        pathParts.forEach { part ->
                            accumulatedPath = File(accumulatedPath, part)
                            val thisPath = accumulatedPath
                            
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            
                            InputChip(
                                selected = currentPath == thisPath,
                                onClick = { onNavigateTo(thisPath) },
                                label = { Text(part) },
                                border = null,
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        if (activeCategoryLabel != null) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            InputChip(
                                selected = true,
                                onClick = onClearCategory,
                                label = { Text(activeCategoryLabel) },
                                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) },
                                border = null,
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }
                    
                    IconButton(onClick = { onSearchExpandedChange(true) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FileVaultDashboard(
    rootDir: File,
    viewModel: LinkThingViewModel,
    onCategoryClick: (List<String>, String) -> Unit,
    onOpenPath: (File) -> Unit,
    onEditFile: (File) -> Unit,
    viewMode: FileViewMode,
    onToggleView: () -> Unit,
    sortMode: FileSortMode,
    onSort: (FileSortMode) -> Unit
) {
    val stats = remember(rootDir) {
        val allFiles = rootDir.walkTopDown().filter { it.isFile }.toList()
        mapOf(
            "Messages" to allFiles.count { file -> 
                file.extension.lowercase() == "msg" && file.name.split("_").size < 4 
            },
            "Replies" to allFiles.count { file -> 
                file.extension.lowercase() == "msg" && file.name.split("_").size >= 4 
            },
            "Network" to allFiles.count { it.extension.lowercase() == "net" },
            "Acks" to allFiles.count { it.extension.lowercase() == "ack" },
            "Media" to allFiles.count { it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "mp3", "m4a", "wav", "ogg", "mp4", "mkv", "avi") },
            "Profiles" to allFiles.count { it.extension.lowercase() == "info" },
            "Chess" to allFiles.count { it.extension.lowercase() == "chess" },
            "Others" to allFiles.count { it.extension.lowercase() !in listOf("msg", "net", "ack", "jpg", "jpeg", "png", "webp", "gif", "mp3", "m4a", "wav", "ogg", "mp4", "mkv", "avi", "info", "chess") }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Categories Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val labelMsg = stringResource(R.string.category_messages)
                CategoryItem(Icons.AutoMirrored.Filled.Message, labelMsg, stats["Messages"] ?: 0, Color(0xFF2196F3), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("msg"), labelMsg)
                }
                val labelReply = stringResource(R.string.category_replies)
                CategoryItem(Icons.AutoMirrored.Filled.Reply, labelReply, stats["Replies"] ?: 0, Color(0xFF00BCD4), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("msg"), labelReply) 
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val labelNet = stringResource(R.string.category_network)
                CategoryItem(Icons.Default.Lan, labelNet, stats["Network"] ?: 0, Color(0xFF4CAF50), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("net"), labelNet) 
                }
                val labelAck = stringResource(R.string.category_acks)
                CategoryItem(Icons.Default.DoneAll, labelAck, stats["Acks"] ?: 0, Color(0xFFFF9800), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("ack"), labelAck) 
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val labelMedia = stringResource(R.string.category_media)
                CategoryItem(Icons.Default.PermMedia, labelMedia, stats["Media"] ?: 0, Color(0xFF9C27B0), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("jpg", "jpeg", "png", "webp", "mp3", "m4a", "mp4"), labelMedia) 
                }
                val labelProfile = stringResource(R.string.category_profiles)
                CategoryItem(Icons.Default.AccountCircle, labelProfile, stats["Profiles"] ?: 0, Color(0xFF795548), Modifier.weight(1f)) { 
                    onCategoryClick(listOf("info"), labelProfile) 
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.vault_occupancy), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val vaultSize = remember(rootDir) {
                        rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    }
                    val totalSpace = rootDir.totalSpace
                    val progress = if (totalSpace > 0) vaultSize.toFloat() / totalSpace else 0f
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatSize(vaultSize), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val percent = (progress * 100).toInt()
                        Text("$percent% usato", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FileVaultListHeader(
                    currentPath = rootDir,
                    rootDir = rootDir,
                    viewMode = viewMode,
                    onToggleView = onToggleView,
                    sortMode = sortMode,
                    onSort = onSort,
                    title = stringResource(R.string.all_files),
                    showGoUp = false
                )

                val allFiles = remember(rootDir) { rootDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList() }
                
                if (allFiles.isEmpty()) {
                    Text(
                        "Nessun file nel root", 
                        modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                allFiles.forEach { file ->
                    val context = LocalContext.current
                    FileVaultItem(
                        file = file,
                        viewMode = FileViewMode.LIST,
                        onTap = { handleFileClick(file, context, viewModel, onOpenPath, onEditFile) },
                        onRename = {}, // Dashboard preview doesn't need all actions
                        onDelete = {},
                        onShowInChat = {}
                    )
                }
            }
        }
    }
}

@Composable
fun FileVaultListHeader(
    currentPath: File,
    rootDir: File,
    viewMode: FileViewMode,
    onToggleView: () -> Unit,
    sortMode: FileSortMode,
    onSort: (FileSortMode) -> Unit,
    title: String? = null,
    showGoUp: Boolean = true,
    onGoUp: () -> Unit = {}
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showGoUp && currentPath != rootDir) {
                IconButton(onClick = onGoUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = title ?: currentPath.name.ifEmpty { "Vault" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (showGoUp) 0.dp else 16.dp)
            )
        }
        
        Row {
            IconButton(onClick = onToggleView) {
                Icon(
                    if (viewMode == FileViewMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    val sortIcon = when(sortMode) {
                        FileSortMode.NAME_ASC, FileSortMode.NAME_DESC -> Icons.Default.SortByAlpha
                        FileSortMode.DATE_ASC, FileSortMode.DATE_DESC -> Icons.Default.Schedule
                        FileSortMode.SIZE_ASC, FileSortMode.SIZE_DESC -> Icons.Default.Storage
                        FileSortMode.TYPE -> Icons.Default.Category
                    }
                    Icon(sortIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, null) },
                        text = { Text(stringResource(R.string.sort_name_asc)) }, 
                        onClick = { onSort(FileSortMode.NAME_ASC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Schedule, null) },
                        text = { Text(stringResource(R.string.sort_date_desc)) }, 
                        onClick = { onSort(FileSortMode.DATE_DESC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Storage, null) },
                        text = { Text(stringResource(R.string.sort_size_desc)) }, 
                        onClick = { onSort(FileSortMode.SIZE_DESC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Category, null) },
                        text = { Text(stringResource(R.string.sort_type)) }, 
                        onClick = { onSort(FileSortMode.TYPE); showSortMenu = false }
                    )
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun CategoryItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun handleFileClick(
    file: File, 
    context: android.content.Context, 
    viewModel: LinkThingViewModel,
    onPathChange: (File) -> Unit,
    onEditFile: (File) -> Unit
) {
    if (file.isDirectory) {
        onPathChange(file)
    } else {
        val ext = file.extension.lowercase()
        val mediaExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "m4a", "mp3", "wav", "ogg")
        if (ext in mediaExtensions) {
            com.fmorea.syncthing.util.FileUtils.openFile(context, file.absolutePath)
        } else {
            onEditFile(file)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileVaultItem(
    file: File, 
    viewMode: FileViewMode,
    highlighted: Boolean = false,
    selected: Boolean = false,
    onTap: () -> Unit, 
    onLongClick: () -> Unit = {},
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onShowInChat: (File) -> Unit
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }

    val icon = when {
        file.isDirectory -> Icons.Default.Folder
        file.name.endsWith(".msg") -> Icons.AutoMirrored.Filled.Message
        file.name.endsWith(".ack") -> Icons.Default.DoneAll
        file.name.endsWith(".net") -> Icons.Default.Lan
        file.name.endsWith(".INFO") -> Icons.Default.Info
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    
    val baseIconColor = if (file.isDirectory) Color(0xFFFFC107) else MaterialTheme.colorScheme.secondary
    val iconColor = if (highlighted || selected) MaterialTheme.colorScheme.error else baseIconColor
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        highlighted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Box(modifier = Modifier.background(backgroundColor, shape = RoundedCornerShape(12.dp))) {
        if (viewMode == FileViewMode.GRID) {
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { 
                            onLongClick()
                            if (!selected) showContextMenu = true 
                        }
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val ext = remember(file.name) { file.extension.lowercase() }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    if (file.isDirectory) {
                        Icon(icon, null, modifier = Modifier.size(56.dp), tint = iconColor)
                    } else if (ext in listOf("jpg", "jpeg", "png", "webp", "gif")) {
                        AsyncImage(
                            file = file,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    } else if (ext == "info") {
                        InfoFilePreview(file)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                Icon(icon, null, modifier = Modifier.size(32.dp), tint = iconColor.copy(alpha = 0.5f))
                            }
                        }
                    }
                    
                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp).offset(x = 4.dp, y = (-4).dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 32.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
            val lastModified = remember(file) { dateFormat.format(Date(file.lastModified())) }
            ListItem(
                modifier = Modifier.combinedClickable(
                    onClick = onTap,
                    onLongClick = { 
                        onLongClick()
                        if (!selected) showContextMenu = true 
                    }
                ),
                headlineContent = { 
                    Text(
                        file.name, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
                    ) 
                },
                supportingContent = {
                    val ext = file.extension.lowercase()
                    if (ext == "info") {
                        val profile = remember(file) { UserProfile.loadFromFile(file) }
                        Text("${profile.getDisplayName()} • $lastModified")
                    } else {
                        Text(if (file.isDirectory) stringResource(R.string.folder_label_vault) else "${formatSize(file.length())} • $lastModified")
                    }
                },
                leadingContent = { 
                    Box {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(32.dp))
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle, 
                                null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape)
                            )
                        }
                    }
                },
                trailingContent = {
                    IconButton(onClick = { showContextMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_in_chat)) },
                onClick = {
                    showContextMenu = false
                    onShowInChat(file)
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    showContextMenu = false
                    onRename(file)
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            if (!file.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    onClick = {
                        showContextMenu = false
                        val intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file
                            )
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            type = context.contentResolver.getType(uri) ?: "*/*"
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, null))
                    },
                    leadingIcon = { Icon(Icons.Default.Share, null) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    showContextMenu = false
                    onDelete(file)
                },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )
        }
    }
}

@Composable
private fun InfoFilePreview(file: File) {
    val profile = remember(file) {
        UserProfile.loadFromFile(file)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.size(64.dp)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = profile.getDisplayName(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
        }
    }
}
