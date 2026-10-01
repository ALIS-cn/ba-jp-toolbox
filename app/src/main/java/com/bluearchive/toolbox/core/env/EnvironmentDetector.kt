package com.bluearchive.toolbox.core.env

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bluearchive.toolbox.core.device.DeviceDetector
import com.bluearchive.toolbox.core.device.DeviceInfo
import com.bluearchive.toolbox.core.shizuku.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PackageState(
    val installed: Boolean,
    val versionName: String? = null,
)

/**
 * 工具箱运行环境快照。
 */
data class EnvState(
    val device: DeviceInfo,
    val gameOfficial: PackageState,   // 官方日服 com.YostarJP.BlueArchive
    val gameCafe: PackageState,       // 咖啡厅汉化版 cafe.YostarJP.BlueArchive
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuAuthorized: Boolean,
    val shizukuVersion: Int,
    val saiInstalled: Boolean,        // Split APKs Installer
    val rootAvailable: Boolean,
)

class EnvironmentDetector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun detect(): EnvState {
        val shizukuInstalled = isPackageInstalled(ShizukuBridge.PACKAGE_SHIZUKU)
        val shizukuRunning = shizukuInstalled && ShizukuBridge.isRunning()
        val shizukuAuthorized = shizukuRunning && ShizukuBridge.isAuthorized()
        val shizukuVersion = if (shizukuRunning) ShizukuBridge.version() else -1
        return EnvState(
            device = DeviceDetector.detect(),
            gameOfficial = queryPackage(GAME_OFFICIAL),
            gameCafe = queryPackage(GAME_CAFE),
            shizukuInstalled = shizukuInstalled,
            shizukuRunning = shizukuRunning,
            shizukuAuthorized = shizukuAuthorized,
            shizukuVersion = shizukuVersion,
            saiInstalled = isPackageInstalled(SAI) || isPackageInstalled(SAI_FOSS),
            rootAvailable = checkRoot(),
        )
    }

    private fun queryPackage(pkg: String): PackageState =
        try {
            val info = pm.getPackageInfo(pkg, 0)
            PackageState(installed = true, versionName = info.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            PackageState(installed = false)
        }

    private fun isPackageInstalled(pkg: String): Boolean =
        try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Root 探测：执行 su -c id，限时 3 秒，避免某些 ROM 弹授权框卡住检测 */
    private suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val exit = withTimeoutOrNull(3_000L) { process.waitFor() }
            if (exit == null) {
                process.destroy()
                false
            } else {
                process.inputStream.bufferedReader().readText().contains("uid=0")
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val GAME_OFFICIAL = "com.YostarJP.BlueArchive"
        const val GAME_CAFE = "cafe.YostarJP.BlueArchive"
        const val SAI = "com.aefyr.sai"
        const val SAI_FOSS = "com.aefyr.sai.foss"
    }
}
