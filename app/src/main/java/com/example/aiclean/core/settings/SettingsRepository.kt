package com.example.aiclean.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val apiKey: Flow<String> = dataStore.data.map { it[API_KEY] ?: "" }
    val aiProvider: Flow<String> = dataStore.data.map { it[AI_PROVIDER] ?: "openai" }
    val aiModel: Flow<String> = dataStore.data.map { it[AI_MODEL] ?: "gpt-3.5-turbo" }
    val aiBaseUrl: Flow<String> = dataStore.data.map { it[AI_BASE_URL] ?: "https://api.openai.com/v1" }
    val aiMaxTokens: Flow<Int> = dataStore.data.map { it[AI_MAX_TOKENS] ?: 1000 }
    val aiTemperature: Flow<Float> = dataStore.data.map { it[AI_TEMPERATURE] ?: 0.7f }
    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: "system" }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun saveAiProvider(provider: String) {
        dataStore.edit { it[AI_PROVIDER] = provider }
    }

    suspend fun saveAiModel(model: String) {
        dataStore.edit { it[AI_MODEL] = model }
    }

    suspend fun saveAiBaseUrl(url: String) {
        dataStore.edit { it[AI_BASE_URL] = url }
    }

    suspend fun saveAiMaxTokens(tokens: Int) {
        dataStore.edit { it[AI_MAX_TOKENS] = tokens }
    }

    suspend fun saveAiTemperature(temp: Float) {
        dataStore.edit { it[AI_TEMPERATURE] = temp }
    }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE] = mode }
    }
}
