package com.example.aiclean.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.ai.AIConfig
import com.example.aiclean.core.ai.AIService
import com.example.aiclean.core.ai.AppInfoForAI
import com.example.aiclean.core.cleaner.AccessLevel
import com.example.aiclean.core.cleaner.StorageCleaner
import com.example.aiclean.core.scanner.AppInfo
import com.example.aiclean.core.scanner.ScanPhase
import com.example.aiclean.core.scanner.ScanResult
import com.example.aiclean.core.scanner.StorageScanner
import com.example.aiclean.core.scanner.StorageStats
import com.example.aiclean.core.settings.SettingsRepository
import com.example.aiclean.core.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val scanResult: ScanResult? = null,
    val storageStats: StorageStats? = null,
    val aiAnalysis: String? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val scanPhase: ScanPhase = ScanPhase.INITIALIZING,
    val scanProgress: Float = 0f,
    val accessLevel: AccessLevel = AccessLevel.NORMAL
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val cleaner: StorageCleaner,
    private val shizukuManager: ShizukuManager,
    private val aiService: AIService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStorageStats()
        startScan()
        checkAccessLevel()
    }

    private fun loadStorageStats() {
        viewModelScope.launch {
            val stats = scanner.getStorageStats()
            _uiState.value = _uiState.value.copy(storageStats = stats)
        }
    }

    private fun checkAccessLevel() {
        viewModelScope.launch {
            val level = cleaner.getAccessLevel()
            _uiState.value = _uiState.value.copy(accessLevel = level)
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun startScan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Observe scan progress
                launch {
                    scanner.progress.collect { progress ->
                        _uiState.value = _uiState.value.copy(
                            scanPhase = progress.phase,
                            scanProgress = if (progress.totalFiles > 0) {
                                progress.scannedFiles.toFloat() / progress.totalFiles
                            } else 0f
                        )
                    }
                }

                val result = scanner.scanStorage()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scanResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun analyzeWithAI() {
        viewModelScope.launch {
            val scanResult = _uiState.value.scanResult ?: return@launch
            _uiState.value = _uiState.value.copy(isAnalyzing = true, error = null)

            try {
                val apiKey = settingsRepository.apiKey.first()
                if (apiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        error = "Please configure your API key in Settings first"
                    )
                    return@launch
                }

                val provider = settingsRepository.aiProvider.first()
                val baseConfig = AIService.DEFAULT_CONFIGS[provider] ?: AIService.DEFAULT_CONFIGS["openai"]!!

                val config = baseConfig.copy(
                    apiKey = apiKey,
                    baseUrl = settingsRepository.aiBaseUrl.first(),
                    model = settingsRepository.aiModel.first(),
                    maxTokens = settingsRepository.aiMaxTokens.first(),
                    temperature = settingsRepository.aiTemperature.first()
                )

                val appsForAI = scanResult.apps.map { app ->
                    val lastUsedDays = if (app.lastUsed > 0) {
                        ((System.currentTimeMillis() - app.lastUsed) / (24 * 60 * 60 * 1000)).toInt()
                    } else {
                        -1 // Unknown
                    }

                    AppInfoForAI(
                        packageName = app.packageName,
                        appName = app.appName,
                        cacheSize = app.cacheSize,
                        dataSize = app.dataSize,
                        lastUsedDaysAgo = lastUsedDays,
                        isSystemApp = app.isSystemApp
                    )
                }

                val result = aiService.analyzeApps(config, appsForAI)
                result.fold(
                    onSuccess = { analysis ->
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            aiAnalysis = analysis.summary
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            error = "AI analysis failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "AI analysis failed: ${e.message}"
                )
            }
        }
    }
}
