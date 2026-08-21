package com.example.aiclean.core.ai

data class AIConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-3.5-turbo",
    val maxTokens: Int = 1000,
    val temperature: Float = 0.7f
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class AIAnalysisResult(
    val summary: String,
    val recommendations: List<AIRecommendation>,
    val estimatedSavings: Long
)

data class AIRecommendation(
    val target: String, // package name or file path
    val action: String, // "clean_cache", "keep", "review", "compress"
    val reason: String,
    val confidence: Float, // 0.0-1.0
    val estimatedSavings: Long
)

data class AICacheValue(
    val packageName: String,
    val cacheType: String,
    val value: Float, // 0.0-1.0
    val reason: String
)
