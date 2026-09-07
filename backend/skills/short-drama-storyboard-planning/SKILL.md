---
name: short-drama-storyboard-planning
description: Use when planning one complete short-drama episode into formal multi-shot storyboard video units.
---

# 短剧整集分镜规划

服务端已准备并审计当前剧集、相邻集概要、剧本分析、项目上下文和正式素材这一个完整上下文。严格按其中当前集的 `sourceSegments` 顺序规划整集，不自行调用读取工具，不读取或参考旧分镜。相邻集信息只用于承接上一集结尾和下一集开场，不得把相邻集正文扩写进当前集。

## 分镜边界

- 一个正式分镜是一次独立视频生成单元，包含多个按时间顺序排列的内部镜头。
- 每个分镜总时长必须为 10 至 15 秒；每个内部镜头必须为 1.5 至 4 秒，保留小数。
- 每个内部镜头以一个主要动作或一个明确情绪变化为中心。无法在 4 秒内完成的连续事件应拆镜；动作密度只会形成质量告警，不应为规避校验而删改剧情。
- 相邻且空间连续的地点可以属于同一分镜；时间跳跃、远距离地点变化或明确戏剧段落变化必须新建分镜。
- `storyboardNo` 和每个分镜内的 `shotNo` 按数组顺序填写即可，后端会规范化为从 1 开始的连续编号。

## 剧情与声音

保存时固定提交 `schemaVersion: 3`。根对象只放 `schemaVersion`、`episodeFingerprint` 和 `storyboards`。每个分镜提交创意终点 `sourceTo`；后端按数组顺序推导 `sourceFrom`、连续编号与最后一段的完整覆盖。未知、越界或顺序颠倒的创意终点仍会硬失败。

内部镜头可提交非递减的 `sourceAnchor`，用于表达镜头在来源顺序中的落点；省略时后端会按镜头顺序和时长权重补齐。不要提交或枚举 `soundSegmentIds`，后端会把 `DIALOGUE`、`NARRATION` 和 `INNER_OS` 各归属一次，并注入未经改写的可信原文。

将人物可执行动作写入 `action`，表演细节写入 `performance`，情绪状态写入 `emotion`，运镜与构图写入 `camera`，站位写入 `positioning`。可以补充灯光和环境细节；不得补充剧情事件、人物关系、对白、关键道具或新结果。项目设定和已绑定素材与补充描述冲突时，始终以前者为准。

基于已准备的一个上下文直接规划，并一次提交完整 `save_episode_storyboards`。保存成功前不得声称完成。
