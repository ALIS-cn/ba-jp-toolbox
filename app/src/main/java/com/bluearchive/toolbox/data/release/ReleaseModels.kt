package com.bluearchive.toolbox.data.release

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    /** 新版 API 可能返回 sha256:xxx 形式的摘要 */
    val digest: String? = null,
)

/**
 * 供 UI 使用的“最新汉化资源”信息。
 */
data class CafeResource(
    val tag: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val publishedAt: String,
    val fetchedAt: Long,
    /** true 表示网络不可用，使用的是本地缓存，不保证是最新 */
    val fromCache: Boolean,
)
