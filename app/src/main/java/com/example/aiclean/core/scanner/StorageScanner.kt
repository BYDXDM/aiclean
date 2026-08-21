package com.example.aiclean.core.scanner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.Settings
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

/**
 * 仅扫描应用可合法访问的公开共享存储；不会伪造其它应用私有目录的数据。
 * 应用体积/缓存统计需要“查看使用情况”授权，未授权时会显示为 0。
 */
@Singleton
class StorageScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow(ScanProgress("", 0, 0, ScanPhase.INITIALIZING))
    val progress: StateFlow<ScanProgress> = _progress

    suspend fun scanStorage(): ScanResult = withContext(Dispatchers.IO) {
        _progress.value = ScanProgress("正在初始化扫描", 0, 0, ScanPhase.INITIALIZING)
        val apps = scanApps()
        val publicFiles = scanPublicFiles()
        val junkFiles = publicFiles.filter { it.isJunk }.sortedByDescending { it.size }
        val duplicates = findDuplicates(publicFiles)
        val categories = publicFiles.groupBy { it.category }.map { (category, files) ->
            FileCategoryStat(category, files.sumOf { it.size }, files.size)
        }.sortedByDescending { it.size }

        _progress.value = ScanProgress("扫描完成", publicFiles.size, publicFiles.size, ScanPhase.COMPLETED)
        ScanResult(
            cacheSize = apps.sumOf { it.cacheSize },
            junkSize = junkFiles.sumOf { it.size },
            duplicateSize = duplicates.sumOf { it.totalSize },
            appCount = apps.size,
            apps = apps,
            junkFiles = junkFiles,
            duplicates = duplicates,
            categories = categories,
            scannedFileCount = publicFiles.size,
            scanTruncated = publicFiles.size >= MAX_SCANNED_FILES
        )
    }

    suspend fun scanDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        findDuplicates(scanPublicFiles())
    }

    suspend fun scanJunkFiles(): List<FileInfo> = withContext(Dispatchers.IO) {
        scanPublicFiles().filter { it.isJunk }.sortedByDescending { it.size }
    }

    private fun scanApps(): List<AppInfo> {
        _progress.value = ScanProgress("正在读取应用存储", 0, 0, ScanPhase.SCANNING_CACHE)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledApplications(0)
        return packages.mapIndexed { index, app ->
            _progress.value = ScanProgress(app.packageName, index + 1, packages.size, ScanPhase.SCANNING_CACHE)
            val stats = getPackageStats(app.packageName, app.uid)
            AppInfo(
                packageName = app.packageName,
                appName = app.loadLabel(pm).toString(),
                cacheSize = stats?.cacheBytes ?: 0L,
                dataSize = stats?.dataBytes ?: 0L,
                totalSize = (stats?.appBytes ?: 0L) + (stats?.dataBytes ?: 0L) + (stats?.cacheBytes ?: 0L),
                lastUsed = getLastUsedTime(app.packageName),
                isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }.sortedByDescending { it.totalSize }
    }

    private fun getPackageStats(packageName: String, uid: Int): android.app.usage.StorageStats? = try {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        manager.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, UserHandle.getUserHandleForUid(uid))
    } catch (e: Exception) {
        null
    }

    private fun scanPublicFiles(): List<FileInfo> {
        _progress.value = ScanProgress("正在扫描公开文件", 0, 0, ScanPhase.SCANNING_JUNK)
        val output = ArrayList<FileInfo>()
        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root, output, 0)
        return output
    }

    private fun scanDirectory(dir: File, output: MutableList<FileInfo>, depth: Int) {
        if (depth > MAX_DEPTH || output.size >= MAX_SCANNED_FILES || !dir.canRead() || shouldSkipDirectory(dir)) return
        val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (file in children) {
            if (output.size >= MAX_SCANNED_FILES) return
            if (file.isDirectory) scanDirectory(file, output, depth + 1)
            else if (file.isFile && file.length() > 0) {
                output.add(analyzeFile(file))
                if (output.size % 100 == 0) {
                    _progress.value = ScanProgress(file.absolutePath, output.size, 0, ScanPhase.SCANNING_JUNK)
                }
            }
        }
    }

    private fun shouldSkipDirectory(dir: File): Boolean {
        val path = dir.absolutePath.lowercase()
        return path.endsWith("/android/data") || path.endsWith("/android/obb") ||
            path.contains("/.trash") || path.contains("/.git")
    }

    private fun analyzeFile(file: File): FileInfo {
        val extension = file.extension.lowercase()
        val path = file.absolutePath.lowercase()
        val junkType = when {
            extension in TEMP_EXTENSIONS || path.contains("/temp/") || path.contains("/tmp/") -> "temp"
            extension == "log" || path.contains("/log/") -> "log"
            extension == "crdownload" || extension == "part" || extension == "download" -> "partial"
            path.contains(".thumbnails") || path.contains("thumbcache") -> "thumbnail"
            path.contains("/cache/") -> "cache"
            extension in setOf("apk", "xapk", "apks") && isOld(file, 14) -> "installer"
            else -> null
        }
        return FileInfo(
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            isJunk = junkType != null,
            junkConfidence = when (junkType) {
                "temp", "log", "partial", "thumbnail" -> 0.95f
                "cache" -> 0.75f
                "installer" -> 0.65f
                else -> 0f
            },
            category = categorizeFile(file),
            junkType = junkType
        )
    }

    private fun isOld(file: File, days: Int): Boolean =
        System.currentTimeMillis() - file.lastModified() > days * 24L * 60 * 60 * 1000

    private fun categorizeFile(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "image"
        "mp4", "avi", "mkv", "mov", "3gp", "webm" -> "video"
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> "audio"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub" -> "document"
        "apk", "xapk", "apks" -> "installer"
        "zip", "rar", "7z", "tar", "gz" -> "archive"
        else -> "other"
    }

    private fun findDuplicates(files: List<FileInfo>): List<DuplicateGroup> {
        _progress.value = ScanProgress("正在比对重复文件", 0, 0, ScanPhase.FINDING_DUPLICATES)
        val output = mutableListOf<DuplicateGroup>()
        val candidates = files.asSequence()
            .filter { it.size in MIN_DUPLICATE_SIZE..MAX_HASH_FILE_SIZE }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
        var completed = 0
        candidates.values.forEach { sameSize ->
            val identicalGroups = sameSize.groupBy { calculateFileHash(File(it.path)) }
                .filter { (hash, group) -> hash != null && group.size > 1 }
            identicalGroups.values.forEach { group ->
                val ordered = group.sortedBy { it.lastModified }
                output += DuplicateGroup(
                    files = ordered,
                    totalSize = ordered.drop(1).sumOf { it.size }
                )
            }
            completed++
            _progress.value = ScanProgress("正在校验文件指纹", completed, candidates.size, ScanPhase.FINDING_DUPLICATES)
        }
        return output.sortedByDescending { it.totalSize }
    }

    private fun calculateFileHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "无法读取文件: ${file.path}")
        null
    }

    private fun getLastUsedTime(packageName: String): Long = try {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val end = System.currentTimeMillis()
        usage.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, end - 90L * DAY, end)
            ?.filter { it.packageName == packageName }?.maxOfOrNull { it.lastTimeUsed } ?: 0L
    } catch (_: Exception) { 0L }

    fun getStorageStats(): StorageStats {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return StorageStats(total, total - free, free, if (total > 0) ((total - free) * 100 / total).toInt() else 0)
    }

    companion object {
        private const val TAG = "StorageScanner"
        private const val MAX_DEPTH = 7
        private const val MAX_SCANNED_FILES = 25_000
        private const val MIN_DUPLICATE_SIZE = 4L * 1024
        private const val MAX_HASH_FILE_SIZE = 512L * 1024 * 1024
        private const val DAY = 24L * 60 * 60 * 1000
        private val TEMP_EXTENSIONS = setOf("tmp", "temp", "bak", "old", "swp")
    }
}

data class StorageStats(val totalBytes: Long, val usedBytes: Long, val freeBytes: Long, val usedPercentage: Int)
