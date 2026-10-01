package com.bluearchive.toolbox.core.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bluearchive.toolbox.core.env.EnvState
import com.bluearchive.toolbox.core.log.LogCollector
import com.bluearchive.toolbox.core.patch.GameFileSystem
import com.bluearchive.toolbox.core.patch.channel.OpResult
import com.bluearchive.toolbox.core.shizuku.ShellExecutor
import com.bluearchive.toolbox.data.release.ReleaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 流程 A：一键安装汉化客户端。
 *
 * 步骤：下载 APKS → 解压 → (可选)卸载原版 → 安装分包 → (可选)迁移游戏资源
 *
 * 安装器选择：
 *  - Shizuku 已授权 → ShizukuInstaller（pm install-multiple，静默）
 *  - 否则 → SystemInstaller（PackageInstaller，弹系统确认框）
 *  - SAI 作为用户可选兜底
 */
class ClientInstallRepository(
    private val context: Context,
    private val releaseRepo: ReleaseRepository,
    private val gameFs: GameFileSystem,
    private val envProvider: () -> EnvState?,
) {

    companion object {
        const val PKG_OFFICIAL = "com.YostarJP.BlueArchive"
        const val PKG_CAFE = "cafe.YostarJP.BlueArchive"
        const val APKS_URL = "https://download.bluearchive.cafe/android/latest"
        // 需迁移的资源子目录
        private val RESOURCE_DIRS = listOf("TableBundles", "MediaPatch", "AssetBundles")
    }

    sealed interface Step {
        data object Check : Step
        data object Download : Step
        data object Extract : Step
        data object Uninstall : Step
        data object Install : Step
        data object Migrate : Step
        data object Done : Step
    }

    data class Progress(val step: Step, val percent: Float = 0f, val message: String = "")

    data class Result(
        val success: Boolean,
        val message: String,
        /** true 表示安装已发起但需用户在系统弹窗确认 */
        val pendingUserConfirmation: Boolean = false,
    )

    data class Options(
        val uninstallOfficial: Boolean = true,
        val migrateResources: Boolean = true,
    )

    suspend fun run(options: Options, onProgress: (Progress) -> Unit): Result = withContext(Dispatchers.IO) {
        // 1. 下载 APKS
        onProgress(Progress(Step.Download, 0f, "下载汉化客户端 APKS…"))
        val apksFile = File(gameFs.stagingDir.parentFile, "ba-cafe-latest.apks")
        val downloadOk = releaseRepo.downloadWithMirrors(APKS_URL, apksFile) { d, t ->
            val p = if (t > 0) (d * 100f / t).coerceIn(0f, 100f) else 0f
            onProgress(Progress(Step.Download, p, "下载中… ${p.toInt()}%"))
        }.isSuccess
        if (!downloadOk) return@withContext Result(false, "下载失败，请检查网络或切换镜像")
        installFromApksFile(apksFile, options, onProgress)
    }

    /** 从本地 APKS 文件安装（用户手动选择的文件） */
    suspend fun installFromLocalUri(
        uri: android.net.Uri,
        options: Options,
        onProgress: (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        onProgress(Progress(Step.Download, 0f, "读取本地安装包…"))
        val apksFile = File(gameFs.stagingDir.parentFile, "ba-cafe-local.apks")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                apksFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result(false, "无法读取选择的文件")
        } catch (e: Exception) {
            return@withContext Result(false, "读取文件失败：${e.message}")
        }
        if (apksFile.length() < 1024) {
            apksFile.delete()
            return@withContext Result(false, "文件过小，可能不是有效的 APKS 安装包")
        }
        installFromApksFile(apksFile, options, onProgress)
    }

    /** 解压 + 卸载 + 安装 + 迁移 的核心流程 */
    private suspend fun installFromApksFile(
        apksFile: File,
        options: Options,
        onProgress: (Progress) -> Unit,
    ): Result {
        val shizukuOk = envProvider()?.shizukuAuthorized == true
        LogCollector.i("Install", "开始安装流程，文件=${apksFile.name}(${apksFile.length()} bytes)，Shizuku=$shizukuOk，卸载原版=${options.uninstallOfficial}，迁移资源=${options.migrateResources}")

        // 2. 解压 APKS（实为 zip，内含 base.apk + split_config.*.apk）
        onProgress(Progress(Step.Extract, 0f, "解压安装包…"))
        val extractDir = File(gameFs.stagingDir.parentFile, "apks_extract").apply {
            deleteRecursively(); mkdirs()
        }
        val apkFiles = extractApks(apksFile, extractDir) { p ->
            onProgress(Progress(Step.Extract, p.toFloat(), "解压中… $p%"))
        }
        apksFile.delete()
        LogCollector.i("Install", "解压完成，APK 数量=${apkFiles.size}")
        if (apkFiles.isEmpty()) {
            LogCollector.e("Install", "解压失败或未找到 APK 文件")
            return Result(false, "解压失败或未找到 APK 文件（文件可能已损坏）")
        }

        // 2.5 供应链校验：base.apk 包名必须是咖啡厅汉化客户端，防止镜像返回被替换的 APK
        val baseApk = apkFiles.firstOrNull { it.name == "base.apk" } ?: apkFiles.first()
        val verify = ApkVerifier.verify(context, baseApk, ApkVerifier.CAFE_PACKAGE)
        if (!verify.ok) {
            LogCollector.e("Install", "汉化客户端校验失败: ${verify.message}")
            extractDir.deleteRecursively()
            return Result(false, verify.message)
        }

        // 3. 卸载原版（若存在且用户选择卸载）
        if (options.uninstallOfficial) {
            val installed = isPackageInstalled(PKG_OFFICIAL)
            LogCollector.i("Install", "官方客户端已安装=$installed")
            if (installed) {
                onProgress(Progress(Step.Uninstall, 0f, "卸载官方原版客户端…"))
                val uninstallOk = if (shizukuOk) {
                    uninstallViaShizuku(PKG_OFFICIAL)
                } else {
                    // 无 Shizuku：跳转系统卸载页，由用户手动确认
                    requestUninstall(PKG_OFFICIAL)
                    // 无法自动确认，提示用户
                    onProgress(Progress(Step.Uninstall, 50f, "请在系统弹窗中确认卸载，完成后继续"))
                    true // 假定用户会卸载，继续安装（若未卸载安装会失败并给出提示）
                }
                LogCollector.i("Install", "卸载结果=$uninstallOk")
                if (!uninstallOk) return Result(false, "卸载失败，请手动卸载官方版后重试")
            }
        }

        // 4. 安装汉化客户端
        onProgress(Progress(Step.Install, 0f, "安装汉化客户端…"))
        val installer: ApkInstaller = if (shizukuOk) ShizukuInstaller() else SystemInstaller(context)
        LogCollector.i("Install", "使用安装器: ${installer.javaClass.simpleName}，APK 列表=${apkFiles.map { it.name }}")
        val installResult = installer.install(apkFiles)
        LogCollector.i("Install", "安装结果: success=${installResult.success}, pending=${installResult.pendingUserConfirmation}, msg=${installResult.message}")
        if (installResult.pendingUserConfirmation) {
            // 系统安装器已发起，等用户确认
            return Result(
                success = true,
                message = "已发起安装，请在系统弹窗中确认。安装完成后可选择迁移游戏资源。",
                pendingUserConfirmation = true,
            )
        }
        if (!installResult.success) {
            LogCollector.e("Install", "安装失败: ${installResult.message}")
            return Result(false, installResult.message)
        }

        // 5. 迁移游戏资源
        if (options.migrateResources) {
            val channel = gameFs.resolveChannel()
            LogCollector.i("Install", "资源迁移通道=${channel?.javaClass?.simpleName}")
            if (channel != null) {
                onProgress(Progress(Step.Migrate, 0f, "迁移游戏资源（避免重下数 GB）…"))
                val total = RESOURCE_DIRS.size
                RESOURCE_DIRS.forEachIndexed { i, subDir ->
                    onProgress(Progress(Step.Migrate, ((i + 1) * 100f / total), "迁移 $subDir…"))
                    val r = channel.copyDir(PKG_OFFICIAL, PKG_CAFE, subDir)
                    LogCollector.d("Install", "迁移 $subDir: $r")
                    // 源目录不存在等非致命错误不中断
                }
            } else {
                onProgress(Progress(Step.Migrate, 0f, "无可用通道，跳过资源迁移（首次启动游戏会自动下载）"))
            }
        }

        // 清理
        extractDir.deleteRecursively()
        onProgress(Progress(Step.Done, 100f, "完成"))
        LogCollector.i("Install", "安装流程完成")
        return Result(true, "汉化客户端安装成功${if (options.migrateResources) "，资源已迁移" else ""}，请启动游戏。")
    }

    /** 用 SAI 安装 APKS（兜底）：分享原始 apks 文件给 SAI */
    suspend fun installWithSai(): Result = withContext(Dispatchers.IO) {
        try {
            val apksFile = File(gameFs.stagingDir.parentFile, "ba-cafe-latest.apks")
            if (!apksFile.exists()) {
                // 先下载
                val ok = releaseRepo.downloadWithMirrors(APKS_URL, apksFile).isSuccess
                if (!ok) return@withContext Result(false, "下载失败，请检查网络或切换镜像")
            }
            val uri = try {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apksFile,
                )
            } catch (e: IllegalArgumentException) {
                return@withContext Result(false, "文件分享失败：${e.message}")
            }
            // SAI 接受 application/zip 或 application/octet-stream
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/zip")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                // 优先用 SAI；没装则让系统选
                val saiPkg = listOf("com.aefyr.sai", "com.aefyr.sai.foss").firstOrNull { pkg ->
                    try {
                        context.packageManager.getPackageInfo(pkg, 0)
                        true
                    } catch (_: Exception) { false }
                }
                if (saiPkg != null) setPackage(saiPkg)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return@withContext Result(false, "未安装 SAI，请先点击下方「安装内置 SAI」按钮安装")
            }
            context.startActivity(intent)
            Result(true, "已打开 SAI，请在 SAI 中确认安装", pendingUserConfirmation = true)
        } catch (e: Exception) {
            Result(false, "SAI 安装失败：${e.message}")
        }
    }

    /** 从 assets 提取内置的 SAI 安装包并安装 */
    suspend fun installBuiltInSai(): Result = withContext(Dispatchers.IO) {
        val saiFile = File(gameFs.stagingDir.parentFile, "sai-4.5.apk")
        try {
            context.assets.open("sai.apk").use { input ->
                saiFile.outputStream().use { output -> input.copyTo(output) }
            }
            LogCollector.i("Install", "内置 SAI 已提取: ${saiFile.length()} bytes")
        } catch (e: Exception) {
            LogCollector.e("Install", "提取内置 SAI 失败: ${e.message}", e)
            return@withContext Result(false, "提取 SAI 安装包失败：${e.message}")
        }
        // 供应链校验：包名 + 签名证书固定
        val verify = ApkVerifier.verify(
            context, saiFile, ApkVerifier.SAI_PACKAGE, ApkVerifier.SAI_CERT_SHA256,
        )
        if (!verify.ok) {
            saiFile.delete()
            LogCollector.e("Install", "内置 SAI 校验失败: ${verify.message}")
            return@withContext Result(false, verify.message)
        }
        // 用 Shizuku 或系统安装器安装 SAI
        val shizukuOk = envProvider()?.shizukuAuthorized == true
        val installer: ApkInstaller = if (shizukuOk) ShizukuInstaller() else SystemInstaller(context)
        val r = installer.install(listOf(saiFile))
        saiFile.delete()
        LogCollector.i("Install", "内置 SAI 安装结果: ${r.success}, ${r.message}")
        if (r.success) {
            Result(true, "SAI 安装成功")
        } else {
            Result(false, "SAI 安装失败：${r.message}")
        }
    }

    /** 检查 SAI 是否已安装 */
    fun isSaiInstalled(): Boolean {
        return listOf("com.aefyr.sai", "com.aefyr.sai.foss").any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) { false }
        }
    }

    // ---------- 内部 ----------

    private fun extractApks(zip: File, dest: File, onProgress: (Int) -> Unit): List<File> {
        LogCollector.i("Install", "开始解压: ${zip.name}(${zip.length()} bytes)")
        val apks = mutableListOf<File>()
        try {
            ZipInputStream(FileInputStream(zip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val target = File(dest, name).canonicalFile
                    if (!target.path.startsWith(dest.canonicalPath + File.separator)) {
                        throw SecurityException("非法路径: $name")
                    }
                    if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zis.copyTo(out) }
                        apks.add(target)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            onProgress(100)
        } catch (e: Exception) {
            LogCollector.e("Install", "解压异常: ${e.javaClass.simpleName}: ${e.message}", e)
            return emptyList()
        }
        // 咖啡厅客户端现在分发单个 APK（非 APKS 分包），
        // 若 zip 内没有 .apk 条目，把整个文件当作单个 APK 安装
        if (apks.isEmpty()) {
            LogCollector.i("Install", "zip 内无 .apk 条目，当作单个 APK 处理")
            val singleApk = File(dest, "base.apk")
            return try {
                zip.copyTo(singleApk, overwrite = true)
                listOf(singleApk)
            } catch (e: Exception) {
                LogCollector.e("Install", "复制单个 APK 失败: ${e.message}", e)
                emptyList()
            }
        }
        LogCollector.i("Install", "解压出 ${apks.size} 个 APK: ${apks.map { it.name }}")
        return apks
    }

    private fun isPackageInstalled(pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }

    private suspend fun uninstallViaShizuku(pkg: String): Boolean {
        val r = ShellExecutor.exec(listOf("pm", "uninstall", pkg), timeoutMs = 60_000L)
        return r.exitCode == 0 && r.stdout.contains("Success")
    }

    private fun requestUninstall(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
