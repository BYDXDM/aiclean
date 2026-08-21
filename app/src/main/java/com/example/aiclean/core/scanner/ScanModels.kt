package com.example.aiclean.core.scanner

data class ScanResult(
    val totalSize: Long = 0,
    val cacheSize: Long = 0,
    val junkSize: Long = 0,
    /** 可释放空间：每组重复文件保留一份后的冗余部分。 */
    val duplicateSize: Long = 0,
    val appCount: Int = 0,
    val apps: List<AppInfo> = emptyList(),
    val junkFiles: List<FileInfo> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val categories: List<FileCategoryStat> = emptyList(),
    val scannedFileCount: Int = 0,
    val scanTruncated: Boolean = false
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
    val aiCacheValue: Float? = null
)

data class FileInfo(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isJunk: Boolean = false,
    val junkConfidence: Float = 0f,
    /** image/video/audio/document/archive/installer/other */
    val category: String = "other",
    /** cache/temp/log/thumbnail/partial/installer/other */
    val junkType: String? = null
)

data class DuplicateGroup(
    val files: List<FileInfo>,
    /** 可释放空间（保留一份文件，删除其余副本）。 */
    val totalSize: Long
)

data class FileCategoryStat(
    val category: String,
    val size: Long,
    val fileCount: Int
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
