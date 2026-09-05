---
name: script-review-execution-framework
description: Use when executing QUICK, DEEP child, semantic quality, or DEEP aggregation phases of script review.
---

# 剧本审核执行框架

## QUICK

读取 context 与完整范围 content，直接审核当前版本的选中维度并调用 `save_review_result`。不得读取或参考历史审核问题。必须声明实际覆盖；容量不足时停止，不得截断后冒充完整覆盖。

## DEEP_CHILD

只读取当前冻结单元，完成该单元覆盖后调用 `save_review_unit_result`。不得读取其他候选或生成正式报告。

## DEEP_SEMANTIC

读取冻结候选与必要原文，逐条完成语义裁决后调用 `save_review_semantic_decisions`。不得修改发现结果或生成正式报告。

## DEEP_AGGREGATION

读取完整 unit results，执行当前版本内的跨单元去重、合并多命中与连续性检查，最后调用 `save_review_result`。不得执行历史轮次比较。

## 完成规则

问题应按稳定 anchor 和维度去重；同一根因的多处证据合为多命中问题。任何缺失覆盖、失败单元、陈旧哈希或保存失败均不得声称完成。
