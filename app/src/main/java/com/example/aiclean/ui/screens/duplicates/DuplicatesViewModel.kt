package com.example.aiclean.ui.screens.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.cleaner.StorageCleaner
import com.example.aiclean.core.scanner.DuplicateGroup
import com.example.aiclean.core.scanner.StorageScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuplicatesUiState(
    val isLoading: Boolean = true,
    val duplicates: List<DuplicateGroup> = emptyList(),
    val totalWastedSize: Long = 0,
    val selectedFiles: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val deleteResult: String? = null,
    val error: String? = null
)

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val cleaner: StorageCleaner
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    init {
        scanDuplicates()
    }

    private fun scanDuplicates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = scanner.scanStorage()
                // For now, we'll create mock duplicates since the scanner
                // needs to be enhanced to return duplicates separately
                // In a real implementation, scanner.findDuplicates() would be called
                
                // Mock data for demonstration
                val mockDuplicates = listOf(
                    DuplicateGroup(
                        files = listOf(
                            com.example.aiclean.core.scanner.FileInfo(
                                path = "/storage/emulated/0/DCIM/photo1.jpg",
                                size = 2_500_000,
                                lastModified = System.currentTimeMillis(),
                                category = "image"
                            ),
                            com.example.aiclean.core.scanner.FileInfo(
                                path = "/storage/emulated/0/Pictures/photo1.jpg",
                                size = 2_500_000,
                                lastModified = System.currentTimeMillis(),
                                category = "image"
                            )
                        ),
                        totalSize = 2_500_000
                    ),
                    DuplicateGroup(
                        files = listOf(
                            com.example.aiclean.core.scanner.FileInfo(
                                path = "/storage/emulated/0/Download/document.pdf",
                                size = 5_000_000,
                                lastModified = System.currentTimeMillis(),
                                category = "other"
                            ),
                            com.example.aiclean.core.scanner.FileInfo(
                                path = "/storage/emulated/0/Documents/document.pdf",
                                size = 5_000_000,
                                lastModified = System.currentTimeMillis(),
                                category = "other"
                            ),
                            com.example.aiclean.core.scanner.FileInfo(
                                path = "/storage/emulated/0/Backup/document.pdf",
                                size = 5_000_000,
                                lastModified = System.currentTimeMillis(),
                                category = "other"
                            )
                        ),
                        totalSize = 10_000_000
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    duplicates = mockDuplicates,
                    totalWastedSize = mockDuplicates.sumOf { it.totalSize }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
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

    fun deleteSelected() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, deleteResult = null)

            try {
                val filesToDelete = _uiState.value.selectedFiles.toList()
                val result = cleaner.cleanJunkFiles(filesToDelete)

                if (result.success) {
                    // Rescan to get updated list
                    scanDuplicates()
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        selectedFiles = emptySet(),
                        deleteResult = "Deleted ${result.cleanedFiles.size} files (${formatSize(result.cleanedBytes)})"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteResult = "Some files failed to delete"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
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
