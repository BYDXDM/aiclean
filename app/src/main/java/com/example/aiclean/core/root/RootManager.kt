package com.example.aiclean.core.root

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isRooted: Boolean? = null

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        isRooted ?: run {
            val result = checkRootAccess()
            isRooted = result
            result
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("id\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            val exitCode = process.exitValue()
            exitCode == 0
        } catch (e: Exception) {
            Log.e("RootManager", "Root check failed: ${e.message}")
            false
        }
    }

    suspend fun executeCommand(command: String): RootResult = withContext(Dispatchers.IO) {
        try {
            if (!isRootAvailable()) {
                return@withContext RootResult(
                    success = false,
                    output = "",
                    error = "Root access not available"
                )
            }

            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()

            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            RootResult(
                success = exitCode == 0,
                output = output,
                error = if (exitCode != 0) error else null
            )
        } catch (e: Exception) {
            RootResult(
                success = false,
                output = "",
                error = e.message
            )
        }
    }

    suspend fun cleanAppCacheAsRoot(packageName: String): Boolean {
        val result = executeCommand("pm clear --cache-only $packageName")
        return result.success
    }

    suspend fun deleteFileAsRoot(path: String): Boolean {
        val result = executeCommand("rm -rf \"$path\"")
        return result.success
    }

    suspend fun getFileSizeAsRoot(path: String): Long {
        val result = executeCommand("du -sb \"$path\" | cut -f1")
        return if (result.success) {
            result.output.trim().toLongOrNull() ?: 0L
        } else {
            0L
        }
    }
}

data class RootResult(
    val success: Boolean,
    val output: String,
    val error: String?
)
