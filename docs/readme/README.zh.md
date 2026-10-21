# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · **中文** · [日本語](README.ja.md)

**运行在你自己服务器上的 Telegram 私聊 AI 自动回复助手。**

当你忙碌时，Zamolvi 会替你回复联系人：收到新消息后交给你选择的 AI，并以你的名义发送回复——只在你选定的私聊中。通过 Android 应用控制，实际工作在你的 VPS 上运行，因此可以关闭应用、锁定手机。

## 功能

| 栏目 | 作用 |
|---|---|
| 1. VPS 连接 | 通过 SSH 在 VPS 上安装和更新服务器，配置 HTTPS |
| 2. Telegram | 通过验证码或二维码登录账号，支持云密码。另有**通知回复**——无需在服务器上登录 Telegram |
| 3. AI 与文本 | 服务商、模型、密钥、指令和回复格式，测试。另有**交流风格**：友善、毒舌、街头混混、学霸、海盗、电脑等 |
| 4. 私聊 | 自动回复哪些联系人 |
| 5. 自动回复 | 开启和停止、状态、免打扰时段、等待连续消息 |
| 6. 服务器日志 | 事件与诊断 |
| 7. 工作原理 | 关于应用和作者 |
| 8. 统计 | 按天统计回复、语音消息和错误 |
| 9. 应用主题 | 浅色、深色或跟随系统。另有**界面语言** |
| 10. 已删除消息 | 所选聊天中被删除消息的存档（最长 30 天），可搜索 |
| 11. 联系人记忆 | 每位联系人的摘要：姓名、兴趣、约定、重要事实；“忘记”按钮；按你的指令载入聊天记录 |
| 12. 照片和文档 | 识别照片文字并描述；读取 PDF、DOCX、TXT、CSV、JSON |
| 13. 回复审批 | 草稿：发送前可发送、修改或拒绝 |
| 14. 详细统计 | AI 费用、按模型和聊天的支出、回复时间、错误 |
| 15. 功能检查 | 哪些正常、哪些不正常以及原因，附修复按钮 |

此外：语音消息转写（通过 Groq 的 Whisper）、桌面小组件、常驻通知和快捷设置磁贴、联系人可用 `/ai_off` 命令关闭自动回复、免打扰时段，以及对多条消息的合并回复。

## 要求

- Android 9 或更高版本。
- 一台装有 Debian 12 或 Ubuntu 24.04、具有公网 IPv4 和 root SSH 访问权限的 VPS。
- 在 [my.telegram.org](https://my.telegram.org) 获取你自己的 Telegram `api_id` 和 `api_hash`（通知回复模式不需要）。
- 一个 AI API 密钥：**Groq**（有免费额度）、**Google Gemini**、**GigaChat** 或任何 **OpenAI 兼容 API**（例如 OpenRouter）。

## 快速开始

1. 从 **Releases** 下载 APK 并安装。
2. **1. VPS 连接**：输入地址和 root 密码，点击“从应用安装服务器”。
3. **2. Telegram**：输入 `api_id`、`api_hash` 和手机号，用验证码或二维码登录；或打开“通知回复”，无需登录即可使用。
4. **3. AI 与文本**：选择服务商、密钥和模型，填写指令并点击“测试”。可选择一种风格。
5. **4. 私聊**：勾选联系人。
6. **5. 自动回复**：开启。

如果某项功能不工作，请打开 **15. 功能检查**，那里会显示原因和解决办法。

## 隐私与负责任的使用

- 消息只会发送给你选择的 AI 服务商，且只来自你选定的聊天。详情：[PRIVACY.md](../../PRIVACY.md)、[SECURITY.md](../../SECURITY.md)。
- 请告知联系人可能由 AI 回复，未经同意不要载入他们的聊天记录。
- 遵守 [Telegram API 条款](https://core.telegram.org/api/terms)：不发垃圾消息，不群发。

构建、仓库结构和翻译：请参阅[英文 README](../../README.md)。

## 作者与许可证

**@stytam** — [t.me/stytam](https://t.me/stytam)。许可证 [MIT](../../LICENSE)；第三方组件：[THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)。
