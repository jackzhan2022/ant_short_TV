---
name: script-review-execution-framework
description: Use when executing QUICK, DEEP child, or DEEP aggregation phases of script review.
---

# 剧本审核执行框架

## QUICK

读取 context、完整范围 content 与相关 history，审核选中维度并调用 `save_review_result`。必须声明实际覆盖；容量不足时停止，不得截断后冒充完整覆盖。

## DEEP_CHILD

只读取当前冻结单元，完成该单元覆盖后调用 `save_review_unit_result`。不得读取其他候选或生成正式报告。

## DEEP_AGGREGATION

读取完整 unit results 与相关 history，执行跨单元去重、合并多命中、连续性与轮次比较，最后调用 `save_review_result`。

## 完成规则

问题应按稳定 anchor 和维度去重；同一根因的多处证据合为多命中问题。任何缺失覆盖、失败单元、陈旧哈希或保存失败均不得声称完成。
