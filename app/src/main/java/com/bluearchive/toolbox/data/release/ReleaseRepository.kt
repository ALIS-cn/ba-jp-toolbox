package com.bluearchive.toolbox.data.release

import com.bluearchive.toolbox.core.log.LogCollector
import com.bluearchive.toolbox.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 汉化资源来源：
 * - 汉化客户端安装包：https://download.bluearchive.cafe/android/latest （APKS，307→302 跳转真实地址）
 * - 文件替换包 TableBundles.zip：bluearchive-cafe/bluearchive-cafe 的 GitHub Releases
 * - Shizuku：RikkaApps/Shizuku 官方 Releases
 *
 * 下载策略：
 * - bluearchive.cafe：国内可直连，直接下载（GitHub 镜像对非 GitHub 链接无效）
 * - GitHub 链接：镜像优先，直连兜底（国内无法直连 GitHub）
 */
class ReleaseRepository(
    private val prefs: AppPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 不跟随重定向的 client，用于解析 bluearchive.cafe 的 302 跳转地址 */
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** 短超时 client，仅用于镜像测速 */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val mirrorProber = MirrorProber(probeClient)

    /** 当前正在进行的下载 Call，用于即时取消 */
    private val currentDownloadCall = AtomicReference<Call?>(null)

    /** 取消当前正在进行的下载（立即中断 OkHttp 请求） */
    fun cancelDownload() {
        currentDownloadCall.getAndSet(null)?.cancel()
    }

    /**
     * 获取最新汉化资源。GitHub API 走国内镜像，失败时降级缓存。
     */
    suspend fun fetchCafeLatest(forceRefresh: Boolean = true): Result<CafeResource> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cachedJson = prefs.releaseJson()
                val cachedResource = cachedJson?.let { parseCafeResource(it, prefs.releaseFetchedAt(), fromCache = true) }

                if (!forceRefresh && cachedResource != null &&
                    System.currentTimeMillis() - prefs.releaseFetchedAt() < CACHE_FRESH_MS
                ) {
                    return@runCatching cachedResource.copy(fromCache = false)
                }

                val etag = prefs.releaseEtag().orEmpty()
                // API 走国内镜像：尝试多个，直到拿到 JSON 响应
                val apiCandidates = buildApiCandidates(CAFE_LATEST_API)
                var apiError: String? = null
                var cafeResource: CafeResource? = null
                for (apiUrl in apiCandidates) {
                    try {
                        val request = Request.Builder()
                            .url(apiUrl)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/vnd.github+json")
                            .apply { if (etag.isNotEmpty()) header("If-None-Match", etag) }
                            .build()
                        client.newCall(request).execute().use { resp ->
                            when (resp.code) {
                                304 -> {
                                    cafeResource = cachedResource?.copy(fromCache = false)
                                        ?: error("304 但本地无缓存")
                                }
                                in 200..299 -> {
                                    val body = resp.body?.string().orEmpty()
                                    // 检测 body 是否真的是 JSON（首字符为 { 或 [）
                                    val trimmed = body.trimStart()
                                    if (trimmed.isEmpty() || (trimmed[0] != '{' && trimmed[0] != '[')) {
                                        apiError = "镜像返回非 JSON 内容"
                                        return@use
                                    }
                                    val newEtag = resp.header("ETag")
                                    prefs.saveReleaseCache(
                                        etag = newEtag,
                                        json = body,
                                        fetchedAt = System.currentTimeMillis(),
                                    )
                                    cafeResource = parseCafeResource(body, System.currentTimeMillis(), fromCache = false)
                                }
                                403 -> apiError = "GitHub 访问限额（403）"
                                else -> apiError = "GitHub 返回 ${resp.code}"
                            }
                        }
                        if (cafeResource != null) break
                    } catch (e: Exception) {
                        apiError = e.message ?: e.javaClass.simpleName
                    }
                }
                cafeResource ?: error(apiError ?: "所有镜像均失败")
            }.recoverCatching { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                // 网络异常时：有缓存就降级用缓存，没有缓存才报错
                val cachedJson = prefs.releaseJson()
                if (cachedJson != null) {
                    parseCafeResource(cachedJson, prefs.releaseFetchedAt(), fromCache = true)
                } else {
                    throw error
                }
            }
        }

    private fun parseCafeResource(body: String, fetchedAt: Long, fromCache: Boolean): CafeResource {
        val release = json.decodeFromString<GithubRelease>(body)
        val asset = release.assets.firstOrNull { it.name == TABLE_BUNDLES_ASSET }
            ?: release.assets.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
            ?: error("Release ${release.tagName} 中未找到 $TABLE_BUNDLES_ASSET")
        val sha256 = asset.digest?.removePrefix("sha256:")
            ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
        return CafeResource(
            tag = release.tagName,
            assetName = asset.name,
            downloadUrl = asset.browserDownloadUrl,
            sizeBytes = asset.size,
            sha256 = sha256,
            publishedAt = release.publishedAt,
            fetchedAt = fetchedAt,
            fromCache = fromCache,
        )
    }

    /** 查询最新版 Shizuku APK 下载信息（API 走多镜像重试）。 */
    suspend fun fetchLatestShizukuApk(): Result<GithubAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val apiCandidates = buildApiCandidates(SHIZUKU_LATEST_API)
            var lastError: String? = null
            for (apiUrl in apiCandidates) {
                try {
                    val request = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/vnd.github+json")
                        .build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            lastError = "HTTP ${resp.code}"
                            return@use
                        }
                        val body = resp.body?.string().orEmpty()
                        // 检测 body 是否真的是 JSON
                        val trimmed = body.trimStart()
                        if (trimmed.isEmpty() || (trimmed[0] != '{' && trimmed[0] != '[')) {
                            lastError = "镜像返回非 JSON 内容"
                            return@use
                        }
                        val release = json.decodeFromString<GithubRelease>(body)
                        return@runCatching release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                            ?: error("未找到 Shizuku APK 资源")
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
            error(lastError ?: "所有镜像均失败")
        }
    }

    /**
     * 解析 bluearchive.cafe 下载链接的 307/302 重定向真实地址。
     * 咖啡厅国内可直连，直接请求原地址获取 Location 头，不走镜像。
     */
    private suspend fun resolveRedirectUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
                .header("Accept", "*/*").build()
            noRedirectClient.newCall(request).execute().use { resp ->
                val location = resp.header("Location")
                if (!location.isNullOrEmpty() && location.startsWith("http")) {
                    return@withContext location
                }
            }
        } catch (_: Exception) {
            // 忽略，返回 null 用原地址尝试
        }
        null
    }

    /**
     * 下载文件。
     * - bluearchive.cafe：直连下载（国内可访问）
     * - GitHub 链接：镜像优先，直连兜底
     * @param onProgress (已下载字节, 总字节)
     */
    suspend fun downloadWithMirrors(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        LogCollector.i("Download", "开始下载: $url -> ${dest.name}")
        // 对 bluearchive.cafe 的 307 跳转链接，先解析真实地址
        val realUrl = if (url.contains("bluearchive.cafe")) {
            val resolved = resolveRedirectUrl(url)
            if (resolved != null) {
                LogCollector.i("Download", "重定向解析成功: $resolved")
                resolved
            } else {
                LogCollector.w("Download", "重定向解析失败，使用原地址")
                url
            }
        } else url

        val candidates = buildDownloadCandidates(realUrl)
        LogCollector.i("Download", "候选源数量: ${candidates.size}")
        var lastError: String? = null
        for ((index, candidate) in candidates.withIndex()) {
            LogCollector.i("Download", "尝试源${index + 1}/${candidates.size}: $candidate")
            try {
                val request = Request.Builder().url(candidate).header("User-Agent", USER_AGENT).build()
                val call = client.newCall(request)
                currentDownloadCall.set(call)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "源${index + 1} HTTP ${resp.code}"
                        LogCollector.w("Download", lastError!!)
                        return@use
                    }
                    val contentType = resp.header("Content-Type").orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        // 镜像返回了 HTML 错误页，不是真实文件
                        lastError = "源${index + 1} 返回 HTML 错误页"
                        LogCollector.w("Download", lastError!!)
                        return@use
                    }
                    val body = resp.body ?: run {
                        lastError = "源${index + 1} 响应体为空"
                        LogCollector.w("Download", lastError!!)
                        return@use
                    }
                    LogCollector.i("Download", "源${index + 1} 响应成功，Content-Type=$contentType，大小=${body.contentLength()}")
                    dest.parentFile?.mkdirs()
                    val partFile = File(dest.absolutePath + ".part")
                    partFile.outputStream().use { out ->
                        val source = body.byteStream()
                        val total = body.contentLength()
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastEmit = 0L
                        val emitIntervalMs = 120L // 节流：每 120ms 上报一次，让进度条动画丝滑
                        while (source.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive() // 响应取消
                            out.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= emitIntervalMs) {
                                lastEmit = now
                                onProgress(downloaded, total)
                            }
                        }
                        out.flush()
                        onProgress(downloaded, total) // 确保最终进度上报
                    }
                    // 校验下载的文件不是 HTML 错误页（检查魔数）
                    if (!isValidDownload(partFile, contentType)) {
                        lastError = "源${index + 1} 下载内容非有效文件"
                        LogCollector.w("Download", lastError!!)
                        partFile.delete()
                        return@use
                    }
                    if (dest.exists()) dest.delete()
                    if (!partFile.renameTo(dest)) {
                        // rename 失败（跨卷/占用）时退回复制
                        partFile.copyTo(dest, overwrite = true)
                        partFile.delete()
                    }
                    LogCollector.i("Download", "下载成功: ${dest.absolutePath} (${dest.length()} bytes)")
                    return@withContext Result.success(dest)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                File(dest.absolutePath + ".part").delete()
                throw e // 取消必须向上传播，不能降级到下一个镜像
            } catch (e: Exception) {
                lastError = "源${index + 1} ${e.javaClass.simpleName}: ${e.message}"
                LogCollector.e("Download", lastError!!, e)
                File(dest.absolutePath + ".part").delete()
            }
        }
        LogCollector.e("Download", "所有源均失败: $lastError")
        Result.failure(IllegalStateException("下载失败（已尝试 ${candidates.size} 个源）：$lastError\n提示：国内网络访问可能不稳定，可改用加速器或浏览器手动下载。"))
    }

    /** 校验下载的文件不是 HTML 错误页：始终检查魔数，zip/apk 以 PK 开头 */
    private fun isValidDownload(file: File, contentType: String): Boolean {
        if (file.length() < 4) return false
        val header = ByteArray(4)
        FileInputStream(file).use { it.read(header) }
        // zip/apk/xapk 魔数：50 4B 03 04 (PK..)
        val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
        // 排除 HTML/XML 错误页：以 '<' 开头
        val isHtml = header[0] == '<'.code.toByte()
        // 排除 JSON 错误页：以 '{' 或 '[' 开头
        val isJson = header[0] == '{'.code.toByte() || header[0] == '['.code.toByte()
        if (isHtml || isJson) return false
        // 非 zip 但大于 1KB 且不是 HTML/JSON 的，可能是其他二进制文件（如 apk 直链）
        return isZip || (file.length() > 1024 && !contentType.contains("text", ignoreCase = true))
    }

    /** 构建 API 请求候选镜像列表：最快优先，直连兜底 */
    private suspend fun buildApiCandidates(apiUrl: String): List<String> {
        val fastest = mirrorProber.pickFastest(GITHUB_MIRRORS, apiUrl)
        return buildList {
            if (fastest != null) add(fastest)
            GITHUB_MIRRORS.forEach { m ->
                val wrapped = m + apiUrl
                if (wrapped != fastest) add(wrapped)
            }
            add(apiUrl) // 直连兜底
        }
    }

    /** 构建下载候选源列表：
     * GitHub 链接 → 镜像优先，直连兜底（国内无法直连 GitHub）
     * bluearchive.cafe → 仅直连（GitHub 镜像对非 GitHub 链接无效，国内可直连咖啡厅）
     * 其他链接 → 镜像优先，直连兜底
     */
    private suspend fun buildDownloadCandidates(url: String): List<String> {
        val isGithub = url.startsWith("https://github.com/") || url.startsWith("https://api.github.com/")
        val isCafe = url.contains("bluearchive.cafe")

        if (isCafe) {
            // 咖啡厅国内可直连，GitHub 镜像对非 GitHub 链接无效，直接返回直连
            return listOf(url)
        }

        val allMirrors = if (isGithub) GITHUB_MIRRORS else (GITHUB_MIRRORS + GENERIC_PROXIES)

        return buildList {
            // 测速最快的镜像
            val fastest = mirrorProber.pickFastest(allMirrors, url)
            if (fastest != null && fastest != url) add(fastest)
            allMirrors.forEach { m ->
                val wrapped = if (isGithub || GITHUB_MIRRORS.contains(m)) {
                    m + url
                } else {
                    m + java.net.URLEncoder.encode(url, "UTF-8")
                }
                if (wrapped != fastest && !contains(wrapped)) add(wrapped)
            }
            add(url) // 直连兜底
        }
    }

    companion object {
        private const val CAFE_LATEST_API =
            "https://api.github.com/repos/bluearchive-cafe/bluearchive-cafe/releases/latest"
        private const val SHIZUKU_LATEST_API =
            "https://api.github.com/repos/RikkaApps/Shizuku/releases/latest"
        private const val TABLE_BUNDLES_ASSET = "TableBundles.zip"
        private const val USER_AGENT = "BA-Toolbox/0.1 (Android)"
        private const val CACHE_FRESH_MS = 60 * 60 * 1000L // 1 小时

        /** 汉化客户端 APKS 直链（307 跳转到版本化路径，再 302 到 OSS） */
        const val CAFE_ANDROID_CLIENT_URL = "https://download.bluearchive.cafe/android/latest"

        // GitHub 下载/API 加速镜像（国内镜像，支持 github.com 和 api.github.com 代理）
        private val GITHUB_MIRRORS = listOf(
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://mirror.ghproxy.com/",
            "https://ghproxy.net/",
            "https://github.moeyy.xyz/",
            "https://githubproxy.cc/",
            "https://slink.ltd/",
            "https://gh.llkk.cc/",
            "https://gitproxy.click/",
            "https://mirrors.chenby.cn/",
            "https://kkgithub.com/",
            "https://g.nite07.org/",
        )

        // 通用 URL 代理（用于 download.bluearchive.cafe 等非 GitHub 链接）
        private val GENERIC_PROXIES = listOf(
            "https://api.allorigins.win/raw?url=",
            "https://corsproxy.io/?url=",
            "https://thingproxy.freeboard.io/fetch/",
        )

        // 官网地址（下载失败时供用户手动下载跳转用）
        const val CAFE_OFFICIAL_SITE = "https://bluearchive.cafe/download"
        const val SHIZUKU_OFFICIAL_SITE = "https://shizuku.rikka.app/download/"
    }
}

