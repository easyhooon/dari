package com.easyhooon.dari.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.Dari
import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.MessageStatus
import com.easyhooon.dari.export.DariExporter
import com.easyhooon.dari.export.ExportFormat
import androidx.compose.foundation.isSystemInDarkTheme
import com.easyhooon.dari.ui.components.MessageListItem
import com.easyhooon.dari.ui.components.SettingsBottomSheet
import com.easyhooon.dari.ui.theme.ApplyDariSystemBars
import com.easyhooon.dari.ui.theme.DariTheme
import com.easyhooon.dari.ui.theme.DariTopBarColors
import com.easyhooon.dari.ui.theme.color

/**
 * Activity displaying the list of bridge messages.
 * Accessed by tapping the notification or launching directly.
 */
class DariActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            Dari.showNotification()
        }
    }

    // Entries captured at launch time, consumed in the SAF callback.
    // Two launchers are registered (one per format) so document providers
    // receive the correct MIME type hint — CreateDocument fixes the type at
    // registration, not per-launch.
    private var pendingSaveEntries: List<MessageEntry> = emptyList()

    private val saveTextDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(DariExporter.mimeTypeFor(ExportFormat.TEXT)),
    ) { uri: Uri? -> handleSaveResult(uri, ExportFormat.TEXT) }

    private val saveJsonDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(DariExporter.mimeTypeFor(ExportFormat.JSON)),
    ) { uri: Uri? -> handleSaveResult(uri, ExportFormat.JSON) }

    private fun handleSaveResult(uri: Uri?, format: ExportFormat) {
        val entries = pendingSaveEntries
        pendingSaveEntries = emptyList()
        if (uri == null || entries.isEmpty()) return
        lifecycleScope.launch {
            DariExporter.saveToUri(this@DariActivity, uri, entries, format)
        }
    }

    internal fun launchSave(entries: List<MessageEntry>, format: ExportFormat) {
        if (entries.isEmpty()) return
        pendingSaveEntries = entries
        val launcher = when (format) {
            ExportFormat.TEXT -> saveTextDocumentLauncher
            ExportFormat.JSON -> saveJsonDocumentLauncher
        }
        launcher.launch(DariExporter.suggestedFilename(format))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            val darkMode by Dari.preferences.darkModeFlow().collectAsStateWithLifecycle(
                initialValue = Dari.preferences.darkMode,
            )
            val isDark = darkMode ?: isSystemInDarkTheme()
            ApplyDariSystemBars(isDark)
            DariTheme(darkTheme = darkMode) {
                val entries by Dari.repository.entries.collectAsStateWithLifecycle()
                val shakeToOpen by Dari.preferences.shakeToOpenFlow().collectAsStateWithLifecycle(
                    initialValue = Dari.preferences.shakeToOpen,
                )
                var isSearchMode by rememberSaveable { mutableStateOf(false) }
                var searchQuery by rememberSaveable { mutableStateOf("") }
                var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedStatus by rememberSaveable { mutableStateOf<MessageStatus?>(null) }
                var showClearDialog by rememberSaveable { mutableStateOf(false) }
                var shareMenuExpanded by remember { mutableStateOf(false) }
                var downloadMenuExpanded by remember { mutableStateOf(false) }
                var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
                val keyboardController = LocalSoftwareKeyboardController.current

                val lazyListState = rememberLazyListState()
                var resumeCount by remember { mutableIntStateOf(0) }

                LifecycleResumeEffect(Unit) {
                    resumeCount++
                    onPauseOrDispose {}
                }

                LaunchedEffect(resumeCount) {
                    if (resumeCount > 0) {
                        lazyListState.scrollToItem(0)
                    }
                }

                val availableTags = entries.mapNotNull { it.tag }.distinct()

                val filteredEntries = entries.reversed().filter { entry ->
                    val matchesSearch = searchQuery.isBlank() ||
                        entry.handlerName.contains(searchQuery, ignoreCase = true)
                    val matchesTag = selectedTag == null || entry.tag == selectedTag
                    val matchesStatus = selectedStatus == null || entry.status == selectedStatus
                    matchesSearch && matchesTag && matchesStatus
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isSearchMode) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text("Search...", color = Color.White.copy(alpha = 0.7f))
                                        },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = Color.White,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { keyboardController?.hide() },
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Column {
                                        Text("Dari")
                                        Text(
                                            text = applicationInfo.loadLabel(packageManager).toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                if (isSearchMode) {
                                    IconButton(onClick = {
                                        isSearchMode = false
                                        searchQuery = ""
                                    }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Close search",
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (!isSearchMode) {
                                    IconButton(onClick = { isSearchMode = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { shareMenuExpanded = true },
                                        enabled = filteredEntries.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share")
                                    }
                                    DropdownMenu(
                                        expanded = shareMenuExpanded,
                                        onDismissRequest = { shareMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Share as TEXT") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                lifecycleScope.launch {
                                                    DariExporter.exportAndShare(
                                                        this@DariActivity,
                                                        filteredEntries,
                                                        ExportFormat.TEXT,
                                                    )
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share as JSON") },
                                            onClick = {
                                                shareMenuExpanded = false
                                                lifecycleScope.launch {
                                                    DariExporter.exportAndShare(
                                                        this@DariActivity,
                                                        filteredEntries,
                                                        ExportFormat.JSON,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { downloadMenuExpanded = true },
                                        enabled = filteredEntries.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = "Save")
                                    }
                                    DropdownMenu(
                                        expanded = downloadMenuExpanded,
                                        onDismissRequest = { downloadMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Save as TEXT") },
                                            onClick = {
                                                downloadMenuExpanded = false
                                                launchSave(filteredEntries, ExportFormat.TEXT)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Save as JSON") },
                                            onClick = {
                                                downloadMenuExpanded = false
                                                launchSave(filteredEntries, ExportFormat.JSON)
                                            },
                                        )
                                    }
                                }
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                                IconButton(onClick = { showSettingsSheet = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            },
                            colors = DariTopBarColors.colors(),
                        )
                    },
                ) { padding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Column {
                        if (availableTags.isNotEmpty()) {
                            FilterChipRow {
                                FilterChip(
                                    label = "All tags",
                                    selected = selectedTag == null,
                                    onClick = { selectedTag = null },
                                )
                                availableTags.forEach { tag ->
                                    FilterChip(
                                        label = tag,
                                        selected = selectedTag == tag,
                                        onClick = {
                                            selectedTag = if (selectedTag == tag) null else tag
                                        },
                                    )
                                }
                            }
                        }
                        FilterChipRow {
                            FilterChip(
                                label = "All statuses",
                                selected = selectedStatus == null,
                                onClick = { selectedStatus = null },
                            )
                            // Order: Error first (most actionable in a debug tool),
                            // then In Progress (stuck calls), Success last (baseline noise).
                            STATUS_FILTER_ORDER.forEach { status ->
                                FilterChip(
                                    label = status.displayLabel(),
                                    selected = selectedStatus == status,
                                    onClick = {
                                        selectedStatus = if (selectedStatus == status) null else status
                                    },
                                    selectedBackground = status.color(),
                                    selectedContent = Color.White,
                                )
                            }
                        }
                        if (filteredEntries.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        "No messages captured yet"
                                    } else {
                                        "No results for \"$searchQuery\""
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(state = lazyListState) {
                                items(
                                    items = filteredEntries,
                                    key = { it.id },
                                ) { entry ->
                                    MessageListItem(
                                        entry = entry,
                                        onClick = {
                                            val intent = Intent(
                                                this@DariActivity,
                                                DariDetailActivity::class.java,
                                            ).apply {
                                                putExtra("id", entry.id)
                                            }
                                            startActivity(intent)
                                        },
                                    )
                                }
                            }
                        }
                        }
                    }

                    if (showSettingsSheet) {
                        SettingsBottomSheet(
                            shakeToOpen = shakeToOpen,
                            onShakeToOpenChange = { Dari.setShakeToOpenEnabled(it) },
                            darkMode = darkMode,
                            onDarkModeChange = { Dari.setDarkMode(it) },
                            onDismiss = { showSettingsSheet = false },
                        )
                    }

                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear") },
                            text = { Text("Do you want to clear all bridge message history?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    Dari.clear()
                                    showClearDialog = false
                                }) {
                                    Text("CLEAR")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text("CANCEL")
                                }
                            },
                        )
                    }

                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    internal companion object {
        /**
         * Tracks whether [DariActivity] is currently in the foreground.
         *
         * Used by `DariShakeManager` to suppress shake-to-open while the activity
         * is already visible (avoids re-launching it on top of itself).
         *
         * - Updated from the main thread in [onStart] / [onStop].
         * - Read from the sensor callback thread in `DariShakeManager`,
         *   so it must be `@Volatile` for cross-thread visibility.
         * - `private set` keeps writes confined to the activity lifecycle.
         */
        @Volatile
        internal var isVisible: Boolean = false
            private set
    }
}

// Display order for the status filter chip row. Errors come first because
// debugging the failure is the most common reason to open Dari.
private val STATUS_FILTER_ORDER = listOf(
    MessageStatus.ERROR,
    MessageStatus.IN_PROGRESS,
    MessageStatus.SUCCESS,
)

private fun MessageStatus.displayLabel(): String = when (this) {
    MessageStatus.IN_PROGRESS -> "In Progress"
    MessageStatus.SUCCESS -> "Success"
    MessageStatus.ERROR -> "Error"
}

@Composable
private fun FilterChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedBackground: Color = MaterialTheme.colorScheme.primary,
    selectedContent: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) {
            selectedContent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    selectedBackground
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
