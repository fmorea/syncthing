package com.fmorea.syncthing.syncthing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.asImageBitmap
import com.fmorea.syncthing.model.Device
import com.fmorea.syncthing.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class FileViewMode {
    GRID, LIST, DASHBOARD
}

enum class FileSortMode {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, TYPE
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileVaultScreen(
    viewModel: LinkThingViewModel,
    modifier: Modifier = Modifier,
    initialFile: java.io.File? = null,
    onShowInChat: (LinkThingMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val friends by viewModel.friends.collectAsState()
    var isDashboard by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(FileViewMode.LIST) }
    var sortMode by remember { mutableStateOf(FileSortMode.TYPE) }

    val labelComm = stringResource(R.string.category_messages)
    val labelNet = stringResource(R.string.category_network)
    val labelMedia = stringResource(R.string.category_media)
    val labelProfile = stringResource(R.string.category_profiles)
    
    var commSubFilter by remember { mutableStateOf("All") }
    
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
                isDashboard = false
                viewMode = FileViewMode.GRID
                // Clear search to show context
                searchQuery = ""
            }
        }
    }

    if (searchQuery.isNotEmpty() && isDashboard) {
        isDashboard = false
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var showAddOptions by remember { mutableStateOf(false) }

    var selectedDeviceForDetails by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val id = viewModel.getLocalDeviceId()
                    val timestamp = System.currentTimeMillis()
                    // Get filename from URI or fallback
                    var fileName = "file_$timestamp"
                    context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    
                    val destFile = File(currentPath, "${timestamp}_${id}_$fileName")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    currentPath = File(currentPath.absolutePath)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.copy_exception), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    var editingFile by remember { mutableStateOf<File?>(null) }
    var isEditorPreviewMode by remember { mutableStateOf(false) }
    var showEditorMetadata by remember { mutableStateOf(false) }
    var editorContentToSave by remember { mutableStateOf("") }
    
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    BackHandler(enabled = editingFile != null || isSearchExpanded || isSelectionMode || activeCategoryLabel != null || !isDashboard) {
        if (isSelectionMode) {
            selectedFiles = emptySet()
        } else if (isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
        } else if (editingFile != null) {
            editingFile = null
            highlightedFile = null
        } else if (activeCategoryLabel != null) {
            activeCategoryLabel = null
            searchQuery = ""
            commSubFilter = "All"
        } else if (!isDashboard) {
            if (currentPath == viewModel.getRootDir()) {
                isDashboard = true
            } else {
                currentPath = currentPath.parentFile ?: viewModel.getRootDir()
            }
        }
    }

    val allFilesOnDisk = remember(currentPath, activeCategoryLabel) {
        if (activeCategoryLabel != null) {
            // Se una categoria è attiva, mostriamo tutti i file del vault ricorsivamente
            viewModel.getRootDir().walkTopDown().filter { it.isFile || it.isDirectory }.toList().toTypedArray()
        } else {
            currentPath.listFiles() ?: emptyArray<File>()
        }
    }
    
    val filteredFiles = remember(allFilesOnDisk, sortMode, searchQuery, isRegexSearch, activeCategoryLabel, commSubFilter) {
        val filtered = allFilesOnDisk.filter { file ->
            val matchesCategory = when (activeCategoryLabel) {
                labelComm -> {
                    val isMsg = file.extension.lowercase() == "msg"
                    val isAck = file.extension.lowercase() == "ack"
                    if (!isMsg && !isAck) return@filter false
                    
                    val isReply = isMsg && file.name.split("_").size >= 4
                    val isDirectMsg = isMsg && !isReply
                    
                    when (commSubFilter) {
                        "Messages" -> isDirectMsg
                        "Replies" -> isReply
                        "Acks" -> isAck
                        else -> true
                    }
                }
                labelNet -> file.extension.lowercase() == "net"
                labelMedia -> {
                    val ext = file.extension.lowercase()
                    val special = listOf("msg", "ack", "net", "info")
                    ext !in special && !file.isDirectory
                }
                labelProfile -> file.extension.lowercase() == "info"
                else -> true
            }
            if (!matchesCategory) return@filter false

            if (searchQuery.isBlank()) true
            else {
                val queries = searchQuery.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
                val (excludeQueries, includeQueries) = queries.partition { it.startsWith("-") }
                
                // Exclusion check
                if (excludeQueries.any { 
                    val extToExclude = it.removePrefix("-")
                    file.extension.equals(extToExclude, ignoreCase = true) 
                }) return@filter false
                
                if (includeQueries.isEmpty()) true
                else if (includeQueries.size > 1 && !isRegexSearch) {
                    includeQueries.any { q -> file.name.contains(q, ignoreCase = true) }
                } else if (isRegexSearch) {
                    try { Regex(searchQuery, RegexOption.IGNORE_CASE).containsMatchIn(file.name) } catch (e: Exception) { file.name.contains(searchQuery, ignoreCase = true) }
                } else {
                    file.name.contains(includeQueries[0], ignoreCase = true)
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
                    isDashboard = isDashboard,
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
                        } else if (editingFile != null) {
                            editingFile = null
                            highlightedFile = null
                        } else if (highlightedFile != null) {
                            highlightedFile = null
                        } else if (isDashboard) { 
                            // Already home
                        } else if (currentPath == viewModel.getRootDir()) { 
                            isDashboard = true 
                        } else { 
                            currentPath = currentPath.parentFile ?: viewModel.getRootDir() 
                        }
                    },
                    onHomeClick = { 
                        isDashboard = true 
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
                            isDashboard = false
                        } else {
                            val msg = context.getString(R.string.invalid_path)
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateTo = { folder ->
                        currentPath = folder
                        isDashboard = false
                    },
                    friends = friends,
                    onConnectedClick = { showConnectedDialog = true },
                    editingFile = editingFile,
                    isEditorPreviewMode = isEditorPreviewMode,
                    showEditorMetadata = showEditorMetadata,
                    onToggleEditorPreview = { isEditorPreviewMode = !isEditorPreviewMode },
                    onToggleEditorMetadata = { showEditorMetadata = !showEditorMetadata },
                    onSaveEditor = {
                        editingFile?.let { file ->
                            try {
                                file.writeText(editorContentToSave)
                                android.widget.Toast.makeText(context, context.getString(R.string.toast_file_saved), android.widget.Toast.LENGTH_SHORT).show()
                                currentPath = File(currentPath.absolutePath)
                            } catch (e: Exception) {
                                val msg = context.getString(R.string.error_saving)
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShowEditorInChat = {
                        editingFile?.let { file ->
                            val msg = viewModel.findMessageForFile(file)
                            if (msg != null) onShowInChat(msg)
                        }
                    },
                    syncStatus = viewModel.syncStatus.collectAsState().value,
                    onSyncClick = { viewModel.forceSync() },
                    onShowId = { viewModel.showMyId() },
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenWebGui = { viewModel.openWebGui() },
                    onOpenChess = {
                        val gameFile = viewModel.shareChessGame()
                        if (gameFile != null) {
                            val intent = Intent(context, com.fmorea.syncthing.chess.ChessActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", gameFile
                                )
                                data = uri
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Bottom bar removed as controls are now context-aware in headers or FAB
        },
        floatingActionButton = {
            val hideFab = isSelectionMode || activeCategoryLabel == labelNet || editingFile != null
            if (!hideFab) {
                FloatingActionButton(
                    onClick = { showAddOptions = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = if (editingFile != null) "EDITOR" to editingFile else if (isDashboard) "DASHBOARD" to null else viewMode.name to null,
                transitionSpec = {
                    val ease = FastOutSlowInEasing
                    if (targetState.first == "EDITOR") {
                        (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(350, easing = ease)) + fadeIn(tween(350))).togetherWith(
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(350, easing = ease)) + fadeOut(tween(350))
                        )
                    } else if (initialState.first == "EDITOR") {
                        (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350, easing = ease)) + fadeIn(tween(350))).togetherWith(
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(350, easing = ease)) + fadeOut(tween(350))
                        )
                    } else {
                        (fadeIn(animationSpec = tween(250, easing = ease)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250, easing = ease))).togetherWith(
                            fadeOut(animationSpec = tween(250, easing = ease)) + scaleOut(targetScale = 0.92f, animationSpec = tween(250, easing = ease))
                        )
                    }
                },
                label = "VaultContentTransition"
            ) { (targetState, targetFile) ->
                when (targetState) {
                    "EDITOR" -> {
                        InternalTextEditor(
                            file = targetFile!!,
                            onDismiss = { 
                                if (highlightedFile != null) {
                                    highlightedFile = null
                                }
                                editingFile = null 
                            },
                            onSave = { editorContentToSave = it },
                            onShowInChat = { msg ->
                                editingFile = null
                                onShowInChat(msg)
                            },
                            searchQuery = searchQuery,
                            isPreviewMode = isEditorPreviewMode,
                            showMetadata = showEditorMetadata,
                            onPreviewModeChange = { isEditorPreviewMode = it },
                            onMetadataToggle = { showEditorMetadata = !showEditorMetadata }
                        )
                    }
                    "DASHBOARD" -> {
                        FileVaultDashboard(
                            rootDir = viewModel.getRootDir(),
                            viewModel = viewModel,
                            onCategoryClick = { extList, label ->
                                if (label == labelMedia) {
                                    activeCategoryLabel = label
                                    searchQuery = extList.joinToString(" ")
                                    currentPath = viewModel.getRootDir()
                                    isDashboard = false
                                } else {
                                    searchQuery = extList.joinToString(" ")
                                    activeCategoryLabel = label
                                    isDashboard = false
                                }
                            },
                            onOpenPath = {
                                currentPath = it
                                isDashboard = false
                            },
                            onEditFile = { editingFile = it },
                            viewMode = viewMode,
                            onToggleView = { viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID },
                            sortMode = sortMode,
                            onSort = { sortMode = it },
                            activeCategoryLabel = activeCategoryLabel,
                            onCategoryUpdate = { activeCategoryLabel = it },
                            searchQuery = searchQuery,
                            onSearchUpdate = { searchQuery = it },
                            onCommSubFilterUpdate = { commSubFilter = it }
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FileVaultListHeader(
                                currentPath = currentPath,
                                rootDir = viewModel.getRootDir(),
                                viewMode = viewMode,
                                onToggleView = { viewMode = if (viewMode == FileViewMode.GRID) FileViewMode.LIST else FileViewMode.GRID },
                                sortMode = sortMode,
                                onSort = { sortMode = it },
                                onGoUp = {
                                    if (currentPath == viewModel.getRootDir()) {
                                        isDashboard = true
                                    } else {
                                        currentPath = currentPath.parentFile ?: viewModel.getRootDir()
                                    }
                                }
                            )
                            
                            if (activeCategoryLabel == labelComm) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val filters = listOf(
                                        stringResource(R.string.none) to "All",
                                        stringResource(R.string.category_messages) to "Messages",
                                        stringResource(R.string.category_replies) to "Replies",
                                        stringResource(R.string.category_acks) to "Acks"
                                    )
                                    filters.forEach { (label, value) ->
                                        FilterChip(
                                            selected = commSubFilter == value,
                                            onClick = { commSubFilter = value },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                            
                            AnimatedContent(
                                targetState = if (activeCategoryLabel == labelNet) "NET" else if (filteredFiles.isEmpty()) "EMPTY" else "LIST_${currentPath.absolutePath}_${viewMode.name}",
                                transitionSpec = {
                                    fadeIn(tween(250, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(250, easing = FastOutSlowInEasing))
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                label = "BrowserContentTransition"
                            ) { browserState ->
                                when {
                                    browserState == "NET" -> {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item {
                                                Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                                                    NetworkGraphView(
                                                        viewModel = viewModel, 
                                                        modifier = Modifier.fillMaxSize(),
                                                        onNodeClick = { selectedDeviceForDetails = it }
                                                    )
                                                }
                                            }
                                            item {
                                                CliqueExplanationBanner()
                                            }
                                            item {
                                                SectionHeader("File di Rete (.net)")
                                            }
                                            items(filteredFiles, key = { it.absolutePath }) { file ->
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
                                            item {
                                                Spacer(modifier = Modifier.height(32.dp))
                                            }
                                        }
                                    }
                                    browserState == "EMPTY" -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                Text(stringResource(R.string.no_files_found), color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                    else -> {
                                        if (viewMode == FileViewMode.GRID) {
                                            LazyVerticalGrid(
                                                state = gridState,
                                                columns = GridCells.Adaptive(minSize = 90.dp),
                                                modifier = Modifier.fillMaxSize(),
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
                                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                    }
                }
            }
        }
    }
    
    if (showAddOptions) {
        AlertDialog(
            onDismissRequest = { showAddOptions = false },
            title = { Text(stringResource(R.string.add)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_folder)) },
                        leadingContent = { Icon(Icons.Default.CreateNewFolder, null) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            showCreateFolderDialog = true 
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.new_file)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            showCreateFileDialog = true 
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.attach_file)) },
                        leadingContent = { Icon(Icons.Default.AttachFile, null) },
                        modifier = Modifier.clickable { 
                            showAddOptions = false
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddOptions = false }) {
                    Text(stringResource(R.string.cancel_title))
                }
            }
        )
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text(stringResource(R.string.new_file)) },
            text = { 
                TextField(
                    value = newFileName, 
                    onValueChange = { newFileName = it }, 
                    placeholder = { Text(stringResource(R.string.filename_hint)) }, 
                    singleLine = true 
                ) 
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isNotBlank()) {
                        val id = viewModel.getLocalDeviceId()
                        val timestamp = System.currentTimeMillis()
                        // Assicurati che abbia un'estensione o usa .msg come default
                        val finalName = if (newFileName.contains(".")) newFileName else "$newFileName.msg"
                        val file = File(currentPath, "${timestamp}_${id}_$finalName")
                        try {
                            file.createNewFile()
                            currentPath = File(currentPath.absolutePath)
                            editingFile = file // Apri l'editor immediatamente
                            showCreateFileDialog = false
                            newFileName = ""
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, context.getString(R.string.file_creation_error), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { showCreateFileDialog = false }) { Text(stringResource(R.string.cancel_title)) } }
        )
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
            title = { Text(stringResource(R.string.online_users)) },
            text = {
                Column {
                    if (connectedFriends.isEmpty()) {
                        Text(stringResource(R.string.no_users_online), color = MaterialTheme.colorScheme.outline)
                    } else {
                        connectedFriends.forEach { friend ->
                            ListItem(
                                headlineContent = { Text(friend.getDisplayName()) },
                                leadingContent = { Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                                supportingContent = { Text("${stringResource(R.string.connected)} • ${friend.deviceID.take(8)}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectedDialog = false }) { Text(stringResource(R.string.action_close)) }
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

    if (selectedDeviceForDetails != null) {
        val deviceId = selectedDeviceForDetails!!
        val device = friends.find { it.deviceID == deviceId } ?: if (deviceId == viewModel.getLocalDeviceId()) viewModel.localDevice.collectAsState().value else null
        val profile = viewModel.friendProfiles.collectAsState().value[deviceId] ?: if (deviceId == viewModel.getLocalDeviceId()) viewModel.userProfile.collectAsState().value else null
        
        val qrBitmap = remember(deviceId) {
            try {
                val size = 512
                val bitMatrix = MultiFormatWriter().encode(deviceId, BarcodeFormat.QR_CODE, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bitmap
            } catch (e: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = { selectedDeviceForDetails = null },
            title = { Text(profile?.getDisplayName() ?: device?.getDisplayName() ?: deviceId.take(8)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(240.dp).padding(8.dp)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code Device ID",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(150.dp), tint = Color.Black)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = deviceId, 
                        style = MaterialTheme.typography.bodySmall, 
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (profile != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.associated_profile), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${stringResource(R.string.profile_first_name)}: ${profile.firstName} ${profile.lastName}", style = MaterialTheme.typography.bodyMedium)
                                if (profile.address.isNotBlank()) Text("${stringResource(R.string.profile_address)}: ${profile.address}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDeviceForDetails = null }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVaultTopBar(
    currentPath: File,
    rootDir: File,
    isDashboard: Boolean,
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
    onConnectedClick: () -> Unit = {},
    editingFile: File? = null,
    isEditorPreviewMode: Boolean = false,
    showEditorMetadata: Boolean = false,
    onToggleEditorPreview: () -> Unit = {},
    onToggleEditorMetadata: () -> Unit = {},
    onSaveEditor: () -> Unit = {},
    onShowEditorInChat: () -> Unit = {},
    syncStatus: String = "",
    onSyncClick: () -> Unit = {},
    onShowId: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenWebGui: () -> Unit = {},
    onOpenChess: () -> Unit = {}
) {
    var isPathEditing by remember { mutableStateOf(false) }
    var editedPath by remember { mutableStateOf(currentPath.absolutePath) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                            placeholder = { Text(if (editingFile != null) "Cerca nel file..." else stringResource(R.string.search_hint)) },
                            trailingIcon = {
                                Row {
                                    if (editingFile == null) {
                                        IconButton(onClick = onToggleRegex) {
                                            Icon(Icons.Default.Code, null, tint = if (isRegexSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                        }
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
            } else if (editingFile != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(editingFile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(editingFile.parentFile?.name ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        if (editingFile.name.endsWith(".msg")) {
                            IconButton(onClick = onShowEditorInChat) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Mostra in Chat")
                            }
                        }
                        IconButton(onClick = onToggleEditorMetadata) {
                            Icon(
                                if (showEditorMetadata) Icons.Default.Fingerprint else Icons.Default.Fingerprint,
                                contentDescription = "Metadati",
                                tint = if (showEditorMetadata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = onToggleEditorPreview) {
                            Icon(
                                if (isEditorPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = if (isEditorPreviewMode) "Modifica" else "Anteprima"
                            )
                        }
                        IconButton(onClick = { onSearchExpandedChange(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Cerca nel testo")
                        }
                        IconButton(onClick = onSaveEditor) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_title))
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
                        Column {
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                syncStatus,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
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

                    IconButton(onClick = onSyncClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(20.dp))
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Il mio ID (QR)") },
                                onClick = {
                                    showMenu = false
                                    onShowId()
                                },
                                leadingIcon = { Icon(Icons.Default.QrCode, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Impostazioni App") },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Console Avanzata (Web)") },
                                onClick = {
                                    showMenu = false
                                    onOpenWebGui()
                                },
                                leadingIcon = { Icon(Icons.Default.Public, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Gioca a Scacchi") },
                                onClick = {
                                    showMenu = false
                                    onOpenChess()
                                },
                                leadingIcon = { Icon(Icons.Default.Extension, null) }
                            )
                        }
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
    onSort: (FileSortMode) -> Unit,
    activeCategoryLabel: String?,
    onCategoryUpdate: (String?) -> Unit,
    searchQuery: String,
    onSearchUpdate: (String) -> Unit,
    onCommSubFilterUpdate: (String) -> Unit
) {
    val stats = remember(rootDir) {
        val allFiles = rootDir.walkTopDown().filter { it.isFile }.toList()
        val special = listOf("msg", "ack", "net", "info")
        mapOf(
            "Messages" to allFiles.count { file -> 
                file.extension.lowercase() == "msg" && file.name.split("_").size < 4 
            },
            "Replies" to allFiles.count { file -> 
                file.extension.lowercase() == "msg" && file.name.split("_").size >= 4 
            },
            "Network" to allFiles.count { it.extension.lowercase() == "net" },
            "Acks" to allFiles.count { it.extension.lowercase() == "ack" },
            "Media" to allFiles.count { it.extension.lowercase() !in special },
            "Profiles" to allFiles.count { it.extension.lowercase() == "info" }
        )
    }

    val labelComm = stringResource(R.string.category_messages)
    val labelNet = stringResource(R.string.category_network)
    val labelMedia = stringResource(R.string.category_media)
    val labelProfile = stringResource(R.string.category_profiles)
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Categories Grid
            val commCount = (stats["Messages"] ?: 0) + (stats["Replies"] ?: 0) + (stats["Acks"] ?: 0)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryItem(
                    icon = Icons.AutoMirrored.Filled.Message, 
                    label = labelComm, 
                    count = commCount, 
                    color = Color(0xFF2196F3), 
                    modifier = Modifier.weight(1f)
                ) { 
                    onCategoryUpdate(labelComm)
                    onCommSubFilterUpdate("All")
                    onToggleView() // Force refresh
                }
                CategoryItem(Icons.Default.Lan, labelNet, stats["Network"] ?: 0, Color(0xFF4CAF50), Modifier.weight(1f)) { 
                    onCategoryUpdate(labelNet)
                    onToggleView() // Force refresh
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryItem(Icons.Default.PermMedia, labelMedia, stats["Media"] ?: 0, Color(0xFF9C27B0), Modifier.weight(1f)) { 
                    onCategoryUpdate(labelMedia)
                    onSearchUpdate("-msg -ack -net -info")
                    onToggleView() // Force refresh
                }
                CategoryItem(Icons.Default.AccountCircle, labelProfile, stats["Profiles"] ?: 0, Color(0xFF795548), Modifier.weight(1f)) { 
                    onCategoryUpdate(labelProfile)
                    onToggleView() // Force refresh
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

                val allFiles = remember(rootDir, sortMode) {
                    rootDir.listFiles()?.sortedWith { f1, f2 ->
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
                    } ?: emptyList()
                }
                
                if (allFiles.isEmpty()) {
                    Text(
                        "Nessun file nel root", 
                        modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (viewMode == FileViewMode.GRID) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        allFiles.forEach { file ->
                            Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                                val context = LocalContext.current
                                FileVaultItem(
                                    file = file,
                                    viewMode = FileViewMode.GRID,
                                    onTap = { handleFileClick(file, context, viewModel, onOpenPath, onEditFile) },
                                    onRename = {}, // Dashboard preview doesn't need all actions
                                    onDelete = {},
                                    onShowInChat = {}
                                )
                            }
                        }
                    }
                } else {
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
        // Whitelist safe text extensions for the internal editor
        val textExtensions = listOf("msg", "ack", "net", "info", "chess", "txt", "log", "md", "json", "xml", "html")
        
        if (ext in textExtensions) {
            onEditFile(file)
        } else {
            // All other formats (PDF, APK, large images, etc.) are treated as media and opened externally
            com.fmorea.syncthing.util.FileUtils.openFile(context, file.absolutePath)
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

    val isReply = remember(file.name) { file.extension.lowercase() == "msg" && file.name.split("_").size >= 4 }
    val isAck = remember(file.name) { file.extension.lowercase() == "ack" }

    val icon = when {
        file.isDirectory -> Icons.Default.Folder
        isReply -> Icons.AutoMirrored.Filled.Reply
        file.name.endsWith(".msg") -> Icons.AutoMirrored.Filled.Message
        isAck -> Icons.Default.DoneAll
        file.name.endsWith(".net") -> Icons.Default.Lan
        file.name.endsWith(".INFO") -> Icons.Default.Info
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    
    val baseIconColor = when {
        file.isDirectory -> Color(0xFFFFC107)
        isReply -> Color(0xFF00BCD4)
        isAck -> Color(0xFFFF9800)
        file.name.endsWith(".msg") -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.secondary
    }
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
fun CliqueExplanationBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mesh Discovery (Clique)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Questa rete tende a una Clique (Grafo Completo). Ogni nodo dichiara i propri vicini tramite file .net. In una rete di N nodi, a convergenza troverai N*(N-1) file, garantendo che ogni partecipante si connetta automaticamente a tutti gli altri senza server centrali.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 14.sp
                )
            }
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
