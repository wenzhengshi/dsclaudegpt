<p align="center">
  <img src="docs/icon.png" width="140" alt="DsClaudeGpt 应用图标">
</p>

<h1 align="center">DsClaudeGpt</h1>

<p align="center">
  <a href="https://tulingai.com/app-market.html?id=48"><img src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD-%E5%AE%89%E8%A3%85%E5%8C%85-ff6a00?style=for-the-badge" alt="下载安装包"></a>
  <img src="https://img.shields.io/badge/Platform-Android%2021%2B-brightgreen?style=for-the-badge" alt="Platform">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License: MIT"></a>
</p>

> Use Claude and GPT by DeepSeek — you only need a DeepSeek API key.
>
> 一个 Android 聊天客户端：三套皮肤（Claude / ChatGPT / DeepSeek）共用一套 DeepSeek 接口与本地聊天记录，只需要一个 DeepSeek API Key。

---

## 📲 下载安装

| 方式 | 地址 |
| --- | --- |
| **应用市场（推荐）** | **<https://tulingai.com/app-market.html?id=48>** |
| 源码自行构建 | 见下方 [构建](#构建) 一节 |

- 安装后首次启动需要填入 **DeepSeek API Key**（`sk-` 开头），没有 Key 无法对话；
- API Key 申请地址：<https://platform.deepseek.com/>；
- 支持 `armeabi-v7a` / `arm64-v8a` 两种架构，Android 5.0 (API 21) 及以上。

## 这个项目是什么

DsClaudeGpt 是一个**纯 Java 写的原生 Android 应用**（无 Kotlin、无 Jetpack Compose、无 WebView 聊天界面，界面全部是 XML + 自定义 View）。它把 Claude / ChatGPT / DeepSeek 三套视觉风格做进同一个 App，但**底层请求全部走同一个 OpenAI 兼容的 `/chat/completions` 接口**，默认指向 DeepSeek。

也就是说：

- 界面里出现的 `Fable 5.1` / `Opus 5.5` / `Sonnet 5.5` / `Haiku 5.5` / `GPT-5.1` 等名称**只是皮肤上的展示文字**，不改变实际请求；
- 真正决定行为的是设置里的 **Base URL + Model**（默认 `https://api.deepseek.com` + `deepseek-flash`）；
- 三套皮肤**共用同一份本地聊天记录**，切换风格不会丢历史。

> 应用内设置页也明说了这一点："以上名称仅用于界面展示，实际请求统一使用设置中配置的 DeepSeek 接口与模型。"

## 功能

| 功能 | 说明 |
| --- | --- |
| 🎨 三套皮肤 | Claude / ChatGPT / DeepSeek，可在设置里切换，也可用桌面长按快捷方式直接进入指定风格 |
| 🖼️ 四款桌面图标 | 蓝豚王冠（默认）、蓝鲸、缠绕几何、星芒，运行时通过 `activity-alias` 切换 |
| 🧠 深度思考 | 思考开关 + 低/中/高三档强度，按 `reasoning_effort` 真实下发；回答上方有可展开的思考卡片，带计时 |
| 📡 流式输出 | 原生 `HttpURLConnection` 直连 SSE，自研增量解析（支持多行 `data:`、`event:` 类型），可中途停止 |
| 🔁 断线续写 | 网络中断自动重试，并从断点续写；内置重叠去重，不会把已输出内容重复一遍 |
| 💾 草稿落盘 | 生成过程中增量写库，进程被杀也不丢；重开会话显示「已中断 · 继续生成」 |
| 📴 非流式兜底 | 接口不回 SSE 时自动按整段结果渲染并提示，不会白屏 |
| 🗂️ 本地会话库 | 会话列表、重命名、删除、搜索，全部落地在本地 SQLite（`ChatStore`，已升到 v3） |
| 🕵️ 无痕对话 | 不写历史、不进搜索，退出即消失 |
| 📎 附件 | 图片（base64 随消息发送）与文件，走 `FileProvider` |
| 🎙️ 语音输入 | 自定义波形 / 录音态界面，带可朗读的状态提示 |
| 🔀 模型切换 | 设置里可直接写模型名，也可调用 `/models` 拉取当前 Key 可用模型列表 |
| 🌐 任意兼容端点 | Base URL 可改，任何 OpenAI 兼容网关都能接；网关不认 `reasoning_effort` / `thinking` 参数时会自动降级重试 |

## 更新日志

### v2（2026-11）

**新增**

- **深度思考全链路**：`Prefs` 新增 `think_level`（低/中/高）与 `show_thought`，请求里真实下发 `reasoning_effort`；设置页、模型面板、回答上方三处共用同一套开关。
- **思考过程卡片**：新增 `bg_thought_card[_ds|_gpt].xml` 三套配色与 `msg_ai_reasoning*` 一组控件，支持流式渲染、计时、展开/收起，历史消息也能回看。
- **断点续写与半成品保护**：`DeepSeekClient` 新增 live buffer、`MAX_AUTO_RETRY` / `MAX_AUTO_RESUME` / `MAX_EOF_RESUME` 重试策略、续写去重（`trimSuffixOverlap` / `stripReplay`），并把 `finish_reason=length` 正确识别为「可能没写完」。
- **草稿增量落库**：`ChatStore` 升到 `DB_VERSION=3`，新增 `reasoning` 与 `partial` 两列，`onUpgrade` 用 `ALTER TABLE` 增量升级、不删老数据。
- **空状态与语音状态**：ChatGPT 皮肤首页补了空对话问候语，语音页补了「等待说话 / 正在聆听」状态文字（带 `accessibilityLiveRegion`）。

**修复**

- 接口不支持 SSE 时整段结果被丢弃 → 现在走非流式兜底并提示。
- 网关不认识 `reasoning_effort` / `thinking` 参数导致 400 → 逐级去掉参数重试。
- 模型只返回 `reasoning_content` 没有正文 → 直接把思考内容当回答展示并提示。
- 生成中途被杀掉后，历史里会留下空的助手草稿行 → 读取时跳过空草稿。
- `strings.xml` 结尾 `</resources>` 缩进错位、`colors.xml` 残留已废弃的 `ai_badge_*` 配色 → 一并清理。


## 构建

**环境要求**

- JDK **17**
- Android SDK Platform **34**、Build-Tools 34.x
- Android Studio（推荐 Ladybug 及以上）或命令行 Gradle

**命令行**

```bash
git clone https://github.com/wenzhengshi/dsclaudegpt.git
cd dsclaudegpt

# 首次构建会由 wrapper 下载 Gradle 8.14.2
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

产物位置：

```
app/build/outputs/apk/debug/app-debug.apk
```

**ABI 分包**：`app/build.gradle` 开启了 ABI splits，会额外产出 `armeabi-v7a`、`arm64-v8a` 以及 universal APK：

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-arm64-v8a-release.apk
# app/build/outputs/apk/release/app-universal-release.apk
```

**用 Android Studio**：直接 `File → Open` 打开仓库根目录，等 Gradle Sync 完成即可运行。

## 发布签名

仓库里**不包含任何密钥**。签名四元组按以下优先级读取（见 `app/build.gradle` 的 `readStore()`）：

1. 项目根目录 `keystore.properties`（已在 `.gitignore` 中）
2. 命令行 `-PSTORE_FILE=... -PSTORE_PW=... -PKEY_ALIAS=... -PKEY_PW=...`
3. 环境变量 `STORE_FILE` / `STORE_PW` / `KEY_ALIAS` / `KEY_PW`
4. `~/.gradle/gradle.properties`

`keystore.properties` 示例：

```properties
STORE_FILE=release.keystore
STORE_PW=your-store-password
KEY_ALIAS=your-key-alias
KEY_PW=your-key-password
```

四项缺任意一项，或 `STORE_FILE` 指向的文件不存在时，会自动跳过 release 签名配置，`assembleRelease` 使用 Android 默认 debug 签名 —— 方便 fork 后直接跑通。

## 使用

1. 从 [应用市场](https://tulingai.com/app-market.html?id=48) 下载安装，或自行构建（见上方 [构建](#构建)）；
2. 首次启动会提示填写 API Key，到 [platform.deepseek.com](https://platform.deepseek.com/) 申请一个（`sk-` 开头）；
3. 粘贴到 `设置 → API Key`，可点「获取模型列表」验证连通性；
4. 需要接自建网关或第三方兼容服务时，把 `Base URL` 改成对应地址即可。

## 代码结构

```
.
├── app/src/main/
│   ├── AndroidManifest.xml                 # 入口 Activity、图标 alias、FileProvider、快捷方式
│   ├── java/cn/jicreate/jicreate_app/a2dde3930ff/
│   │   ├── MainActivity.java               # 主界面与三套皮肤的装配、事件分发（约 6000 行）
│   │   ├── DeepSeekClient.java             # SSE 流式请求、/models、连通性探测
│   │   ├── ChatStore.java                  # 本地会话持久化
│   │   ├── Prefs.java                      # API Key / Base URL / 模型 / 风格偏好
│   │   ├── LaunchEntryActivity.java        # 桌面图标入口（配合 activity-alias 切换图标）
│   │   ├── SkyOrbView.java                 # 首页光球 / 背景动效
│   │   ├── DrawerSwipeLayout.java          # 侧边栏手势
│   │   ├── GptHomeNavView.java             # ChatGPT 皮肤底部导航
│   │   ├── GptHomeGlowView.java            # ChatGPT 皮肤光晕
│   │   ├── GptChatNavBarView.java          # ChatGPT 皮肤会话页导航
│   │   ├── GptChatNavController.java       # 导航状态机
│   │   ├── GptNavBackdropDrawable.java     # 导航背景
│   │   ├── DsActionsView.java              # DeepSeek 皮肤消息操作条
│   │   ├── GptActionsView.java             # ChatGPT 皮肤消息操作条
│   │   ├── GptAttachMenuView.java          # ChatGPT 皮肤附件菜单
│   │   └── OrbHaloView.java                # 光晕绘制
│   ├── assets/
│   │   ├── ds_actions.html                 # DeepSeek 皮肤操作栏（WebView 渲染）
│   │   └── gpt_actions.html                # ChatGPT 皮肤操作栏
│   └── res/                                # 布局 / 矢量图 / 颜色 / 字符串
├── gradle/wrapper/                         # Gradle 8.14.2 wrapper
├── docs/icon.png                           # 应用图标（README 展示用）
├── build.gradle · settings.gradle
├── gradle.properties
└── LICENSE
```

几乎所有 UI 都是**手写 XML 布局 + 自定义 `View` 的 `onDraw`**，没有引入第三方 UI 库；依赖只有 `androidx.core`、`appcompat`、`material`、`constraintlayout` 四个。

## 隐私与安全

- API Key 与接口地址仅保存在**本机** `SharedPreferences`，不会上报任何服务器，仓库与代码中**没有任何硬编码的 Key**；
- 应用只与你在设置里填写的 `Base URL` 通信，没有埋点、没有统计 SDK；
- ⚠️ `AndroidManifest.xml` 中开启了 `android:usesCleartextTraffic="true"`，以便接入自建 HTTP 网关。**如果你只用 HTTPS 服务，建议把它改成 `false`**；
- ⚠️ API Key 以明文存于 App 私有目录（`MODE_PRIVATE`），未做额外加密。如需更强保护可自行接入 `EncryptedSharedPreferences`；
- 公开本仓库前已移除原作者的签名口令与本地 SDK 路径，请勿再把自己的密钥提交进来（`.gitignore` 已覆盖 `keystore.properties`、`*.jks`、`local.properties`）。

## 免责声明

本项目为**个人独立开发的非官方客户端**，与 Anthropic、OpenAI、DeepSeek 均无任何关联，也未获其授权或认可。界面风格仅作学习与本地使用参考，相关商标与品牌归各自权利人所有。请遵守所接入服务的使用条款。

## License

[MIT](LICENSE) © 2026 wenzhengshi
