package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeletionResult
import com.example.data.StorageScanner
import com.example.data.StorageStats
import com.example.model.AgeFilter
import com.example.model.FileItem
import com.example.model.FormatCategory
import com.example.model.SizeTier
import com.example.model.SortOrder
import com.example.model.StorageCategorySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode {
    LIST, GRID
}

data class StorageUiState(
    val isLoading: Boolean = true,
    val allFiles: List<FileItem> = emptyList(),
    val filteredFiles: List<FileItem> = emptyList(),
    val selectedFileIds: Set<String> = emptySet(),
    val selectedFormat: FormatCategory = FormatCategory.ALL,
    val selectedSizeTier: SizeTier = SizeTier.ALL,
    val selectedAgeFilter: AgeFilter = AgeFilter.ALL,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.SIZE_DESC,
    val viewMode: ViewMode = ViewMode.LIST,
    val categorySummaries: List<StorageCategorySummary> = emptyList(),
    val storageStats: StorageStats = StorageStats(64L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024),
    val filesPendingDelete: List<FileItem>? = null,
    val activeFileDetail: FileItem? = null,
    val snackbarMessage: String? = null,
    val hasStoragePermission: Boolean = true,
    val quickFilterLargeOnly: Boolean = false
) {
    val isSelectionMode: Boolean
        get() = selectedFileIds.isNotEmpty()

    val selectedFilesCount: Int
        get() = selectedFileIds.size

    val selectedFilesBytes: Long
        get() = allFiles.filter { selectedFileIds.contains(it.id) }.sumOf { it.size }

    val formattedSelectedBytes: String
        get() = FileItem.formatFileSize(selectedFilesBytes)
}

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = StorageScanner(application)

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val stats = scanner.getStorageStats()
            var files = scanner.scanAllFiles()

            // If completely empty (e.g. freshly created emulator container), automatically generate sample files
            if (files.isEmpty()) {
                files = scanner.generateDemoFiles()
            }

            val summaries = calculateCategorySummaries(files)
            _uiState.update { current ->
                val updated = current.copy(
                    isLoading = false,
                    allFiles = files,
                    storageStats = stats,
                    categorySummaries = summaries,
                    selectedFileIds = emptySet()
                )
                applyFilters(updated)
            }
        }
    }

    fun generateMoreDemoFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val files = scanner.generateDemoFiles()
            val stats = scanner.getStorageStats()
            val summaries = calculateCategorySummaries(files)
            _uiState.update { current ->
                val updated = current.copy(
                    isLoading = false,
                    allFiles = files,
                    storageStats = stats,
                    categorySummaries = summaries,
                    snackbarMessage = "Created sample media files across categories!"
                )
                applyFilters(updated)
            }
        }
    }

    fun setFormatFilter(category: FormatCategory) {
        _uiState.update { current ->
            val updated = current.copy(selectedFormat = category)
            applyFilters(updated)
        }
    }

    fun setSizeTierFilter(tier: SizeTier) {
        _uiState.update { current ->
            val updated = current.copy(selectedSizeTier = tier)
            applyFilters(updated)
        }
    }

    fun setAgeFilter(age: AgeFilter) {
        _uiState.update { current ->
            val updated = current.copy(selectedAgeFilter = age)
            applyFilters(updated)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val updated = current.copy(searchQuery = query)
            applyFilters(updated)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { current ->
            val updated = current.copy(sortOrder = order)
            applyFilters(updated)
        }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        }
    }

    fun toggleQuickFilterLargeOnly() {
        _uiState.update { current ->
            val newLargeOnly = !current.quickFilterLargeOnly
            val updated = current.copy(
                quickFilterLargeOnly = newLargeOnly,
                selectedSizeTier = if (newLargeOnly) SizeTier.LARGE else SizeTier.ALL
            )
            applyFilters(updated)
        }
    }

    fun toggleFileSelection(fileId: String) {
        _uiState.update { current ->
            val newSelected = current.selectedFileIds.toMutableSet()
            if (newSelected.contains(fileId)) {
                newSelected.remove(fileId)
            } else {
                newSelected.add(fileId)
            }
            current.copy(selectedFileIds = newSelected)
        }
    }

    fun selectAllFiltered() {
        _uiState.update { current ->
            val allFilteredIds = current.filteredFiles.map { it.id }.toSet()
            current.copy(selectedFileIds = allFilteredIds)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFileIds = emptySet()) }
    }

    fun selectFilesLargerThan(minBytes: Long) {
        _uiState.update { current ->
            val ids = current.filteredFiles.filter { it.size >= minBytes }.map { it.id }.toSet()
            current.copy(selectedFileIds = ids)
        }
    }

    fun requestDeleteSelected() {
        val selected = _uiState.value.allFiles.filter { _uiState.value.selectedFileIds.contains(it.id) }
        if (selected.isNotEmpty()) {
            _uiState.update { it.copy(filesPendingDelete = selected) }
        }
    }

    fun requestDeleteSingle(item: FileItem) {
        _uiState.update { it.copy(filesPendingDelete = listOf(item)) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(filesPendingDelete = null) }
    }

    fun confirmDelete() {
        val toDelete = _uiState.value.filesPendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, filesPendingDelete = null) }
            val result = scanner.deleteFiles(toDelete)
            val updatedFiles = scanner.scanAllFiles()
            val stats = scanner.getStorageStats()
            val summaries = calculateCategorySummaries(updatedFiles)

            val freedStr = FileItem.formatFileSize(result.freedBytes)
            val message = if (result.deletedCount > 0) {
                "Freed $freedStr! Deleted ${result.deletedCount} file${if (result.deletedCount > 1) "s" else ""}."
            } else {
                "Unable to delete files (system restricted or protected)."
            }

            _uiState.update { current ->
                val updated = current.copy(
                    isLoading = false,
                    allFiles = updatedFiles,
                    storageStats = stats,
                    categorySummaries = summaries,
                    selectedFileIds = emptySet(),
                    activeFileDetail = null,
                    snackbarMessage = message
                )
                applyFilters(updated)
            }
        }
    }

    fun showFileDetail(item: FileItem) {
        _uiState.update { it.copy(activeFileDetail = item) }
    }

    fun dismissFileDetail() {
        _uiState.update { it.copy(activeFileDetail = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun applyFilters(state: StorageUiState): StorageUiState {
        var result = state.allFiles

        // 1. Format filter
        if (state.selectedFormat != FormatCategory.ALL) {
            result = result.filter { it.formatCategory == state.selectedFormat }
        }

        // 2. Size tier filter
        if (state.selectedSizeTier != SizeTier.ALL) {
            result = result.filter { state.selectedSizeTier.matches(it.size) }
        }

        // 3. Age filter
        if (state.selectedAgeFilter != AgeFilter.ALL) {
            result = result.filter { state.selectedAgeFilter.matches(it.dateModified) }
        }

        // 4. Search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            result = result.filter {
                it.name.lowercase().contains(query) ||
                it.extension.lowercase().contains(query) ||
                it.parentDirectory.lowercase().contains(query)
            }
        }

        // 5. Sorting
        result = when (state.sortOrder) {
            SortOrder.SIZE_DESC -> result.sortedByDescending { it.size }
            SortOrder.SIZE_ASC -> result.sortedBy { it.size }
            SortOrder.DATE_DESC -> result.sortedByDescending { it.dateModified }
            SortOrder.DATE_ASC -> result.sortedBy { it.dateModified }
            SortOrder.NAME_ASC -> result.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.name.lowercase() }
        }

        return state.copy(filteredFiles = result)
    }

    private fun calculateCategorySummaries(files: List<FileItem>): List<StorageCategorySummary> {
        val totalBytes = files.sumOf { it.size }.coerceAtLeast(1L)
        return FormatCategory.entries
            .filter { it != FormatCategory.ALL }
            .map { cat ->
                val matching = files.filter { it.formatCategory == cat }
                val catBytes = matching.sumOf { it.size }
                val count = matching.size
                val percentage = (catBytes.toFloat() / totalBytes.toFloat())
                StorageCategorySummary(
                    category = cat,
                    totalBytes = catBytes,
                    fileCount = count,
                    percentage = percentage
                )
            }
            .filter { it.fileCount > 0 }
            .sortedByDescending { it.totalBytes }
    }
}
