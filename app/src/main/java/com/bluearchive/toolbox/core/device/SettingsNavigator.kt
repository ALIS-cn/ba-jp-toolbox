package com.bluearchive.toolbox.core.device

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * 跳转到「开发者选项」。不同 ROM 的 action 兼容性不同，按顺序尝试：
 * 1. 标准 ACTION_APPLICATION_DEVELOPMENT_SETTINGS（绝大多数有效）
 * 2. 兜底 ACTION_SETTINGS（让用户自己找）
 */
object SettingsNavigator {

    fun openDeveloperOptions(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
        Toast.makeText(context, "无法打开开发者选项，请手动进入 设置 → 关于手机 → 连续点击版本号", Toast.LENGTH_LONG).show()
    }

    /** 跳转到应用详情页（用于引导用户关闭省电、允许后台等） */
    fun openAppDetails(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "请手动进入设置 → 应用 → 对应应用", Toast.LENGTH_LONG).show()
        }
    }
}
