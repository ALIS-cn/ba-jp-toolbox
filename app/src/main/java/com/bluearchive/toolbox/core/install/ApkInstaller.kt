package com.bluearchive.toolbox.core.install

import java.io.File

/**
 * APK 分包安装器抽象。
 */
interface ApkInstaller {
    val name: String

    /**
     * 安装多个 apk（APKS 解压后的 base.apk + split_config.*.apk）。
     * @return 安装结果；静默安装返回真实结果，系统安装器返回"已发起"由系统回调。
     */
    suspend fun install(apkFiles: List<File>): InstallResult
}

data class InstallResult(
    val success: Boolean,
    val message: String,
    /** true 表示已通过系统弹窗发起，结果由用户在系统界面决定 */
    val pendingUserConfirmation: Boolean = false,
)
