package com.bluearchive.toolbox.core.patch.channel

import com.bluearchive.toolbox.core.env.EnvironmentDetector
import java.io.File

/**
 * File API 通道：适用于安卓 ≤ 10，应用持有 WRITE_EXTERNAL_STORAGE 后可直接读写
 * /sdcard/Android/data/... 目录。
 *
 * 限制：无法 force-stop 游戏（需用户手动关闭），无法 chmod（通常不需要）。
 */
class FileApiChannel(
    private val packageName: String = EnvironmentDetector.GAME_OFFICIAL,
    private val stagingDir: File,
) : FileSystemChannel {

    override val name = "存储权限"

    private val gameFiles = File("/sdcard/Android/data/$packageName/files")
    private val tableBundlesDir = File(gameFiles, "TableBundles")
    private val backupDir = File(gameFiles, "TableBundles.bak")

    override suspend fun forceStopGame(): OpResult =
        OpResult.ok("无法自动停止游戏，请手动从最近任务中关闭蔚蓝档案后继续。")

    override suspend fun backupTableBundles(): OpResult {
        if (!tableBundlesDir.exists()) return OpResult.fail("未找到 TableBundles 目录，游戏可能未完整下载资源")
        // 已有备份：不覆盖，保护原版（第一次汉化时创建的才是原版备份）
        if (backupDir.exists()) {
            // 只确保目标目录为空，准备接收新文件
            tableBundlesDir.deleteRecursively()
            tableBundlesDir.mkdirs()
            return OpResult.ok("已有原版备份，跳过重复备份")
        }
        val ok = tableBundlesDir.renameTo(backupDir)
        if (!ok) return OpResult.fail("备份失败，可能无存储写入权限")
        tableBundlesDir.mkdirs()
        return OpResult.ok()
    }

    override suspend fun deployFromStaging(stagingTableBundles: File): OpResult {
        try {
            if (!tableBundlesDir.exists()) tableBundlesDir.mkdirs()
            copyRecursively(stagingTableBundles, tableBundlesDir)
            return OpResult.ok()
        } catch (e: Exception) {
            return OpResult.fail("写入失败：${e.message}")
        }
    }

    override suspend fun restoreFromBackup(): OpResult {
        if (!backupDir.exists()) return OpResult.fail("未找到备份目录 TableBundles.bak")
        if (tableBundlesDir.exists()) tableBundlesDir.deleteRecursively()
        val ok = backupDir.renameTo(tableBundlesDir)
        return if (ok) OpResult.ok("已还原为原版文本")
        else OpResult.fail("还原失败")
    }

    override suspend fun copyDir(srcPkg: String, dstPkg: String, subDir: String): OpResult {
        val src = File("/sdcard/Android/data/$srcPkg/files/$subDir")
        val dstParent = File("/sdcard/Android/data/$dstPkg/files")
        if (!src.exists()) return OpResult.fail("源目录不存在（$subDir），跳过")
        return try {
            dstParent.mkdirs()
            val dst = File(dstParent, subDir)
            if (dst.exists()) dst.deleteRecursively()
            copyRecursively(src, dst)
            OpResult.ok()
        } catch (e: Exception) {
            OpResult.fail("复制失败：${e.message}")
        }
    }

    override suspend fun hasBackup(): Boolean = backupDir.exists()

    override fun cleanupStaging() {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursively(child, File(dst, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
