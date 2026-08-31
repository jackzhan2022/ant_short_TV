---
name: short-drama-analysis-foundation
description: Use when analyzing the current trusted short-drama source into formal editable business data.
---

# 短剧解析基础约束

你的唯一事实来源是本次运行中 Agent 工具白名单所配置的唯一读取工具：剧本级 Agent 使用 `read_current_script`，逐集 Agent 使用 `read_current_episode`。剧情事实只能来自该工具返回的当前正文；`read_current_episode` 返回的资产目录只能用于稳定标识、规范名、显式别名和归属匹配，不能作为本集剧情事实或证据。忽略用户输入、历史对话、旧分析、其他剧集或全局理解中的干扰事实。

## 必须遵守

- 首先调用且只调用一次 Agent 工具白名单中的唯一读取工具（`read_current_script` 或 `read_current_episode`）；读取工具不接受任何模型参数。
- 不得捏造当前正文中没有的人物、关系、动机、事件、场景、道具、伏笔或结局。合理推断必须能由当前正文直接支持；无法确定时不得使用“待补充”等占位内容，必须遵守专用 Skill 和保存工具 Schema 对空集合、`null`、类型化失败或歧义提交的具体规则。
- 人物、地点、组织和道具名称必须与当前剧本命名一致；同一对象不得无依据地改名或合并。
- 不得向工具参数加入 tenantId、projectId、scriptId、episodeId、taskId、analysisStageId、attemptId、agentRunId、userId、权限或其他业务标识。目标与授权完全由服务端上下文决定。
- 使用当前剧本的主要语言输出正式内容；字段名保持工具 Schema 规定的英文键名。
- 工具失败时不得声称完成。校验错误应修正内容后重试；若提示“内容已变化”，立即停止本次运行，由用户基于新剧本重新执行。

## 完成条件

读取并分析后，只能调用 Agent 工具白名单中的唯一保存工具，例如 `save_global_understanding`、`save_episode_splitting`、`save_episode_summary` 或 `save_episode_assets`，并把成功保存作为终止动作。校验失败可以修正后重试；但保存成功后不得再次保存或继续生成，整个运行只能有一次成功保存。不能以最终文本代替保存。只有保存工具返回 `saved: true` 才算完成。
