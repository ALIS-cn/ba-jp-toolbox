package com.bluearchive.toolbox.core.patch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.bluearchive.toolbox.core.env.EnvState
import com.bluearchive.toolbox.core.patch.channel.FileApiChannel
import com.bluearchive.toolbox.core.patch.channel.FileSystemChannel
import com.bluearchive.toolbox.core.patch.channel.SafChannel
import com.bluearchive.toolbox.core.patch.channel.ShizukuChannel
import com.bluearchive.toolbox.data.prefs.AppPreferences
import java.io.File

/**
 * 文件系统路由器：按安卓版本与权限状态，自动选择最佳文件操作通道。
 *
 * 优先级：
 *  1. Shizuku 已授权 → ShimChannel（全功能，可 force-stop）
 *  2. 安卓 ≤ 10 且有存储权限 → FileApiChannel
 *  3. 安卓 11~12 且有 SAF 持久化 URI → SafChannel
 *  4. 否则不可用，UI 引导用户完成对应授权
 */
class GameFileSystem(
    private val context: Context,
    private val prefs: AppPreferences,
    private val envProvider: () -> EnvState?,
) {

    /** 中转目录：解压后的 TableBundles 放在这里，shell/SAF 均可读取 */
    val stagingDir: File = File(context.getExternalFilesDir(null), "patch_staging").apply { mkdirs() }

    /**
     * 通道候选项，供 UI 展示「当前可用通道 / 需要什么权限」。
     */
    data class ChannelCandidate(
        val name: String,
        val available: Boolean,
        val reason: String,
    )

    fun candidates(): List<ChannelCandidate> {
        val env = envProvider()
        val sdk = Build.VERSION.SDK_INT
        val shizukuOk = env?.shizukuAuthorized == true
        val storageOk = hasStoragePermission()
        val safUri = try {
            kotlinx.coroutines.runBlocking { prefs.safTreeUri() }
        } catch (_: Exception) {
            null
        }

        return buildList {
            add(
                ChannelCandidate(
                    "Shizuku",
                    shizukuOk,
                    if (shizukuOk) "已授权" else "需激活并授权 Shizuku",
                ),
            )
            if (sdk <= Build.VERSION_CODES.Q) {
                add(
                    ChannelCandidate(
                        "存储权限",
                        storageOk,
                        if (storageOk) "已授予" else "需授予存储权限",
                    ),
                )
            }
            if (sdk in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2) {
                add(
                    ChannelCandidate(
                        "SAF 目录授权",
                        safUri != null,
                        if (safUri != null) "已授权" else "需选择游戏 files 目录",
                    ),
                )
            }
        }
    }

    /** 解析当前最佳可用通道；均不可用时返回 null */
    suspend fun resolveChannel(): FileSystemChannel? {
        val env = envProvider()
        val sdk = Build.VERSION.SDK_INT

        // 1. Shizuku（所有版本首选，功能最全）
        if (env?.shizukuAuthorized == true) {
            return ShizukuChannel(stagingDir = stagingDir)
        }

        // 2. 安卓 ≤ 10：存储权限
        if (sdk <= Build.VERSION_CODES.Q && hasStoragePermission()) {
            return FileApiChannel(stagingDir = stagingDir)
        }

        // 3. 安卓 11~12：SAF
        if (sdk in Build.VERSION_CODES.R..Build.VERSION_CODES.S_V2) {
            val uri = prefs.safTreeUri()
            if (uri != null) {
                return SafChannel(context, Uri.parse(uri), stagingDir)
            }
        }

        return null
    }

    private fun hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
}
