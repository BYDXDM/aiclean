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

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, cleanResult = null, error = null)
        runCatching { scanner.scanJunkFiles() }.onSuccess { files ->
            applyFiles(files, _uiState.value.selectedCategory)
            _uiState.value = _uiState.value.copy(isLoading = false, selectedFiles = emptySet())
        }.onFailure { e ->
            _uiState.value = _uiState.value.copy(isLoading = false, error = "扫描失败：${e.message ?: "未知错误"}")
        }
    }

    fun filterByCategory(category: String?) = applyFiles(_uiState.value.junkFiles, category)

    private fun applyFiles(files: List<FileInfo>, category: String?) {
        val filtered = if (category == null) files else files.filter { it.junkType == category }
        _uiState.value = _uiState.value.copy(
            junkFiles = files,
            filteredFiles = filtered,
            totalJunkSize = files.sumOf { it.size },
            selectedCategory = category
        )
    }

    fun toggleFile(path: String) {
        val selected = _uiState.value.selectedFiles
        _uiState.value = _uiState.value.copy(selectedFiles = if (path in selected) selected - path else selected + path)
    }

    fun cleanSelected() = viewModelScope.launch {
        val files = _uiState.value.selectedFiles.toList()
        if (files.isEmpty()) return@launch
        _uiState.value = _uiState.value.copy(isCleaning = true, cleanResult = null)
        val result = cleaner.cleanJunkFiles(files)
        _uiState.value = _uiState.value.copy(
            isCleaning = false,
            cleanResult = if (result.success) "已清理 ${result.cleanedFiles.size} 个文件，释放 ${formatSize(result.cleanedBytes)}" else result.message
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
