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

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, deleteResult = null)
        runCatching { scanner.scanDuplicates() }.onSuccess { groups ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                duplicates = groups,
                totalWastedSize = groups.sumOf { it.totalSize },
                selectedFiles = emptySet()
            )
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(isLoading = false, error = "扫描失败：${e.message ?: "未知错误"}")
        }
    }

    fun toggleFile(path: String) {
        val current = _uiState.value.selectedFiles
        _uiState.value = _uiState.value.copy(selectedFiles = if (path in current) current - path else current + path)
    }

    fun deleteSelected() = viewModelScope.launch {
        val files = _uiState.value.selectedFiles.toList()
        if (files.isEmpty()) return@launch
        _uiState.value = _uiState.value.copy(isDeleting = true, deleteResult = null)
        val result = cleaner.cleanJunkFiles(files)
        _uiState.value = _uiState.value.copy(
            isDeleting = false,
            deleteResult = if (result.success) "已删除 ${result.cleanedFiles.size} 个文件，释放 ${formatSize(result.cleanedBytes)}" else result.message
        )
        refresh()
    }

    private fun formatSize(bytes: Long) = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
}
