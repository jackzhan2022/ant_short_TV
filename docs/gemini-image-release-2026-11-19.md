# Gemini 图片供应商上线记录

- 日期：2026-11-19（Asia/Shanghai）。
- 供应商：复用已有 Gemini，ID 2；Base URL 为 `http://89.58.47.162:8080`。
- 模型：显示名称 `Gemini-image`，上游调用 ID `gemini-image`（区分大小写），
  ID 20，类型 IMAGE，启用 IMAGE_GENERATION。
- API Key：由用户在后台填写；维护过程未写入、清空或输出密钥。
- 定价：复制线上 `gpt-image-2`（ID 8）的当前有效成本价和积分价全部组件。
  输出图片每张 25.2 积分、成本 0.60 元；额外参考图每张 0.84 积分、成本 0.02 元；CALL 为零。
- 默认选择：不设置为默认，sort=-1，保留原先图片模型回退顺序。

Gemini 图片请求改为原生 `/v1beta/models/{model}:generateContent`，使用
`x-goog-api-key`、`contents.parts` 和 `generationConfig`；支持 Base64 与 HTTP
参考图，解析返回的 inlineData 图片并交给现有图片存储链路。

线上发布基于原始运行 JAR，只替换 `GeminiAdapter.class`。已逐项确认其他
JAR 条目内容一致，未发布工作区中其他未完成的变更。

- 原版本：`/opt/antv/releases/20261118-3be1e09f-free-creation-auto-title`。
- 新版本：`/opt/antv/releases/20261119151055-gemini-native`。
- 新 JAR SHA-256：`ed4ce4309f80e479304b87ae7980f980602d497160cd14605da18de78024e492`。
- 私有配置备份：`/opt/antv/backups/20261119151055-gemini-provider/before.private.json`。
- 发布回执：`/tmp/antv-gemini-20261119-native-final/activation-receipt.json`。

验证：Gemini 2 项测试和现有 OpenAI 21 项测试全部通过；候选 JAR 的原生协议
离线探针通过；线上成本价和积分价组件与来源逐项一致；服务 active，公网首页
HTTP 200，未登录的 currentUser 接口 HTTP 401。未进行真实付费出图验证。

维护脚本和回退说明见 `scripts/gemini-provider/README.md`。回退旧应用前，先停用
新 Gemini 模型，避免旧适配器按 OpenAI 协议调用它；保留模型及其价格记录用于
历史审计，不删除用户填写的密钥或覆盖后续配置。

## 上游模型 ID 大小写修正

15:13:38 创建的任务 182837 失败，调用日志 15152 为 HTTP 404。通过该供应商
配置的密钥读取 `/v1beta/models`，确认上游提供的是 `models/gemini-image`，而非
`models/Gemini-image`。使用错误大小写请求返回“Model is not supported by any
configured account in this group”；改为小写后，同一空请求返回正常的参数校验
400“contents is empty”。已仅修改模型 20 的 model_code 为 `gemini-image`，显示
名称、积分价格、密钥和原有模型选择保持不变。本次配置修正不需要重启。

修正后使用同一供应商密钥及原生请求格式进行单张简单图形验证，上游返回
HTTP 200，耗时 35.2 秒，包含 1 张 JPEG（40,639 字节），响应 ID
`da1b48a7d4118107`。该调用直接验证上游，不重跑用户任务、不修改用户任务状态
或平台积分账本。原任务可由用户重新提交。
