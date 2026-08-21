package com.example.aiclean.core.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow(ScanProgress("", 0, 0, ScanPhase.INITIALIZING))
    val progress: StateFlow<ScanProgress> = _progress

    suspend fun scanStorage(): ScanResult = withContext(Dispatchers.IO) {
        _progress.value = ScanProgress("Starting scan...", 0, 0, ScanPhase.INITIALIZING)

        val apps = scanApps()
        val junkFiles = scanJunkFiles()
        val duplicates = findDuplicates(junkFiles)

        val totalCache = apps.sumOf { it.cacheSize }
        val totalJunk = junkFiles.filter { it.isJunk }.sumOf { it.size }
        val totalDuplicate = duplicates.sumOf { it.totalSize }

        _progress.value = ScanProgress("Scan complete", 100, 100, ScanPhase.COMPLETED)

        ScanResult(
            totalSize = totalCache + totalJunk + totalDuplicate,
            cacheSize = totalCache,
            junkSize = totalJunk,
            duplicateSize = totalDuplicate,
            appCount = apps.size,
            apps = apps
        )
    }

    private suspend fun scanApps(): List<AppInfo> {
        _progress.value = ScanProgress("Scanning apps...", 0, 100, ScanPhase.SCANNING_CACHE)

        val packageManager = context.packageManager
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val apps = mutableListOf<AppInfo>()

        packages.forEachIndexed { index, appInfo ->
            _progress.value = ScanProgress(
                appInfo.packageName,
                index,
                packages.size,
                ScanPhase.SCANNING_CACHE
            )

            try {
                val cacheDir = File(appInfo.dataDir, "cache")
                val codeCacheDir = File(appInfo.dataDir, "code_cache")
                val externalCacheDir = context.getExternalFilesDir(null)?.let {
                    File(it.parentFile, "${appInfo.packageName}/cache")
                }

                val cacheSize = calculateDirSize(cacheDir) +
                        calculateDirSize(codeCacheDir) +
                        calculateDirSize(externalCacheDir)

                val dataSize = calculateDirSize(File(appInfo.dataDir)) - cacheSize

                val lastUsed = getLastUsedTime(appInfo.packageName)

                apps.add(
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        cacheSize = cacheSize,
                        dataSize = dataSize,
                        totalSize = cacheSize + dataSize,
                        lastUsed = lastUsed,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                )
            } catch (e: Exception) {
                Log.e("StorageScanner", "Error scanning ${appInfo.packageName}: ${e.message}")
            }
        }

        return apps.sortedByDescending { it.totalSize }
    }

    private suspend fun scanJunkFiles(): List<FileInfo> {
        _progress.value = ScanProgress("Scanning junk files...", 0, 0, ScanPhase.SCANNING_JUNK)

        val junkFiles = mutableListOf<FileInfo>()
        val externalStorage = Environment.getExternalStorageDirectory()

        scanDirectory(externalStorage, junkFiles, 0, maxDepth = 5)

        return junkFiles
    }

    private fun scanDirectory(
        dir: File,
        results: MutableList<FileInfo>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.canRead()) return

        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val fileInfo = analyzeFile(file)
                    results.add(fileInfo)
                } else if (file.isDirectory) {
                    scanDirectory(file, results, currentDepth + 1, maxDepth)
                }
            }
        } catch (e: Exception) {
            Log.e("StorageScanner", "Error scanning ${dir.path}: ${e.message}")
        }
    }

    private fun analyzeFile(file: File): FileInfo {
        val extension = file.extension.lowercase()
        val path = file.absolutePath.lowercase()

        val isJunk = when {
            // Temporary files
            extension in listOf("tmp", "temp", "log", "bak", "old", "swp") -> true
            path.contains("/cache/") -> true
            path.contains("/temp/") -> true
            path.contains("/tmp/") -> true
            // Download partial files
            extension == "crdownload" || extension == "part" -> true
            // Thumbnail caches
            path.contains(".thumbnails") || path.contains("thumbcache") -> true
            // App leftovers
            path.contains("/tombstones/") -> true
            else -> false
        }

        val category = categorizeFile(file)

        return FileInfo(
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            isJunk = isJunk,
            junkConfidence = if (isJunk) 0.8f else 0.2f,
            category = category
        )
    }

    private fun categorizeFile(file: File): String {
        val extension = file.extension.lowercase()
        return when {
            extension in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> "image"
            extension in listOf("mp4", "avi", "mkv", "mov", "3gp") -> "video"
            extension in listOf("mp3", "wav", "aac", "flac", "ogg") -> "audio"
            extension in listOf("db", "sqlite", "sqlite3") -> "database"
            extension in listOf("log", "txt") -> "text"
            extension in listOf("apk", "xapk") -> "installer"
            extension in listOf("zip", "rar", "7z", "tar", "gz") -> "archive"
            else -> "other"
        }
    }

    private suspend fun findDuplicates(files: List<FileInfo>): List<DuplicateGroup> {
        _progress.value = ScanProgress("Finding duplicates...", 0, 0, ScanPhase.FINDING_DUPLICATES)

        val sizeGroups = files.filter { it.size > 1024 } // Only check files > 1KB
            .groupBy { it.size }
            .filter { it.value.size > 1 }

        val duplicates = mutableListOf<DuplicateGroup>()

        sizeGroups.values.forEach { group ->
            val hashGroups = group.groupBy { file ->
                calculateFileHash(File(file.path))
            }.filter { it.value.size > 1 }

            hashGroups.values.forEach { duplicateFiles ->
                duplicates.add(
                    DuplicateGroup(
                        files = duplicateFiles,
                        totalSize = duplicateFiles.sumOf { it.size }
                    )
                )
            }
        }

        return duplicates.sortedByDescending { it.totalSize }
    }

    private fun calculateFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.absolutePath // Fallback to path if can't hash
        }
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun getLastUsedTime(packageName: String): Long {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 30L * 24 * 60 * 60 * 1000 // Last 30 days
            val stats = usageStatsManager?.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            stats?.filter { it.packageName == packageName }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.lastTimeUsed ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getStorageStats(): StorageStats {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes

        return StorageStats(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            usedPercentage = (usedBytes * 100 / totalBytes).toInt()
        )
    }
}

data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usedPercentage: Int
)
