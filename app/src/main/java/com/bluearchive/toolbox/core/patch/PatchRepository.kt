package com.bluearchive.toolbox.core.patch

import com.bluearchive.toolbox.core.log.LogCollector
import com.bluearchive.toolbox.data.release.CafeResource
import com.bluearchive.toolbox.data.release.ReleaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 流程 B：文件替换汉化。
 * 强制使用最新资源 → 下载 → sha256 校验 → 解压(防 ZipSlip) → 备份 → 覆盖。
 * 任一步失败自动停止并尝试回滚，不会留下半成品。
 *
 * 文件操作通过 GameFileSystem 按安卓版本/权限自动选通道（Shizuku / File API / SAF）。
 */
class PatchRepository(
    private val releaseRepo: ReleaseRepository,
    private val gameFs: GameFileSystem,
) {

    sealed interface Step {
        data object CheckLatest : Step
        data object Download : Step
        data object Verify : Step
        data object Extract : Step
        data object Backup : Step
        data object Deploy : Step
        data object Done : Step
    }

    data class Progress(
        val step: Step,
        val percent: Float = 0f,
        val message: String = "",
    )

    data class PatchResult(
        val success: Boolean,
        val message: String,
        val resourceTag: String? = null,
    )

    suspend fun run(onProgress: (Progress) -> Unit): PatchResult = withContext(Dispatchers.IO) {
        // 0. 解析可用通道
        val channel = gameFs.resolveChannel()
            ?: run {
                LogCollector.e("Patch", "无可用文件操作通道")
                return@withContext PatchResult(
                    false,
                    "没有可用的文件操作通道：安卓 13+ 需激活 Shizuku；安卓 11-12 需授权游戏目录；安卓 10 及以下需授予存储权限。",
                )
            }
        LogCollector.i("Patch", "使用通道: ${channel.name}")
        onProgress(Progress(Step.CheckLatest, 0f, "使用通道：${channel.name}"))

        // 1. 强制确认最新资源
        onProgress(Progress(Step.CheckLatest, 0f, "检查最新汉化资源…"))
        val resource = releaseRepo.fetchCafeLatest(forceRefresh = true).fold(
            onSuccess = { it },
            onFailure = {
                LogCollector.e("Patch", "检查更新失败: ${it.message}", it as? Exception)
                return@withContext PatchResult(false, "检查更新失败：${it.message}")
            },
        )
        LogCollector.i("Patch", "最新资源: tag=${resource.tag}, 文件=${resource.assetName}, 大小=${resource.sizeBytes}")

        // 2. 下载
        onProgress(Progress(Step.Download, 0f, "下载 ${resource.assetName}（${formatMB(resource.sizeBytes)}）…"))
        val zipFile = gameFs.stagingDir.parentFile!!.resolve(resource.assetName).canonicalFile.also {
            if (!it.path.startsWith(gameFs.stagingDir.parentFile!!.canonicalPath)) throw SecurityException("非法文件名: ${resource.assetName}")
        }
        val downloadOk = releaseRepo.downloadWithMirrors(resource.downloadUrl, zipFile) { downloaded, total ->
            val p = if (total > 0) (downloaded * 100f / total).coerceIn(0f, 100f) else 0f
            onProgress(Progress(Step.Download, p, "下载中… ${p.toInt()}%"))
        }.isSuccess
        if (!downloadOk) {
            LogCollector.e("Patch", "下载失败")
            return@withContext PatchResult(false, "下载失败，请检查网络或切换镜像后重试")
        }

        // 3. sha256 校验（Release 提供时）
        if (!resource.sha256.isNullOrBlank()) {
            onProgress(Progress(Step.Verify, 0f, "校验文件完整性…"))
            val actual = sha256(zipFile)
            LogCollector.d("Patch", "sha256 校验: 期望=${resource.sha256.take(12)}, 实际=${actual.take(12)}")
            if (actual.lowercase() != resource.sha256.lowercase()) {
                zipFile.delete()
                return@withContext PatchResult(
                    false,
                    "校验失败：文件可能损坏（期望 ${resource.sha256.take(12)}…，实际 ${actual.take(12)}…），请重试",
                )
            }
        }

        // 4. 解压到 staging/TableBundles
        onProgress(Progress(Step.Extract, 0f, "解压资源…"))
        gameFs.stagingDir.let { it.deleteRecursively(); it.mkdirs() }
        val extractDir = File(gameFs.stagingDir, "TableBundles").apply { mkdirs() }
        val extractOk = extractZipSafe(zipFile, extractDir) { percent ->
            onProgress(Progress(Step.Extract, percent.toFloat(), "解压中… $percent%"))
        }
        zipFile.delete()
        if (!extractOk) {
            LogCollector.e("Patch", "解压失败")
            return@withContext PatchResult(false, "解压失败（压缩包可能损坏）")
        }

        // 5. 停止游戏 + 备份
        onProgress(Progress(Step.Backup, 0f, "停止游戏并备份原版文本…"))
        val stop = channel.forceStopGame()
        LogCollector.i("Patch", "停止游戏: ${stop.success}, msg=${stop.message}")
        if (stop.message.isNotBlank()) {
            // 非 Shizuku 通道会提示手动关闭，把消息透传给用户
            onProgress(Progress(Step.Backup, 50f, stop.message))
        }
        val backup = channel.backupTableBundles()
        LogCollector.i("Patch", "备份结果: ${backup.success}, msg=${backup.message}")
        if (!backup.success) {
            return@withContext PatchResult(false, "备份失败：${backup.message}")
        }

        // 6. 部署新文本
        onProgress(Progress(Step.Deploy, 0f, "写入汉化文本…"))
        val deploy = channel.deployFromStaging(extractDir)
        LogCollector.i("Patch", "部署结果: ${deploy.success}, msg=${deploy.message}")
        if (!deploy.success) {
            // 部署失败：尝试用备份还原，避免游戏目录为空
            channel.restoreFromBackup()
            return@withContext PatchResult(false, "写入失败，已还原原版：${deploy.message}")
        }

        gameFs.stagingDir.let { it.deleteRecursively(); it.mkdirs() }
        onProgress(Progress(Step.Done, 100f, "完成"))
        LogCollector.i("Patch", "汉化成功")
        PatchResult(true, "汉化成功（通道：${channel.name}），请启动游戏体验。文本汉化，字体可能显示异常。", resource.tag)
    }

    /** 还原为原版日文文本 */
    suspend fun restore(): PatchResult = withContext(Dispatchers.IO) {
        val channel = gameFs.resolveChannel()
            ?: return@withContext PatchResult(false, "没有可用的文件操作通道，请先完成环境准备。")
        val stop = channel.forceStopGame()
        val r = channel.restoreFromBackup()
        if (r.success) PatchResult(true, r.message.ifBlank { "已还原为原版文本" })
        else PatchResult(false, r.message.ifBlank { "还原失败" })
    }

    private fun extractZipSafe(zip: File, dest: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            ZipInputStream(FileInputStream(zip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val target = File(dest, name).canonicalFile
                    if (!target.path.startsWith(dest.canonicalPath + File.separator) &&
                        target.path != dest.canonicalPath
                    ) {
                        throw SecurityException("非法路径: $name")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            onProgress(100)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatMB(bytes: Long): String =
        if (bytes <= 0) "—" else String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
