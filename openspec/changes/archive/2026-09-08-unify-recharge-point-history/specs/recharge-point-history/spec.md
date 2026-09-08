## ADDED Requirements

### Requirement: Recharge center is the team point information entry
充值中心 SHALL 展示当前团队积分余额和基于统一积分账本的积分明细，并 MUST NOT 将商业权益发放记录与积分流水作为两套数据拼接展示。

#### Scenario: Open recharge center with point history
- **WHEN** 已选择团队的成员打开充值中心
- **THEN** 页面显示团队当前积分余额，并从团队积分流水接口加载唯一一份积分明细

#### Scenario: Commercial entitlement appears once
- **WHEN** 一次商业权益积分发放已经写入权益记录和积分账本
- **THEN** 充值中心仅通过积分账本显示一条对应的权益发放明细

### Requirement: Point history exposes understandable change categories
充值中心 SHALL 根据积分流水的 `transactionType` 展示变化类型，并 SHALL 展示变动积分、变动后余额、说明和时间。积分流水接口 MUST 以负数返回首次预扣、追加预扣和消耗，以正数返回发放、释放和退款。未知变化类型 MUST 回退显示接口原始值。

#### Scenario: Display known ledger changes
- **WHEN** 积分流水包含权益发放、手动增加、首次预扣、追加预扣、消耗、释放或退款类型
- **THEN** 页面为每条记录显示对应的中文变化类型及其变动值、余额、说明和时间

#### Scenario: Display an unknown ledger change
- **WHEN** 积分流水返回前端尚未配置文案的新类型
- **THEN** 页面显示该类型的原始值而不是隐藏或错误归类该记录

#### Scenario: Display debit and credit directions
- **WHEN** 积分流水同时包含首次预扣、追加预扣、消耗、发放、释放或退款记录
- **THEN** 首次预扣、追加预扣和消耗显示为负数，发放、释放和退款显示为正数

### Requirement: Team settings excludes point information
团队设置页 MUST NOT 展示团队积分汇总或积分明细，并 MUST NOT 请求团队积分账户或积分流水。

#### Scenario: Open team settings
- **WHEN** 已选择团队的成员打开团队设置页
- **THEN** 页面加载团队设置所需数据且不加载或显示任何积分信息
