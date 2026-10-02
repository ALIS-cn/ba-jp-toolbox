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
        // 已有备份：不覆盖，保护原版
        if (filesDir.findFile("TableBundles.bak") != null) {
            // 清空 TableBundles 目录准备接收新文件
            tb.delete()
            filesDir.createDirectory("TableBundles")
            return OpResult.ok("已有原版备份，跳过重复备份")
        }
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

    override suspend fun fileMd5(relativePath: String): String? {
        val doc = resolveGameFile(relativePath) ?: return null
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            resolver.openInputStream(doc.uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) md.update(buf, 0, read)
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { null }
    }

    override suspend fun fileSize(relativePath: String): Long =
        resolveGameFile(relativePath)?.length() ?: -1L

    override fun cleanupStaging() {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
    }

    private fun rootFilesDir(): DocumentFile? =
        DocumentFile.fromTreeUri(context, treeUri)

    /** 按相对路径逐级查找游戏 files 目录下的文件 */
    private fun resolveGameFile(relativePath: String): DocumentFile? {
        var node = rootFilesDir() ?: return null
        for (part in relativePath.split("/")) {
            if (part.isEmpty()) continue
            node = node.findFile(part) ?: return null
        }
        return node.takeIf { it.isFile }
    }

    private fun copyRecursive(src: File, dstDir: DocumentFile) {
        if (src.isDirectory) {
            src.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val sub = dstDir.findFile(child.name) ?: dstDir.createDirectory(child.name) ?: throw java.io.IOException("无法创建目录: ${child.name}")
                    if (sub != null) copyRecursive(child, sub)
                } else {
                    val existing = dstDir.findFile(child.name)
                    val doc = existing ?: dstDir.createFile("application/octet-stream", child.name)
                        ?: throw java.io.IOException("无法创建文件: ${child.name}")
                    val out = resolver.openOutputStream(doc.uri)
                        ?: throw java.io.IOException("无法打开输出流: ${child.name}")
                    out.use { output ->
                        child.inputStream().use { input -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

