package com.bluearchive.toolbox.core.patch.channel

import com.bluearchive.toolbox.core.env.EnvironmentDetector
import com.bluearchive.toolbox.core.shizuku.ShellExecutor
import java.io.File

/**
 * Shizuku 通道：以 shell uid(2000) 执行命令，可读写 Android/data、force-stop。
 * 适用于安卓 13+（或任何已激活 Shizuku 的设备）。
 *
 * 所有命令均以 argv 数组直传，不经过 sh -c，无命令注入风险。
 */
class ShizukuChannel(
    private val shell: ShellExecutor = ShellExecutor,
    packageName: String = EnvironmentDetector.GAME_OFFICIAL,
    stagingDir: File,
) : FileSystemChannel {

    override val name = "Shizuku"

    private val gameFiles = "/storage/emulated/0/Android/data/$packageName/files"
    private val tableBundlesDir = "$gameFiles/TableBundles"
    private val backupDir = "$gameFiles/TableBundles.bak"

    private val staging = stagingDir

    override suspend fun forceStopGame(): OpResult {
        val r = shell.exec("am", "force-stop", EnvironmentDetector.GAME_OFFICIAL)
        // force-stop 在游戏未运行时也正常返回，不视为错误
        return OpResult.ok(r.stdout.ifBlank { "已尝试停止游戏" })
    }

    override suspend fun backupTableBundles(): OpResult {
        // 已有备份（原版文件）时不覆盖，直接清理当前目录准备部署
        // 这样即使上次部署失败，原版备份也不会丢失
        if (hasBackup()) {
            // sdcardfs 上 chmod 可能失败，单独执行且忽略结果，仅用于修复历史遗留的破损权限
            shell.exec("chmod", "-R", "777", tableBundlesDir)
            val r = shell.execChain(
                listOf("rm", "-rf", tableBundlesDir),
                listOf("mkdir", "-p", tableBundlesDir),
            )
            return if (r.exitCode == 0) OpResult.ok()
            else OpResult.fail(r.stderr.ifBlank { "备份失败，可能是 Shizuku 权限不足" })
        } else {
            val r = shell.execChain(
                listOf("mv", tableBundlesDir, backupDir),
                listOf("mkdir", "-p", tableBundlesDir),
            )
            return if (r.exitCode == 0) OpResult.ok()
            else OpResult.fail(r.stderr.ifBlank { "备份失败，可能是 Shizuku 权限不足" })
        }
    }

    override suspend fun deployFromStaging(stagingTableBundles: File): OpResult {
        // 注意：不要在 /storage/emulated/0/Android/data/ 上执行 chmod，
        // sdcardfs 不支持修改权限，且会破坏目录执行位导致后续 rm 失败。
        // cp 复制 200MB+ 跨 FUSE 挂载可能耗时数分钟，给 10 分钟超时
        val mkdir = shell.exec(listOf("mkdir", "-p", tableBundlesDir))
        if (mkdir.exitCode != 0) return OpResult.fail(mkdir.stderr.ifBlank { "创建目录失败" })
        val cp = shell.exec(
            listOf("cp", "-r", "${stagingTableBundles.absolutePath}/.", "$tableBundlesDir/"),
            timeoutMs = 600_000L,
        )
        return if (cp.exitCode == 0) OpResult.ok()
        else OpResult.fail(cp.stderr.ifBlank { "写入失败（退出码 ${cp.exitCode}）" })
    }

    override suspend fun fileMd5(relativePath: String): String? {
        val r = shell.exec("md5sum", "$gameFiles/$relativePath")
        if (r.exitCode != 0) return null
        // 输出格式：<32位hex>  文件名
        return r.stdout.trim().takeIf { it.length >= 32 }?.substring(0, 32)
    }

    override suspend fun fileSize(relativePath: String): Long {
        val r = shell.exec("stat", "-c", "%s", "$gameFiles/$relativePath")
        if (r.exitCode != 0) return -1L
        return r.stdout.trim().toLongOrNull() ?: -1L
    }

    override suspend fun restoreFromBackup(): OpResult {
        if (!hasBackup()) return OpResult.fail("未找到备份目录 TableBundles.bak")
        // 先尝试修复可能被破坏的目录权限（忽略错误），再删除旧目录，最后还原备份
        shell.exec("chmod", "-R", "777", tableBundlesDir)
        val r = shell.execChain(
            listOf("rm", "-rf", tableBundlesDir),
            listOf("mv", backupDir, tableBundlesDir),
        )
        return if (r.exitCode == 0) OpResult.ok("已还原为原版文本")
        else OpResult.fail(r.stderr.ifBlank { "还原失败" })
    }

    override suspend fun copyDir(srcPkg: String, dstPkg: String, subDir: String): OpResult {
        val src = "/storage/emulated/0/Android/data/$srcPkg/files/$subDir"
        val dstParent = "/storage/emulated/0/Android/data/$dstPkg/files"
        if (!shell.dirExists(src)) return OpResult.fail("源目录不存在（$subDir），跳过")
        // 不执行 chmod，原因同 deployFromStaging
        val r = shell.execChain(
            listOf("mkdir", "-p", dstParent),
            listOf("rm", "-rf", "$dstParent/$subDir"),
            listOf("cp", "-r", src, "$dstParent/"),
        )
        return if (r.exitCode == 0) OpResult.ok()
        else OpResult.fail(r.stderr.ifBlank { "复制失败" })
    }

    override suspend fun hasBackup(): Boolean = shell.dirExists(backupDir)

    override fun cleanupStaging() {
        staging.deleteRecursively()
        staging.mkdirs()
    }
}
