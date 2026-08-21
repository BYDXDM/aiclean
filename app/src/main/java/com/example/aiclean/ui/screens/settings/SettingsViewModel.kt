package com.example.aiclean.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val selectedProvider: String = "openai",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-3.5-turbo",
    val maxTokens: Int = 1000,
    val temperature: Float = 0.7f,
    val isSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.apiKey.collect { key ->
                _uiState.value = _uiState.value.copy(apiKey = key)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiProvider.collect { provider ->
                _uiState.value = _uiState.value.copy(selectedProvider = provider)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiBaseUrl.collect { url ->
                _uiState.value = _uiState.value.copy(baseUrl = url)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiModel.collect { model ->
                _uiState.value = _uiState.value.copy(model = model)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiMaxTokens.collect { tokens ->
                _uiState.value = _uiState.value.copy(maxTokens = tokens)
            }
        }
        viewModelScope.launch {
            settingsRepository.aiTemperature.collect { temp ->
                _uiState.value = _uiState.value.copy(temperature = temp)
            }
        }
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, isSaved = false)
    }

    fun updateProvider(provider: String) {
        val defaultModels = mapOf(
            "openai" to "gpt-3.5-turbo",
            "dashscope" to "qwen-turbo",
            "deepseek" to "deepseek-chat",
            "ollama" to "llama2"
        )
        val defaultUrls = mapOf(
            "openai" to "https://api.openai.com/v1",
            "dashscope" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "deepseek" to "https://api.deepseek.com/v1",
            "ollama" to "http://localhost:11434/v1"
        )

        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            model = defaultModels[provider] ?: "gpt-3.5-turbo",
            baseUrl = defaultUrls[provider] ?: "https://api.openai.com/v1",
            isSaved = false
        )
    }

    fun updateBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url, isSaved = false)
    }

    fun updateModel(model: String) {
        _uiState.value = _uiState.value.copy(model = model, isSaved = false)
    }

    fun updateMaxTokens(tokens: Int) {
        _uiState.value = _uiState.value.copy(maxTokens = tokens, isSaved = false)
    }

    fun updateTemperature(temp: Float) {
        _uiState.value = _uiState.value.copy(temperature = temp, isSaved = false)
    }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveApiKey(_uiState.value.apiKey)
            settingsRepository.saveAiProvider(_uiState.value.selectedProvider)
            settingsRepository.saveAiBaseUrl(_uiState.value.baseUrl)
            settingsRepository.saveAiModel(_uiState.value.model)
            settingsRepository.saveAiMaxTokens(_uiState.value.maxTokens)
            settingsRepository.saveAiTemperature(_uiState.value.temperature)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
