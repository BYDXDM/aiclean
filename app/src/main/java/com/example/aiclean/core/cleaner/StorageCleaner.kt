package com.example.aiclean.core.cleaner

import android.content.Context
import android.util.Log
import com.example.aiclean.core.root.RootManager
import com.example.aiclean.core.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager
) {
    suspend fun cleanCache(packageName: String): CleanResult = withContext(Dispatchers.IO) {
        try {
            // Try Shizuku first (faster than root)
            if (shizukuManager.isPermissionGranted.first()) {
                val success = shizukuManager.cleanAppCache(packageName)
                if (success) {
                    return@withContext CleanResult(
                        success = true,
                        cleanedBytes = 0, // Actual size unknown with Shizuku
                        cleanedFiles = listOf(packageName),
                        message = "Cleaned cache for $packageName via Shizuku"
                    )
                }
            }

            // Try Root
            if (rootManager.isRootAvailable()) {
                val success = rootManager.cleanAppCacheAsRoot(packageName)
                if (success) {
                    return@withContext CleanResult(
                        success = true,
                        cleanedBytes = 0,
                        cleanedFiles = listOf(packageName),
                        message = "Cleaned cache for $packageName via Root"
                    )
                }
            }

            // Fallback to standard method
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            var cleanedBytes = 0L
            val cleanedFiles = mutableListOf<String>()

            // Clean internal cache
            val cacheDir = File(appInfo.dataDir, "cache")
            if (cacheDir.exists() && cacheDir.canWrite()) {
                val size = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                cacheDir.deleteRecursively()
                cleanedBytes += size
                cleanedFiles.add(cacheDir.path)
            }

            // Clean code cache
            val codeCacheDir = File(appInfo.dataDir, "code_cache")
            if (codeCacheDir.exists() && codeCacheDir.canWrite()) {
                val size = codeCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                codeCacheDir.deleteRecursively()
                cleanedBytes += size
                cleanedFiles.add(codeCacheDir.path)
            }

            // Clean external cache
            val externalCacheDir = context.getExternalFilesDir(null)?.let {
                File(it.parentFile, "$packageName/cache")
            }
            if (externalCacheDir != null && externalCacheDir.exists() && externalCacheDir.canWrite()) {
                val size = externalCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                externalCacheDir.deleteRecursively()
                cleanedBytes += size
                cleanedFiles.add(externalCacheDir.path)
            }

            CleanResult(
                success = true,
                cleanedBytes = cleanedBytes,
                cleanedFiles = cleanedFiles,
                message = "Cleaned ${formatSize(cleanedBytes)} from $packageName"
            )
        } catch (e: Exception) {
            Log.e("StorageCleaner", "Error cleaning $packageName: ${e.message}")
            CleanResult(
                success = false,
                cleanedBytes = 0,
                cleanedFiles = emptyList(),
                message = "Failed to clean $packageName: ${e.message}"
            )
        }
    }

    suspend fun cleanJunkFiles(files: List<String>): CleanResult = withContext(Dispatchers.IO) {
        var cleanedBytes = 0L
        val cleanedFiles = mutableListOf<String>()
        val errors = mutableListOf<String>()

        files.forEach { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val size = file.length()
                    
                    // Try Shizuku
                    if (shizukuManager.isPermissionGranted.first()) {
                        val success = shizukuManager.deleteFile(filePath)
                        if (success) {
                            cleanedBytes += size
                            cleanedFiles.add(filePath)
                            return@forEach
                        }
                    }

                    // Try Root
                    if (rootManager.isRootAvailable()) {
                        val success = rootManager.deleteFileAsRoot(filePath)
                        if (success) {
                            cleanedBytes += size
                            cleanedFiles.add(filePath)
                            return@forEach
                        }
                    }

                    // Fallback
                    if (file.delete()) {
                        cleanedBytes += size
                        cleanedFiles.add(filePath)
                    } else {
                        errors.add("Failed to delete: $filePath")
                    }
                }
            } catch (e: Exception) {
                errors.add("Error deleting $filePath: ${e.message}")
            }
        }

        CleanResult(
            success = errors.isEmpty(),
            cleanedBytes = cleanedBytes,
            cleanedFiles = cleanedFiles,
            message = if (errors.isEmpty()) {
                "Cleaned ${cleanedFiles.size} files (${formatSize(cleanedBytes)})"
            } else {
                "Cleaned ${cleanedFiles.size} files, ${errors.size} errors"
            }
        )
    }

    suspend fun cleanDuplicates(files: List<List<String>>): CleanResult = withContext(Dispatchers.IO) {
        var cleanedBytes = 0L
        val cleanedFiles = mutableListOf<String>()

        files.forEach { duplicateGroup ->
            // Keep the first file, delete the rest
            if (duplicateGroup.size > 1) {
                duplicateGroup.drop(1).forEach { filePath ->
                    try {
                        val file = File(filePath)
                        if (file.exists()) {
                            val size = file.length()
                            
                            // Try Shizuku
                            if (shizukuManager.isPermissionGranted.first()) {
                                val success = shizukuManager.deleteFile(filePath)
                                if (success) {
                                    cleanedBytes += size
                                    cleanedFiles.add(filePath)
                                    return@forEach
                                }
                            }

                            // Try Root
                            if (rootManager.isRootAvailable()) {
                                val success = rootManager.deleteFileAsRoot(filePath)
                                if (success) {
                                    cleanedBytes += size
                                    cleanedFiles.add(filePath)
                                    return@forEach
                                }
                            }

                            // Fallback
                            if (file.delete()) {
                                cleanedBytes += size
                                cleanedFiles.add(filePath)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StorageCleaner", "Error deleting duplicate $filePath: ${e.message}")
                    }
                }
            }
        }

        CleanResult(
            success = true,
            cleanedBytes = cleanedBytes,
            cleanedFiles = cleanedFiles,
            message = "Removed ${cleanedFiles.size} duplicate files (${formatSize(cleanedBytes)})"
        )
    }

    suspend fun getAccessLevel(): AccessLevel {
        return when {
            shizukuManager.isPermissionGranted.first() -> AccessLevel.SHIZUKU
            rootManager.isRootAvailable() -> AccessLevel.ROOT
            else -> AccessLevel.NORMAL
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

data class CleanResult(
    val success: Boolean,
    val cleanedBytes: Long,
    val cleanedFiles: List<String>,
    val message: String
)

enum class AccessLevel {
    NORMAL,
    ROOT,
    SHIZUKU
}
