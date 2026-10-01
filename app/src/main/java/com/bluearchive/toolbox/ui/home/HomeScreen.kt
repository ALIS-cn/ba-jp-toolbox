package com.bluearchive.toolbox.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.bluearchive.toolbox.core.env.EnvState
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.common.CardTitle
import java.io.File
import com.bluearchive.toolbox.ui.common.SectionCard
import com.bluearchive.toolbox.ui.common.StatusLevel
import com.bluearchive.toolbox.ui.common.StatusRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: ToolboxViewModel,
    onOpenEnv: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenFilePatch: () -> Unit,
    onOpenClientInstall: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenDisclaimer: () -> Unit,
) {
    val env by vm.env.collectAsState()
    val envLoading by vm.envLoading.collectAsState()
    val release by vm.release.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLogDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshEnv()
        vm.checkLatest(forceRefresh = false)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("BA日服工具箱", fontWeight = FontWeight.SemiBold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // —— 环境状态 ——
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardTitle(Icons.Filled.Security, "运行环境")
                    Text(
                        text = envSummary(env, envLoading),
                        fontSize = 15.sp,
                        color = if (env?.shizukuAuthorized == true)
                            ColorSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "检测游戏安装情况与 Shizuku 权限状态，使用前建议先完成环境准备。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenEnv,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("环境检测 / Shizuku 激活向导", fontSize = 17.sp)
                }
            }

            // —— 最新汉化资源 ——
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(Icons.Filled.Verified, "最新汉化资源")
                }
                Spacer(Modifier.height(8.dp))
                when (val ui = release) {
                    is ToolboxViewModel.ReleaseUi.Idle -> Text(
                        "尚未检查，点击下方按钮检查最新版本。",
                        fontSize = 15.sp,
                    )
                    is ToolboxViewModel.ReleaseUi.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在检查最新汉化资源…", fontSize = 15.sp)
                    }
                    is ToolboxViewModel.ReleaseUi.Error -> Text(
                        ui.message,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    is ToolboxViewModel.ReleaseUi.Loaded -> {
                        val r = ui.resource
                        StatusRow("资源版本", r.tag, StatusLevel.OK)
                        StatusRow("文件", r.assetName, StatusLevel.INFO)
                        StatusRow("大小", formatMB(r.sizeBytes), StatusLevel.INFO)
                        StatusRow("发布日期", formatDate(r.publishedAt), StatusLevel.INFO)
                        StatusRow(
                            "来源",
                            if (r.fromCache) "离线缓存（不保证最新）" else "GitHub 最新",
                            if (r.fromCache) StatusLevel.WARN else StatusLevel.OK,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "每次执行汉化前都会强制联网检查，确保使用最新资源。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { vm.checkLatest(forceRefresh = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("立即检查更新", fontSize = 16.sp)
                }
            }

            // —— 功能入口 ——
            FeatureCard(
                icon = Icons.Filled.InstallMobile,
                title = "一键安装汉化客户端",
                desc = "下载咖啡厅汉化版 APKS 并引导安装（完整汉化：文本/公告/中配/图像）。需卸载原版，自动迁移游戏资源。",
                buttonText = "一键安装汉化客户端",
                enabled = true,
                onClick = onOpenClientInstall,
            )

            FeatureCard(
                icon = Icons.Filled.FolderCopy,
                title = "替换游戏文件汉化",
                desc = "保留官方原版客户端，下载最新 TableBundles.zip 替换文本表（仅文本，应急方案），支持备份与一键还原。",
                buttonText = "开始替换",
                enabled = true,
                onClick = onOpenFilePatch,
            )

            // —— 下载官方日服客户端 ——
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(Icons.Filled.Download, "下载官方日服客户端")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "未安装官方原版时，可通过以下渠道下载（国内网络可达）。汉化需要先有官方客户端。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OfficialDownloadChannels(
                    onOpen = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                )
            }

            // —— 底部 ——
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) { "—" }
            }
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.clickable { onOpenChangelog }) {
                        Text("版本", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "v$versionName",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onOpenChangelog) {
                        Text("更新日志", fontSize = 14.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDisclaimer() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("查看用户协议与免责声明", fontSize = 15.sp)
                }
            }

            Text(
                "汉化资源由「蔚蓝咖啡厅」bluearchive.cafe 提供 · Shizuku (GPL-3.0) RikkaApps",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // —— 底部工具 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showLogDialog = true }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.FolderCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "导出运行日志",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // —— 日志导出对话框 ——
        if (showLogDialog) {
            val logText = remember(showLogDialog) { com.bluearchive.toolbox.core.log.LogCollector.getLogsText() }
            val scrollState = rememberScrollState()
            AlertDialog(
                onDismissRequest = { showLogDialog = false },
                title = { Text("运行日志") },
                text = {
                    Column {
                        Text(
                            "日志用于排查问题，可复制文字或保存为文件后发给开发者。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .verticalScroll(scrollState),
                        ) {
                            Text(
                                text = logText.ifEmpty { "（暂无日志）" },
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            // 复制到剪贴板
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("BA日服工具箱日志", logText)
                            clipboard.setPrimaryClip(clip)
                            showLogDialog = false
                        }) { Text("复制文字") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            // 保存到文件
                            val file = File(context.getExternalFilesDir(null), "ba-toolbox-log.txt")
                            file.writeText(logText)
                            showLogDialog = false
                        }) { Text("保存文件") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogDialog = false }) { Text("关闭") }
                },
            )
        }
    }
}

