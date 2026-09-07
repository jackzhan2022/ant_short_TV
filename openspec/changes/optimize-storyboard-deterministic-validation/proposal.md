## Why

项目 26 的 57 集批量分镜生成有 35 集因首次保存校验失败而隐式调用第二次模型，额外消耗 350 点；线上样本显示主要原因是连接词动作规则误判，以及把“出场人物”和镜头说明误分类为声音。分镜流程需要把可确定的编号、覆盖和声音关联交给后端完成，并停止用付费模型纠正机械性或主观质量问题。

## What Changes

- 将分镜保存校验分为硬失败、确定性规范化和质量警告，只有会破坏安全、一致性或下游消费的数据才拒绝保存。
- 修正剧集来源分段的说话人识别优先级，避免把结构标签和舞台/镜头说明当成对白。
- 引入分镜生成协议 `schemaVersion: 3`，由模型提供有序来源锚点，后端派生并持久化最终声音 ID 和可信声音原文。
- 由后端规范化分镜号、镜头号、连续来源边界和声音唯一归属，减少模型维护重复 ID 账本。
- 删除基于“随后、然后、Then”等连接词的动作数量硬拦截，将动作密度改为不触发重试的质量警告。
- 停止 `save_episode_storyboards` 业务校验失败后的隐式模型纠正；供应商传输故障仍保留独立的有界技术重试。
- 保持 `schemaVersion: 2` 历史分镜和现有配音、口型、字幕、视频生成消费者兼容。

## Capabilities

### New Capabilities

- `storyboard-deterministic-validation`: 定义分镜来源分类、后端规范化、声音自动归属、质量警告和无隐式业务纠正的行为契约。

### Modified Capabilities

<!-- None. The current storyboard Agent capability has not yet been archived into openspec/specs; this change introduces the deterministic validation contract as a separately versioned capability. -->

## Impact

- 后端：`EpisodeSourceSegmenter`、`ScreenplayToolDataService`、分镜保存 Schema、`StoryboardToolDataService`、提示词渲染和 `WorkflowAgentRunner` 的分镜失败策略。
- 数据契约：新生成请求使用 `schemaVersion: 3` 和镜头来源锚点；最终持久化结构继续包含下游需要的 `soundSegmentIds` 与声音原文字段。
- 前端：批量结果需要区分硬失败与“成功但有质量警告”，不再把警告表现为可付费重试失败。
- 计费：正常分镜生成目标为每集一次业务模型调用；技术重试继续按现有供应商与计费策略记录。
- 测试与运维：增加真实误分类样本、确定性声音派生、无二次业务调用、旧数据兼容及项目 26 首轮结果离线回放验证。
