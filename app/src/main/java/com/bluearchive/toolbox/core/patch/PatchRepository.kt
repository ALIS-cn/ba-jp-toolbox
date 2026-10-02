package com.bluearchive.toolbox.core.patch

import com.bluearchive.toolbox.core.log.LogCollector
import com.bluearchive.toolbox.data.release.CafeResource
import com.bluearchive.toolbox.data.release.ReleaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 流程 B：文件替换汉化。
 * 强制使用最新资源 → 下载 → sha256 校验 → 解压(防 ZipSlip) → 版本匹配预检 →
 * 停止游戏 → 备份 → 覆盖 → 部署后逐文件校验。
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

        // 3. sha256 校验（必须提供且匹配，缺失则拒绝）
        if (resource.sha256.isNullOrBlank()) {
            zipFile.delete()
            LogCollector.e("Patch", "Release 未提供 sha256，拒绝安装")
            return@withContext PatchResult(false, "无法验证资源完整性（缺少校验摘要），请稍后重试")
        }
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

        // 4. 解压到 staging/TableBundles
        onProgress(Progress(Step.Extract, 0f, "解压资源…"))
        gameFs.stagingDir.let { it.deleteRecursively(); it.mkdirs() }
        val extractDir = File(gameFs.stagingDir, "TableBundles").apply { mkdirs() }
        val extractOk = try {
            extractZipSafe(zipFile, extractDir) { percent ->
                onProgress(Progress(Step.Extract, percent.toFloat(), "解压中… $percent%"))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            zipFile.delete()
            throw e
        } catch (e: Exception) {
            LogCollector.e("Patch", "解压异常: ${e.message}", e)
            false
        }
        zipFile.delete()
        if (!extractOk) {
            LogCollector.e("Patch", "解压失败")
            return@withContext PatchResult(false, "解压失败（压缩包可能损坏或空间不足），请清理存储空间后重试")
        }

        // 4.5 版本匹配预检（只读，尚未改动游戏目录，失败零风险）
        // Catalog/TableCatalog.bytes 的内容只由游戏资源版本决定，不含翻译文本；
        // 它与游戏自带 catalog 不一致时，游戏启动后会判定资源过期并全量重下日文原版。
        val newCatalog = File(extractDir, "Catalog/TableCatalog.bytes")
        if (newCatalog.isFile) {
            onProgress(Progress(Step.Verify, 0f, "校验游戏版本是否匹配…"))
            val newCatalogMd5 = md5(newCatalog)
            val oldCatalogMd5 = channel.fileMd5("TableBundles/Catalog/TableCatalog.bytes")
            LogCollector.i("Patch", "catalog 版本比对: 游戏内=${oldCatalogMd5?.take(12)}, 汉化包=${newCatalogMd5.take(12)}")
            when {
                oldCatalogMd5 == null -> {
                    gameFs.stagingDir.let { it.deleteRecursively(); it.mkdirs() }
                    return@withContext PatchResult(
                        false,
                        "没有检测到游戏已下载的资源目录，请先启动日服游戏、等它把游戏资源下载完成后再使用汉化。",
                    )
                }
                !oldCatalogMd5.equals(newCatalogMd5, ignoreCase = true) -> {
                    gameFs.stagingDir.let { it.deleteRecursively(); it.mkdirs() }
                    return@withContext PatchResult(
                        false,
                        "汉化资源版本（${resource.tag.substringBefore("_")}）与你当前游戏版本不一致。\n" +
                            "强行替换会导致游戏重新下载日文资源。\n" +
                            "请先在应用商店把游戏更新到最新版，再重新汉化；若游戏已是最新，则说明汉化包尚未跟进，请稍后再试。",
                    )
                }
            }
        } else {
            LogCollector.w("Patch", "汉化包中未找到 Catalog/TableCatalog.bytes，跳过版本预检")
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
        onProgress(Progress(Step.Deploy, 0f, "写入汉化文本（文件较大，可能需要几分钟）…"))
        val deploy = channel.deployFromStaging(extractDir)
        LogCollector.i("Patch", "部署结果: ${deploy.success}, msg=${deploy.message}")
        if (!deploy.success) {
            // 部署失败：尝试用备份还原，避免游戏目录为空
            val rollback = channel.restoreFromBackup()
            val msg = if (rollback.success) "写入失败，已自动还原原版：${deploy.message}"
            else "写入失败且还原失败（${rollback.message}），请手动点「还原原版」后重试"
            return@withContext PatchResult(false, msg)
        }

        // 7. 部署后逐文件校验：防止命令返回成功但文件实际缺失（如跨用户复制静默失败）
        val expectedFiles = extractDir.walkTopDown().filter { it.isFile }.toList()
        val missing = mutableListOf<String>()
        var mismatched = 0L
        for (f in expectedFiles) {
            val rel = "TableBundles/" + f.relativeTo(extractDir).invariantSeparatorsPath
            val actualSize = channel.fileSize(rel)
            if (actualSize < 0) {
                missing.add(rel)
                LogCollector.e("Patch", "部署校验发现缺失文件: $rel")
            } else if (actualSize != f.length()) {
                mismatched++
                LogCollector.e("Patch", "部署校验文件大小不符: $rel 期望=${f.length()} 实际=$actualSize")
            }
        }
        if (missing.isNotEmpty() || mismatched > 0) {
            LogCollector.e("Patch", "部署校验失败: 缺失=${missing.size}, 大小不符=$mismatched, 期望文件数=${expectedFiles.size}")
            val rollback = channel.restoreFromBackup()
            val msg = if (rollback.success)
                "写入校验失败（缺失 ${missing.size} 个、损坏 $mismatched 个文件），已自动还原原版。请重试或更换文件通道。"
            else
                "写入校验失败且还原失败（缺失 ${missing.size} 个、损坏 $mismatched 个文件），请手动点「还原原版」。"
            return@withContext PatchResult(false, msg)
        }
        LogCollector.i("Patch", "部署校验通过: ${expectedFiles.size} 个文件")

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

    /**
     * 安全解压：防 ZipSlip、文件数量/大小上限、可取消、拒绝空包。
     * 咖啡厅当前资源含 200MB+ 单文件，故单文件上限取 512MB。
     */
    private suspend fun extractZipSafe(zip: File, dest: File, onProgress: (Int) -> Unit): Boolean {
        val maxFiles = 2000
        val maxTotalSize = 2L * 1024 * 1024 * 1024 // 2GB
        val maxSingleFile = 512L * 1024 * 1024 // 512MB
        var fileCount = 0
        var totalSize = 0L
        return try {
            ZipInputStream(FileInputStream(zip)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
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
                        fileCount++
                        if (fileCount > maxFiles) throw java.io.IOException("文件数量超过限制 ($maxFiles)")
                        // 部分 zip 把大小放在数据尾，entry.size 可能为 -1，此时只用实时计数兜底
                        if (entry.size in 1..maxTotalSize) {
                            totalSize += entry.size
                        }
                        if (totalSize > maxTotalSize) throw java.io.IOException("解压总大小超过限制（2GB）")
                        if (entry.size > maxSingleFile) {
                            throw java.io.IOException("单个文件过大：$name（${entry.size / 1024 / 1024}MB）")
                        }
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var fileSize = 0L
                            while (zis.read(buf).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                out.write(buf, 0, read)
                                fileSize += read
                                if (fileSize > maxSingleFile) throw java.io.IOException("单个文件过大：$name")
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (fileCount == 0) throw java.io.IOException("压缩包中没有文件")
            onProgress(100)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCollector.e("Patch", "解压失败: ${e.message}", e)
            false
        }
    }

    private fun sha256(file: File): String = digest(file, "SHA-256")

    private fun md5(file: File): String = digest(file, "MD5")

    private fun digest(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
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