/**
 * 镜像测速器：并发探测多个镜像，选择当前最快可用的。
 * 结果缓存 5 分钟，避免每次下载都测速。
 */
class MirrorProber(
    private val client: OkHttpClient,
) {
    private data class ProbeResult(val mirror: String, val latencyMs: Long)

    private val cachedFastest = AtomicReference<Pair<String, Long>?>(null) // mirror + 缓存时间戳

    /** 直接返回最快镜像包装后的 URL（不测速，用缓存）；无缓存返回 null */
    fun wrapWithFastestMirror(url: String): String? {
        val cached = cachedFastest.get()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < CACHE_TTL_MS) {
            return cached.first + url
        }
        return null
    }

    /**
     * 并发探测镜像列表，返回最快可用镜像包装后的 URL。
     * 测速用 GET 请求但不读 body，超时 5 秒。
     */
    suspend fun pickFastest(mirrors: List<String>, targetUrl: String): String? = coroutineScope {
        // 优先用缓存
        val cached = cachedFastest.get()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < CACHE_TTL_MS) {
            return@coroutineScope cached.first + targetUrl
        }

        // 并发测速（直接拼接，测速只需验证可达性）
        val deferreds = mirrors.map { mirror ->
            async {
                val wrapped = mirror + targetUrl
                val start = System.currentTimeMillis()
                try {
                    val req = Request.Builder().url(wrapped).head().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful || resp.code == 301 || resp.code == 302 || resp.code == 405) {
                            // 存 mirror 前缀，不存完整 URL
                            ProbeResult(mirror, System.currentTimeMillis() - start)
                        } else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
        val results = deferreds.mapNotNull { it.await() }
        val fastest = results.minByOrNull { it.latencyMs }
        if (fastest != null) {
            cachedFastest.set(fastest.mirror to now) // 存 mirror 前缀
            fastest.mirror + targetUrl // 返回完整 wrapped URL
        } else {
            null
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 分钟
    }
}