private data class DownloadChannel(
    val name: String,
    val desc: String,
    val url: String,
    val badge: String? = null,
)

private val OFFICIAL_CHANNELS = listOf(
    DownloadChannel(
        name = "biubiu 加速器",
        desc = "加速器内置日服安装包，国内直连下载，蔚蓝档案日服加速免费",
        url = "https://www.biubiu001.com/",
        badge = "加速免费",
    ),
    DownloadChannel(
        name = "QooApp",
        desc = "外服游戏专用平台，更新快，支持日服 APK/XAPK 下载",
        url = "https://apps.qoo-app.com/en/app/12252",
    ),
    DownloadChannel(
        name = "APKPure",
        desc = "老牌 APK 站，提供 XAPK 含数据包，国内基本可达",
        url = "https://apkpure.net/blue-archive/com.YostarJP.BlueArchive",
    ),
    DownloadChannel(
        name = "迅游手游加速器",
        desc = "加速器内置日服安装包，国内直连，BA 日服加速免费",
        url = "https://www.xunyou.com/",
        badge = "加速免费",
    ),
)

@Composable
private fun OfficialDownloadChannels(onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OFFICIAL_CHANNELS.forEach { ch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(ch.url) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (ch.badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ch.badge,
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE6F4EA))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(ch.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SectionCard {
        CardTitle(icon, title)
        Spacer(Modifier.height(8.dp))
        Text(
            desc,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(buttonText, fontSize = 17.sp)
            if (!enabled) {
                Spacer(Modifier.width(8.dp))
                Text("· 下一阶段", fontSize = 13.sp)
            }
        }
    }
}

private val ColorSafe = androidx.compose.ui.graphics.Color(0xFF2E7D32)

private fun envSummary(env: EnvState?, loading: Boolean): String = when {
    loading || env == null -> "检测中…"
    env.shizukuAuthorized -> "Shizuku 已授权"
    env.shizukuRunning -> "Shizuku 待授权"
    env.shizukuInstalled -> "Shizuku 未激活"
    else -> "未安装 Shizuku"
}

private fun formatMB(bytes: Long): String =
    if (bytes <= 0) "—" else String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

private fun formatDate(iso: String): String =
    try {
        if (iso.length < 10) iso else iso.substring(0, 10)
    } catch (_: Exception) {
        iso
    }
