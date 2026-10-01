package com.bluearchive.toolbox.core.install

import com.bluearchive.toolbox.core.log.LogCollector
import com.bluearchive.toolbox.core.shizuku.ShellExecutor
import java.io.File

/**
 * Shizuku 静默安装：用 shell 身份执行 pm install-multiple，无需用户确认。
 * apk 文件需放在 shell 可读的位置（如 app 外部私有目录）。
 */
class ShizukuInstaller(
    private val shell: ShellExecutor = ShellExecutor,
) : ApkInstaller {

    override val name = "Shizuku 静默安装"

    override suspend fun install(apkFiles: List<File>): InstallResult {
        if (apkFiles.isEmpty()) return InstallResult(false, "没有可安装的 APK 文件")
        // argv 数组直传：单个 APK 用 pm install，多个分包用 pm install-multiple
        // -r 覆盖已装包；-t 允许 test apk。路径作为独立参数，无注入风险。
        val cmd = buildList {
            if (apkFiles.size == 1) {
                addAll(listOf("pm", "install", "-r", "-t"))
            } else {
                addAll(listOf("pm", "install-multiple", "-r", "-t"))
            }
            addAll(apkFiles.map { it.absolutePath })
        }
        LogCollector.i("Install", "执行安装命令: ${cmd.joinToString(" ")}")
        val r = shell.exec(cmd, timeoutMs = 120_000L)
        LogCollector.i("Install", "安装命令退出码=${r.exitCode}, stdout=${r.stdout.take(300)}, stderr=${r.stderr.take(300)}")
        val output = (r.stdout + r.stderr).trim()
        val success = r.exitCode == 0 && output.contains("Success")
        return InstallResult(
            success = success,
            message = when {
                success -> "安装成功"
                output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") ->
                    "安装失败：已安装版本更高，请先卸载原版再试"
                output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") ->
                    "安装失败：存储空间不足"
                output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ->
                    "安装失败：签名不兼容，请先卸载已安装的同包名应用"
                else -> "安装失败：${output.ifBlank { "exit=${r.exitCode}" }}"
            },
        )
    }
}
