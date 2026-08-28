# commercial-point-grants Specification

## Purpose
TBD - created by archiving change add-subscription-package-management. Update Purpose after archive.
## Requirements
### Requirement: Commercial grants use unified ledger
商业化订单和订阅发放的积分 SHALL 通过现有团队积分账户及追加式积分账本入账，并关联订单或权益发放记录。团队积分 SHALL 仅在已支付的积分包或订阅套餐权益发放时增加；系统 MUST NOT 提供团队 Owner 或其他普通 API 调用方手工增加积分的能力。

#### Scenario: Grant points after payment
- **WHEN** 订单支付确认且权益发放动作首次执行
- **THEN** 团队余额和积分账本同步增加，且记录订单关联信息

#### Scenario: Retry grant
- **WHEN** 同一权益发放任务被重复重试
- **THEN** 系统保持余额不变并返回既有发放结果

#### Scenario: Manual adjustment is unavailable
- **WHEN** 客户端请求团队积分手工调整接口
- **THEN** 接口不存在并返回 HTTP 404，团队积分余额不发生变化

#### Scenario: Query historical transactions
- **WHEN** 团队成员查询积分流水
- **THEN** 系统继续返回包含历史手工调整记录在内的已有账本记录

