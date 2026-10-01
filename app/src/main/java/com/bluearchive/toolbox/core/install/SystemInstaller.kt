package com.bluearchive.toolbox.core.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 系统安装器：
 * - 单个 APK：直接用 Intent.ACTION_VIEW 拉起系统安装器（最稳定，必弹窗）
 * - 多个分包 APK：用 PackageInstaller 会话 API 安装
 */
class SystemInstaller(
    private val context: Context,
) : ApkInstaller {

    override val name = "系统安装器（需手动确认）"

    override suspend fun install(apkFiles: List<File>): InstallResult {
        if (apkFiles.isEmpty()) return InstallResult(false, "没有可安装的 APK 文件")
        return try {
            if (apkFiles.size == 1) {
                installSingleApk(apkFiles[0])
            } else {
                installSplitApks(apkFiles)
            }
        } catch (e: Exception) {
            InstallResult(false, "安装失败：${e.message}")
        }
    }

    /** 单个 APK：用 ACTION_VIEW 拉起系统安装器，兼容性最好 */
    private fun installSingleApk(apk: File): InstallResult {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return InstallResult(
            success = true,
            message = "已发起安装，请在系统弹窗中确认",
            pendingUserConfirmation = true,
        )
    }

    /** 多个分包 APK：用 PackageInstaller 会话 API */
    private suspend fun installSplitApks(apkFiles: List<File>): InstallResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            apkFiles.forEach { apk ->
                session.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                }
            }
        } catch (e: Exception) {
            session.abandon()
            return InstallResult(false, "写入安装包失败：${e.message}")
        }

        val intent = Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        // commit 必须在主线程执行，否则部分 ROM 不弹窗
        withContext(Dispatchers.Main) {
            session.commit(pi.intentSender)
        }
        return InstallResult(
            success = true,
            message = "已发起安装，请在系统弹窗中确认",
            pendingUserConfirmation = true,
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.bluearchive.toolbox.INSTALL_RESULT"
    }
}
