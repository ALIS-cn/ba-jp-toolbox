package com.bluearchive.toolbox.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.shizuku.ShizukuActivationSteps
import com.bluearchive.toolbox.ui.shizuku.ShizukuTipsCard

/**
 * 新手引导 · Shizuku 就绪检查。
 *
 * 不分页，单页按实时状态推进：安装 → 启动服务 → 授权，完成后 [onReady] 进入下一步。
 * 从安装器/Shizuku 应用返回时（ON_RESUME）自动刷新状态，按钮会自行变化。
 *
 * @param novice true=新手路径，默认展开图文激活教程；false=熟练路径，教程折叠按需展开
 */
@Composable
fun OnboardingSetupStep(
    vm: ToolboxViewModel,
    novice: Boolean,
    onReady: () -> Unit,
) {
    val env by vm.env.collectAsState()
    val installUi by vm.shizukuInstall.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEnv()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installed = env?.shizukuInstalled == true
    val running = env?.shizukuRunning == true
    val authorized = env?.shizukuAuthorized == true

    // 熟练用户默认收起教程；新手默认展开，且在未运行阶段始终展示
    var showTutorial by remember { mutableStateOf(novice) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (novice) "第 2 步：准备 Shizuku" else "准备 Shizuku",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        // 三段进度指示
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressDot(1, "安装", installed, Modifier.weight(1f))
            ProgressDot(2, "启动", running, Modifier.weight(1f))
            ProgressDot(3, "授权", authorized, Modifier.weight(1f))
        }

        // 状态主体
        when {
            authorized -> {
                StatusBanner(
                    backgroundColor = Color(0xFFE6F4EA),
                    foregroundColor = Color(0xFF1B5E20),
                    text = "Shizuku 已激活并授权，全部功能已解锁。",
                )
                Button(
                    onClick = onReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("下一步：选择汉化方式", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }

            !installed -> {
                StatusBanner(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    foregroundColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "手机上还没有安装 Shizuku。工具箱内置了官方安装包，点一下即可安装（约 2MB）。",
                )
                Button(
                    onClick = { vm.installBuiltInShizuku() },
                    enabled = installUi !is ToolboxViewModel.ShizukuInstallUi.Downloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (installUi is ToolboxViewModel.ShizukuInstallUi.Downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在准备安装包…", fontSize = 17.sp)
                    } else {
                        Text("一键安装 Shizuku", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { vm.openShizukuOfficialSite() }, modifier = Modifier.fillMaxWidth()) {
                    Text("内置包安装失败？去 Shizuku 官网手动下载", fontSize = 14.sp)
                }
                if (installUi is ToolboxViewModel.ShizukuInstallUi.Error) {
                    Text(
                        (installUi as ToolboxViewModel.ShizukuInstallUi.Error).message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "安装完成后系统会回到本页面，状态会自动更新。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            !running -> {
                StatusBanner(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    foregroundColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "Shizuku 已安装，但服务还没启动。需要在 Shizuku 应用里通过无线调试（或电脑 ADB）激活一次。",
                )
                Button(
                    onClick = { vm.openShizukuApp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("打开 Shizuku 去启动", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(22.dp))
                }

                // 教程展开开关（新手默认展开）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTutorial = !showTutorial }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (showTutorial) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (showTutorial) "收起图文激活教程" else "不知道怎么激活？查看图文教程",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                AnimatedVisibility(visible = showTutorial) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShizukuActivationSteps(vm = vm)
                        ShizukuTipsCard()
                    }
                }
            }

            else -> {
                // 已运行、未授权
                StatusBanner(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    foregroundColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = "Shizuku 已在运行，最后一步：授权工具箱使用它。点击后在系统弹窗中选择「允许」。",
                )
                Button(
                    onClick = { vm.requestShizukuPermissionFromButton() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("授予工具箱权限", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProgressDot(step: Int, label: String, done: Boolean, modifier: Modifier = Modifier) {
    val color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text(step.toString(), color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, color = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBanner(backgroundColor: Color, foregroundColor: Color, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .padding(16.dp),
    ) {
        Text(text, fontSize = 15.sp, lineHeight = 23.sp, color = foregroundColor)
    }
}
