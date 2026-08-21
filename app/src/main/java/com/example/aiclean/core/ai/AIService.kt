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

    suspend fun fetchModels(apiKey: String, baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("请求失败: ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw Exception("响应为空")
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            
            // OpenAI format: {"data": [{"id": "model-name", ...}, ...]}
            val dataArray = jsonResponse.getAsJsonArray("data")
            if (dataArray != null) {
                return@withContext dataArray.map { element ->
                    element.asJsonObject.get("id").asString
                }.sorted()
            }
            
            // DashScope format: {"data": [{"model_name": "...", ...}, ...]}
            if (dataArray != null) {
                return@withContext dataArray.map { element ->
                    val obj = element.asJsonObject
                    obj.get("id")?.asString ?: obj.get("model_name")?.asString ?: ""
                }.filter { it.isNotEmpty() }.sorted()
            }
            
            emptyList()
        } catch (e: Exception) {
            throw Exception("获取模型列表失败: ${e.message}")
        }
    }

    suspend fun analyzeApps(
        config: AIConfig,
        apps: List<AppInfoForAI>
    ): Result<AIAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API 密钥未配置"))
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
                return@withContext Result.failure(Exception("API 密钥未配置"))
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
                return@withContext Result.failure(Exception("API 密钥未配置"))
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
            throw Exception("API 调用失败: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("响应为空")
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        val choices = jsonResponse.getAsJsonArray("choices")
        if (choices.size() == 0) {
            throw Exception("AI 无响应")
        }

        return choices[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    }

    private fun buildAnalyzeAppsPrompt(apps: List<AppInfoForAI>): String {
        return buildString {
            appendLine("分析以下 Android 应用的存储使用情况。")
            appendLine("对每个应用判断：")
            appendLine("1. 缓存是否有价值？是否可以安全清理？")
            appendLine("2. 建议操作：clean_cache（清理缓存）、keep（保留）、review（审查）、compress（压缩）")
            appendLine("3. 给出简要理由")
            appendLine("4. 置信度 0.0-1.0")
            appendLine()
            appendLine("应用列表：")
            apps.forEach { app ->
                appendLine("- ${app.appName} (${app.packageName})")
                appendLine("  缓存: ${formatSize(app.cacheSize)}, 数据: ${formatSize(app.dataSize)}")
                appendLine("  最后使用: ${if (app.lastUsedDaysAgo >= 0) "${app.lastUsedDaysAgo} 天前" else "未知"}")
                appendLine("  系统应用: ${app.isSystemApp}")
            }
            appendLine()
            appendLine("请用 JSON 格式回复：")
            appendLine("""{"summary": "...", "recommendations": [{"target": "包名", "action": "...", "reason": "...", "confidence": 0.8, "estimatedSavings": 1234}]}""")
        }
    }

    private fun buildCacheValuePrompt(packageName: String, cacheFiles: List<CacheFileInfo>): String {
        return buildString {
            appendLine("分析应用 $packageName 的缓存文件。")
            appendLine("判断每种缓存类型的价值（0.0=无价值，1.0=关键）。")
            appendLine()
            appendLine("缓存文件：")
            cacheFiles.forEach { file ->
                appendLine("- ${file.path} (${formatSize(file.size)}, ${file.type})")
            }
            appendLine()
            appendLine("请用 JSON 格式回复：")
            appendLine("""{"cacheType": "...", "value": 0.5, "reason": "..."}""")
        }
    }

    private fun parseAnalysisResponse(response: String): AIAnalysisResult {
        return try {
            val json = JsonParser.parseString(response).asJsonObject
            AIAnalysisResult(
                summary = json.get("summary")?.asString ?: "分析完成",
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
                cacheType = json.get("cacheType")?.asString ?: "未知",
                value = json.get("value")?.asFloat ?: 0.5f,
                reason = json.get("reason")?.asString ?: ""
            )
        } catch (e: Exception) {
            AICacheValue(
                packageName = packageName,
                cacheType = "未知",
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
        private const val SYSTEM_PROMPT = """你是一个 Android 存储优化专家。
分析应用存储数据并提供可操作的清理建议。
要保守——只在确信安全时才建议清理。
考虑：应用使用频率、缓存类型、系统应用vs用户应用、数据重要性。
请务必用有效的 JSON 格式回复。"""

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
