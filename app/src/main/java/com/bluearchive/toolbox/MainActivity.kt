package com.bluearchive.toolbox

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluearchive.toolbox.data.prefs.AppPreferences
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.disclaimer.DisclaimerScreen
import com.bluearchive.toolbox.ui.nav.Routes
import com.bluearchive.toolbox.ui.nav.ToolboxScaffold
import com.bluearchive.toolbox.ui.onboarding.OnboardingScreen
import com.bluearchive.toolbox.ui.onboarding.OnboardingTarget
import com.bluearchive.toolbox.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ToolboxTheme {
                val vm: ToolboxViewModel = viewModel()
                val agreement by vm.agreementVersion.collectAsState()
                val onboarding by vm.onboardingDone.collectAsState()
                // 新手选择「我不会」后进入 Shizuku 引导页；其余情况进主页
                var startRoute by remember { mutableStateOf(Routes.HOME) }

                when {
                    // 本地状态尚未加载
                    agreement == null -> LoadingView()
                    // 未同意（或协议已更新需重新确认）
                    agreement != AppPreferences.DISCLAIMER_VERSION -> DisclaimerScreen(
                        onAgree = vm::acceptDisclaimer,
                        onExit = { (this as? Activity)?.finish() },
                    )
                    // 已同意协议，但首次引导未完成
                    onboarding == null -> LoadingView()
                    onboarding == false -> OnboardingScreen(
                        vm = vm,
                        onEnterApp = { target ->
                            startRoute = when (target) {
                                OnboardingTarget.HOME -> Routes.HOME
                                OnboardingTarget.CLIENT_INSTALL -> Routes.CLIENT_INSTALL
                                OnboardingTarget.FILE_PATCH -> Routes.FILE_PATCH
                            }
                            vm.completeOnboarding()
                        },
                    )
                    // 正常进入主界面
                    else -> ToolboxScaffold(vm = vm, startDestination = startRoute)
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}