---
name: script-review-execution-framework
description: Use when executing a frozen Markdown script-review task in a QUICK, DEEP child, or DEEP aggregation phase.
---

# 剧本审核执行框架

## MARKDOWN_QUICK

按顺序调用 `read_review_context` 与 `read_review_content`，分页读完完整冻结范围，审核当前版本的选中维度并返回完整 Markdown 报告。不得读取或参考历史审核问题，不得生成候选 JSON 或调用保存工具。必须声明实际覆盖；容量不足时停止，不得截断后冒充完整覆盖。

## MARKDOWN_DEEP_CHILD

按顺序调用 `read_review_context` 与 `read_review_content`，只读取当前冻结单元并检查本次授权维度，完成该单元覆盖后返回非空 Markdown 片段。保留精确引文与稳定位置，不得越过单元范围、生成候选 JSON、调用保存工具或生成最终报告。

## MARKDOWN_DEEP_AGGREGATION

接收服务端按冻结顺序提供的全部成功 Markdown 片段，执行当前版本内的跨单元去重、合并引用与连续性检查，直接返回完整 Markdown 报告。本阶段不调用工具，不得执行历史轮次比较。

## 完成规则

问题应按维度、根因和稳定位置去重；同一根因的多处证据合并并保留全部不同引用。未发现问题时也应说明实际覆盖与判断依据，不能返回空白报告。任何缺失覆盖、失败单元、陈旧哈希或输出截断均不得声称完成。服务端负责保存 Markdown 与更新任务状态。
