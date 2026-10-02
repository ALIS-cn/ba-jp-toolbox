package com.bluearchive.toolbox.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluearchive.toolbox.ui.ToolboxViewModel

/**
 * 引导结束后进入的目标页面。route 字符串须与 ui.nav.Routes 常量保持一致。
 */
enum class OnboardingTarget(val route: String) {
    HOME("home"),
    CLIENT_INSTALL("client_install"),
    FILE_PATCH("file_patch"),
}

private enum class Stage { WELCOME, INTRO, SETUP, CHOOSE }

/**
 * 首次使用新手引导：同意用户协议后展示，分「会用 Shizuku」与「不会」两条路径。
 *
 * - 会用：跳过科普，直接进入环境自检（安装/启动/授权缺什么补什么）
 * - 不会：先科普，再全程图文带着做
 * 两条路径在 Shizuku 就绪后汇合到「选择汉化方式」。
 */
@Composable
fun OnboardingScreen(
    vm: ToolboxViewModel,
    onEnterApp: (OnboardingTarget) -> Unit,
) {
    var stage by remember { mutableStateOf(Stage.WELCOME) }
    var novice by remember { mutableStateOf(false) }

    // 进入引导即获取一次环境状态，SETUP 页依赖它
    LaunchedEffect(Unit) { vm.refreshEnv() }

    // 系统返回键：向导内逐步回退，不在欢迎页退出应用
    BackHandler(enabled = stage != Stage.WELCOME) {
        stage = when (stage) {
            Stage.CHOOSE -> Stage.SETUP
            Stage.SETUP -> if (novice) Stage.INTRO else Stage.WELCOME
            Stage.INTRO -> Stage.WELCOME
            Stage.WELCOME -> Stage.WELCOME
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (stage != Stage.WELCOME) {
                WizardTopBar(
                    onBack = {
                        stage = when (stage) {
                            Stage.CHOOSE -> Stage.SETUP
                            Stage.SETUP -> if (novice) Stage.INTRO else Stage.WELCOME
                            else -> Stage.WELCOME
                        }
                    },
                    onSkip = { onEnterApp(OnboardingTarget.HOME) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (stage) {
                    Stage.WELCOME -> WelcomePane(
                        onKnowShizuku = {
                            novice = false
                            stage = Stage.SETUP
                        },
                        onNeedGuide = {
                            novice = true
                            stage = Stage.INTRO
                        },
                        onSkip = { onEnterApp(OnboardingTarget.HOME) },
                    )

                    Stage.INTRO -> IntroPane(onNext = { stage = Stage.SETUP })

                    Stage.SETUP -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        OnboardingSetupStep(
                            vm = vm,
                            novice = novice,
                            onReady = { stage = Stage.CHOOSE },
                        )
                    }

                    Stage.CHOOSE -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        OnboardingChooseStep(
                            onChooseClientInstall = { onEnterApp(OnboardingTarget.CLIENT_INSTALL) },
                            onChooseFilePatch = { onEnterApp(OnboardingTarget.FILE_PATCH) },
                            onEnterHome = { onEnterApp(OnboardingTarget.HOME) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardTopBar(onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一步")
        }
        Text("新手引导", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onSkip) {
            Icon(Icons.Filled.Close, contentDescription = "跳过引导")
        }
    }
}

@Composable
private fun WelcomePane(
    onKnowShizuku: () -> Unit,
    onNeedGuide: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "欢迎使用 BA日服工具箱",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "本工具通过替换游戏文件实现蔚蓝档案日服汉化，资源来自开源组织「蔚蓝咖啡厅」。\n\n写入游戏目录需要借助 Shizuku（一款免 Root 的授权工具）。请选择你的情况：",
            fontSize = 16.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onKnowShizuku,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(10.dp))
            Text("我会使用 Shizuku", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNeedGuide,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(10.dp))
            Text("我不会，带我一步步做", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))

        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("跳过引导，先进入应用", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntroPane(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("第 1 步：先了解 Shizuku", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        IntroCard(
            icon = Icons.Filled.Security,
            title = "Shizuku 是什么？",
            body = "一款免费、开源（GPL-3.0）的安卓授权工具，由 RikkaApps 开发。它能让本工具箱临时获得系统级的文件写入能力，而不需要 Root 手机。",
        )
        IntroCard(
            icon = Icons.Filled.Bolt,
            title = "为什么必须用它？",
            body = "安卓 11 以上，普通应用不能直接写入 Android/data 里的游戏目录。Shizuku 借助系统自带的「调试」权限代替我们完成写入，游戏文件才能被替换成中文。",
        )
        IntroCard(
            icon = Icons.Filled.CheckCircle,
            title = "安全吗？",
            body = "不需要 Root；授权弹窗由系统弹出，只有你手动点「允许」才生效，随时可以在 Shizuku 里撤销。本工具箱内置的是官方原版安装包，并做了签名校验。",
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("你将要做的事", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "① 安装 Shizuku（约 2MB，一键完成）\n" +
                        "② 按图文教程激活服务（约 3 分钟）\n" +
                        "③ 授权本工具箱（点一下）\n" +
                        "④ 选择汉化方式\n\n" +
                        "无 Root 手机重启后 Shizuku 会失效，重新激活一次即可，教程随时可以在首页再次打开。",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("明白了，开始设置", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IntroCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
