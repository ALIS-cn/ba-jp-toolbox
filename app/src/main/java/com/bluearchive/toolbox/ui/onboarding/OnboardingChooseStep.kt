package com.bluearchive.toolbox.ui.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 新手引导 · 选择汉化方式。Shizuku 就绪后两条路径汇合到本页。
 */
@Composable
fun OnboardingChooseStep(
    onChooseClientInstall: () -> Unit,
    onChooseFilePatch: () -> Unit,
    onEnterHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 成功标识
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6F4EA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("环境准备完成", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            "最后一步，选择你想用的汉化方式。两种方式使用的都是「蔚蓝咖啡厅」的公开汉化资源，可以随时切换。",
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // 方式一：一键安装客户端（推荐）
        MethodCard(
            icon = Icons.Filled.Download,
            title = "一键安装汉化客户端",
            recommended = true,
            description = "下载并安装咖啡厅制作的独立汉化版 App（与官方版共存），自动迁移已下载的游戏资源，省去重新下载数 GB。\n\n适合：大多数玩家。后续跟着客户端更新即可，最省心。",
            onClick = onChooseClientInstall,
        )

        // 方式二：替换游戏文件
        MethodCard(
            icon = Icons.Filled.SwapHoriz,
            title = "替换游戏文件汉化",
            recommended = false,
            description = "直接把汉化文本写入官方版游戏目录，不用装第二个 App。\n\n适合：想继续用官方客户端的玩家。注意：游戏每次更新版本后，需要重新执行一次替换；版本不匹配时工具会自动拦截。",
            onClick = onChooseFilePatch,
        )

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onEnterHome, modifier = Modifier.fillMaxWidth()) {
            Text("我先自己看看，进入首页", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    title: String,
    recommended: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (recommended) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (recommended) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (recommended) {
                        Text(
                            "推荐新手",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                description,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
