package com.bluearchive.toolbox.core.device

import android.os.Build

/**
 * 识别出的 ROM 类型。用于自适应 Shizuku 激活路径、安装提示与反作弊注意事项。
 */
enum class RomType(val displayName: String) {
    MIUI("MIUI / 澎湃 OS"),
    HARMONY("鸿蒙 HarmonyOS"),
    EMUI("华为 EMUI"),
    COLOR_OS("ColorOS"),
    ORIGIN_OS("OriginOS / Funtouch OS"),
    ONE_UI("三星 One UI"),
    STOCK("原生 / 其他"),
}

/**
 * 设备信息快照。
 * - manufacturer/brand/model 取自 Build
 * - romType/romVersion 通过系统属性推断
 * - features 汇总该机型与 ROM 对本工具关键操作的影响
 */
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val androidRelease: String,
    val romType: RomType,
    val romVersion: String,
    val features: DeviceFeatures,
)

data class DeviceFeatures(
    /** 是否支持「无线调试」（安卓 11+） */
    val supportsWirelessDebug: Boolean,
    /** 该 ROM 下无线调试在息屏后是否易被切断，需提示加白名单 */
    val wirelessDebugKilledWhenScreenOff: Boolean,
    /** 是否存在「USB 调试(安全设置)」类额外开关（MIUI/部分 ColorOS） */
    val needsSecurityDebugSetting: Boolean,
    /** 安装未知来源 APKS 是否有额外拦截（MIUI/ColorOS 严格） */
    val strictUnknownSourceCheck: Boolean,
    /** 该 ROM 是否已知会与游戏反作弊冲突而触发 Use of unauthorized apps */
    val knownAntiCheatRisk: Boolean,
)
