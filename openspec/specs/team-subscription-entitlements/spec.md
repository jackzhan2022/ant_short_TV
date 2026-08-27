# team-subscription-entitlements Specification

## Purpose
TBD - created by archiving change add-subscription-package-management. Update Purpose after archive.
## Requirements
### Requirement: Team subscription lifecycle
系统 SHALL 将会员归属团队；同一团队同一时间 MUST 只有一个有效订阅。同档续费从当前到期日顺延，其他档位在当前订阅结束后排队生效。

#### Scenario: First activation
- **WHEN** 首次会员订单支付成功
- **THEN** 订阅立即生效并发放首期周期积分

#### Scenario: Queued package
- **WHEN** 有效订阅期间购买其他会员档位
- **THEN** 新订单保持待生效并记录当前订阅到期后的生效时间

### Requirement: Permanent points and periodic grants
积分包积分和已发放会员积分 SHALL 永久有效。周期积分 SHALL 按订阅生效日逐月发放，并以订阅 ID 与周期序号保证幂等。

#### Scenario: Monthly grant
- **WHEN** 到达订阅生效日的后续月度周期
- **THEN** 系统向团队积分账户记入该周期积分且只记入一次

### Requirement: Global discount snapshot
有效会员的全局算力折扣 SHALL 对团队所有模型积分价生效。AI 执行创建时 MUST 冻结原始积分、折扣率和折后积分，折后积分保留 8 位小数四舍五入。

#### Scenario: Discounted execution
- **WHEN** 团队有有效折扣会员并创建 AI 执行
- **THEN** 预占和后续结算使用冻结后的折后积分

#### Scenario: Expired membership
- **WHEN** 会员在新执行创建前已到期
- **THEN** 新执行不享受折扣，但历史执行继续使用其已冻结快照

