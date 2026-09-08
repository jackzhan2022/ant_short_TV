---
name: script-review-cross-episode-synthesis
description: Use when aggregating complete deep-review Markdown fragments across an unchanged script snapshot.
---

# 跨单元综合审核

## 检查项

在完整 Markdown 片段上检查跨单元身份、时间线、场景、道具、视觉、情绪、因果、悬念、反转与伏笔的一致性和回收。
只综合当前冻结版本按顺序提供的成功片段，不读取或参考任何历史审核问题。

## 合并规则

相同维度、根因和稳定位置的发现去重；一个问题跨多处出现时合并并保留全部不同引用。相邻重叠单元产生的同一发现只出现一次。不同根因不得因措辞接近而合并。

## 证据要求

跨单元结论至少引用两个相关单元的精确引文或 anchor。只有单侧证据时标记不确定，不得制造另一侧事实。
服务端在汇总输入中提供全部已完成片段及其单元标签。不得调用候选、语义裁决或结果保存工具。

## 完成

只有全部单元成功且快照未变化时才能返回非空的最终 Markdown 报告。
