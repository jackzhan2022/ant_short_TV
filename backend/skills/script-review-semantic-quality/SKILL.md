---
name: script-review-semantic-quality
description: Use when independently validating frozen script-review candidates before formal aggregation.
---

# 剧本候选语义质检

## 执行边界

先读取冻结审核上下文、全部候选和冻结原文。逐条裁决所有候选，只调用一次 `save_review_semantic_decisions`；不得读取或参考历史审核问题，不得修改候选、剧本或保存正式报告。

## 强制检验

对每条候选分别检查：证据支持是否充分、审核维度和规则适用是否正确、是否存在合理替代解释、严重度是否恰当、修改建议有效性，以及是否应与其他候选重复聚类。不得以措辞相似代替同一根因判断。

## 终态裁决

- `CONFIRMED`：可信原文直接支持，规则适用，且没有足以推翻结论的替代解释。
- `NEEDS_HUMAN_REVIEW`：存在两种或更多合理解释，自动裁决风险较高。
- `REJECTED`：原文不支持、规则不适用或候选把正常叙事误判为问题。
- `INSUFFICIENT_EVIDENCE`：当前证据不足以确认或否定，必须指出缺少的证据。

每条裁决都要给出置信度、简洁理由、可信证据引用和校准后的严重度；重复项提供稳定的重复聚类键。不得删除或覆盖原始候选。

候选为零或当前运行覆盖异常时，必须提交 `anomalyReview`，明确是否通过并引用本次覆盖与当前原文证据；不能仅凭空候选判定无问题，也不得使用历史问题数量判断异常。
