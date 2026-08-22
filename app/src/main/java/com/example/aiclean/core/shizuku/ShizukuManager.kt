package com.example.aiclean.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/** 官方 UserService 接入：命令由 Shizuku 的 shell/root 身份进程执行。 */
@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable
    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    @Volatile private var commandService: ICommandService? = null
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, CommandUserService::class.java.name)
    ).tag("aiclean-command-service").version(2).daemon(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            commandService = ICommandService.Stub.asInterface(service)
            Log.i(TAG, "Shizuku UserService 已连接")
        }
        override fun onServiceDisconnected(name: ComponentName) {
            commandService = null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isAvailable.value = true
        checkPermission()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _isPermissionGranted.value = false
        commandService = null
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        _isPermissionGranted.value = result == PackageManager.PERMISSION_GRANTED
        if (_isPermissionGranted.value) bindUserService()
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            checkPermission()
        }.onFailure { Log.w(TAG, "Shizuku 未启动：${it.message}") }
    }

    fun checkPermission() {
        runCatching {
            _isAvailable.value = true
            _isPermissionGranted.value = !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (_isPermissionGranted.value) bindUserService()
        }.onFailure {
            _isAvailable.value = false
            _isPermissionGranted.value = false
        }
    }

    fun requestPermission() {
        if (!_isAvailable.value) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "无法请求 Shizuku 授权：${it.message}") }
    }

    private fun bindUserService() {
        if (commandService != null || !_isPermissionGranted.value) return
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { Log.w(TAG, "UserService 连接失败：${it.message}") }
    }

    suspend fun executeCommand(command: String): ShizukuResult = withContext(Dispatchers.IO) {
        if (!_isAvailable.value) return@withContext ShizukuResult(false, "", "Shizuku 未启动")
        if (!_isPermissionGranted.value) return@withContext ShizukuResult(false, "", "未授予 Shizuku 权限")
        bindUserService()
        val service = waitForService()
            ?: return@withContext ShizukuResult(false, "", "Shizuku 服务连接超时")
        try {
            parseResult(service.exec(command))
        } catch (e: Exception) {
            ShizukuResult(false, "", "Shizuku 执行失败：${e.message}")
        }
    }

    private suspend fun waitForService(): ICommandService? {
        repeat(20) {
            commandService?.let { return it }
            kotlinx.coroutines.delay(100)
        }
        return null
    }

    private fun parseResult(raw: String): ShizukuResult {
        val parts = raw.split("|", limit = 3)
        val code = parts.getOrNull(0)?.toIntOrNull() ?: -1
        fun decode(value: String?) = runCatching {
            String(Base64.decode(value.orEmpty(), Base64.DEFAULT))
        }.getOrDefault("")
        return ShizukuResult(code == 0, decode(parts.getOrNull(1)), decode(parts.getOrNull(2)).ifBlank { null })
    }

    fun cleanup() {
        runCatching {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
        commandService = null
    }

    companion object {
        private const val TAG = "ShizukuManager"
        private const val REQUEST_CODE = 1001
    }
}

data class ShizukuResult(val success: Boolean, val output: String, val error: String?)
