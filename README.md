# BA日服工具箱

蔚蓝档案（Blue Archive）日服汉化工具箱（Android）。

## 功能

- **汉化客户端一键安装**：下载蔚蓝咖啡厅重签客户端（`cafe.YostarJP.BlueArchive`），自动迁移游戏资源，避免重下数 GB
- **文件替换汉化**：下载 `TableBundles.zip`（SHA-256 校验）替换游戏文本，自动备份原版，支持一键还原
- **环境检测与引导**：Shizuku 激活引导（按 MIUI/HarmonyOS/ColorOS 等 ROM 分别给出指引）、游戏安装状态检测

## 汉化资源来源

所有汉化文本来自开源汉化组织[蔚蓝咖啡厅](https://bluearchive.cafe)。本工具不内置、不分发任何游戏数据。

## 安全设计

- 安装 APK 前校验**包名 + 官方签名证书指纹**（防镜像投毒）
- Shizuku 命令全部以 argv 数组直传，不经 `sh -c` 拼接，无命令注入面
- FileProvider 仅暴露应用私有目录；`allowBackup=false`
- 权限克制：无定位/通讯录/短信等任何隐私权限；无统计埋点 SDK
- R8 压缩 release 构建，APK 约 7 MB

## 下载

见 [Releases](../../releases)。

## 免责声明

本工具与 Yostar、Nexon、蔚蓝咖啡厅无任何隶属或合作关系。修改游戏文件可能违反游戏用户协议，存在封号风险；使用前请务必在游戏内发行引继码并绑定 Yostar 账号。
