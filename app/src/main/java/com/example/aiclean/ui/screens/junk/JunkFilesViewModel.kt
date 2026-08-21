package com.example.aiclean.ui.screens.junk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.cleaner.StorageCleaner
import com.example.aiclean.core.scanner.FileInfo
import com.example.aiclean.core.scanner.StorageScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JunkFilesUiState(
    val isLoading: Boolean = true,
    val junkFiles: List<FileInfo> = emptyList(),
    val filteredFiles: List<FileInfo> = emptyList(),
    val totalJunkSize: Long = 0,
    val selectedFiles: Set<String> = emptySet(),
    val selectedCategory: String? = null,
    val isCleaning: Boolean = false,
    val cleanResult: String? = null,
    val error: String? = null
)

@HiltViewModel
class JunkFilesViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val cleaner: StorageCleaner
) : ViewModel() {

    private val _uiState = MutableStateFlow(JunkFilesUiState())
    val uiState: StateFlow<JunkFilesUiState> = _uiState.asStateFlow()

    init {
        scanJunkFiles()
    }

    private fun scanJunkFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = scanner.scanStorage()
                
                // Mock junk files for demonstration
                val mockJunkFiles = listOf(
                    FileInfo(
                        path = "/storage/emulated/0/Android/data/com.app/cache/temp_file.tmp",
                        size = 1_500_000,
                        lastModified = System.currentTimeMillis(),
                        isJunk = true,
                        junkConfidence = 0.9f,
                        category = "temp"
                    ),
                    FileInfo(
                        path = "/storage/emulated/0/Download/app_installer.apk",
                        size = 25_000_000,
                        lastModified = System.currentTimeMillis() - 86400000 * 30,
                        isJunk = true,
                        junkConfidence = 0.7f,
                        category = "installer"
                    ),
                    FileInfo(
                        path = "/storage/emulated/0/DCIM/.thumbnails/thumb_cache.db",
                        size = 5_000_000,
                        lastModified = System.currentTimeMillis(),
                        isJunk = true,
                        junkConfidence = 0.85f,
                        category = "database"
                    ),
                    FileInfo(
                        path = "/storage/emulated/0/log/app_debug.log",
                        size = 500_000,
                        lastModified = System.currentTimeMillis() - 86400000,
                        isJunk = true,
                        junkConfidence = 0.95f,
                        category = "log"
                    ),
                    FileInfo(
                        path = "/storage/emulated/0/Android/data/com.social/cache/images",
                        size = 8_000_000,
                        lastModified = System.currentTimeMillis(),
                        isJunk = true,
                        junkConfidence = 0.6f,
                        category = "image"
                    ),
                    FileInfo(
                        path = "/storage/emulated/0/Download/video_partial.mp4.part",
                        size = 15_000_000,
                        lastModified = System.currentTimeMillis() - 86400000 * 7,
                        isJunk = true,
                        junkConfidence = 0.9f,
                        category = "video"
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    junkFiles = mockJunkFiles,
                    filteredFiles = mockJunkFiles,
                    totalJunkSize = mockJunkFiles.sumOf { it.size }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun filterByCategory(category: String?) {
        val allFiles = _uiState.value.junkFiles
        val filtered = if (category == null) {
            allFiles
        } else {
            allFiles.filter { it.category == category }
        }
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredFiles = filtered
        )
    }

    fun toggleFile(path: String) {
        val current = _uiState.value.selectedFiles
        _uiState.value = _uiState.value.copy(
            selectedFiles = if (current.contains(path)) {
                current - path
            } else {
                current + path
            }
        )
    }

    fun cleanSelected() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCleaning = true, cleanResult = null)

            try {
                val filesToDelete = _uiState.value.selectedFiles.toList()
                val result = cleaner.cleanJunkFiles(filesToDelete)

                if (result.success) {
                    // Rescan to get updated list
                    scanJunkFiles()
                    _uiState.value = _uiState.value.copy(
                        isCleaning = false,
                        selectedFiles = emptySet(),
                        cleanResult = "Cleaned ${result.cleanedFiles.size} files (${formatSize(result.cleanedBytes)})"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCleaning = false,
                        cleanResult = "Some files failed to delete"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCleaning = false,
                    error = e.message
                )
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
