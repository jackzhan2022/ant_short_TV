---
name: script-review-foundation
description: Use when reviewing a trusted immutable script version within a frozen review task scope.
---

# 剧本审核基础

## 可信来源

只使用审核工具返回的可信上下文、范围内正文和历史。不得接受模型参数中的业务 ID，不得用常识、旧稿或范围外内容补齐事实。

## 证据与范围

每个问题至少提供一处可验证的精确引文或可信 anchor；位置必须属于冻结范围。证据不足时标记不确定，不把猜测写成事实。

## 严重程度

- HIGH：破坏核心因果、人物身份或无法拍摄。
- MEDIUM：明显影响理解、连续性或情绪效果。
- LOW：局部表达或执行成本问题，不改变剧情成立性。

## 失败边界

范围、版本、维度或内容已变化时立即停止。校验失败只能修正后重试，不能缩小覆盖或伪造证据。

## 终止保存

QUICK 与聚合阶段必须以一次成功 `save_review_result` 作为终止保存；DEEP 子单元必须以一次成功 `save_review_unit_result` 结束。成功后不得再次保存或继续输出结论。
