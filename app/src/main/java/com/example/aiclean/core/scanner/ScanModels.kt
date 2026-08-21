package com.example.aiclean.core.scanner

import java.io.File

data class ScanResult(
    val totalSize: Long = 0,
    val cacheSize: Long = 0,
    val junkSize: Long = 0,
    val duplicateSize: Long = 0,
    val appCount: Int = 0,
    val apps: List<AppInfo> = emptyList()
)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val dataSize: Long,
    val totalSize: Long,
    val lastUsed: Long,
    val isSystemApp: Boolean,
    val aiRecommendation: String? = null,
    val aiCacheValue: Float? = null // 0.0-1.0, higher = more valuable
)

data class FileInfo(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isJunk: Boolean = false,
    val junkConfidence: Float = 0f,
    val category: String = "unknown"
)

data class DuplicateGroup(
    val files: List<FileInfo>,
    val totalSize: Long
)

data class ScanProgress(
    val currentPath: String,
    val scannedFiles: Int,
    val totalFiles: Int,
    val phase: ScanPhase
)

enum class ScanPhase {
    INITIALIZING,
    SCANNING_CACHE,
    SCANNING_DATA,
    SCANNING_JUNK,
    FINDING_DUPLICATES,
    ANALYZING_WITH_AI,
    COMPLETED
}
