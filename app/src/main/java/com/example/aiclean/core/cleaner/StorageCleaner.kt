package com.example.aiclean.core.cleaner

import android.content.Context
import com.example.aiclean.core.root.RootManager
import com.example.aiclean.core.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 所有删除均只处理用户选中的公开文件；应用私有缓存仅在 Root 可用时按 cache 路径处理。 */
@Singleton
class StorageCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager
) {
    suspend fun cleanCache(packageName: String): CleanResult = withContext(Dispatchers.IO) {
        if (rootManager.isRootAvailable()) {
            // pm clear 会删除应用数据，绝不能用于“清缓存”。只触及 cache 与 code_cache。
            val safePackage = packageName.takeIf { it.matches(Regex("[A-Za-z0-9._]+")) }
                ?: return@withContext CleanResult(false, 0, emptyList(), "无效的应用包名")
            val paths = listOf(
                "/data/user/0/$safePackage/cache",
                "/data/user/0/$safePackage/code_cache",
                "/data/data/$safePackage/cache",
                "/data/data/$safePackage/code_cache"
            )
            val command = paths.joinToString("; ") { "rm -rf '$it'" }
            val result = rootManager.executeCommand(command)
            return@withContext if (result.success) {
                CleanResult(true, 0, listOf(packageName), "已通过 Root 清理 ${packageName} 的缓存")
            } else CleanResult(false, 0, emptyList(), "Root 清理失败：${result.error ?: "未知错误"}")
        }

        // Android 沙盒禁止普通应用访问其它应用私有缓存；明确告知而非伪造清理成功。
        CleanResult(false, 0, emptyList(), "普通权限无法清理其它应用缓存。请启用 Root，或在系统设置中清理该应用缓存。")
    }

    suspend fun cleanJunkFiles(files: List<String>): CleanResult = withContext(Dispatchers.IO) {
        var cleaned = 0L
        val deleted = mutableListOf<String>()
        val errors = mutableListOf<String>()
        files.distinct().forEach { path ->
            val file = File(path)
            if (!isPublicStoragePath(file)) {
                errors += "拒绝删除非公开路径：$path"
                return@forEach
            }
            try {
                val size = if (file.isFile) file.length() else directorySize(file)
                val success = when {
                    file.deleteRecursively() -> true
                    rootManager.isRootAvailable() -> rootManager.deleteFileAsRoot(file.absolutePath)
                    else -> false
                }
                if (success) {
                    cleaned += size
                    deleted += path
                } else errors += "无法删除：$path"
            } catch (e: Exception) {
                errors += "删除失败：${e.message ?: path}"
            }
        }
        CleanResult(
            success = errors.isEmpty(),
            cleanedBytes = cleaned,
            cleanedFiles = deleted,
            message = if (errors.isEmpty()) "已清理 ${deleted.size} 个文件，释放 ${formatSize(cleaned)}" else "已清理 ${deleted.size} 个文件；${errors.size} 个文件未能删除"
        )
    }

    suspend fun getAccessLevel(): AccessLevel = when {
        rootManager.isRootAvailable() -> AccessLevel.ROOT
        shizukuManager.isPermissionGranted.first() -> AccessLevel.SHIZUKU
        else -> AccessLevel.NORMAL
    }

    private fun isPublicStoragePath(file: File): Boolean {
        val root = android.os.Environment.getExternalStorageDirectory().canonicalFile
        return runCatching { file.canonicalFile.path.startsWith(root.path + File.separator) }.getOrDefault(false)
    }

    private fun directorySize(file: File): Long = if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun formatSize(bytes: Long) = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
}

data class CleanResult(val success: Boolean, val cleanedBytes: Long, val cleanedFiles: List<String>, val message: String)
enum class AccessLevel { NORMAL, ROOT, SHIZUKU }
