## ADDED Requirements

### Requirement: Apply frozen team discount
模型计费管理 SHALL 为 AI 执行提供团队有效会员折扣快照；该折扣 MUST 在模型积分价解析后、积分预占前应用，并与执行版本一同保存。

#### Scenario: Freeze discount before reservation
- **WHEN** 有效会员团队创建已完成模型计费解析的 AI 执行
- **THEN** 系统保存折扣来源和折后积分后再创建积分预占

#### Scenario: No active subscription
- **WHEN** 团队没有有效会员
- **THEN** 系统按模型积分价原价计费且不写入会员折扣
