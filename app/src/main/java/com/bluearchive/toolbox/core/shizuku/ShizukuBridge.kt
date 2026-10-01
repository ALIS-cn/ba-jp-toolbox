package com.bluearchive.toolbox.core.shizuku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Shizuku 客户端封装：
 * - 服务端是独立安装的 Shizuku App（moe.shizuku.privileged.api），本工具只作为客户端绑定其 Binder。
 * - API 13.x：执行 shell 通过 IShizukuService.newProcess，所有特权调用前必须先 pingBinder + checkSelfPermission。
 */
object ShizukuBridge {

    const val PACKAGE_SHIZUKU = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 1001

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean =
        isRunning() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** 返回 Shizuku 服务版本号（如 13），未运行时为 -1 */
    fun version(): Int =
        if (isRunning()) runCatching { Shizuku.getVersion() }.getOrDefault(-1) else -1

    /**
     * 发起授权并挂起等待结果。必须在服务运行时调用；返回 true 表示已授权。
     */
    suspend fun requestPermission(): Boolean {
        if (isAuthorized()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode == REQUEST_CODE) {
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (cont.isActive) {
                            cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                }
            }
            cont.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.addRequestPermissionResultListener(listener)
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // 用户之前拒绝过：依然发起请求，由系统/Shizuku 展示说明
            }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    /** 打开 Shizuku App；未安装返回 false */
    fun openShizukuApp(context: Context): Boolean =
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_SHIZUKU)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
