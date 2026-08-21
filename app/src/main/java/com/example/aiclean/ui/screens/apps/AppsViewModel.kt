package com.example.aiclean.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.ai.AIConfig
import com.example.aiclean.core.ai.AIService
import com.example.aiclean.core.ai.AppInfoForAI
import com.example.aiclean.core.cleaner.StorageCleaner
import com.example.aiclean.core.scanner.AppInfo
import com.example.aiclean.core.scanner.StorageScanner
import com.example.aiclean.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppsUiState(
    val apps: List<AppInfo> = emptyList(),
    val selectedApps: Set<String> = emptySet(),
    val totalCacheSize: Long = 0,
    val isCleaning: Boolean = false,
    val cleanResult: String? = null,
    val isAnalyzing: Boolean = false,
    val aiRecommendations: Map<String, String> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val cleaner: StorageCleaner,
    private val aiService: AIService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val result = scanner.scanStorage()
            val apps = result.apps.filter { it.cacheSize > 0 }
            _uiState.value = _uiState.value.copy(
                apps = apps,
                totalCacheSize = apps.sumOf { it.cacheSize }
            )
        }
    }

    fun toggleApp(packageName: String) {
        val current = _uiState.value.selectedApps
        _uiState.value = _uiState.value.copy(
            selectedApps = if (current.contains(packageName)) {
                current - packageName
            } else {
                current + packageName
            }
        )
    }

    fun cleanSelectedApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCleaning = true, cleanResult = null)

            var totalCleaned = 0L
            val errors = mutableListOf<String>()

            _uiState.value.selectedApps.forEach { packageName ->
                val result = cleaner.cleanCache(packageName)
                if (result.success) {
                    totalCleaned += result.cleanedBytes
                } else {
                    errors.add(result.message)
                }
            }

            // Refresh the app list
            val scanResult = scanner.scanStorage()
            val apps = scanResult.apps.filter { it.cacheSize > 0 }

            _uiState.value = _uiState.value.copy(
                isCleaning = false,
                selectedApps = emptySet(),
                apps = apps,
                totalCacheSize = apps.sumOf { it.cacheSize },
                cleanResult = if (errors.isEmpty()) {
                    "Successfully cleaned ${formatSize(totalCleaned)}"
                } else {
                    "Cleaned ${formatSize(totalCleaned)}, ${errors.size} errors"
                }
            )
        }
    }

    fun analyzeWithAI() {
        viewModelScope.launch {
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

                val appsForAI = _uiState.value.apps.map { app ->
                    val lastUsedDays = if (app.lastUsed > 0) {
                        ((System.currentTimeMillis() - app.lastUsed) / (24 * 60 * 60 * 1000)).toInt()
                    } else {
                        -1
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
                        val recommendations = analysis.recommendations.associate { rec ->
                            rec.target to "${rec.action}: ${rec.reason}"
                        }
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            aiRecommendations = recommendations
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

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            else -> "${bytes / (1024 * 1024 * 1024)}GB"
        }
    }
}
