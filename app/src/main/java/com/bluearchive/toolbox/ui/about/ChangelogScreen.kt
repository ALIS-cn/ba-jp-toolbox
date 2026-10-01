package com.bluearchive.toolbox.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluearchive.toolbox.ui.common.SectionCard

private data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>,
)

private val CHANGELOG = listOf(
    ChangelogEntry(
        version = "0.8.8",
        date = "2026-10-02",
        items = listOf(
            "修复：安装流程改为先迁移资源再卸载，避免卸载后资源丢失",
            "修复：重复汉化不再覆盖原版备份，保护真正的原版文本",
            "修复：部署失败时检查还原结果，还原失败会准确提示",
            "修复：zip 解压加入文件数量/大小限制（防恶意压缩包）",
            "修复：SHA-256 摘要缺失时拒绝安装，不再跳过校验",
            "修复：SHA-256 格式严格校验（64 位十六进制）",
            "修复：SAI 安装前也验证安装包包名，不再绕过供应链校验",
            "修复：SAF 通道写入失败时不再静默跳过，正确报错",
        ),
    ),
    ChangelogEntry(
        version = "0.8.7",
        date = "2026-10-02",
        items = listOf(
            "修复：下载取消后仍会尝试下一个镜像源，导致取消失效",
            "修复：Shizuku 命令 stdout/stderr 顺序读取可能死锁，改为并行读取",
            "修复：汉化/安装操作失败时界面卡住，现在会正确显示错误并复位",
            "修复：Shizuku 授权请求无超时，服务异常时界面会一直等待",
            "修复：下载完成时文件重命名失败导致后续校验出错",
            "修复：解压 zip 时不响应取消操作",
            "修复：还原原版时未提示手动关闭游戏",
            "修复：SAF 通道创建目录失败时静默跳过导致文件丢失",
            "修复：点击版本号不跳转更新日志",
            "修复：Shizuku 安装器打开失败时直接崩溃",
            "修复：PackageInstaller 会话异常时泄漏",
        ),
    ),
    ChangelogEntry(
        version = "0.8.6",
        date = "2026-10-01",
        items = listOf(
            "安全加固：安装 Shizuku / SAI 前校验包名并固定官方签名证书指纹，防止镜像返回被篡改的安装包",
            "安全加固：安装汉化客户端前校验包名为 cafe.YostarJP.BlueArchive，异常包立即中止安装",
            "安全加固：Shizuku 文件操作改为 argv 参数直传（不再经过 sh -c 拼接），彻底消除命令注入风险",
            "安全加固：收窄 FileProvider 可分享范围（移除整个外部存储），关闭应用数据备份",
        ),
    ),
    ChangelogEntry(
        version = "0.8.5",
        date = "2026-10-01",
        items = listOf(
            "开启 R8 代码压缩与资源压缩，自动剔除上万个未使用的图标类，安装包体积大幅减小",
            "改用 release 构建（仍用同一签名，可直接覆盖安装，无需卸载）",
            "移除误打包的构建说明与调试探针文件",
        ),
    ),
    ChangelogEntry(
        version = "0.8.4",
        date = "2026-10-01",
        items = listOf(
            "修复系统安装器不弹窗：单个 APK 改用 Intent.ACTION_VIEW 直接拉起系统安装器（兼容性最好），分包才用 PackageInstaller API；commit 改为主线程执行",
            "重新生成应用图标，角色显示更大更清晰",
            "「导出运行日志」按钮改为主题色，更醒目",
        ),
    ),
    ChangelogEntry(
        version = "0.8.3",
        date = "2026-10-01",
        items = listOf(
            "修复系统安装器安装失败：将 PendingIntent 标志从 FLAG_IMMUTABLE 改为 FLAG_MUTABLE（Android 12+ 要求 PackageInstaller 的 status receiver 必须可变）",
        ),
    ),
    ChangelogEntry(
        version = "0.8.2",
        date = "2026-10-01",
        items = listOf(
            "修复文件替换汉化失败：移除 Shizuku 通道中的 chmod 命令（sdcardfs 不支持修改权限，会导致部署和回滚均失败）",
            "优化备份逻辑：已有原版备份时不覆盖，避免上次失败后原版文件丢失",
            "还原操作增加权限修复步骤，可恢复被 chmod 破坏的目录",
        ),
    ),
    ChangelogEntry(
        version = "0.8.1",
        date = "2026-10-01",
        items = listOf(
            "Shizuku 安装提供两个选项：「官网下载」跳转浏览器，「本地安装包」直接使用应用内置的 Shizuku v13.6.0 安装",
        ),
    ),
    ChangelogEntry(
        version = "0.8.0",
        date = "2026-10-01",
        items = listOf(
            "更换应用图标为蔚蓝档案角色头像",
        ),
    ),
    ChangelogEntry(
        version = "0.7.9",
        date = "2026-10-01",
        items = listOf(
            "内置 SAI 安装包（v4.5）：客户端安装页新增「安装内置 SAI」按钮，无需单独下载即可安装 SAI 兜底工具",
            "导出日志按钮移至主界面最底部，更易找到",
        ),
    ),
    ChangelogEntry(
        version = "0.7.8",
        date = "2026-10-01",
        items = listOf(
            "新增运行日志导出功能：首页底部点击「导出运行日志」，可复制日志文字或保存为文件，方便排查问题",
            "在下载、解压、安装、Shizuku 命令执行等关键环节加入日志记录",
        ),
    ),
    ChangelogEntry(
        version = "0.7.7",
        date = "2026-10-01",
        items = listOf(
            "修复咖啡厅客户端安装失败：咖啡厅现已分发单个 APK（非 APKS 分包），解压时若 zip 内无 .apk 条目则将整个文件作为单个 APK 安装",
            "Shizuku 安装器：单个 APK 用 pm install，多个分包用 pm install-multiple",
        ),
    ),
    ChangelogEntry(
        version = "0.7.6",
        date = "2026-10-01",
        items = listOf(
            "优化下载进度条：进度上报节流至 120ms 一次，百分比改用 Float，进度条动画更丝滑",
        ),
    ),
    ChangelogEntry(
        version = "0.7.5",
        date = "2026-10-01",
        items = listOf(
            "修复咖啡厅客户端下载失败：bluearchive.cafe 国内可直连，移除无效的 GitHub 镜像（镜像对非 GitHub 链接返回 403/HTML）",
            "修复重定向解析：download.bluearchive.cafe 实际返回 307 跳转，解析时改用直连请求获取真实地址",
        ),
    ),
    ChangelogEntry(
        version = "0.7.4",
        date = "2026-10-01",
        items = listOf(
            "修复文件替换汉化失败：改用 IShizukuService 直接创建进程，替代反射调用 Shizuku.newProcess（该方法被 @RestrictTo 标记，getMethod 无法找到）",
        ),
    ),
    ChangelogEntry(
        version = "0.7.3",
        date = "2026-10-01",
        items = listOf(
            "优化咖啡厅下载策略：bluearchive.cafe 国内可直连，改为直连优先、镜像兜底",
        ),
    ),
    ChangelogEntry(
        version = "0.7.2",
        date = "2026-10-01",
        items = listOf(
            "优化取消下载响应速度：取消时立即中断 OkHttp 请求，无需等待数据块读完",
        ),
    ),
    ChangelogEntry(
        version = "0.7.1",
        date = "2026-10-01",
        items = listOf(
            "修复咖啡厅客户端下载失败：先解析 download.bluearchive.cafe 的 302 重定向真实地址，再用镜像下载",
        ),
    ),
    ChangelogEntry(
        version = "0.7.0",
        date = "2026-10-01",
        items = listOf(
            "新增下载可取消功能，文件替换和客户端安装过程中可随时取消",
            "首页底部新增版本号显示，点击查看更新日志",
            "首页底部可查看完整用户协议",
            "新增更新日志页面，记录每次版本更新内容",
        ),
    ),
    ChangelogEntry(
        version = "0.6.3",
        date = "2026-10-01",
        items = listOf(
            "修复咖啡厅客户端下载失败：bluearchive.cafe 链接现在也走 GitHub 镜像（支持大文件）",
            "修复镜像缓存 bug：cachedFastest 存 mirror 前缀而非完整 URL",
            "镜像测速统一直接拼接 URL，不再对非 GitHub 链接做 URL 编码",
        ),
    ),
    ChangelogEntry(
        version = "0.6.2",
        date = "2026-10-01",
        items = listOf(
            "修复 API 返回 HTML 导致 JSON 解析错误：检测 body 首字符是否为 JSON",
            "加强文件下载校验：始终检查魔数，排除 HTML/JSON 错误页",
            "重写用户协议为 9 条标准条款，符合常规软件协议规范",
            "协议版本升 v2，老用户首次启动重新确认",
        ),
    ),
    ChangelogEntry(
        version = "0.6.1",
        date = "2026-10-01",
        items = listOf(
            "修复环境检测中未满足条件仍显示打勾图标，改用红色关闭图标",
            "修复 SAI 安装崩溃：FileProvider 路径、MIME 类型、异常捕获",
            "新增「从本地文件安装」入口，下载失败时可选本地 APKS 文件",
            "所有国外下载走国内镜像优先，超时延长至 120s",
        ),
    ),
    ChangelogEntry(
        version = "0.6.0",
        date = "2026-10-01",
        items = listOf(
            "实现机型/ROM/系统版本自适应（MIUI/鸿蒙/ColorOS/OriginOS/OneUI）",
            "按安卓版本自适应文件操作通道（Shizuku/FileApi/SAF）",
            "完成流程 A：汉化客户端一键安装（APKS 下载、分包安装、资源迁移）",
            "完成流程 B：文件替换汉化（下载、备份、覆盖、还原）",
            "内置 Shizuku 安装包支持（assets 优先，镜像兜底）",
            "下载失败提供官网手动下载选项",
            "智能镜像测速：并发测速选最快镜像",
        ),
    ),
    ChangelogEntry(
        version = "0.5.0",
        date = "2026-10-01",
        items = listOf(
            "工程骨架搭建（Kotlin 2.0.21 + Compose）",
            "首次启动用户守则与免责声明",
            "环境检测 + Shizuku 引导向导（按安卓版本分支）",
            "每次汉化强制使用最新资源（GitHub Releases API）",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更新日志", fontWeight = FontWeight.SemiBold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            CHANGELOG.forEach { entry ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "v${entry.version}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            entry.date,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    entry.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Text("·  ", fontSize = 15.sp)
                            Text(item, fontSize = 15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
