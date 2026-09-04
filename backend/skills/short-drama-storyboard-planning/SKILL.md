---
name: short-drama-storyboard-planning
description: Use when planning one complete short-drama episode into formal multi-shot storyboard video units.
---

# 短剧整集分镜规划

严格按 `read_current_episode` 返回的当前集正文顺序规划整集，不读取或参考旧分镜。相邻集信息只用于承接上一集结尾和下一集开场，不得把相邻集正文扩写进当前集。

## 分镜边界

- 一个正式分镜是一次独立视频生成单元，包含多个按时间顺序排列的内部镜头。
- 每个分镜总时长必须为 10 至 15 秒；每个内部镜头必须为 1.5 至 4 秒，保留小数。
- 每个内部镜头只承载一个主要动作或一个明确情绪变化。无法在 4 秒内完成的连续事件必须拆镜。
- 相邻且空间连续的地点可以属于同一分镜；时间跳跃、远距离地点变化或明确戏剧段落变化必须新建分镜。
- 分镜从 `storyboardNo: 1` 连续编号；每个分镜内的 `shotNo` 都从 1 重新连续编号。

## 剧情与声音

全部有意义的正文必须用按顺序且不重叠的 `sourceStartMarker` 和 `sourceEndMarker` 完整覆盖，不得遗漏、重复、重排或改变结果。对白、旁白和内心 OS 必须保持原文，不能翻译、润色、改写或新增，并且每条声音内容只归属一个镜头。

可以补充可执行的灯光、构图、微表情、走位、运镜和环境细节；不得补充剧情事件、人物关系、对白、关键道具或新结果。项目设定和已绑定素材与补充描述冲突时，始终以前者为准。

完成全部读取后一次提交完整 `save_episode_storyboards`。保存成功前不得声称完成。
