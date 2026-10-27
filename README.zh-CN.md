<p align="center">
  <img src="iosApp/steaL/Assets.xcassets/AppIconImage.imageset/AppIconImage.png" width="120" alt="steaL 图标">
</p>

<h1 align="center">steaL</h1>

<p align="center">
  <a href="README.md">English</a> | <b>简体中文</b>
</p>

<p align="center">
  一个原生 iOS App：找 Steam 折扣、跨商店比价，并帮你判断现在买还是再等等。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/iOS-26.5%2B-black?logo=apple" alt="iOS 26.5+">
  <img src="https://img.shields.io/badge/Swift-5-F05138?logo=swift&logoColor=white" alt="Swift 5">
  <img src="https://img.shields.io/badge/UI-SwiftUI-0A84FF" alt="SwiftUI">
  <img src="https://img.shields.io/badge/Data-SwiftData%20%2B%20Firestore-FFCA28" alt="SwiftData + Firestore">
</p>

<p align="center">
  <img src="screenshots/games.png" width="200" alt="Games tab">
  <img src="screenshots/detail.png" width="200" alt="Game detail">
  <img src="screenshots/ai-advice.png" width="200" alt="AI advice">
  <img src="screenshots/wishlist.png" width="200" alt="Wishlist">
</p>

## 简介

