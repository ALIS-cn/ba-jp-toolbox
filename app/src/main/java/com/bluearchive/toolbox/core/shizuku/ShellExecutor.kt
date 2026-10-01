package com.bluearchive.toolbox.core.shizuku

import com.bluearchive.toolbox.core.log.LogCollector
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import moe.shizuku.server.IShizukuService
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 统一的 Shell 执行入口，基于 Shizuku（shell uid 2000）。
 *
 * 直接通过 IShizukuService.Stub.asInterface(Shizuku.getBinder()) 获取服务，
 * 再调用 newProcess 创建远程进程。
 *
 * 安全设计：一律以 argv 数组形式直传（execve 风格），绝不经过 `sh -c` 拼接字符串，
 * 因此路径中即使含有空格、引号、;、|、$ 等字符也不会产生命令注入。
 */
object ShellExecutor {

    data class Result(val exitCode: Int, val stdout: String, val stderr: String)

    private fun serviceOrNull(): IShizukuService? {
        val binder = runCatching { Shizuku.getBinder() }.getOrNull() ?: return null
        return IShizukuService.Stub.asInterface(binder)
    }

    /** 执行命令（argv 直传，不经过 shell 解释） */
    suspend fun exec(vararg args: String, timeoutMs: Long = 30_000L): Result =
        exec(args.toList(), timeoutMs)

    /** 直接执行带参数的命令列表 */
    suspend fun exec(args: List<String>, timeoutMs: Long = 30_000L): Result = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            LogCollector.e("Shell", "Shizuku 服务未运行")
            return@withContext Result(-1, "", "Shizuku 服务未运行")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            LogCollector.e("Shell", "Shizuku 未授权")
            return@withContext Result(-1, "", "Shizuku 未授权")
        }
        val service = serviceOrNull()
            ?: run {
                LogCollector.e("Shell", "无法获取 Shizuku 服务")
                return@withContext Result(-1, "", "无法获取 Shizuku 服务")
            }
        try {
            LogCollector.d("Shell", "执行: ${args.joinToString(" ")}")
            val remoteProcess = service.newProcess(args.toTypedArray(), null, null)
                ?: run {
                    LogCollector.e("Shell", "创建进程失败")
                    return@withContext Result(-1, "", "创建进程失败")
                }
            val stdout = BufferedReader(
                InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.inputStream))
            ).use { it.readText() }
            val stderr = BufferedReader(
                InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(remoteProcess.errorStream))
            ).use { it.readText() }
            val exit = withTimeoutOrNull(timeoutMs) { remoteProcess.waitFor() }
                ?: run { remoteProcess.destroy(); -1 }
            LogCollector.d("Shell", "退出码=$exit, stdout=${stdout.take(200)}, stderr=${stderr.take(200)}")
            Result(exit, stdout, stderr)
        } catch (t: Throwable) {
            LogCollector.e("Shell", "执行异常: ${t.message ?: t::class.java.simpleName}", t as? Exception)
            Result(-1, "", "执行异常: ${t.message ?: t::class.java.simpleName}")
        }
    }

    /** 目录是否存在（shell 身份执行 test -d，能看到 Android/data） */
    suspend fun dirExists(path: String): Boolean =
        exec(listOf("test", "-d", path)).exitCode == 0

    /**
     * 批量顺序执行 argv 命令，任一退出码非 0 即停止并返回该结果。
     * 每条命令都是独立的 argv 数组，不经过 shell。
     */
    suspend fun execChain(vararg commands: List<String>): Result {
        var last = Result(0, "", "")
        for (cmd in commands) {
            last = exec(cmd)
            if (last.exitCode != 0) return last
        }
        return last
    }
}
