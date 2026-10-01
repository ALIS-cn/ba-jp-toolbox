package com.bluearchive.toolbox.ui.shizuku

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluearchive.toolbox.core.device.RomType
import com.bluearchive.toolbox.core.device.SettingsNavigator
import com.bluearchive.toolbox.ui.ToolboxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuGuideScreen(
    vm: ToolboxViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val env by vm.env.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val authorized = env?.shizukuAuthorized == true

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
                title = { Text("Shizuku 激活向导", fontWeight = FontWeight.SemiBold) },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (authorized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE6F4EA))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Shizuku 已激活并授权，全部功能已解锁。",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1B5E20),
                    )
                }
            }

            val device = env?.device
            val supportsWifi = device?.features?.supportsWirelessDebug == true
            val rom = device?.romType

            if (supportsWifi) {
                GuideStep(
                    number = 1,
                    title = "开启「开发者选项」",
                    body = "进入 系统设置 → 关于手机（或 关于本机），连续点击「版本号」7 次，按提示输入锁屏密码即可开启开发者选项。",
                    actionText = "打开开发者选项设置",
                    onAction = { SettingsNavigator.openDeveloperOptions(context) },
                )

                val wifiBody = buildString {
                    append("在开发者选项中找到并打开「无线调试」开关（部分品牌显示为「通过 WLAN 调试」）。")
                    if (rom == RomType.MIUI) {
                        append("\n你的机型为 MIUI/澎湃 OS，还需额外打开「USB 调试（安全设置）」，并关闭「安装监控 / 系统优化」，否则 Shizuku 无法正常工作。")
                    }
                    if (rom == RomType.HARMONY) {
                        append("\n你的机型为鸿蒙系统，无线调试入口通常在 设置 → 系统和更新 → 开发人员选项 中。")
                    }
                    if (device?.features?.wirelessDebugKilledWhenScreenOff == true) {
                        append("\n该 ROM 息屏后可能中断无线调试，建议把 Shizuku 加入后台保护名单并关闭省电限制。")
                    }
                }
                GuideStep(
                    number = 2,
                    title = "打开「无线调试」",
                    body = wifiBody,
                    actionText = "前往开发者选项",
                    onAction = { SettingsNavigator.openDeveloperOptions(context) },
                )

                GuideStep(
                    number = 3,
                    title = "在 Shizuku 中通过无线调试启动",
                    body = "打开 Shizuku，点击「通过无线调试启动」，按系统弹窗提示完成配对（需要输入一次配对码，该弹窗由系统提供，任何应用都无法代填）。",
                    actionText = "打开 Shizuku",
                    onAction = { vm.openShizukuApp() },
                    enabled = env?.shizukuInstalled == true,
                )

                GuideStep(
                    number = 4,
                    title = "回到工具箱完成授权",
                    body = "Shizuku 激活成功后，点击下方按钮，在系统授权弹窗中选择允许。",
                    actionText = if (authorized) "已授权 ✓" else "我已激活，授予权限",
                    onAction = { vm.requestShizukuPermissionFromButton() },
                    enabled = env?.shizukuRunning == true && !authorized,
                )
            } else {
                // 安卓 < 11：不支持无线调试，直接引导 ADB
                GuideStep(
                    number = 1,
                    title = "开启「开发者选项」与「USB 调试」",
                    body = "进入 设置 → 关于手机，连续点击「版本号」7 次开启开发者选项；再进入开发者选项，打开「USB 调试」。",
                    actionText = "打开开发者选项",
                    onAction = { SettingsNavigator.openDeveloperOptions(context) },
                )
                GuideStep(
                    number = 2,
                    title = "用电脑执行 ADB 命令激活",
                    body = "用数据线连接电脑（需安装 ADB 工具），在电脑命令行执行：\n" +
                        "adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                        "执行成功后 Shizuku 即被激活。具体命令以 Shizuku App 内「通过 ADB 激活」页面显示为准。",
                    actionText = "打开 Shizuku",
                    onAction = { vm.openShizukuApp() },
                    enabled = env?.shizukuInstalled == true,
                )
                GuideStep(
                    number = 3,
                    title = "回到工具箱完成授权",
                    body = "激活成功后点击下方按钮，在授权弹窗中选择允许。",
                    actionText = if (authorized) "已授权 ✓" else "我已激活，授予权限",
                    onAction = { vm.requestShizukuPermissionFromButton() },
                    enabled = env?.shizukuRunning == true && !authorized,
                )
            }

            // 电脑 ADB 备选 / 补充
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
            ) {
                Text("补充说明", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. 无 Root 时，手机重启后 Shizuku 会失效，需要重新激活一次。\n" +
                        "2. 部分国产 ROM 息屏后会中断无线调试，请将 Shizuku 加入后台白名单、关闭省电限制。\n" +
                        "3. 安卓 11 以下机型仅支持电脑 ADB 激活。\n" +
                        "4. Shizuku 为 GPL-3.0 开源软件（© RikkaApps，github.com/RikkaApps/Shizuku），本工具未修改其二进制文件。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
            ) {
                Text("注意事项", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. 无 Root 时，手机重启后 Shizuku 会失效，回到第 3 步重新激活即可。\n" +
                        "2. 部分国产 ROM 息屏后会中断无线调试，请将 Shizuku 加入后台白名单、关闭省电限制。\n" +
                        "3. Shizuku 为 GPL-3.0 开源软件（© RikkaApps，github.com/RikkaApps/Shizuku），本工具未修改其二进制文件。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("完成", fontSize = 17.sp) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(actionText, fontSize = 15.sp)
        }
    }
}
