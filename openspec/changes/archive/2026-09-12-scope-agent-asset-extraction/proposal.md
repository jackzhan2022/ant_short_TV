## Why

资产设定页的“批量生成”仍调用旧的原始 JSON 提取链路。该链路与当前归一化 Schema 的字段约束不一致，合理的模型输出会被整批拒绝，且与剧本页正式资产识别 Agent 产生两套行为。资产设定页需要复用正式 Agent，同时让用户在可能影响已有资产或提示词时明确确认。

## What Changes

- 新增资产设定页的作用域重提取工作流，支持 `ALL`、`CHARACTER`、`SCENE`、`PROP`。
- 在提交前预检选定作用域内的既有资产、视觉形态和非空提示词；无既有内容时直接开始，有既有内容时展示确认及提示词策略。
- 为正式资产识别 Agent、写入工具、绑定替换和收口逻辑传递作用域，确保单类型重提取不会写入、退役或解绑其他类型。
- 让作用域任务在正式资产写入后按用户确认的策略生成提示词：默认仅补空值，显式确认后可重新生成选定范围的已有提示词。
- 将资产设定页从旧的 `ai-extract-elements` 原始 JSON/候选归一化链路迁移出去，并展示异步任务的具体失败原因。

## Capabilities

### New Capabilities
- `scoped-asset-reextraction`: 从资产设定页预检、确认并提交按资产类型作用域执行的正式资产重提取工作流。

### Modified Capabilities
- `short-drama-asset-recognition-agent`: 资产识别 Agent、正式写入和完成收口支持 `ALL` 或单资产类型作用域。
- `asset-prompt-lifecycle`: 作用域重提取在用户确认的提示词策略下补全或重生成对应资产提示词。
- `script-element-workflow-boundaries`: 资产设定页不再使用旧的元素提取/候选归一化链路。

## Impact

- Backend: 资产识别 Agent 输入、`save_episode_assets` 工具契约、按剧集持久化、收口服务、异步执行任务与项目脚本工作流 API。
- Frontend: 资产设定页的批量生成入口、预检确认弹窗、任务状态和失败信息展示。
- Tests: Agent 作用域隔离、提示词策略、预检分支、任务失败可见性，以及旧入口不再由资产设定页调用。
