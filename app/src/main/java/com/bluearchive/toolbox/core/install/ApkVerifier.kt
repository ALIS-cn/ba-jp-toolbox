package com.bluearchive.toolbox.core.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.bluearchive.toolbox.core.log.LogCollector
import java.io.File
import java.security.MessageDigest

/**
 * 安装前对下载/内置的 APK 做供应链校验：
 *  1. 包名必须与预期一致（防止镜像/代理返回被替换的其他 APK）；
 *  2. 若提供了固定的签名证书 SHA-256，则签名证书必须匹配（防止同名重打包）。
 *
 * 指纹取自随 App 内置、可信的官方安装包，属于证书固定（certificate pinning）。
 */
object ApkVerifier {

    /** Shizuku 官方包名 与 官方签名证书 SHA-256（CN=Rikka） */
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_CERT_SHA256 = "268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30"

    /** SAI 官方包名 与 签名证书 SHA-256（CN=Tony Stark） */
    const val SAI_PACKAGE = "com.aefyr.sai"
    const val SAI_CERT_SHA256 = "2c4c506a3c14a310a1281cb9a85c03ef94c610890d5731678ebd7ec8b26bca79"

    /** 咖啡厅汉化客户端包名（签名未公开固定，仅校验包名） */
    const val CAFE_PACKAGE = "cafe.YostarJP.BlueArchive"

    data class Result(val ok: Boolean, val actualPackage: String?, val message: String)

    fun verify(
        context: Context,
        apk: File,
        expectedPackage: String,
        expectedCertSha256: String? = null,
    ): Result {
        if (!apk.exists()) {
            return Result(false, null, "安装包文件不存在")
        }
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val pi = try {
            pm.getPackageArchiveInfo(apk.absolutePath, flag)
        } catch (e: Exception) {
            LogCollector.e("ApkVerify", "解析安装包失败: ${e.message}", e)
            null
        } ?: return Result(false, null, "无法解析安装包，文件可能已损坏或不是有效 APK")

        val actualPkg = pi.packageName
        if (actualPkg != expectedPackage) {
            LogCollector.e("ApkVerify", "包名不匹配: 期望=$expectedPackage, 实际=$actualPkg")
            return Result(
                false, actualPkg,
                "安装包校验失败：包名不匹配（期望 $expectedPackage，实际 $actualPkg），已中止安装以防风险",
            )
        }

        if (expectedCertSha256 != null) {
            val certs: Array<android.content.pm.Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pi.signingInfo
                when {
                    signingInfo == null -> emptyArray()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                    else -> signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pi.signatures ?: emptyArray()
            }
            val matched = certs.any { sig ->
                runCatching { sha256Hex(sig.toByteArray()) == expectedCertSha256 }.getOrDefault(false)
            }
            if (!matched) {
                val actual = certs.joinToString(",") { runCatching { sha256Hex(it.toByteArray()).take(12) }.getOrDefault("?") }
                LogCollector.e("ApkVerify", "签名证书不匹配: pkg=$actualPkg, 实际指纹=$actual")
                return Result(
                    false, actualPkg,
                    "安装包校验失败：签名证书与官方不一致，可能被篡改，已中止安装",
                )
            }
        }

        LogCollector.i("ApkVerify", "校验通过: pkg=$actualPkg${if (expectedCertSha256 != null) "（签名固定校验通过）" else ""}")
        return Result(true, actualPkg, "校验通过")
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { b ->
                val v = b.toInt() and 0xFF
                append(Character.forDigit(v ushr 4, 16))
                append(Character.forDigit(v and 0x0F, 16))
            }
        }
    }
}
