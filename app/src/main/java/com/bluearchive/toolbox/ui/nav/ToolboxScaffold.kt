package com.bluearchive.toolbox.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bluearchive.toolbox.ui.about.ChangelogScreen
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.disclaimer.DisclaimerScreen
import com.bluearchive.toolbox.ui.env.EnvScreen
import com.bluearchive.toolbox.ui.home.HomeScreen
import com.bluearchive.toolbox.ui.install.ClientInstallScreen
import com.bluearchive.toolbox.ui.patch.FilePatchScreen
import com.bluearchive.toolbox.ui.shizuku.ShizukuGuideScreen

object Routes {
    const val HOME = "home"
    const val ENV = "env"
    const val SHIZUKU_GUIDE = "shizuku_guide"
    const val FILE_PATCH = "file_patch"
    const val CLIENT_INSTALL = "client_install"
    const val CHANGELOG = "changelog"
    const val DISCLAIMER = "disclaimer"
}

@Composable
fun ToolboxScaffold(
    vm: ToolboxViewModel,
    startDestination: String = Routes.HOME,
) {
    val navController = rememberNavController()

    // 从新手引导直接落到功能页时，该页是导航栈唯一项，popBackStack 无效，需改回首页
    val rootBack: () -> Unit = {
        if (navController.previousBackStackEntry == null) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.popBackStack()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenEnv = { navController.navigate(Routes.ENV) },
                onOpenGuide = { navController.navigate(Routes.SHIZUKU_GUIDE) },
                onOpenFilePatch = { navController.navigate(Routes.FILE_PATCH) },
                onOpenClientInstall = { navController.navigate(Routes.CLIENT_INSTALL) },
                onOpenChangelog = { navController.navigate(Routes.CHANGELOG) },
                onOpenDisclaimer = { navController.navigate(Routes.DISCLAIMER) },
            )
        }
        composable(Routes.ENV) {
            EnvScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenGuide = { navController.navigate(Routes.SHIZUKU_GUIDE) },
            )
        }
        composable(Routes.SHIZUKU_GUIDE) {
            ShizukuGuideScreen(
                vm = vm,
                onBack = {
                    if (startDestination == Routes.SHIZUKU_GUIDE) {
                        // 从新手引导进入：看完引导回到主页，而不是退出应用
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SHIZUKU_GUIDE) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(Routes.FILE_PATCH) {
            FilePatchScreen(
                vm = vm,
                onBack = rootBack,
                onOpenEnv = { navController.navigate(Routes.ENV) },
            )
        }
        composable(Routes.CLIENT_INSTALL) {
            ClientInstallScreen(
                vm = vm,
                onBack = rootBack,
            )
        }
        composable(Routes.CHANGELOG) {
            ChangelogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(
                onAgree = { navController.popBackStack() },
                onExit = { navController.popBackStack() },
                viewOnly = true,
                onClose = { navController.popBackStack() },
            )
        }
    }
}
