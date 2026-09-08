---
name: script-review-execution-framework
description: Use when executing Markdown QUICK, DEEP child, or DEEP aggregation phases of script review, or a legacy structured review phase.
---

# 剧本审核执行框架

## QUICK

读取 context 与完整范围 content，直接审核当前版本的选中维度并返回完整 Markdown 报告。不得读取或参考历史审核问题，不得生成候选 JSON 或调用保存工具。必须声明实际覆盖；容量不足时停止，不得截断后冒充完整覆盖。

## DEEP_CHILD

只读取当前冻结单元，完成该单元覆盖后返回非空 Markdown 片段。不得读取其他候选、生成候选 JSON、调用保存工具或生成正式报告。

## DEEP_SEMANTIC

读取冻结候选与必要原文，逐条完成语义裁决后调用 `save_review_semantic_decisions`。不得修改发现结果或生成正式报告。

## DEEP_AGGREGATION

接收服务端按冻结顺序提供的全部成功 Markdown 片段，执行当前版本内的跨单元去重、合并引用与连续性检查，直接返回完整 Markdown 报告。不得执行历史轮次比较或调用结果保存工具。

## 完成规则

问题应按维度、根因和稳定位置去重；同一根因的多处证据合并并保留全部不同引用。任何缺失覆盖、失败单元、陈旧哈希或输出截断均不得声称完成。`DEEP_SEMANTIC` 及结构化保存规则只适用于已冻结的历史结构化任务。
