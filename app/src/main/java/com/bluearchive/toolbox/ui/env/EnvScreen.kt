package com.bluearchive.toolbox.ui.env

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluearchive.toolbox.core.env.EnvState
import com.bluearchive.toolbox.core.env.EnvironmentDetector
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.common.CardTitle
import com.bluearchive.toolbox.ui.common.SectionCard
import com.bluearchive.toolbox.ui.common.StatusLevel
import com.bluearchive.toolbox.ui.common.StatusRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvScreen(
    vm: ToolboxViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val env by vm.env.collectAsState()
    val loading by vm.envLoading.collectAsState()
    val install by vm.shizukuInstall.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEnv()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境检测", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
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

            SectionCard {
                CardTitle(Icons.Filled.Security, "系统与游戏")
                Spacer(Modifier.height(4.dp))
                StatusRow(
                    "设备",
                    "${env?.device?.manufacturer ?: "—"} ${env?.device?.model ?: "—"}",
                    StatusLevel.INFO,
                )
                StatusRow(
                    "系统",
                    "${env?.device?.romType?.displayName ?: "—"} ${env?.device?.romVersion ?: "—"} · Android ${env?.device?.androidRelease ?: "—"} (API ${env?.device?.sdkInt ?: "—"})",
                    StatusLevel.INFO,
                )
                GameRow(env, EnvironmentDetector.GAME_OFFICIAL, "日服官方客户端", "未安装（替换汉化需先装原版）")
                GameRow(env, EnvironmentDetector.GAME_CAFE, "咖啡厅汉化客户端", "未安装")
                val rootLevel = if (env?.rootAvailable == true) StatusLevel.WARN else StatusLevel.INFO
                StatusRow(
                    "Root",
                    if (env?.rootAvailable == true) "已获取（注意反作弊风险）" else "未获取",
                    rootLevel,
                )
                env?.let { RomTips(it) }
            }

            // —— Shizuku ——
            SectionCard {
                CardTitle(Icons.Filled.Security, "Shizuku 权限")
                Spacer(Modifier.height(4.dp))
                ShizukuStatusRow(env, loading)

                val e = env
                when {
                    e == null || loading -> {
                        Text("正在检测…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    !e.shizukuInstalled -> {
                        Text(
                            "未安装 Shizuku。安卓 13 及以上机型必须借助它才能写入游戏目录与静默安装 APKS。",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        when (val ui = install) {
                            is ToolboxViewModel.ShizukuInstallUi.Downloading -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("正在下载官方 Shizuku… ${ui.percent}%", fontSize = 16.sp)
                                }
                            }
                            is ToolboxViewModel.ShizukuInstallUi.Error -> {
                                Text(ui.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                InstallShizukuButton(vm)
                            }
                            else -> InstallShizukuButton(vm)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenGuide,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("查看安装与激活图文向导", fontSize = 16.sp) }
                    }
                    e.shizukuInstalled && !e.shizukuRunning -> {
                        Text(
                            "Shizuku 已安装，但服务未激活（手机重启后需重新激活）。",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.openShizukuApp() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("打开 Shizuku 去激活", fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenGuide,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("查看激活图文向导", fontSize = 16.sp) }
                    }
                    e.shizukuRunning && !e.shizukuAuthorized -> {
                        Text("Shizuku 已运行，请授权本工具箱使用。", fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.requestShizukuPermissionFromButton() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("授予工具箱 Shizuku 权限", fontSize = 16.sp)
                        }
                    }
                    e.shizukuAuthorized -> {
                        Text(
                            "已授权，全部自动化功能可用（写入游戏目录、安装分包、卸载与强制停止）。",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // —— SAI ——
            SectionCard {
                CardTitle(Icons.Filled.Download, "SAI 分包安装器（备选）")
                Spacer(Modifier.height(4.dp))
                StatusRow(
                    "SAI",
                    if (env?.saiInstalled == true) "已安装" else "未安装",
                    if (env?.saiInstalled == true) StatusLevel.OK else StatusLevel.WARN,
                )
                Text(
                    "不使用 Shizuku 时，可由 SAI 手动安装 APKS 分包。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Aefyr/SAI/releases"),
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开 SAI 官方下载页", fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = { vm.refreshEnv() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新检测", fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InstallShizukuButton(vm: ToolboxViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { vm.openShizukuOfficialSite() },
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("官网下载", fontSize = 15.sp)
        }
        Button(
            onClick = { vm.installBuiltInShizuku() },
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("本地安装包", fontSize = 15.sp)
        }
    }
}

@Composable
private fun GameRow(env: EnvState?, pkg: String, label: String, missingText: String) {
    val state = when (pkg) {
        com.bluearchive.toolbox.core.env.EnvironmentDetector.GAME_OFFICIAL -> env?.gameOfficial
        else -> env?.gameCafe
    }
    StatusRow(
        label = label,
        value = when {
            state == null -> "检测中…"
            state.installed -> "已安装 ${state.versionName.orEmpty()}".trim()
            else -> missingText
        },
        level = if (state?.installed == true) StatusLevel.OK else StatusLevel.WARN,
    )
}

@Composable
private fun ShizukuStatusRow(env: EnvState?, loading: Boolean) {
    val (text, level) = when {
        loading || env == null -> "检测中…" to StatusLevel.INFO
        !env.shizukuInstalled -> "未安装" to StatusLevel.WARN
        !env.shizukuRunning -> "已安装 · 服务未激活" to StatusLevel.ERROR
        !env.shizukuAuthorized -> "已激活 · 等待授权" to StatusLevel.WARN
        else -> "已授权（v${env.shizukuVersion}）" to StatusLevel.OK
    }
    StatusRow("Shizuku 状态", text, level)
}

/**
 * 按机型 / ROM 显示专属注意事项，自适应 Shizuku 激活与安装流程。
 */
@Composable
private fun RomTips(env: EnvState) {
    val f = env.device.features
    val tips = buildList<String> {
        if (env.device.romType == com.bluearchive.toolbox.core.device.RomType.MIUI) {
            add("当前为 MIUI / 澎湃 OS：还需在开发者选项中开启「USB 调试(安全设置)」，并关闭「安装监控 / 系统优化」。")
        }
        if (env.device.romType == com.bluearchive.toolbox.core.device.RomType.COLOR_OS) {
            add("当前为 ColorOS / realme：安装 APKS 时若被拦截，需在设置中允许「未知来源安装」并关闭安全验证。")
        }
        if (env.device.romType == com.bluearchive.toolbox.core.device.RomType.HARMONY) {
            add("当前为鸿蒙 HarmonyOS：无线调试入口通常在 设置 → 系统和更新 → 开发人员选项；如无「无线调试」请使用电脑 ADB 激活。")
        }
        if (!f.supportsWirelessDebug) {
            add("当前安卓版本低于 11，不支持无线调试，只能通过电脑 ADB 激活 Shizuku。")
        }
        if (f.wirelessDebugKilledWhenScreenOff) {
            add("该 ROM 息屏后可能中断无线调试，建议把 Shizuku 加入后台保护名单并关闭省电限制。")
        }
        if (env.rootAvailable) {
            add("检测到 Root：日服反作弊可能触发「Use of unauthorized apps」闪退，请隐藏 Root 或改用原版方案。")
        }
    }
    if (tips.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp),
    ) {
        Text("机型适配提示", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.height(4.dp))
        tips.forEachIndexed { i, tip ->
            Text("${i + 1}. $tip", fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}
