---
name: short-drama-global-understanding-framework
description: Use when producing the version-one global-understanding document for a short-drama script.
---

# 剧情全局理解框架

本 Skill 只负责整部剧本的宏观叙事理解，为后续剧集拆分和概要提炼提供统一语境。不要在此生成剧集列表，也不要识别或保存正式的角色、角色变装、场景、道具资产；这些属于后续 Agent。

## schemaVersion 1

传给 `save_global_understanding` 的顶层对象只能包含 `schemaVersion: 1` 与 `content`。`content` 必须完整包含：

- `logline`：一句话概括主人公、目标、阻力与核心悬念。
- `synopsis`：按因果链说明故事起因、发展、高潮和结局。
- `genres`：剧本直接支持的题材类型数组。
- `themes`：作品实际呈现的主题数组，不把情节标签当主题。
- `worldSetting`：时代、地点、社会规则和必要背景。
- `coreConflict`：贯穿全剧的核心冲突及对立力量。
- `relationships`：人物关系数组；每项包含 `characterA`、`characterB`、`relationship`、`description`。
- `turningPoints`：关键转折数组；每项包含从 1 开始的 `sequence`、`title`、`description`、`impact`，顺序与剧本一致。
- `ending`：当前剧本已写明的结局状态；开放结局应如实说明。
- `endingHook`：结尾留下的悬念、承诺或续作钩子；没有时用保守的空字符串，不得编造。
- `narrativeStyle`：节奏、视角、结构与情绪风格。
- `targetAudience`：从题材、冲突和表达方式推导的核心受众，避免无依据的精细画像。

## 职责边界

可以提及理解全局剧情所必需的角色关系和关键地点，但不得代替角色、场景、道具识别产物，不得提前决定剧集拆分结果。所有描述都应服务于后续剧集拆分，而不是扩写原剧本。

## 保存前检查

确认所有必填字段存在且类型正确，字符串不是占位语；专有名词与当前剧本一致；关系双方确实出现；转折顺序、因果和结局可由原文支持；无旧稿信息、无模型自行补写、无业务标识。检查通过后调用一次保存工具。

保存参数必须是可一次完整提交的完整 JSON。表达保持精简：同一事实不得重复，关系只保留影响主线的关键人物关系，转折只保留改变主线方向的关键节点；不要在多个字段重复复述整段剧情。若剧本很长，优先缩短描述而不是遗漏字段或输出不完整 JSON。
