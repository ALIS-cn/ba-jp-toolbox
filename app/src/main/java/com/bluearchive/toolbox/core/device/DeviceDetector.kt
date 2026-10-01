package com.bluearchive.toolbox.core.device

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 检测机型、ROM 与系统版本，输出可用于自适应 UI 与流程的 DeviceInfo。
 *
 * ROM 判定优先级（系统属性优先于品牌名）：
 *  1. 鸿蒙 HarmonyOS → ro.build.version.harmonyos 存在 / 显示版本含 Harmony
 *  2. MIUI/澎湃 OS → ro.miui.ui.version.name 存在
 *  3. 华为 EMUI → ro.build.version.emui 存在
 *  4. ColorOS/realme → ro.build.version.oplusrom 或 ro.build.display.id 含 ColorOS
 *  5. OriginOS(vivo) → ro.vivo.os.version 或 ro.vivo.os.build.display.id
 *  6. 三星 One UI → ro.build.version.sep 存在
 *  7. 其余按 Build.MANUFACTURER/BRAND 兜底，最终归为原生/其他
 */
object DeviceDetector {

    fun detect(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val model = Build.MODEL ?: ""
        val display = Build.DISPLAY ?: ""
        val sdkInt = Build.VERSION.SDK_INT
        val androidRelease = Build.VERSION.RELEASE ?: ""

        val romType = detectRomType(manufacturer, brand, display)
        val romVersion = detectRomVersion(romType)
        val features = buildFeatures(romType, sdkInt, manufacturer, brand)

        return DeviceInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            sdkInt = sdkInt,
            androidRelease = androidRelease,
            romType = romType,
            romVersion = romVersion,
            features = features,
        )
    }

    private fun detectRomType(manufacturer: String, brand: String, display: String): RomType {
        // 1. 鸿蒙：优先于 EMUI 与小米判定
        val harmony = getSystemProperty("ro.build.version.harmonyos")
        if (!harmony.isNullOrBlank() || display.contains("HarmonyOS", ignoreCase = true) ||
            getSystemProperty("ro.huawei.build.display.id")?.contains("Harmony", ignoreCase = true) == true
        ) return RomType.HARMONY

        // 2. MIUI / 澎湃 OS
        val miui = getSystemProperty("ro.miui.ui.version.name")
        if (!miui.isNullOrBlank() || manufacturer.equals("Xiaomi", ignoreCase = true) ||
            brand.equals("Redmi", ignoreCase = true) || brand.equals("POCO", ignoreCase = true)
        ) return RomType.MIUI

        // 3. EMUI（华为/荣耀老机型，鸿蒙之前）
        val emui = getSystemProperty("ro.build.version.emui")
        if (!emui.isNullOrBlank() || manufacturer.equals("HUAWEI", ignoreCase = true) ||
            manufacturer.equals("HONOR", ignoreCase = true)
        ) return RomType.EMUI

        // 4. ColorOS / realme UI
        val oplus = getSystemProperty("ro.build.version.oplusrom")
        if (!oplus.isNullOrBlank() || display.contains("ColorOS", ignoreCase = true) ||
            manufacturer.equals("OPPO", ignoreCase = true) || manufacturer.equals("realme", ignoreCase = true) ||
            manufacturer.equals("OnePlus", ignoreCase = true)
        ) return RomType.COLOR_OS

        // 5. OriginOS / Funtouch OS (vivo/iQOO)
        val vivo = getSystemProperty("ro.vivo.os.version")
        if (!vivo.isNullOrBlank() || getSystemProperty("ro.vivo.os.build.display.id")?.isNotBlank() == true ||
            manufacturer.equals("vivo", ignoreCase = true) || manufacturer.equals("iQOO", ignoreCase = true)
        ) return RomType.ORIGIN_OS

        // 6. 三星 One UI
        val oneui = getSystemProperty("ro.build.version.sep")
        if (!oneui.isNullOrBlank() || manufacturer.equals("samsung", ignoreCase = true)
        ) return RomType.ONE_UI

        return RomType.STOCK
    }

    private fun detectRomVersion(rom: RomType): String = when (rom) {
        RomType.MIUI -> listOfNotNull(
            getSystemProperty("ro.miui.ui.version.name"),
            getSystemProperty("ro.build.version.incremental")?.takeIf { it.isNotBlank() },
        ).joinToString(" / ").ifBlank { "未知" }
        RomType.HARMONY -> listOfNotNull(
            getSystemProperty("ro.build.version.harmonyos"),
        ).joinToString(" / ").ifBlank { getSystemProperty("ro.huawei.build.display.id").orEmpty().ifBlank { "未知" } }
        RomType.EMUI -> getSystemProperty("ro.build.version.emui").orEmpty().ifBlank { "未知" }
        RomType.COLOR_OS -> listOfNotNull(
            getSystemProperty("ro.build.version.oplusrom"),
            getSystemProperty("ro.build.version.opporom"),
        ).joinToString(" / ").ifBlank { "未知" }
        RomType.ORIGIN_OS -> getSystemProperty("ro.vivo.os.version").orEmpty().ifBlank { "未知" }
        RomType.ONE_UI -> getSystemProperty("ro.build.version.sep").orEmpty().ifBlank { "未知" }
        RomType.STOCK -> Build.VERSION.RELEASE.orEmpty()
    }

    private fun buildFeatures(
        rom: RomType,
        sdkInt: Int,
        manufacturer: String,
        brand: String,
    ): DeviceFeatures {
        val supportsWirelessDebug = sdkInt >= Build.VERSION_CODES.R
        // 小米、OPPO、vivo 系 ROM 息屏后无线调试常被省电策略中断
        val killedWhenScreenOff = rom == RomType.MIUI || rom == RomType.COLOR_OS || rom == RomType.ORIGIN_OS
        // MIUI/澎湃 OS 有「USB 调试(安全设置)」；部分 ColorOS 也有类似开关
        val needsSecurityDebug = rom == RomType.MIUI || rom == RomType.COLOR_OS
        // MIUI/ColorOS 对未知来源 APK 安装管控更严格，需引导关闭安装校验
        val strictInstallCheck = rom == RomType.MIUI || rom == RomType.COLOR_OS || rom == RomType.ORIGIN_OS
        // 已知某些机型/ROM 与 BA 日服反作弊冲突概率更高（仅作提示，不绝对）
        val antiCheatRisk = false
        return DeviceFeatures(
            supportsWirelessDebug = supportsWirelessDebug,
            wirelessDebugKilledWhenScreenOff = killedWhenScreenOff,
            needsSecurityDebugSetting = needsSecurityDebug,
            strictUnknownSourceCheck = strictInstallCheck,
            knownAntiCheatRisk = antiCheatRisk,
        )
    }

    /** 通过反射读取系统属性，兼容绝大多数 ROM，无 Root 也可读取非敏感属性 */
    private fun getSystemProperty(key: String): String? {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val method = cls.getMethod("get", String::class.java)
            method.invoke(cls, key) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
