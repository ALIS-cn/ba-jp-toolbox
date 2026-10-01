package com.bluearchive.toolbox.ui.install

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluearchive.toolbox.core.install.ClientInstallRepository
import com.bluearchive.toolbox.ui.ToolboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientInstallScreen(
    vm: ToolboxViewModel,
    onBack: () -> Unit,
) {
    val env by vm.env.collectAsState()
    val install by vm.install.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var uninstallOfficial by remember { mutableStateOf(true) }
    var migrateResources by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEnv()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shizukuOk = env?.shizukuAuthorized == true
    val isRunning = install is ToolboxViewModel.InstallUi.Running

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            vm.installFromLocalFile(uri, uninstallOfficial, migrateResources)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安装汉化客户端", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
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

            // 说明
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
            ) {
                Text("完整汉化方案", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "下载并安装咖啡厅汉化版客户端（包名 cafe.YostarJP.BlueArchive），相比文件替换提供：\n" +
                        "✓ 完整文本 + 公告汉化\n" +
                        "✓ 中文字体修复、主线中配\n" +
                        "✓ 图像/视频汉化、控制面板\n" +
                        "✓ 登录加速\n\n" +
                        "由于签名不同，需先卸载官方原版客户端。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            // 安装方式提示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (shizukuOk) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.primaryContainer)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (shizukuOk) Icons.Filled.Verified else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (shizukuOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (shizukuOk) "Shizuku 已授权：将静默安装，无需手动确认" else "未检测到 Shizuku：将使用系统安装器，需手动确认安装弹窗",
                        fontSize = 14.sp,
                        color = if (shizukuOk) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 选项
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uninstallOfficial, onCheckedChange = { uninstallOfficial = it }, enabled = !isRunning)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("安装前卸载官方原版", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("包名冲突，必须卸载才能安装汉化版", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = migrateResources, onCheckedChange = { migrateResources = it }, enabled = !isRunning)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("迁移游戏资源到汉化版", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("避免重新下载数 GB 资源（需 Shizuku 或存储权限）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 执行区
            when (val ui = install) {
                is ToolboxViewModel.InstallUi.Idle, is ToolboxViewModel.InstallUi.Finished -> {
                    if (ui is ToolboxViewModel.InstallUi.Finished) {
                        InstallResultBanner(
                            ui.result,
                            onPickLocalFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Button(
                        onClick = { vm.startInstall(uninstallOfficial, migrateResources) },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("一键下载并安装汉化客户端", fontSize = 17.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.installWithSai() },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("用 SAI 安装（兜底）", fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.installBuiltInSai() },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.GetApp, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (vm.isSaiInstalled()) "重新安装内置 SAI" else "安装内置 SAI（v4.5）",
                            fontSize = 16.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("从本地文件安装（.apks/.zip）", fontSize = 16.sp)
                    }
                }
                is ToolboxViewModel.InstallUi.Running -> {
                    InstallProgressCard(ui.progress)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.cancelInstall() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("取消操作", fontSize = 16.sp)
                    }
                }
            }

            // 注意事项
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重要提醒", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. 卸载前请务必在游戏内发行引继码并绑定 Yostar 账号，否则账号可能丢失。\n" +
                        "2. 汉化版包名与官方版不同，两者不能共存。\n" +
                        "3. 日服需日本节点加速器才能登录与更新。\n" +
                        "4. 安装后首次启动会校验资源，若异常可在标题界面「检查资源完整性」。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InstallResultBanner(
    result: ClientInstallRepository.Result,
    onPickLocalFile: () -> Unit = {},
) {
    val ok = result.success
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (ok) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (result.pendingUserConfirmation) "等待确认" else if (ok) "安装成功" else "安装失败",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(result.message, fontSize = 14.sp, lineHeight = 20.sp)
        if (!ok) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPickLocalFile,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("选本地文件", fontSize = 14.sp) }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(com.bluearchive.toolbox.data.release.ReleaseRepository.CAFE_OFFICIAL_SITE),
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("去官网下载", fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun InstallProgressCard(progress: ClientInstallRepository.Progress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(progress.message.ifBlank { "处理中…" }, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    }
}
