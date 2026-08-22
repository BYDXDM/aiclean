package com.example.aiclean.core.shizuku

import android.util.Base64

/**
 * 该 Binder 运行在 Shizuku 启动的 shell/root 进程，而不是普通 App 进程。
 * 仅接收 Manager 构造的固定清理命令，调用端不向 UI 暴露任意 shell。
 */
class CommandUserService : ICommandService.Stub() {
    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            listOf(
                exit.toString(),
                Base64.encodeToString(stdout.toByteArray(), Base64.NO_WRAP),
                Base64.encodeToString(stderr.toByteArray(), Base64.NO_WRAP)
            ).joinToString("|")
        } catch (e: Exception) {
            "-1||" + Base64.encodeToString((e.message ?: "执行失败").toByteArray(), Base64.NO_WRAP)
        }
    }
}
