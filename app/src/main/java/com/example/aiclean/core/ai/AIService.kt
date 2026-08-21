package com.example.aiclean.core.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIService @Inject constructor(
    private val gson: Gson
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeApps(
        config: AIConfig,
        apps: List<AppInfoForAI>
    ): Result<AIAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key not configured"))
            }

            val prompt = buildAnalyzeAppsPrompt(apps)
            val response = callLLM(config, prompt)
            val result = parseAnalysisResponse(response)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeCacheValue(
        config: AIConfig,
        packageName: String,
        cacheFiles: List<CacheFileInfo>
    ): Result<AICacheValue> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key not configured"))
            }

            val prompt = buildCacheValuePrompt(packageName, cacheFiles)
            val response = callLLM(config, prompt)
            val result = parseCacheValueResponse(packageName, response)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun chat(
        config: AIConfig,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key not configured"))
            }

            val response = callLLMWithMessages(config, messages)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callLLM(config: AIConfig, userPrompt: String): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userPrompt)
        )
        return callLLMWithMessages(config, messages.map { ChatMessage(it["role"]!!, it["content"]!!) })
    }

    private fun callLLMWithMessages(config: AIConfig, messages: List<ChatMessage>): String {
        val requestBody = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", gson.toJsonTree(messages.map {
                mapOf("role" to it.role, "content" to it.content)
            }))
            addProperty("max_tokens", config.maxTokens)
            addProperty("temperature", config.temperature)
        }

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("API call failed: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        val choices = jsonResponse.getAsJsonArray("choices")
        if (choices.size() == 0) {
            throw Exception("No response from AI")
        }

        return choices[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    }

    private fun buildAnalyzeAppsPrompt(apps: List<AppInfoForAI>): String {
        return buildString {
            appendLine("Analyze the following Android apps and their storage usage.")
            appendLine("For each app, determine:")
            appendLine("1. Is the cache likely valuable or safe to clean?")
            appendLine("2. Suggest an action: clean_cache, keep, review, or compress")
            appendLine("3. Provide a brief reason")
            appendLine("4. Rate confidence 0.0-1.0")
            appendLine()
            appendLine("Apps:")
            apps.forEach { app ->
                appendLine("- ${app.appName} (${app.packageName})")
                appendLine("  Cache: ${formatSize(app.cacheSize)}, Data: ${formatSize(app.dataSize)}")
                appendLine("  Last used: ${app.lastUsedDaysAgo} days ago")
                appendLine("  System app: ${app.isSystemApp}")
            }
            appendLine()
            appendLine("Respond in JSON format:")
            appendLine("""{"summary": "...", "recommendations": [{"target": "package", "action": "...", "reason": "...", "confidence": 0.8, "estimatedSavings": 1234}]}""")
        }
    }

    private fun buildCacheValuePrompt(packageName: String, cacheFiles: List<CacheFileInfo>): String {
        return buildString {
            appendLine("Analyze cache files for app: $packageName")
            appendLine("Determine the value of each cache type (0.0=worthless, 1.0=critical).")
            appendLine()
            appendLine("Cache files:")
            cacheFiles.forEach { file ->
                appendLine("- ${file.path} (${formatSize(file.size)}, ${file.type})")
            }
            appendLine()
            appendLine("Respond in JSON format:")
            appendLine("""{"cacheType": "...", "value": 0.5, "reason": "..."}""")
        }
    }

    private fun parseAnalysisResponse(response: String): AIAnalysisResult {
        return try {
            val json = JsonParser.parseString(response).asJsonObject
            AIAnalysisResult(
                summary = json.get("summary")?.asString ?: "Analysis complete",
                recommendations = json.getAsJsonArray("recommendations")?.map { element ->
                    val obj = element.asJsonObject
                    AIRecommendation(
                        target = obj.get("target").asString,
                        action = obj.get("action").asString,
                        reason = obj.get("reason").asString,
                        confidence = obj.get("confidence")?.asFloat ?: 0.5f,
                        estimatedSavings = obj.get("estimatedSavings")?.asLong ?: 0L
                    )
                } ?: emptyList(),
                estimatedSavings = json.get("estimatedSavings")?.asLong ?: 0L
            )
        } catch (e: Exception) {
            AIAnalysisResult(
                summary = response.take(200),
                recommendations = emptyList(),
                estimatedSavings = 0L
            )
        }
    }

    private fun parseCacheValueResponse(packageName: String, response: String): AICacheValue {
        return try {
            val json = JsonParser.parseString(response).asJsonObject
            AICacheValue(
                packageName = packageName,
                cacheType = json.get("cacheType")?.asString ?: "unknown",
                value = json.get("value")?.asFloat ?: 0.5f,
                reason = json.get("reason")?.asString ?: ""
            )
        } catch (e: Exception) {
            AICacheValue(
                packageName = packageName,
                cacheType = "unknown",
                value = 0.5f,
                reason = response.take(100)
            )
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

    companion object {
        private const val SYSTEM_PROMPT = """You are an Android storage optimization expert. 
Analyze app storage data and provide actionable recommendations for cleaning.
Be conservative - only recommend cleaning when confident it's safe.
Consider: app usage patterns, cache types, system vs user apps, data importance.
Always respond in valid JSON format."""

        val DEFAULT_CONFIGS = mapOf(
            "openai" to AIConfig(
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-3.5-turbo"
            ),
            "dashscope" to AIConfig(
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "qwen-turbo"
            ),
            "deepseek" to AIConfig(
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat"
            ),
            "ollama" to AIConfig(
                baseUrl = "http://localhost:11434/v1",
                model = "llama2"
            )
        )
    }
}

data class AppInfoForAI(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val dataSize: Long,
    val lastUsedDaysAgo: Int,
    val isSystemApp: Boolean
)

data class CacheFileInfo(
    val path: String,
    val size: Long,
    val type: String // "image", "video", "database", "temp", etc.
)
