package com.bluearchive.toolbox.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bluearchive.toolbox.core.env.EnvState
import com.bluearchive.toolbox.core.env.EnvironmentDetector
import com.bluearchive.toolbox.core.shizuku.ShizukuBridge
import com.bluearchive.toolbox.data.prefs.AppPreferences
import com.bluearchive.toolbox.data.release.CafeResource
import com.bluearchive.toolbox.data.release.ReleaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.bluearchive.toolbox.core.install.ApkVerifier
import com.bluearchive.toolbox.core.install.ClientInstallRepository
import com.bluearchive.toolbox.core.patch.GameFileSystem
import com.bluearchive.toolbox.core.patch.PatchRepository

class ToolboxViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo = ReleaseRepository(prefs)
    private val envDetector = EnvironmentDetector(app)
    private val gameFs = GameFileSystem(app, prefs) { _env.value }
    private val patchRepo = PatchRepository(repo, gameFs)
    private val installRepo = ClientInstallRepository(app, repo, gameFs) { _env.value }

    // ---------- 守则 ----------

    private val _agreementVersion = MutableStateFlow<Int?>(null)
    val agreementVersion: StateFlow<Int?> = _agreementVersion.asStateFlow()

    /** null=尚未加载；false=首次使用未完成引导；true=已完成 */
    private val _onboardingDone = MutableStateFlow<Boolean?>(null)
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone.asStateFlow()

    init {
        viewModelScope.launch {
            _agreementVersion.value = prefs.disclaimerAcceptedVersion()
            _onboardingDone.value = prefs.onboardingCompleted()
        }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            prefs.setDisclaimerAccepted(AppPreferences.DISCLAIMER_VERSION)
            _agreementVersion.value = AppPreferences.DISCLAIMER_VERSION
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted()
            _onboardingDone.value = true
        }
    }

    // ---------- 环境检测 ----------

    private val _env = MutableStateFlow<EnvState?>(null)
    val env: StateFlow<EnvState?> = _env.asStateFlow()

    private val _envLoading = MutableStateFlow(false)
    val envLoading: StateFlow<Boolean> = _envLoading.asStateFlow()

    private val _candidates = MutableStateFlow<List<GameFileSystem.ChannelCandidate>>(emptyList())
    val candidates: StateFlow<List<GameFileSystem.ChannelCandidate>> = _candidates.asStateFlow()

    fun refreshEnv() {
        viewModelScope.launch {
            _envLoading.value = true
            try {
                _env.value = envDetector.detect()
                _candidates.value = gameFs.candidates()
            } finally {
                _envLoading.value = false
            }
        }
    }

    // ---------- 最新汉化资源 ----------

    sealed interface ReleaseUi {
        data object Idle : ReleaseUi
        data object Loading : ReleaseUi
        data class Loaded(val resource: CafeResource) : ReleaseUi
        data class Error(val message: String) : ReleaseUi
    }

    private val _release = MutableStateFlow<ReleaseUi>(ReleaseUi.Idle)
    val release: StateFlow<ReleaseUi> = _release.asStateFlow()

    /** 首页手动检查；启动汉化流程时一律 forceRefresh = true */
    fun checkLatest(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            _release.value = ReleaseUi.Loading
            val result = repo.fetchCafeLatest(forceRefresh = forceRefresh)
            _release.value = result.fold(
                onSuccess = { ReleaseUi.Loaded(it) },
                onFailure = {
                    ReleaseUi.Error(it.message ?: "检查更新失败，请检查网络")
                },
            )
        }
    }

    /**
     * 两个汉化入口共用：每次都强制确认最新资源，确认后返回资源信息（下一阶段接下载/替换流程）。
     */
    suspend fun ensureLatestOrError(): CafeResource? {
        _release.value = ReleaseUi.Loading
        return repo.fetchCafeLatest(forceRefresh = true).fold(
            onSuccess = {
                _release.value = ReleaseUi.Loaded(it)
                it
            },
            onFailure = {
                _release.value = ReleaseUi.Error(it.message ?: "检查更新失败")
                null
            },
        )
    }

    // ---------- Shizuku 授权 ----------

    suspend fun requestShizukuPermission(): Boolean {
        val granted = ShizukuBridge.requestPermission()
        refreshEnv()
        return granted
    }

    /** 供按钮直接调用：发起授权后自动刷新环境状态 */
    fun requestShizukuPermissionFromButton() {
        viewModelScope.launch { requestShizukuPermission() }
    }

    // ---------- Shizuku 安装 ----------

    sealed interface ShizukuInstallUi {
        data object Idle : ShizukuInstallUi
        data class Downloading(val percent: Int) : ShizukuInstallUi
        data class Error(val message: String) : ShizukuInstallUi
    }

    private val _shizukuInstall = MutableStateFlow<ShizukuInstallUi>(ShizukuInstallUi.Idle)
    val shizukuInstall: StateFlow<ShizukuInstallUi> = _shizukuInstall.asStateFlow()

    fun resetShizukuInstallState() {
        _shizukuInstall.value = ShizukuInstallUi.Idle
    }

    /**
     * 安装 Shizuku：优先使用 assets 内置的 shizuku.apk，没有则通过国内镜像下载最新版。
     * 下载失败时 UI 会提供「去官网手动下载」入口。
     */
    fun downloadAndInstallShizuku() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _shizukuInstall.value = ShizukuInstallUi.Downloading(0)

            // 1. 优先使用 assets 内置的 shizuku.apk
            val builtIn = copyShizukuFromAssets(context)
            val apkFile: File = if (builtIn != null) {
                _shizukuInstall.value = ShizukuInstallUi.Downloading(100)
                builtIn
            } else {
                // 2. assets 没有 → 通过国内镜像下载最新版
                val asset = repo.fetchLatestShizukuApk().getOrElse {
                    _shizukuInstall.value = ShizukuInstallUi.Error("获取下载地址失败：${it.message}")
                    return@launch
                }
                val dest = File(context.cacheDir, "shizuku-latest.apk")
                val ok = repo.downloadWithMirrors(asset.browserDownloadUrl, dest) { downloaded, total ->
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    _shizukuInstall.value = ShizukuInstallUi.Downloading(percent.coerceIn(0, 99))
                }.isSuccess
                if (!ok) {
                    _shizukuInstall.value = ShizukuInstallUi.Error("下载失败，可点击下方按钮去官网手动下载")
                    return@launch
                }
                dest
            }

            // 3. 校验包名+官方签名固定后，拉起系统安装器
            verifyAndLaunchShizukuInstaller(context, apkFile)
        }
    }

    /**
     * 直接使用内置的 Shizuku 安装包安装（不下载）。
     * 若 assets 中没有内置 APK，则回退到 downloadAndInstallShizuku。
     */
    fun installBuiltInShizuku() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _shizukuInstall.value = ShizukuInstallUi.Downloading(80)
            val apkFile = copyShizukuFromAssets(context)
            if (apkFile == null) {
                _shizukuInstall.value = ShizukuInstallUi.Error("未找到内置安装包，已切换为在线下载")
                downloadAndInstallShizuku()
                return@launch
            }
            verifyAndLaunchShizukuInstaller(context, apkFile)
        }
    }

    /** 校验 Shizuku APK（包名 + 官方签名证书固定），通过后拉起系统安装器 */
    private suspend fun verifyAndLaunchShizukuInstaller(context: Context, apkFile: File) {
        val verify = withContext(Dispatchers.IO) {
            ApkVerifier.verify(
                context, apkFile,
                ApkVerifier.SHIZUKU_PACKAGE, ApkVerifier.SHIZUKU_CERT_SHA256,
            )
        }
        if (!verify.ok) {
            _shizukuInstall.value = ShizukuInstallUi.Error(verify.message)
            return
        }
        _shizukuInstall.value = ShizukuInstallUi.Idle
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    /** 从 assets 复制内置的 shizuku.apk 到缓存目录；不存在返回 null */
    private fun copyShizukuFromAssets(context: Context): File? {
        return try {
            val input = context.assets.open("shizuku.apk")
            val dest = File(context.cacheDir, "shizuku-builtin.apk")
            input.use { src ->
                dest.outputStream().use { dst -> src.copyTo(dst) }
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    /** 打开 Shizuku 官网下载页 */
    fun openShizukuOfficialSite() {
        val context = getApplication<Application>()
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(com.bluearchive.toolbox.data.release.ReleaseRepository.SHIZUKU_OFFICIAL_SITE),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    fun openShizukuApp() {
        ShizukuBridge.openShizukuApp(getApplication())
    }

    // ---------- 文件替换汉化（流程 B） ----------

    sealed interface PatchUi {
        data object Idle : PatchUi
        data class Running(val progress: PatchRepository.Progress) : PatchUi
        data class Finished(val result: PatchRepository.PatchResult) : PatchUi
    }

    private val _patch = MutableStateFlow<PatchUi>(PatchUi.Idle)
    val patch: StateFlow<PatchUi> = _patch.asStateFlow()

    fun resetPatch() {
        _patch.value = PatchUi.Idle
    }

    private var patchJob: kotlinx.coroutines.Job? = null
    private var installJob: kotlinx.coroutines.Job? = null

    /** 执行文件替换汉化；需 Shizuku 已授权，调用方应先检查 env */
    fun startPatch() {
        patchJob?.cancel()
        patchJob = viewModelScope.launch {
            try {
                _patch.value = PatchUi.Running(PatchRepository.Progress(PatchRepository.Step.CheckLatest))
                val result = patchRepo.run { progress ->
                    _patch.value = PatchUi.Running(progress)
                }
                _patch.value = PatchUi.Finished(result)
            } catch (e: Exception) {
                _patch.value = PatchUi.Finished(
                    PatchRepository.PatchResult(false, if (e is kotlinx.coroutines.CancellationException) "已取消" else e.message ?: "发生错误")
                )
            }
        }
    }

    /** 取消正在执行的文件替换汉化 */
    fun cancelPatch() {
        repo.cancelDownload()
        patchJob?.cancel()
        patchJob = null
    }

    /** 用备份还原为原版日文 */
    fun restoreOriginal() {
        viewModelScope.launch {
            _patch.value = PatchUi.Running(PatchRepository.Progress(PatchRepository.Step.Backup, message = "还原中…"))
            val result = patchRepo.restore()
            _patch.value = PatchUi.Finished(result)
        }
    }

    // ---------- 文件通道 / 权限 ----------

    /** 当前可用的文件操作通道候选列表（供 UI 展示） */
    fun channelCandidates(): List<GameFileSystem.ChannelCandidate> = _candidates.value

    /** 当前是否已有任一可用通道 */
    fun hasAnyChannel(): Boolean = _candidates.value.any { it.available }

    /** 存储权限申请结果回调 */
    fun onStoragePermissionResult() {
        refreshEnv()
    }

    /**
     * SAF 目录选择结果：持久化 URI 权限并保存。
     * 调用方需已通过 contentResolver.takePersistableUriPermission 授权。
     */
    fun onSafTreeSelected(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                prefs.saveSafTreeUri(uri.toString())
            } catch (_: Exception) {
                // 某些 ROM 不支持持久化，仍保存 URI 尝试使用
                prefs.saveSafTreeUri(uri.toString())
            }
            refreshEnv()
        }
    }

    // ---------- 汉化客户端安装（流程 A） ----------

    sealed interface InstallUi {
        data object Idle : InstallUi
        data class Running(val progress: ClientInstallRepository.Progress) : InstallUi
        data class Finished(val result: ClientInstallRepository.Result) : InstallUi
    }

    private val _install = MutableStateFlow<InstallUi>(InstallUi.Idle)
    val install: StateFlow<InstallUi> = _install.asStateFlow()

    fun resetInstall() {
        _install.value = InstallUi.Idle
    }

    fun startInstall(
        uninstallOfficial: Boolean = true,
        migrateResources: Boolean = true,
    ) {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            try {
                _install.value = InstallUi.Running(ClientInstallRepository.Progress(ClientInstallRepository.Step.Check))
                val result = installRepo.run(
                    ClientInstallRepository.Options(uninstallOfficial, migrateResources),
                ) { progress ->
                    _install.value = InstallUi.Running(progress)
                }
                _install.value = InstallUi.Finished(result)
            } catch (_: kotlinx.coroutines.CancellationException) {
                _install.value = InstallUi.Finished(
                    ClientInstallRepository.Result(false, "已取消")
                )
            }
        }
    }

    /** 取消正在执行的安装 */
    fun cancelInstall() {
        repo.cancelDownload()
        installJob?.cancel()
        installJob = null
    }

    fun installWithSai() {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            _install.value = InstallUi.Running(ClientInstallRepository.Progress(ClientInstallRepository.Step.Download))
            val result = installRepo.installWithSai()
            _install.value = InstallUi.Finished(result)
        }
    }

    /** 安装内置的 SAI（从 assets 提取） */
    fun installBuiltInSai() {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            _install.value = InstallUi.Running(ClientInstallRepository.Progress(ClientInstallRepository.Step.Install, 0f, "安装内置 SAI…"))
            val result = installRepo.installBuiltInSai()
            _install.value = InstallUi.Finished(result)
        }
    }

    /** 检查 SAI 是否已安装 */
    fun isSaiInstalled(): Boolean = installRepo.isSaiInstalled()

    /** 从本地 APKS 文件安装（用户手动选择） */
    fun installFromLocalFile(
        uri: android.net.Uri,
        uninstallOfficial: Boolean = true,
        migrateResources: Boolean = true,
    ) {
        installJob?.cancel()
        installJob = viewModelScope.launch {
            try {
                _install.value = InstallUi.Running(ClientInstallRepository.Progress(ClientInstallRepository.Step.Check))
                val result = installRepo.installFromLocalUri(
                    uri,
                    ClientInstallRepository.Options(uninstallOfficial, migrateResources),
                ) { progress ->
                    _install.value = InstallUi.Running(progress)
                }
                _install.value = InstallUi.Finished(result)
            } catch (_: kotlinx.coroutines.CancellationException) {
                _install.value = InstallUi.Finished(
                    ClientInstallRepository.Result(false, "已取消")
                )
            }
        }
    }
}
