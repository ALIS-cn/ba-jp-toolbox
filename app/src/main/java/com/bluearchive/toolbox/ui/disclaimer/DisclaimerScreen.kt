package com.bluearchive.toolbox.ui.disclaimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TERMS_VERSION = "v1.0"
private const val TERMS_UPDATED = "2026-10-01"

private const val TERMS = """
BA日服工具箱 用户协议与免责声明
（协议版本：$TERMS_VERSION　更新日期：$TERMS_UPDATED）

欢迎使用「BA日服工具箱」（以下简称"本工具"）。在使用本工具前，请您仔细阅读并充分理解本协议全部内容。您点击"同意并继续"即表示您已阅读、理解并同意接受本协议的全部条款。如您不同意，请立即停止使用并退出本工具。

───────────────────────────────

第一条 工具性质与声明
1.1 本工具是一款面向蔚蓝档案日服（Android 版）玩家的辅助工具，仅提供以下功能：
    （1）汉化资源（TableBundles.zip）的下载与文件替换辅助；
    （2）汉化版客户端（APKS）的下载与安装引导；
    （3）游戏运行环境（Shizuku、Root、存储权限等）的检测与引导。
1.2 本工具本身不包含、不内置、不分发任何游戏数据、汉化文本、图像、音频或视频内容。所有汉化资源均来自第三方开源汉化组织「蔚蓝咖啡厅」（bluearchive.cafe），相关著作权归原汉化组及游戏版权方 Yostar 所有。
1.3 本工具与 Yostar、Nexon、蔚蓝咖啡厅等组织无任何隶属、赞助或合作关系。本工具仅供个人学习研究使用，请勿用于任何商业用途。

第二条 知识产权
2.1 本工具的软件代码以开源方式发布，受相关开源协议保护。
2.2 游戏《蔚蓝档案》（Blue Archive）及其所有相关内容的著作权、商标权归 Yostar / Nexon 所有。
2.3 汉化资源的著作权归蔚蓝咖啡厅汉化组所有，使用需遵守其开源协议。
2.4 Shizuku 为 RikkaApps 开发的开源软件（GPL-3.0 协议），本工具仅引导安装与调用，不修改其代码。

第三条 使用风险提示
3.1 替换游戏文件、安装重签名客户端的行为可能违反游戏用户协议，存在账号被限制、封禁的风险。由此产生的一切后果（包括但不限于账号封禁、数据丢失）由使用者自行承担，本工具开发者不承担任何责任。
3.2 Root 设备、安装 Xposed/LSPosed/Magisk 等模块可能触发游戏反作弊系统（"Use of unauthorized apps"）导致闪退或封号，请使用者自行评估风险。
3.3 日服采用引继码制度，卸载游戏或执行操作前，请务必在游戏内发行并妥善保管引继码，同时建议绑定 Yostar 账号。因未备份引继码导致的账号丢失，与本工具无关。
3.4 日服客户端锁区，登录与游戏更新需要日本网络节点，相关网络服务由使用者自行解决。

第四条 Shizuku 权限与隐私
4.1 为实现写入 Android/data 游戏目录、安装 APKS 分包、强制停止游戏等操作，本工具会引导您安装并激活 Shizuku（RikkaApps 开源项目）。
4.2 Shizuku 授权仅在您主动发起汉化、安装、还原等操作时使用，用于执行文件读写与应用安装。
4.3 本工具不会读取您的通讯录、短信、通话记录、定位、相册等个人隐私信息，也不会修改任何与游戏汉化无关的系统文件或用户数据。
4.4 本工具不会收集、上传您的任何个人信息。所有数据仅保存在您的设备本地。

第五条 资源更新与网络
5.1 每次执行汉化前，本工具会强制联网检查蔚蓝咖啡厅发布的最新汉化资源，确保使用最新版本。
5.2 网络不可用时，本工具会提示并允许使用本地缓存，但缓存版本不保证为最新。
5.3 下载地址来自 GitHub 及汉化组官网，可能因网络环境需要使用加速镜像。镜像服务由第三方提供，其可用性与稳定性不在本工具控制范围内。
5.4 本工具不对下载速度、下载成功率、资源完整性作出任何保证。

第六条 用户行为规范
6.1 您承诺不利用本工具从事任何违反法律法规的行为。
6.2 您承诺不将本工具用于商业用途，包括但不限于出售、出租、提供付费汉化服务等。
6.3 您承诺不篡改、逆向工程本工具，或利用本工具进行任何损害游戏版权方或汉化组权益的行为。

第七条 免责声明
7.1 本工具按"现状"提供，不提供任何明示或暗示的保证，包括但不限于适销性、特定用途适用性、不侵权等。
7.2 在法律允许的最大范围内，本工具开发者不对因使用或无法使用本工具而造成的任何直接、间接、附带、特殊或后果性损害承担责任。
7.3 因不可抗力（包括但不限于网络故障、服务器宕机、镜像服务停止、政策变化等）导致本工具无法正常使用的，开发者不承担责任。

第八条 协议变更
8.1 本工具开发者保留随时修改本协议的权利。协议更新后，首次启动时会再次提示您确认。
8.2 如您不同意修改后的协议，请停止使用本工具。继续使用即视为您接受修改后的协议。

第九条 适用法律与争议解决
9.1 本协议的订立、执行和解释均适用中华人民共和国法律。
9.2 因本协议引起的或与本协议有关的任何争议，双方应友好协商解决；协商不成的，任何一方均可向开发者所在地有管辖权的人民法院提起诉讼。

───────────────────────────────

点击「同意并继续」即表示您已阅读、理解并同意以上全部条款。
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerScreen(
    onAgree: () -> Unit,
    onExit: () -> Unit,
    viewOnly: Boolean = false,
    onClose: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户协议与免责声明", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (viewOnly) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = TERMS,
                    fontSize = 17.sp,
                    lineHeight = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (viewOnly) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("我知道了", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  同意并继续", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("不同意，退出", fontSize = 18.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
