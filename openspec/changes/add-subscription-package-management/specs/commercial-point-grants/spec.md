## ADDED Requirements

### Requirement: Commercial grants use unified ledger
商业化订单和订阅发放的积分 SHALL 通过现有团队积分账户及追加式积分账本入账，并关联订单或权益发放记录。

#### Scenario: Grant points after payment
- **WHEN** 订单支付确认且权益发放动作首次执行
- **THEN** 团队余额和积分账本同步增加，且记录订单关联信息

#### Scenario: Retry grant
- **WHEN** 同一权益发放任务被重复重试
- **THEN** 系统保持余额不变并返回既有发放结果
