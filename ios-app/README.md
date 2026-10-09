# BJMFSign iOS

班级魔方签到的 iOS 原生客户端，功能与 `android-app/` 保持一致，界面采用 iOS 26 **液态玻璃（Liquid Glass）** 设计。

## 功能对照

| Android | iOS |
| --- | --- |
| 微信扫码登录、自动获取 Cookie 与班级信息 | ✅ 相同流程（`BjmfNativeService.swift`） |
| 多任务管理：姓名 / 班级 ID / Cookie / 坐标 / 精度 / 执行时间 / 日期范围 / 通知 Key | ✅ |
| 地图选点、定位并收藏、位置收藏 | ✅ MapKit 选点；当前位置 WGS-84 → GCJ-02 转换 |
| 立即签到、执行全部、重排定时 | ✅ |
| QQ（Qmsg酱）/ 微信（Server酱）通知、汇总推送 | ✅ |
| 日志查看、复制、清除（最多 300 条） | ✅ |
| 主题取色（预设 + HEX） | ✅ 另支持系统取色器 |
| `AlarmManager` 精确定时后台签到 | ⚠️ 见下方「定时签到说明」 |

## 液态玻璃界面

- 系统 Liquid Glass `TabView`（滚动时自动收起 `tabBarMinimizeBehavior`）
- 卡片使用 `glassEffect`，状态卡使用主题色着色玻璃
- 按钮使用 `.glass` / `.glassProminent`，成组按钮放在 `GlassEffectContainer` 中融合变形
- 地图定位按钮、主题色色块使用可交互玻璃（`.interactive()`）
- 背景为随主题色变化的 `MeshGradient`，让玻璃有可折射的内容

## 定时签到说明

iOS 不允许 App 在精确时间点后台运行代码，因此定时签到采用以下组合：

1. **本地通知**：到达执行时间推送通知，点击通知或通知上的「立即签到」按钮即执行签到（按钮可在后台执行，无需打开 App）。
2. **后台刷新**：通过 `BGAppRefreshTask` 申请在执行时间之后由系统唤醒自动签到，具体时间由系统决定。
3. **前台补签**：打开 App 时自动补签 30 分钟内错过的执行时间点。

请在系统设置中允许本 App 的通知权限，并开启「后台 App 刷新」。

## 构建

需要 macOS + Xcode 26（iOS 26 SDK）及 [XcodeGen](https://github.com/yonaskolb/XcodeGen)：

```bash
cd ios-app
brew install xcodegen
xcodegen generate
open BJMFSign.xcodeproj
```

在 Xcode 中选择自己的开发者团队后即可运行到真机。依赖 [SwiftSoup](https://github.com/scinfu/SwiftSoup) 通过 Swift Package Manager 自动拉取。

### CI

`.github/workflows/ios-build.yml` 会在 `ios-app/` 改动时于 macOS 26 runner 上构建**未签名 IPA**，可在 Actions 运行页的 Artifacts 中下载 `BJMFSign-ios-unsigned-ipa`，再用 AltStore / Sideloadly 等工具自签安装。
