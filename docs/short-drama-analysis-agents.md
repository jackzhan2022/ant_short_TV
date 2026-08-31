# 短剧剧本四阶段 Agent

剧本分析依次执行剧情全局理解、剧集智能拆分、逐集概要提炼、逐集角色场景道具识别。三个新适配器默认关闭，旧流程继续可用；开启后，Agent 的终态保存工具直接覆盖剧本页正式可编辑数据，不经过候选确认。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | ---: | --- |
| `AI_WORKFLOW_EPISODE_SPLITTING_ENABLED` | `false` | 开启剧集智能拆分 Agent |
| `AI_WORKFLOW_EPISODE_SUMMARY_ENABLED` | `false` | 开启逐集概要 Agent |
| `AI_WORKFLOW_ASSET_RECOGNITION_ENABLED` | `false` | 开启逐集资产识别 Agent |
| `AI_WORKFLOW_FANOUT_CONCURRENCY` | `3` | 单阶段逐集并发数，服务端限制为 1–16 |
| `AI_WORKFLOW_SPLIT_SAFE_CONTEXT_TOKENS` | `800000` | 全文请求的安全预算；估算值含提示词和工具输出预留 |
| `AI_WORKFLOW_SPLIT_PROMPT_RESERVE_TOKENS` | `12000` | 全文拆分提示词预留 |
| `AI_WORKFLOW_SPLIT_TOOL_RESERVE_TOKENS` | `24000` | 终态保存工具输出预留 |
| `AI_WORKFLOW_SPLIT_CHUNK_TARGET_MIN` | `15000` | 回退分块目标下限（字符） |
| `AI_WORKFLOW_SPLIT_CHUNK_TARGET_MAX` | `20000` | 回退分块目标上限（字符） |
| `AI_WORKFLOW_SPLIT_CHUNK_HARD_MAX` | `24000` | 单块硬上限（字符） |
| `AI_WORKFLOW_SPLIT_CHUNK_OVERLAP` | `1500` | 相邻块重叠字符数 |
| `AI_WORKFLOW_SPLIT_CHUNK_CONCURRENCY` | `3` | 分块模型调用并发数 |

逐集阶段持久化快照和单集状态。默认重试只运行失败、缺失或过期单元；剧集集合或正文指纹变化会拒绝旧快照。拆分、概要和资产识别也有独立运行入口。

## 剧集拆分调用路径

正常路径优先把当前完整剧本交给模型，调用顺序为 `read_current_script -> save_episode_splitting`。DeepSeek 兼容模型会显式发送 `thinking.type=disabled`；提示词要求只返回标题和可校验的原文边界标记，不复述正文、不输出推理过程。正式剧集正文由服务端依据标记从可信剧本中精确截取。

只有以下容量或不完整调用问题会在同一个用户 Run 内自动切换一次分块路径：预检超过安全预算、供应商返回上下文长度错误、`finish_reason=length`、空响应，或未调用必需的保存工具。校验错误、原文已变化、边界重叠或缺口不会触发回退。

回退路径为 `read_script_structure -> analyze_script_chunks -> save_episode_splitting`。服务端优先按显式集标题、场景标题、段落和换行生成持久化重叠分块；这些只是分析输入，不会被当作正式分集边界，也不要求原文出现“第 N 集”。分块候选转换为全文绝对位置、去重并完成覆盖校验后，仍只执行一次正式保存。

`script_split_snapshot` 保存当前 Run 的 `FULL` 或 `CHUNK_FALLBACK` 模式、回退原因、剧本哈希和总体进度；`script_split_chunk` 保存每块范围、状态、候选和 AI 调用引用。失败重试只重跑失败块，成功块直接复用；剧本正文变化会把旧快照标为过期。剧本页展示回退原因以及完成块数、总块数和失败块数。

每次模型调用都写入既有 AI 调用日志、token 用量和计费流水；全文失败调用也保留审计，但其生成文本不会进入回退上下文。正常路径通常一次边界分析调用，回退路径为每个待处理分块一次调用，最终保存工具本身不产生额外模型调用。

## 正式数据归属

- `script_episode`：当前正式剧集正文和稳定 ID。
- `script_episode_summary`：正式概要、亮点和结尾钩子，并兼容镜像 `script_episode.summary`。
- `character_asset`、`scene_asset`、`prop_asset`：与剧本关联的正式身份。
- `asset_visual_variant`：角色变装、场景默认视觉形态、道具不同状态。
- `asset_visual_variant_episode`：形态与剧集的绑定；场景时间和氛围是绑定使用元数据。
- `script_episode_asset_analysis`：每集资产识别的正式覆盖和来源指纹。
- `script_analysis_fanout_snapshot`、`script_analysis_fanout_unit`：逐集进度、失败与子 Run 引用。

旧资产提取、候选归一化、合并和确认接口保留给旧调用方，但不阻塞新 Agent 的正式结果。

## 上线与回滚

先部署数据库迁移和保持关闭的应用版本，再按“拆分 → 概要 → 资产识别”逐项开启并做非生产冒烟。拆分阶段先观察全文路径的上下文占用、输出截断率和保存成功率，再用较低的 `AI_WORKFLOW_SPLIT_SAFE_CONTEXT_TOKENS` 验证分块路径、失败块重试、恢复进度和唯一正式保存。监控 `SCRIPT_CONTENT_CHANGED`、`EPISODE_CONTENT_CHANGED`、`ANALYSIS_EPISODE_SNAPSHOT_CHANGED`、`ENTITY_MATCH_AMBIGUOUS`、`ANALYSIS_AGENT_INCOMPLETE`、分块失败数及模型超时。

回滚时依次关闭三个开关即可恢复旧执行路径；新增正式表和列保持不删除，避免破坏已经生成或用户编辑的数据。分块异常时可先降低 `AI_WORKFLOW_SPLIT_CHUNK_CONCURRENCY`，逐集并发过高时降低 `AI_WORKFLOW_FANOUT_CONCURRENCY`，均不需要回退迁移。需要临时阻止全文调用时，下调安全预算即可让预检直接进入分块路径。
