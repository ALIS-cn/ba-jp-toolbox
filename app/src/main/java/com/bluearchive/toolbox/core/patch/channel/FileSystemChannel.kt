package com.bluearchive.toolbox.core.patch.channel

import java.io.File

/**
 * 文件操作通道的统一结果。
 */
data class OpResult(
    val success: Boolean,
    val message: String = "",
) {
    companion object {
        fun ok(msg: String = "") = OpResult(true, msg)
        fun fail(msg: String) = OpResult(false, msg)
    }
}

/**
 * 对游戏数据目录进行操作的抽象通道。
 *
 * 不同安卓版本 / 权限条件下选择不同实现：
 * - ShizukuChannel：安卓 13+ 首选，shell uid 可读写 Android/data 并可 force-stop
 * - FileApiChannel：安卓 ≤ 10，普通存储权限即可直接读写
 * - SafChannel：安卓 11~12，通过 SAF 用户授权后访问 Android/data
 */
interface FileSystemChannel {

    /** 通道名称，用于 UI 展示 */
    val name: String

    /** 强制停止游戏。不能实现的通道返回 ok(true) 但 message 提示用户手动关闭。 */
    suspend fun forceStopGame(): OpResult

    /** 备份 TableBundles → TableBundles.bak */
    suspend fun backupTableBundles(): OpResult

    /** 把 staging 下已解压的 TableBundles 内容部署到游戏目录 */
    suspend fun deployFromStaging(stagingTableBundles: File): OpResult

    /** 用备份还原 */
    suspend fun restoreFromBackup(): OpResult

    /**
     * 跨包复制资源目录：把 /sdcard/Android/data/<srcPkg>/files/<subDir>
     * 复制到 /sdcard/Android/data/<dstPkg>/files/<subDir>
     * 用于汉化客户端安装后迁移游戏资源，避免重下数 GB。
     */
    suspend fun copyDir(srcPkg: String, dstPkg: String, subDir: String): OpResult

    suspend fun hasBackup(): Boolean

    fun cleanupStaging()
}