steaL 是 Monash University **FIT3178 iOS App Development** 课程的个人项目。它把
[IsThereAnyDeal](https://isthereanydeal.com) 的价格数据和 Steam 商店 API 结合起来：
一个游戏在各商店的现价、史低价格，以及 AI 给出的「值不值得买」建议，都能在同一页看到。

界面全部用 SwiftUI 和 Apple 自家框架实现，唯一的第三方依赖是 Firebase（Auth 与 Firestore）。

## 功能

- **折扣与免费领取**：Games 页展示当天的 Steam 热门折扣和限时免费游戏；Hot Deals 是可分页、可按游戏类型筛选的网格。
- **比价**：每个游戏页显示各商店的价格、史低价，以及 Steam 详情：预告片与截图、评测、类型、Demo 与本体互相跳转。
- **AI 购买建议**：在游戏页点 ✨，根据现价、史低和发售状态给出 **Buy / Wait / Skip** 结论和理由。
  - 支持任何兼容 OpenAI Chat Completions 的服务商。
  - 可以添加自己的服务商和模型，API key 存在 **Keychain**，不进数据库。
- **愿望单与提醒**：点心形收藏，愿望单经 Firestore 同步，可按 *On Sale* / *Lowest* / *Upcoming* 筛选；降价和发售时发本地通知。
- **搜索**：使用 iOS 26 的搜索标签页搜索 Steam 商店。
- **账号可选**：邮箱登录、找回密码、App 内删除账号；浏览不需要登录。
- **个性化**：7 个商店地区与货币，Steam 内容可选英文或简体中文，浅色 / 深色 / 跟随系统主题。
- **无障碍**：Dynamic Type 在辅助功能字号下自动换布局，VoiceOver 标签与 trait 完整，另有欢迎页和 TipKit 提示。

## 技术栈

| 方面 | 技术 |
| --- | --- |
| 界面 | SwiftUI（iOS 26、Liquid Glass）、TipKit、AVKit |
| 状态 | Observation（`@Observable`）、Swift Concurrency（`async`/`await`、`async let`、任务取消） |
| 本地存储 | SwiftData（设置、LLM 服务商）、Keychain（API key） |
| 云端 | Firebase Auth、Cloud Firestore（愿望单用 `snapshots` 异步序列实时监听） |
| 网络 | `URLSession` + `Codable`，不用网络库 |
| API | [IsThereAnyDeal API](https://docs.isthereanydeal.com)、Steam Store API、OpenAI 兼容的 Chat Completions |
| 工具 | SwiftLint、`os.Logger`、隐私清单（`PrivacyInfo.xcprivacy`） |

## 架构

MVVM，每个 tab 一个 `NavigationStack`：

```
Views       SwiftUI 页面与组件；用 @Query 读设置，用 @State 持有 ViewModel
ViewModels  @MainActor @Observable 状态机（每个异步区块一个 enum 状态）
Services    无状态的 API 客户端（SteamAPI、ITADAPI、LLMAPI）与可观察的服务（Auth、Wishlist）
Models      ITAD / Steam / Chat Completions 的 Codable DTO、SwiftData 模型、App 内的视图模型
```

几个设计决定：

- **区块独立加载**：页面的每个区块各有自己的状态（`loading / loaded / notFound / failed`），比价请求慢或失败不会拖垮整个游戏页。
- **「没找到」不是错误**：Service 查不到时返回 `nil`，只有请求失败才抛错；共用的 `LoadError` 把网络错误归成四种给用户看的失败。
- **查找 Steam appID**：ITAD 用自己的 UUID 标识游戏，需要转换成 Steam appID。ITAD 给出多个候选 appID 时，App 会向 Steam 逐个核对，选还在售的那个。
- **唯一数据源**：愿望单只存在 Firestore；ViewModel 只负责查询和核对，不另存一份。
- **自制预告片播放器**：分页画廊里的预告片用 `AVPlayerLayer` 播放器，拖进度条不会和左右翻页冲突；全屏时用系统的 `AVPlayerViewController`。

## 运行

**环境要求**：Xcode 26，带 iOS 26.5 SDK。

1. 克隆仓库，打开 `iosApp/steaL.xcodeproj`。
2. 复制密钥模板并填入你的 key：
   ```sh
   cp iosApp/Secrets.example.xcconfig iosApp/Secrets.xcconfig
   ```
   | Key | 是否必需 | 获取方式 |
   | --- | --- | --- |
   | `ITAD_API_KEY` | 必需（折扣和价格要用） | [isthereanydeal.com/apps](https://isthereanydeal.com/apps/my/) |
   | `LLM_API_KEY`、`LLM_API_URL`、`LLM_MODEL` | 只有内置 AI 模型要用 | 任何兼容 OpenAI 的服务商（默认 DeepSeek） |

   `.xcconfig` 里 `//` 是注释，网址要写成 `https:/$()/example.com`。
   也可以不配内置模型，在 App 里的 **Profile → AI** 添加自己的服务商。
3. **Firebase（可选）**：`iosApp/steaL/GoogleService-Info.plist` 指向作者的 Firebase 项目。想用自己的，就新建一个 Firebase 项目，开启 Email/Password 登录和 Firestore，再替换这个文件。愿望单存在 `users/{uid}/wishlist/{appID}`。
4. 在 iOS 26.5 模拟器或真机上运行。

没有 `Secrets.xcconfig` 也能编译，但在填好 ITAD key 之前，折扣和价格都会加载失败。

## 目录结构

```
iosApp/                  iOS App（Xcode 工程、xcconfig、SwiftLint 配置）
└── steaL/
    ├── Config/          读取 Info.plist 的配置、Logger 分类
    ├── Models/          ITAD/、Steam/、AI/、App/、Settings/、Previews/
    ├── Services/        API 客户端、登录、愿望单、Keychain、通知
    ├── ViewModels/      每个页面一个
    └── Views/           页面，以及按使用页面分组的 Components/
firebase/
└── email-templates/     Firebase 重设密码邮件的 HTML 正文
icon/                    Liquid Glass App 图标的 Icon Composer 源文件
```

## 已知限制

- 降价和发售提醒在 App 回到前台时检查，还没有后台刷新。
- 内置 LLM key 是从 `Info.plist` 读的，正式上架不安全，需要改成服务端代理。
- 没有上架 App Store。

## 数据来源与声明

- 价格和折扣数据来自 [IsThereAnyDeal](https://isthereanydeal.com)。
- 游戏详情、媒体和搜索来自 Steam 商店。
- AI 建议由语言模型生成，可能有误，不构成消费建议。

steaL 与 IsThereAnyDeal、Valve 均无隶属关系。Steam 及其标志是 Valve Corporation 的商标。

## 作者

**Yijiang Luo**，Monash University。[GitHub](https://github.com/JohnWes7)
