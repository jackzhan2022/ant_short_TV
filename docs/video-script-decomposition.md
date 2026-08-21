# 视频拆剧接入说明

## 功能入口

视频拆剧是独立一级菜单，路由为 `/video-script-decomposition`。用户在该页面选择项目、上传视频并创建批次；每个上传视频固定对应一集，后端按提交顺序生成 `episodeNo`。

## 阿里百炼 Qwen 配置

平台 AI 模型管理中需要启用阿里云百炼 Provider，并配置：

- `VIDEO_UNDERSTANDING` 模型：`qwen3.7-plus`
- `TEXT` 模型：用于根据结构化拆解结果生成剧本草稿
- Provider Base URL：百炼兼容 OpenAI Chat Completions 的 `/v1` 地址
- API Key：保存在平台 Provider 配置中，调用日志不会记录明文密钥

视频理解阶段的业务场景写入 `ai_call_log.business_scene = video_understanding`。草稿生成阶段写入 `video_script_draft`，两阶段会分别记录调用日志。

## 视频 URL 要求

上传文件保存在项目素材路径 `/materials/{tenantId}/{projectId}/...` 下。执行拆剧时，系统会把对象存储路径解析为模型可访问 URL：

- 对象存储开启时使用受控公开 URL 或签名 URL。
- 本地存储调试时需要配置 `app.public-base-url`，否则模型无法访问本地文件。
- URL 不应暴露对象存储凭据。

如果无法生成可访问 URL，单集会失败，错误信息会展示在单集详情中，可重试该单集。

## 视频限制

- 支持格式：`mp4`、`mov`、`avi`
- 单文件大小：最大 1GB
- 单集时长：最大 30 分钟
- 批次大小：最多 50 个视频

## 状态含义

- `PENDING_ANALYSIS`：等待视频理解。
- `ANALYZING`：正在调用 Qwen 解析视频。
- `ANALYSIS_SUCCEEDED`：结构化解析已成功，等待或准备生成草稿。
- `DRAFT_GENERATING`：正在根据解析结果生成剧本草稿。
- `PENDING_REVIEW`：草稿已生成，等待用户审核确认。
- `CONFIRMED`：用户已确认并创建 `VIDEO_IMPORT` 剧本版本。
- `FAILED`：当前阶段失败，可查看错误信息并按阶段重试。

批次状态会根据单集状态聚合：存在失败时为 `PARTIAL_FAILED`，全部待审核或确认后进入审核/完成态。

## 排查解析失败

1. 查看单集详情中的错误信息、结构化解析和原始响应。
2. 到 AI 调用日志按 `task_id = episodeId` 和业务场景筛选真实调用。
3. 如果 HTTP 成功但 JSON 字段缺失，日志会标记业务解析失败，不会当作拆剧成功。
4. 如果是 `AI_RATE_LIMIT`、`AI_PROVIDER_TIMEOUT` 或 `AI_PROVIDER_ERROR`，确认百炼额度、限流、Base URL、视频 URL 可访问性后重试。
5. 确认导入出现 `SCRIPT_VERSION_CONFLICT` 时，刷新单集详情后重新确认，草稿内容会保留。
