package com.bluearchive.toolbox.ui.patch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
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
import com.bluearchive.toolbox.core.patch.PatchRepository
import com.bluearchive.toolbox.ui.ToolboxViewModel
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePatchScreen(
    vm: ToolboxViewModel,
    onBack: () -> Unit,
    onOpenEnv: () -> Unit,
) {
    val env by vm.env.collectAsState()
    val patch by vm.patch.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEnv()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val gameInstalled = env?.gameOfficial?.installed == true
    val candidates = vm.channelCandidates()
    val hasChannel = candidates.any { it.available }
    val canPatch = gameInstalled && hasChannel && patch !is ToolboxViewModel.PatchUi.Running

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.onStoragePermissionResult() }

    val safTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) vm.onSafTreeSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件替换汉化", fontWeight = FontWeight.SemiBold) },
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
                Text("工作原理", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "保留官方原版客户端，下载最新 TableBundles.zip 替换游戏文本数据表。\n" +
                        "• 仅文本汉化，游戏内字体显示可能异常\n" +
                        "• 不支持公告汉化、中配、图像视频汉化、控制面板\n" +
                        "• 自动备份原版文本，可一键还原\n" +
                        "• 每次执行前强制联网检查最新资源",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            // 前置条件
            ConditionItem("日服官方客户端已安装", gameInstalled) {
                Text("请先从 Google Play 或加速器下载官方版（com.YostarJP.BlueArchive）。", fontSize = 13.sp)
            }

            // 文件操作通道（按安卓版本自动适配）
            ChannelCard(
                candidates = candidates,
                onRequestStorage = {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                },
                onPickSafDir = { safTreeLauncher.launch(null) },
                onOpenEnv = onOpenEnv,
            )

            // 执行区
            when (val ui = patch) {
                is ToolboxViewModel.PatchUi.Idle, is ToolboxViewModel.PatchUi.Finished -> {
                    if (ui is ToolboxViewModel.PatchUi.Finished) {
                        ResultBanner(ui.result)
                        Spacer(Modifier.height(4.dp))
                    }
                    Button(
                        onClick = { vm.startPatch() },
                        enabled = canPatch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (!gameInstalled) "未检测到官方客户端"
                            else if (!hasChannel) "需先完成文件通道授权"
                            else "开始替换汉化（自动备份原版）",
                            fontSize = 17.sp,
                        )
                    }
                    if (ui !is ToolboxViewModel.PatchUi.Finished || !ui.result.success) {
                        // 不显示还原按钮在成功刚完成时（避免误触），但 Idle/失败时显示
                        OutlinedButton(
                            onClick = { vm.restoreOriginal() },
                            enabled = hasChannel && patch !is ToolboxViewModel.PatchUi.Running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("还原为原版日文文本", fontSize = 16.sp)
                        }
                    }
                }
                is ToolboxViewModel.PatchUi.Running -> {
                    PatchProgressCard(ui.progress)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.cancelPatch() },
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("注意", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. 替换前请在游戏内发行引继码并绑定账号，以防万一。\n" +
                        "2. 游戏大版本更新后文本表可能失效，请重新执行本工具获取最新汉化资源。\n" +
                        "3. 若出现汉化异常，可点击「还原为原版日文文本」恢复。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConditionItem(label: String, ok: Boolean, extra: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Filled.Verified else Icons.Filled.Close,
                contentDescription = null,
                tint = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (ok) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!ok) {
            Spacer(Modifier.height(8.dp))
            extra()
        }
    }
}

@Composable
private fun ChannelCard(
    candidates: List<com.bluearchive.toolbox.core.patch.GameFileSystem.ChannelCandidate>,
    onRequestStorage: () -> Unit,
    onPickSafDir: () -> Unit,
    onOpenEnv: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text("文件操作通道（自动选择可用项）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "按你的安卓版本与权限状态，自动选择写入游戏目录的方式，任一可用即可。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        candidates.forEach { c ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    tint = if (c.available) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${c.name}${if (c.available) "" else "（${c.reason}）"}",
                        fontSize = 14.sp,
                        color = if (c.available) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!c.available) {
                    when (c.name) {
                        "Shizuku" -> OutlinedButton(
                            onClick = onOpenEnv,
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("去授权", fontSize = 13.sp) }
                        "存储权限" -> OutlinedButton(
                            onClick = onRequestStorage,
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("申请", fontSize = 13.sp) }
                        "SAF 目录授权" -> OutlinedButton(
                            onClick = onPickSafDir,
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("选择目录", fontSize = 13.sp) }
                    }
                }
            }
        }
        if (candidates.any { it.name == "SAF 目录授权" && !it.available }) {
            Spacer(Modifier.height(6.dp))
            Text(
                "提示：在系统文件选择器中依次进入 Android → data → com.YostarJP.BlueArchive → files，然后点击「使用此目录」。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultBanner(result: PatchRepository.PatchResult) {
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
                if (ok) "汉化成功" else "操作失败",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(result.message, fontSize = 14.sp, lineHeight = 20.sp)
        if (!ok && result.message.contains("下载", ignoreCase = true)) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(com.bluearchive.toolbox.data.release.ReleaseRepository.CAFE_OFFICIAL_SITE),
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("去咖啡厅官网手动下载", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun PatchProgressCard(progress: PatchRepository.Progress) {
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
        Spacer(Modifier.height(6.dp))
        Text("${progress.percent.toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
