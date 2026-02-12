package com.easyhooon.dari.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.easyhooon.dari.Dari
import com.easyhooon.dari.ui.components.MessageListItem
import com.easyhooon.dari.ui.theme.DariTopBarColors

/**
 * Activity displaying the list of bridge messages.
 * Accessed by tapping the notification or launching directly.
 */
class DariActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            Dari.showNotification()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                val entries by Dari.repository.entries.collectAsState()
                var isSearchMode by rememberSaveable { mutableStateOf(false) }
                var searchQuery by rememberSaveable { mutableStateOf("") }
                var showClearDialog by rememberSaveable { mutableStateOf(false) }
                val keyboardController = LocalSoftwareKeyboardController.current

                val filteredEntries = if (searchQuery.isBlank()) {
                    entries.reversed()
                } else {
                    entries.reversed().filter {
                        it.handlerName.contains(searchQuery, ignoreCase = true)
                    }
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
                                IconButton(onClick = { showClearDialog = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
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
                            LazyColumn {
                                items(
                                    items = filteredEntries,
                                    key = { it.requestId },
                                ) { entry ->
                                    MessageListItem(
                                        entry = entry,
                                        onClick = {
                                            val intent = Intent(
                                                this@DariActivity,
                                                DariDetailActivity::class.java,
                                            ).apply {
                                                putExtra("requestId", entry.requestId)
                                            }
                                            startActivity(intent)
                                        },
                                    )
                                }
                            }
                        }
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
}