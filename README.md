<div align="center">

<img src="ios-app/BJMFSign/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png" width="96" alt="BJMFSign" />

# BJMFSign

**班级魔方 GPS 自动签到 · Android / iOS 原生客户端**

[![Release](https://img.shields.io/github/v/release/bai-piao/BJMFSign?label=Release)](https://github.com/bai-piao/BJMFSign/releases/latest)
[![Android](https://github.com/bai-piao/BJMFSign/actions/workflows/android-release.yml/badge.svg)](https://github.com/bai-piao/BJMFSign/actions/workflows/android-release.yml)
[![iOS](https://github.com/bai-piao/BJMFSign/actions/workflows/ios-build.yml/badge.svg)](https://github.com/bai-piao/BJMFSign/actions/workflows/ios-build.yml)

</div>

微信扫码登录班级魔方，保存多个签到任务，到点自动完成 GPS 签到，并把结果推送到 QQ / 微信。所有数据只保存在手机本地，不需要服务器。

## ✨ 功能

- **扫码登录**：微信扫码后自动获取 Cookie、姓名、班级 ID 与班级码
- **多人任务**：每个任务独立设置坐标、定位精度、执行时间（可多个）和生效日期范围
- **地图选点**：在地图上点选签到位置，或一键定位当前位置；常用位置可收藏
- **定时签到**：按设定时间自动执行，也可单个立即签到或一键执行全部
- **结果推送**：支持 [Qmsg酱](https://qmsg.zendee.cn/)（QQ）和 [Server酱](https://sct.ftqq.com/)（微信），批量执行时额外推送汇总
- **执行日志**：保留最近 300 条完整日志，可复制排查问题
- **主题取色**：预设色板或自定义 HEX 颜色

| | Android | iOS |
| --- | --- | --- |
| 系统要求 | Android 8.0+ | iOS 26+ |
| 界面 | Jetpack Compose + Miuix | SwiftUI + 液态玻璃（Liquid Glass） |
| 定时签到 | 后台精确定时，全自动 | 到点通知，点按即签；后台刷新 / 打开 App 时自动补签 |

## 📥 下载安装

前往 [**Releases**](https://github.com/bai-piao/BJMFSign/releases/latest) 下载最新版本：

- **Android**：下载 `BJMFSign-vX.Y.apk` 直接安装（需允许"安装未知来源应用"）。
- **iOS**：下载 `BJMFSign-vX.Y-ios-unsigned.ipa`，用 [AltStore](https://altstore.io/)、[Sideloadly](https://sideloadly.io/) 等工具自签安装。

> 想体验最新提交？在 [Actions](https://github.com/bai-piao/BJMFSign/actions) 中打开 **Android Release APK** 或 **iOS Build** 最近一次成功的运行，从 Artifacts 下载（需登录 GitHub）。

## 🚀 使用

1. **登录**：在「登录」页获取二维码，用微信扫码并确认。iOS 可截图后在微信「扫一扫 → 相册」中识别。
2. **建任务**：登录成功后自动跳到「任务」页并填好姓名、班级 ID 和 Cookie；在地图上选择签到点，填写执行时间（如 `07:30:00,12:00:00`），保存。
3. **签到**：到点自动执行；也可在任务页「立即签到」，或在「管理」页「执行全部」。结果在「日志」页查看。

**定时提示**

- **Android**：Android 12+ 请在系统设置中允许本 App 的「闹钟和提醒」权限，并关闭电池优化，以保证准时执行。
- **iOS**：系统不允许 App 在精确时间后台运行。到点会推送通知，点击通知或通知上的「立即签到」按钮即可执行（按钮可在后台完成，无需打开 App）；系统后台刷新时、以及打开 App 时会自动补签 30 分钟内错过的任务。请允许通知并开启「后台 App 刷新」。

## 🛠️ 从源码构建

<details>
<summary><b>Android</b></summary>

需要 JDK 17+ 与 Android SDK Platform 37。

```bash
cd android-app
./gradlew assembleRelease
```

release 包默认使用本机 debug keystore 签名；设置 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 环境变量即可使用自己的签名。

</details>

<details>
<summary><b>iOS</b></summary>

需要 macOS + Xcode 26 与 [XcodeGen](https://github.com/yonaskolb/XcodeGen)。

```bash
cd ios-app
brew install xcodegen
xcodegen generate
open BJMFSign.xcodeproj
```

在 Xcode 中选择自己的开发者团队即可运行到真机。更多说明见 [ios-app/README.md](ios-app/README.md)。

</details>

## 📦 发布

推送 `v*` 标签（如 `v1.2`），或在 Actions 中手动运行 **Publish GitHub Release** 并填写标签，会自动构建 APK 与 IPA 并发布到 Releases。发布前记得同步修改版本号：

- Android：`android-app/app/build.gradle.kts` 中的 `versionCode` / `versionName`
- iOS：`ios-app/project.yml` 中的 `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`

**固定 APK 签名**：未配置时 CI 每次使用临时密钥签名，新版本需先卸载旧版才能安装。在仓库 **Settings → Secrets and variables → Actions** 添加以下 Secrets 后即可覆盖升级：

| Secret | 说明 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | keystore 文件的 base64（`base64 -w0 release.jks`） |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | key 别名 |
| `RELEASE_KEY_PASSWORD` | key 密码 |

```bash
keytool -genkeypair -v -keystore release.jks -alias bjmf -keyalg RSA -keysize 2048 -validity 10000
```

## 📁 项目结构

```
BJMFSign/
├── android-app/          Android 客户端（Kotlin / Compose）
├── ios-app/              iOS 客户端（SwiftUI / Liquid Glass）
├── .github/workflows/    CI：构建 APK、IPA 与发布 Release
├── BJMF.py               旧版 Python 签到脚本
├── auto_add_user.py      旧版扫码添加用户工具
├── utils/                旧版脚本模块
└── web_signin/           旧版 Web 管理端（仅保留源码）
```

两端的签到逻辑一一对应：

| | Android | iOS |
| --- | --- | --- |
| 签到 / 登录网络逻辑 | `data/BjmfNativeService.kt` | `Data/BjmfNativeService.swift` |
| 本地存储 | `data/BjmfStore.kt` | `Data/BjmfStore.swift` |
| 定时调度 | `data/BjmfScheduler.kt` | `Data/BjmfScheduler.swift` |
| 界面状态 | `BjmfViewModel.kt` | `App/BjmfViewModel.swift` |

<details>
<summary><b>旧版 Python 脚本（可选）</b></summary>

App 不依赖以下脚本，仅为需要在电脑或服务器上运行的用户保留。

```bash
pip install -r requirements.txt
python auto_add_user.py   # 微信扫码，自动写入 data.json
python BJMF.py            # 为 data.json 中的所有用户执行签到
```

可在项目根目录创建 `.env` 设置公共参数，`auto_add_user.py` 会自动套用：

```properties
ENABLE_COMMON_CONFIG=True
COMMON_LAT=30.123456   # 纬度
COMMON_LNG=120.123456  # 经度
COMMON_ACC=30          # 精度
COMMON_QMSG_KEY=       # Qmsg酱 Key（可选）
COMMON_WX_KEY=         # Server酱 Key（可选）
```

也可以手动编写 `data.json`（参考 `data.json.example`）：

```json
{
  "students": [
    {
      "name": "用户备注",
      "class": "110141",
      "lat": "30.123456",
      "lng": "120.123456",
      "acc": "30",
      "cookie": "remember_student_...=...",
      "QmsgKEY": "",
      "WXKey": ""
    }
  ]
}
```

- **Cookie**：电脑微信打开 `http://g8n.cn/student/login?ref=%2Fstudent`，按 F12 在「网络」中找到请求头里的 `Cookie`。
- **班级 ID**：签到页网址 `course/110141` 中的数字。
- **经纬度**：用 [高德坐标拾取器](https://lbs.amap.com/tools/picker) 获取。

定时运行可使用 Windows 任务计划程序、Linux `crontab` 或云函数。Cookie 失效时重新运行 `auto_add_user.py` 即可。

</details>

## 🔒 隐私与免责

- Cookie、坐标和推送 Key 只保存在本机 App 私有存储（旧脚本为本地 `data.json` / `.env`，已加入 `.gitignore`），卸载 App 即清除。
- 分享日志或截图前，请先打码 Cookie、班级 ID 和坐标等个人信息。
- 本项目仅供学习交流，请遵守所在学校的考勤规定，使用产生的后果由使用者自行承担。

## 🙏 致谢

本项目基于 [JasonYANG170/AutoCheckBJMF](https://github.com/JasonYANG170/AutoCheckBJMF) 修改简化而来，感谢原作者的工作。原项目支持更多签到方式，如有需要请前往查看。

如果觉得好用，欢迎点个 ⭐ Star。
