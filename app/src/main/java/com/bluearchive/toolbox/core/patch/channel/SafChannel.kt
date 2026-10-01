package com.bluearchive.toolbox.core.patch.channel

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.bluearchive.toolbox.core.env.EnvironmentDetector
import java.io.File

/**
 * SAF 通道：适用于安卓 11~12。用户通过系统文件选择器授权
 * Android/data/com.YostarJP.BlueArchive/files 目录后，用 DocumentsContract 操作。
 *
 * 限制：无法 force-stop 游戏（提示手动关闭）；文件操作走 ContentProvider，大量小文件较慢。
 */
class SafChannel(
    private val context: Context,
    private val treeUri: Uri,
    private val stagingDir: File,
) : FileSystemChannel {

    override val name = "SAF 目录授权"

    private val resolver = context.contentResolver

    override suspend fun forceStopGame(): OpResult =
        OpResult.ok("无法自动停止游戏，请手动从最近任务中关闭蔚蓝档案后继续。")

    override suspend fun backupTableBundles(): OpResult {
        val filesDir = rootFilesDir() ?: return OpResult.fail("SAF 目录不可用，请重新授权")
        val tb = filesDir.findFile("TableBundles")
            ?: return OpResult.fail("未找到 TableBundles 目录，游戏可能未完整下载资源")
        // 删除旧备份
        filesDir.findFile("TableBundles.bak")?.delete()
        // 重命名 TableBundles → TableBundles.bak
        val renamed = tb.renameTo("TableBundles.bak")
        if (!renamed) return OpResult.fail("备份失败（重命名被拒绝）")
        // 新建空的 TableBundles 目录
        filesDir.createDirectory("TableBundles")
        return OpResult.ok()
    }

    override suspend fun deployFromStaging(stagingTableBundles: File): OpResult {
        val filesDir = rootFilesDir() ?: return OpResult.fail("SAF 目录不可用")
        val tb = filesDir.findFile("TableBundles") ?: filesDir.createDirectory("TableBundles")
        ?: return OpResult.fail("无法创建 TableBundles 目录")
        return try {
            copyRecursive(stagingTableBundles, tb)
            OpResult.ok()
        } catch (e: Exception) {
            OpResult.fail("写入失败：${e.message}")
        }
    }

    override suspend fun restoreFromBackup(): OpResult {
        val filesDir = rootFilesDir() ?: return OpResult.fail("SAF 目录不可用")
        val backup = filesDir.findFile("TableBundles.bak")
            ?: return OpResult.fail("未找到备份目录 TableBundles.bak")
        filesDir.findFile("TableBundles")?.delete()
        val ok = backup.renameTo("TableBundles")
        return if (ok) OpResult.ok("已还原为原版文本")
        else OpResult.fail("还原失败")
    }

    override suspend fun copyDir(srcPkg: String, dstPkg: String, subDir: String): OpResult {
        // SAF 单次授权只能操作一个目录，无法直接跨包复制
        return OpResult.fail("SAF 通道不支持跨包迁移，请使用 Shizuku 或手动用系统文件管理器复制 $subDir")
    }

    override suspend fun hasBackup(): Boolean {
        val filesDir = rootFilesDir() ?: return false
        return filesDir.findFile("TableBundles.bak") != null
    }

    override fun cleanupStaging() {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
    }

    private fun rootFilesDir(): DocumentFile? =
        DocumentFile.fromTreeUri(context, treeUri)

    private fun copyRecursive(src: File, dstDir: DocumentFile) {
        if (src.isDirectory) {
            src.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val sub = dstDir.findFile(child.name) ?: dstDir.createDirectory(child.name) ?: throw java.io.IOException("无法创建目录: ${child.name}")
                    if (sub != null) copyRecursive(child, sub)
                } else {
                    val existing = dstDir.findFile(child.name)
                    val doc = existing ?: dstDir.createFile("application/octet-stream", child.name)
                    if (doc != null) {
                        resolver.openOutputStream(doc.uri)?.use { out ->
                            child.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                }
            }
        }
    }
}

