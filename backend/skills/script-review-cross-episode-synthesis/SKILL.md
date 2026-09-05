---
name: script-review-cross-episode-synthesis
description: Use when aggregating complete deep-review unit candidates across an unchanged script snapshot.
---

# 跨单元综合审核

## 检查项

在完整候选上检查跨单元身份、时间线、场景、道具、视觉、情绪、因果、悬念、反转与伏笔的一致性和回收。
只综合当前冻结版本的候选、裁决和原文，不读取或参考任何历史审核问题。

## 合并规则

相同维度、根因和稳定 anchor 的候选去重；一个问题跨多处出现时合并为多命中。不同根因不得因措辞接近而合并。
正式问题只能来自 `CONFIRMED` 候选，并必须在 `sourceCandidateIds` 中保留全部来源候选标识；`NEEDS_HUMAN_REVIEW` 单独保留供人工处理，其他裁决不得进入正式问题。

## 证据要求

跨单元结论至少引用两个相关单元的精确引文或 anchor。只有单侧证据时标记不确定，不得制造另一侧事实。
先用 `read_review_unit_results` 读取全部已完成单元及其语义裁决；需要复核跨单元证据时，使用有界的
`read_review_content` 读取任一审核范围内正文，汇总阶段不绑定单个发现单元。

## 完成

只有全部单元成功且快照未变化时才能综合并保存正式结果。
