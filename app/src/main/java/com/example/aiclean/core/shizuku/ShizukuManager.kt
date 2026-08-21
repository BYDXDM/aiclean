package com.example.aiclean.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d("ShizukuManager", "Binder received")
        _isAvailable.value = true
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("ShizukuManager", "Binder dead")
        _isAvailable.value = false
        _isPermissionGranted.value = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            _isPermissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d("ShizukuManager", "Permission result: ${_isPermissionGranted.value}")
        }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Failed to initialize Shizuku: ${e.message}")
        }
    }

    fun checkPermission() {
        try {
            if (Shizuku.isPreV11()) {
                _isPermissionGranted.value = false
                return
            }

            _isPermissionGranted.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Permission check failed: ${e.message}")
            _isPermissionGranted.value = false
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Permission request failed: ${e.message}")
        }
    }

    suspend fun executeCommand(command: String): ShizukuResult = withContext(Dispatchers.IO) {
        try {
            if (!_isAvailable.value) {
                return@withContext ShizukuResult(
                    success = false,
                    output = "",
                    error = "Shizuku not available"
                )
            }

            if (!_isPermissionGranted.value) {
                return@withContext ShizukuResult(
                    success = false,
                    output = "",
                    error = "Shizuku permission not granted"
                )
            }

            // Use Runtime.exec with sh -c since Shizuku.newProcess is private
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            ShizukuResult(
                success = exitCode == 0,
                output = output,
                error = if (exitCode != 0) error else null
            )
        } catch (e: Exception) {
            ShizukuResult(
                success = false,
                output = "",
                error = e.message
            )
        }
    }

    suspend fun cleanAppCache(packageName: String): Boolean {
        val result = executeCommand("pm clear --cache-only $packageName")
        return result.success
    }

    suspend fun deleteFile(path: String): Boolean {
        val result = executeCommand("rm -rf \"$path\"")
        return result.success
    }

    suspend fun getInstalledPackages(): List<String> {
        val result = executeCommand("pm list packages -3")
        return if (result.success) {
            result.output.lines()
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:") }
        } else {
            emptyList()
        }
    }

    fun cleanup() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e("ShizukuManager", "Cleanup failed: ${e.message}")
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1001
    }
}

data class ShizukuResult(
    val success: Boolean,
    val output: String,
    val error: String?
)
