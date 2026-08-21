package com.example.aiclean.ui.screens.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.scanner.FileCategoryStat
import com.example.aiclean.core.scanner.StorageScanner
import com.example.aiclean.core.scanner.StorageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUiState(
    val isLoading: Boolean = true,
    val storageStats: StorageStats? = null,
    val categories: List<StorageCategory> = emptyList(),
    val topApps: List<AppStorageInfo> = emptyList(),
    val scannedFiles: Int = 0,
    val scanTruncated: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StorageViewModel @Inject constructor(private val scanner: StorageScanner) : ViewModel() {
    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init { loadStorageData() }

    private fun loadStorageData() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        runCatching {
            val scan = scanner.scanStorage()
            Triple(scanner.getStorageStats(), scan, scan.categories)
        }.onSuccess { (stats, scan, categories) ->
            _uiState.value = StorageUiState(
                isLoading = false,
                storageStats = stats,
                categories = categories.map { it.toUiCategory(stats.usedBytes) },
                topApps = scan.apps.filter { it.totalSize > 0 }.take(10).map {
                    AppStorageInfo(it.appName, it.packageName, it.totalSize, it.cacheSize)
                },
                scannedFiles = scan.scannedFileCount,
                scanTruncated = scan.scanTruncated
            )
        }.onFailure { e ->
            _uiState.value = StorageUiState(isLoading = false, error = "分析失败：${e.message ?: "未知错误"}")
        }
    }

    private fun FileCategoryStat.toUiCategory(totalStorageUsed: Long): StorageCategory {
        val info = when (category) {
            "image" -> Triple("图片", Icons.Default.Image, Color(0xFF4CAF50))
            "video" -> Triple("视频", Icons.Default.VideoFile, Color(0xFFF44336))
            "audio" -> Triple("音频", Icons.Default.MusicNote, Color(0xFF2196F3))
            "document" -> Triple("文档", Icons.Default.Folder, Color(0xFFFF9800))
            "archive" -> Triple("压缩包", Icons.Default.Folder, Color(0xFF9C27B0))
            "installer" -> Triple("安装包", Icons.Default.Folder, Color(0xFF795548))
            else -> Triple("其他文件", Icons.Default.Folder, Color(0xFF607D8B))
        }
        return StorageCategory(info.first, info.second, info.third, size, if (totalStorageUsed > 0) (size * 100 / totalStorageUsed).toInt().coerceAtMost(100) else 0, fileCount)
    }
}
