package com.example.aiclean.ui.screens.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val scanner: StorageScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        loadStorageData()
    }

    private fun loadStorageData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val stats = scanner.getStorageStats()
                val scanResult = scanner.scanStorage()

                // Calculate categories
                val totalSize = stats.totalBytes
                val categories = listOf(
                    StorageCategory(
                        name = "Images",
                        icon = Icons.Default.Image,
                        color = Color(0xFF4CAF50),
                        size = scanResult.apps.sumOf { it.cacheSize } / 4, // Mock distribution
                        percentage = 25,
                        fileCount = 1250
                    ),
                    StorageCategory(
                        name = "Videos",
                        icon = Icons.Default.VideoFile,
                        color = Color(0xFFF44336),
                        size = scanResult.apps.sumOf { it.cacheSize } / 3,
                        percentage = 33,
                        fileCount = 85
                    ),
                    StorageCategory(
                        name = "Audio",
                        icon = Icons.Default.MusicNote,
                        color = Color(0xFF2196F3),
                        size = scanResult.apps.sumOf { it.cacheSize } / 6,
                        percentage = 12,
                        fileCount = 320
                    ),
                    StorageCategory(
                        name = "Documents",
                        icon = Icons.Default.Folder,
                        color = Color(0xFFFF9800),
                        size = scanResult.apps.sumOf { it.cacheSize } / 8,
                        percentage = 8,
                        fileCount = 450
                    ),
                    StorageCategory(
                        name = "Apps",
                        icon = Icons.Default.Folder,
                        color = Color(0xFF9C27B0),
                        size = scanResult.apps.sumOf { it.totalSize },
                        percentage = 22,
                        fileCount = scanResult.appCount
                    )
                )

                // Top apps by storage
                val topApps = scanResult.apps
                    .sortedByDescending { it.totalSize }
                    .take(10)
                    .map { app ->
                        AppStorageInfo(
                            name = app.appName,
                            packageName = app.packageName,
                            totalSize = app.totalSize,
                            cacheSize = app.cacheSize
                        )
                    }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storageStats = stats,
                    categories = categories,
                    topApps = topApps
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
