package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AgeFilter
import com.example.model.FileItem
import com.example.model.FormatCategory
import com.example.model.SizeTier
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.FileDetailSheet
import com.example.ui.components.FileItemRow
import com.example.ui.components.FilterChipsRow
import com.example.ui.components.SelectionBottomBar
import com.example.ui.components.StorageOverviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    viewModel: StorageViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Request permissions launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadData()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "StorageLens",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${FileItem.formatFileSize(state.storageStats.usedBytes)} used • ${FileItem.formatFileSize(state.storageStats.freeBytes)} free",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Button to generate sample demo files
                    IconButton(
                        onClick = { viewModel.generateMoreDemoFiles() },
                        modifier = Modifier.testTag("add_demo_files_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add demo files",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Grid / List toggle
                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.testTag("toggle_view_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (state.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle view mode"
                        )
                    }

                    // Refresh / Rescan
                    IconButton(
                        onClick = { viewModel.loadData() },
                        modifier = Modifier.testTag("refresh_files_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan files"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            SelectionBottomBar(
                isVisible = state.isSelectionMode,
                selectedCount = state.selectedFilesCount,
                totalCount = state.filteredFiles.size,
                selectedSizeFormatted = state.formattedSelectedBytes,
                onSelectAll = { viewModel.selectAllFiltered() },
                onClearSelection = { viewModel.clearSelection() },
                onSelectLargeFiles = { viewModel.selectFilesLargerThan(25L * 1024 * 1024) },
                onDeleteRequested = { viewModel.requestDeleteSelected() }
            )
        },
        floatingActionButton = {
            // Quick selection FAB when not in selection mode and has files
            if (!state.isSelectionMode && state.filteredFiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.selectFilesLargerThan(25L * 1024 * 1024)
                        if (viewModel.uiState.value.selectedFileIds.isEmpty()) {
                            viewModel.selectAllFiltered()
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Select to clean"
                        )
                    },
                    text = { Text("Select to Clean") },
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("select_clean_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading && state.allFiles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Scanning device files…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Analyzing sizes and dividing formats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("files_list"),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Storage Overview Card
                    item {
                        StorageOverviewCard(
                            stats = state.storageStats,
                            summaries = state.categorySummaries,
                            totalFilesCount = state.allFiles.size,
                            totalFilesBytes = state.allFiles.sumOf { it.size },
                            selectedCategory = state.selectedFormat,
                            onCategoryClick = { viewModel.setFormatFilter(it) },
                            onCleanLargeFilesClick = { viewModel.toggleQuickFilterLargeOnly() },
                            isLargeFilterActive = state.quickFilterLargeOnly,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    // Filters section
                    item {
                        FilterChipsRow(
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            selectedFormat = state.selectedFormat,
                            onFormatSelect = { viewModel.setFormatFilter(it) },
                            selectedSizeTier = state.selectedSizeTier,
                            onSizeTierSelect = { viewModel.setSizeTierFilter(it) },
                            selectedAgeFilter = state.selectedAgeFilter,
                            onAgeFilterSelect = { viewModel.setAgeFilter(it) },
                            currentSort = state.sortOrder,
                            onSortSelect = { viewModel.setSortOrder(it) }
                        )
                    }

                    // Results status bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filteredTotalBytes = state.filteredFiles.sumOf { it.size }
                            Text(
                                text = "${state.filteredFiles.size} files found (${FileItem.formatFileSize(filteredTotalBytes)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (state.filteredFiles.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            if (state.selectedFileIds.size == state.filteredFiles.size) {
                                                viewModel.clearSelection()
                                            } else {
                                                viewModel.selectAllFiltered()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("toggle_select_all_btn")
                                    ) {
                                        Text(
                                            text = if (state.selectedFileIds.size == state.filteredFiles.size) "Deselect" else "Select All",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty State
                    if (state.filteredFiles.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SdStorage,
                                            contentDescription = "No files",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No files match current filters",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Try clearing search filters or add sample demo files to test dividing and cleaning.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setFormatFilter(FormatCategory.ALL)
                                            viewModel.setSizeTierFilter(SizeTier.ALL)
                                            viewModel.setAgeFilter(AgeFilter.ALL)
                                            viewModel.setSearchQuery("")
                                        }
                                    ) {
                                        Text("Reset Filters")
                                    }
                                    Button(
                                        onClick = { viewModel.generateMoreDemoFiles() }
                                    ) {
                                        Text("Add Demo Files")
                                    }
                                }
                            }
                        }
                    } else {
                        // File Items list
                        items(
                            items = state.filteredFiles,
                            key = { it.id }
                        ) { file ->
                            val isSelected = state.selectedFileIds.contains(file.id)
                            FileItemRow(
                                item = file,
                                isSelected = isSelected,
                                isSelectionMode = state.isSelectionMode,
                                onToggleSelect = { viewModel.toggleFileSelection(file.id) },
                                onClick = { viewModel.showFileDetail(file) },
                                onDeleteSingle = { viewModel.requestDeleteSingle(file) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before deleting
    state.filesPendingDelete?.let { files ->
        DeleteConfirmDialog(
            filesToDelete = files,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    // File details bottom sheet
    state.activeFileDetail?.let { file ->
        FileDetailSheet(
            item = file,
            onDismiss = { viewModel.dismissFileDetail() },
            onDelete = {
                viewModel.requestDeleteSingle(file)
            }
        )
    }
}
